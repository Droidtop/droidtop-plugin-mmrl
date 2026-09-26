package dev.droidtop.plugins.mmrl

import dev.droidtop.pluginhost.DroidtopPlugin
import dev.droidtop.pluginhost.PluginArgs
import dev.droidtop.pluginhost.PluginCapability
import dev.droidtop.pluginhost.PluginContext
import dev.droidtop.pluginhost.PluginJobProgress
import dev.droidtop.pluginhost.PluginResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * MMRL (github.com/MMRLApp/MMRL, GPL-3.0) as a droidtop plugin: a
 * status tile + settings rows for whichever Magisk-module-compatible
 * root solution is present, per this repo's PLUGIN-PLAN.md. droidtop has
 * no business rendering a module's own WebUI config screen or a repo
 * browser (the plan's own scope note) -- this plugin only reports the
 * module list/update state back and fires enable/disable/update-check/
 * install actions by id, exactly the surface the plan asks for.
 *
 * `requestsRoot` is declared true in the manifest; every real action
 * here checks [PluginContext.hasRootApproval] first and answers cleanly
 * (never throws, never a hard failure) when it is false OR the device
 * has no root-module provider at all -- "root is used only after the
 * user's explicit approval of this plugin's root declaration", and a
 * device with nothing to manage is the ordinary case, not an error.
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

    override fun invoke(capability: PluginCapability, args: PluginArgs): PluginResult = when (capability) {
        PluginCapability.STATUS_TILE -> statusTile()
        PluginCapability.SETTINGS_ROWS -> settingsRows()
        else -> PluginResult.failure("MmrlPlugin does not implement ${capability.id}")
    }

    /**
     * "Where no root solution is present, it hides itself rather than
     * breaking." There is no droidtop-side registry yet to actually hide
     * a tile from (docs/plugin-catalog.md: the launcher/settings surface
     * for plugin capabilities is still a seam, not built) -- this
     * plugin's own half of that contract is to answer cleanly here
     * rather than fail, so whatever eventually reads this (today: the
     * manual "call status tile" action in droidtop's Plugins settings
     * screen) never sees an error for the ordinary no-root-provider
     * case.
     */
    private fun statusTile(): PluginResult {
        val provider = provider()
        if (provider == RootProvider.NONE) {
            return PluginResult.success(mapOf("label" to "Root modules", "value" to RootProvider.NONE.displayName))
        }
        val modules = ModuleManager.list()
        val active = modules.count { it.state == ModuleManager.STATE_ENABLE }
        return PluginResult.success(
            mapOf(
                "label" to "Root modules",
                "value" to "${provider.displayName}, ${modules.size} module(s), $active active",
            ),
        )
    }

    /**
     * One row per module, JSON-encoded under one key: [PluginResult]'s
     * values map is flat string-to-string ([PluginArgs]/[PluginResult]
     * cross the binder as a JSON object of strings, PluginRuntimeService.kt),
     * so a variable-length module list has to travel as one JSON-array
     * string rather than one key per module.
     */
    private fun settingsRows(): PluginResult {
        val provider = provider()
        if (provider == RootProvider.NONE) {
            return PluginResult.success(mapOf("provider" to RootProvider.NONE.displayName, "modules_json" to "[]"))
        }
        val rows = JSONArray()
        ModuleManager.list().forEach { m ->
            rows.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("version", m.version)
                    .put("author", m.author)
                    .put("description", m.description)
                    .put("state", m.state),
            )
        }
        return PluginResult.success(mapOf("provider" to provider.displayName, "modules_json" to rows.toString()))
    }

    /**
     * The long-running counterpart to [settingsRows]: enable/disable/
     * remove are near-instant file touches but still worth a job (one
     * `su` round trip can stall on a slow/starting root daemon); update
     * checking and install are genuinely slow (a network fetch per
     * module, a multi-MB download) and would blow [invoke]'s 15s
     * watchdog on a real module list.
     */
    override fun startJob(capability: PluginCapability, args: PluginArgs, progress: PluginJobProgress) {
        if (capability != PluginCapability.SETTINGS_ROWS) {
            throw UnsupportedOperationException("MmrlPlugin only runs jobs for settings_rows actions")
        }
        val provider = provider()
        if (provider == RootProvider.NONE) {
            progress.complete(PluginResult.failure("no root solution on this device, or root not approved for this plugin"))
            return
        }
        when (val action = args.string("action")) {
            ACTION_ENABLE, ACTION_DISABLE, ACTION_REMOVE -> runStateAction(action, args, progress)
            ACTION_CHECK_UPDATES -> runCheckUpdates(progress)
            ACTION_INSTALL -> runInstall(provider, args, progress)
            else -> progress.complete(PluginResult.failure("unknown action \"$action\""))
        }
    }

    private fun runStateAction(action: String, args: PluginArgs, progress: PluginJobProgress) {
        val moduleId = args.string("module_id")
        if (moduleId.isNullOrBlank()) {
            progress.complete(PluginResult.failure("missing module_id"))
            return
        }
        progress.report(50, "Applying \"$action\" to $moduleId")
        val stateAction = when (action) {
            ACTION_ENABLE -> ModuleManager.STATE_ENABLE
            ACTION_DISABLE -> ModuleManager.STATE_DISABLE
            else -> ModuleManager.STATE_REMOVE
        }
        val ok = ModuleManager.setState(moduleId, stateAction)
        progress.complete(
            if (ok) {
                PluginResult.success(mapOf("module_id" to moduleId, "action" to action))
            } else {
                PluginResult.failure("$action failed for $moduleId (module missing, or su refused)")
            },
        )
    }

    private fun runCheckUpdates(progress: PluginJobProgress) {
        val modules = ModuleManager.list()
        val updates = JSONArray()
        val total = modules.size.coerceAtLeast(1)
        modules.forEachIndexed { index, module ->
            progress.report(index * 100 / total, "Checking ${module.name}")
            if (hasUpdate(module)) updates.put(module.id)
        }
        progress.complete(PluginResult.success(mapOf("modules_with_updates_json" to updates.toString())))
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

    private fun runInstall(provider: RootProvider, args: PluginArgs, progress: PluginJobProgress) {
        val zipUrl = args.string("zip_url")
        if (zipUrl.isNullOrBlank()) {
            progress.complete(PluginResult.failure("missing zip_url"))
            return
        }
        progress.report(10, "Downloading module")
        val zipFile = File(context.privateDataDir(), "install-${System.currentTimeMillis()}.zip")
        try {
            downloadTo(zipUrl, zipFile)
        } catch (e: Exception) {
            progress.complete(PluginResult.failure("download failed: ${e.message ?: e::class.java.simpleName}"))
            return
        }
        progress.report(70, "Installing with ${provider.displayName}")
        val result = RootShell.exec(ModuleManager.installCommand(provider, zipFile.absolutePath))
        zipFile.delete()
        progress.complete(
            if (result.ok) {
                PluginResult.success(mapOf("zip_url" to zipUrl))
            } else {
                PluginResult.failure(result.stderr.ifBlank { "install failed" })
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
        const val ACTION_ENABLE = "enable"
        const val ACTION_DISABLE = "disable"
        const val ACTION_REMOVE = "remove"
        const val ACTION_CHECK_UPDATES = "check_updates"
        const val ACTION_INSTALL = "install"
    }
}
