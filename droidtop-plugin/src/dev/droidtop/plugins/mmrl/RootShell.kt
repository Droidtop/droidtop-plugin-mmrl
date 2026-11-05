package dev.droidtop.plugins.mmrl

/**
 * Minimal root shell access, one `su -c` invocation per command. Unlike
 * MMRL's own upstream (a persistent `libsu` `RootService` bound from the
 * app's own manifest-declared service, `platform/service/ServiceManager.kt`),
 * a droidtop plugin has no manifest component of its own to bind a
 * persistent root service to -- see this repo's PLUGIN-PLAN.md's "Reuse
 * as a native bundle" section: `platform`/`compat`'s real value here is
 * the root-provider file paths and module-state semantics
 * (`BaseModuleManager`/`ModId`), not their `IServiceManager`/`RootService`
 * transport, which assumes a full standalone app. Each call pays `su`'s
 * own per-invocation cost, which is fine for the request/response and
 * occasional-job shape a droidtop plugin call actually has -- this is
 * not a hot path.
 */
object RootShell {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok get() = exitCode == 0
    }

    fun exec(command: String): Result = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        Result(code, out, err)
    } catch (e: Exception) {
        Result(-1, "", e.message ?: "su exec failed")
    }
}
