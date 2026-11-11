package dev.droidtop.plugins.mmrl

import android.util.Base64
import dev.droidtop.pluginhost.PluginContext
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Root commands through droidtop's enhanced privileges provider (`priv.shell` at level root, Droidtop/droidtop
 * docs/plugin-api.md 2.4 and 2.7), never `su` from this plugin's own process (Droidtop/tracker#892). droidtop's broker
 * checks this plugin's `priv.shell.root` grant on every call, so the person's choice on the approval screen is the
 * gate, and turning it off stops every command here.
 *
 * Every command is an argument list the provider starts directly, with no shell between, so nothing a module folder is
 * called can become a command.
 */
class PrivilegedShell(private val context: PluginContext) {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok get() = exitCode == 0
    }

    /** One quick command (`exec`): the provider caps it at its quick-op deadline and each stream at 64 KiB. */
    fun exec(vararg argv: String): Result {
        val reply = call("exec", JSONObject().put("argv", JSONArray(argv.toList())))
            ?: return Result(-1, "", lastError)
        return Result(reply.optInt("exit", -1), reply.optString("stdout"), reply.optString("stderr"))
    }

    /**
     * A command that can outlive one quick call (a module install), through a stream session (`exec_stream`, then
     * `stream_read` until it exits), stopped after [timeoutMs].
     */
    fun execLong(argv: List<String>, timeoutMs: Long = INSTALL_TIMEOUT_MS): Result {
        val session = UUID.randomUUID().toString()
        call("exec_stream", JSONObject().put("argv", JSONArray(argv)).put("session", session))
            ?: return Result(-1, "", lastError)
        val out = StringBuilder()
        val err = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = call("stream_read", JSONObject().put("session", session).put("waitMs", 5_000).put("maxBytes", 65_536))
                ?: return Result(-1, out.toString(), err.append(lastError).toString())
            out.append(decode(read.optString("stdout")))
            err.append(decode(read.optString("stderr")))
            if (read.optBoolean("exited")) return Result(read.optInt("exit", -1), out.toString(), err.toString())
        }
        call("stream_kill", JSONObject().put("session", session))
        return Result(-1, out.toString(), err.append("It took too long and was stopped").toString())
    }

    /** Whether root can be used at all: a provider is there, at root level, and the person allowed it for this plugin. */
    fun available(): Boolean = exec("id").ok

    @Volatile
    private var lastError = ""

    /** The reply's data, or null with [lastError] set to what droidtop said (not allowed, no provider, a timeout). */
    private fun call(op: String, args: JSONObject): JSONObject? {
        val reply = runCatching { JSONObject(context.call(API, 1, op, args.toString())) }.getOrNull()
        if (reply != null && reply.optBoolean("ok")) return reply.optJSONObject("data") ?: JSONObject()
        lastError = reply?.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Root is not available" }
        return null
    }

    private fun decode(base64: String): String =
        if (base64.isEmpty()) "" else String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8)

    companion object {
        const val API = "priv.shell"

        /** A module install's longest run: the root managers' own installers finish well inside this. */
        const val INSTALL_TIMEOUT_MS = 5 * 60_000L
    }
}
