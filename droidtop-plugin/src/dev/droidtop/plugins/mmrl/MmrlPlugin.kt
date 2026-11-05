package dev.droidtop.plugins.mmrl

import dev.droidtop.pluginhost.DroidtopPlugin
import dev.droidtop.pluginhost.PluginArgs
import dev.droidtop.pluginhost.PluginCall
import dev.droidtop.pluginhost.PluginCapability
import dev.droidtop.pluginhost.PluginContext
import dev.droidtop.pluginhost.PluginErrorCode
import dev.droidtop.pluginhost.PluginJobProgress
import dev.droidtop.pluginhost.PluginReply
import dev.droidtop.pluginhost.PluginResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * MMRL (github.com/MMRLApp/MMRL, GPL-3.0) as a droidtop plugin, per this repo's PLUGIN-PLAN.md: the Magisk, KernelSU
 * or APatch modules on this device, switched, removed, checked for updates and installed from a link. droidtop has
 * no business rendering a module's own WebUI or a repo browser (the plan's scope note).
 *
 * Facets only (Droidtop/droidtop docs/plugin-api.md 1.10, "Facets first"; Droidtop/tracker#602): droidtop draws the
 * settings page, the Quick Menu entry and the status line from [ModuleFacets], and their actions come back here as
 * `ui.facets` calls and jobs. The contract 1 status tile and settings rows are gone: droidtop showed the module list
 * as one row of raw JSON, and nothing in it could start the enable, remove, update and install jobs.
 *
 * `requestsRoot` is declared true in the manifest; everything here checks [PluginContext.hasRootApproval] first and
 * answers cleanly (never throws, never a hard failure) when it is false OR the device has no root-module provider at
 * all -- "root is used only after the user's explicit approval of this plugin's root declaration", and a device with
 * nothing to manage is the ordinary case, not an error.
 */
class MmrlPlugin : DroidtopPlugin {
    private lateinit var context: PluginContext

    override fun onLoad(context: PluginContext) {
        this.context = context
    }

    private fun provider(): RootProvider {
        if (!context.hasRootApproval()) return RootProvider.NONE
        return RootProvider.detect()
    }

    override fun invoke(capability: PluginCapability, args: PluginArgs): PluginResult =
        PluginResult.failure("Root modules answers through facets only")

    override fun handle(call: PluginCall): PluginReply {
        if (call.point != ModuleFacets.POINT) return PluginReply.error(PluginErrorCode.UNSUPPORTED, "Nothing here for ${call.point}")
        return when (call.op) {
            "get" -> {
                val provider = provider()
                val modules = if (provider == RootProvider.NONE) emptyList() else ModuleManager.list()
                PluginReply.ok(ModuleFacets.forTarget(call.args.optString("target"), provider, modules, readUpdates()))
            }
            ModuleFacets.OP_SET_ENABLED -> stateChange(call.args.optString("id"), if (call.args.optBoolean("value")) ModuleManager.STATE_ENABLE else ModuleManager.STATE_DISABLE)
            ModuleFacets.OP_REMOVE -> stateChange(call.args.optString("id"), ModuleManager.STATE_REMOVE)
            else -> PluginReply.error(PluginErrorCode.UNSUPPORTED, "Root modules has no \"${call.op}\"")
        }
    }

    /** A module switched on or off, or marked for removal: a file touch, quick enough for a call. */
    private fun stateChange(moduleId: String, state: String): PluginReply {
        if (provider() == RootProvider.NONE) return PluginReply.error(PluginErrorCode.FAILED, "No root modules here, or root is not allowed for this plugin")
        if (moduleId.isBlank()) return PluginReply.error(PluginErrorCode.FAILED, "No module was named")
        if (!ModuleManager.setState(moduleId, state)) return PluginReply.error(PluginErrorCode.FAILED, "The module is gone, or root refused")
        val said = when (state) {
            ModuleManager.STATE_ENABLE -> "On after the device restarts"
            ModuleManager.STATE_DISABLE -> "Off after the device restarts"
            else -> "Removed when the device restarts"
        }
        return PluginReply.ok(JSONObject().put("message", said))
    }

    /** The slow ones, as jobs: an update check asks every module's address, an install downloads a .zip. */
    override fun startJob(jobId: String, capability: PluginCapability, args: PluginArgs, progress: PluginJobProgress) {
        val call = args.string("call")?.let { PluginCall.fromJson(it) }
        if (call == null || call.point != ModuleFacets.POINT) {
            progress.complete(PluginResult.failure("Root modules runs only its own facet jobs"))
            return
        }
        val provider = provider()
        if (provider == RootProvider.NONE) {
            progress.complete(PluginResult.failure("No root modules here, or root is not allowed for this plugin"))
            return
        }
        when (call.op) {
            ModuleFacets.OP_CHECK_UPDATES -> runCheckUpdates(progress)
            ModuleFacets.OP_INSTALL -> runInstall(provider, call.args.optString("value").trim(), progress)
            else -> progress.complete(PluginResult.failure("Root modules has no job \"${call.op}\""))
        }
    }

    private fun runCheckUpdates(progress: PluginJobProgress) {
        val modules = ModuleManager.list()
        val updates = mutableSetOf<String>()
        val total = modules.size.coerceAtLeast(1)
        modules.forEachIndexed { index, module ->
            progress.report(index * 100 / total, "Checking ${module.name}")
            if (hasUpdate(module)) updates += module.id
        }
        writeUpdates(updates)
        val said = when (updates.size) {
            0 -> "No module has an update"
            1 -> "1 module has an update"
            else -> "${updates.size} modules have an update"
        }
        progress.complete(PluginResult.success(mapOf("message" to said)))
    }

    /** The ids the last check found an update for, kept in the plugin's own data, shown on each module's line. */
    private fun readUpdates(): Set<String> = runCatching {
        val array = JSONArray(File(context.privateDataDir(), UPDATES_FILE).readText())
        (0 until array.length()).map { array.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    private fun writeUpdates(ids: Set<String>) {
        runCatching { File(context.privateDataDir(), UPDATES_FILE).writeText(JSONArray(ids.toList()).toString()) }
    }

    private fun hasUpdate(module: ModuleManager.Module): Boolean {
        if (module.updateJson.isBlank()) return false
        return try {
            val text = httpGet(module.updateJson)
            JSONObject(text).optInt("versionCode", -1) > module.versionCode
        } catch (e: Exception) {
            false
        }
    }

    private fun runInstall(provider: RootProvider, zipUrl: String, progress: PluginJobProgress) {
        if (!zipUrl.startsWith("https://")) {
            progress.complete(PluginResult.failure("Give the module's https:// address"))
            return
        }
        progress.report(10, "Downloading the module")
        val zipFile = File(context.privateDataDir(), "install-${System.currentTimeMillis()}.zip")
        try {
            downloadTo(zipUrl, zipFile)
        } catch (e: Exception) {
            progress.complete(PluginResult.failure("The download failed: ${e.message ?: e::class.java.simpleName}"))
            return
        }
        progress.report(70, "Installing with ${provider.displayName}")
        val result = RootShell.exec(ModuleManager.installCommand(provider, zipFile.absolutePath))
        zipFile.delete()
        progress.complete(
            if (result.ok) {
                PluginResult.success(mapOf("message" to "Installed. It is on after the device restarts"))
            } else {
                PluginResult.failure(result.stderr.ifBlank { "The install failed" })
            },
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "GET"
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        try {
            conn.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val UPDATES_FILE = "updates.json"
    }
}
