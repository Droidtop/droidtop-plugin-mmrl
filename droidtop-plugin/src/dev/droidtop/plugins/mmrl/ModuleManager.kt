package dev.droidtop.plugins.mmrl

/**
 * Module listing/state, ported from MMRL's own
 * `platform/manager/BaseModuleManager.kt` + `platform/model/ModId.kt`
 * (GPL-3.0, github.com/MMRLApp/MMRL) -- the exact directory layout,
 * `module.prop` fields and `disable`/`remove`/`update` marker-file
 * semantics every Magisk-module-compatible provider (Magisk, KernelSU,
 * APatch) already shares, run here through a per-call `su -c`
 * ([RootShell]) instead of MMRL's own bound root service, since a
 * droidtop plugin has no manifest component of its own to bind that to
 * (see this repo's PLUGIN-PLAN.md).
 */
object ModuleManager {
    private const val ADB_DIR = "/data/adb"
    private const val MODULES_DIR = "modules"
    private const val PROP_FILE = "module.prop"
    private const val DISABLE_FILE = "disable"
    private const val REMOVE_FILE = "remove"
    private const val UPDATE_FILE = "update"

    const val STATE_ENABLE = "enable"
    const val STATE_DISABLE = "disable"
    const val STATE_REMOVE = "remove"
    const val STATE_UPDATE = "update"

    data class Module(
        val id: String,
        val name: String,
        val version: String,
        val versionCode: Int,
        val author: String,
        val description: String,
        val updateJson: String,
        val state: String,
    )

    private fun moduleDir(id: String) = "$ADB_DIR/$MODULES_DIR/$id"

    fun list(): List<Module> {
        val listing = RootShell.exec("ls -1 $ADB_DIR/$MODULES_DIR")
        if (!listing.ok) return emptyList()
        return listing.stdout.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull(::readModule)
    }

    private fun readModule(id: String): Module? {
        val propResult = RootShell.exec("cat '${moduleDir(id)}/$PROP_FILE'")
        if (!propResult.ok || propResult.stdout.isBlank()) return null
        val props = propResult.stdout.lines().mapNotNull { line ->
            val parts = line.split("=", limit = 2).map { it.trim() }
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val state = when {
            RootShell.exec("test -f '${moduleDir(id)}/$REMOVE_FILE'").ok -> STATE_REMOVE
            RootShell.exec("test -f '${moduleDir(id)}/$DISABLE_FILE'").ok -> STATE_DISABLE
            RootShell.exec("test -f '${moduleDir(id)}/$UPDATE_FILE'").ok -> STATE_UPDATE
            else -> STATE_ENABLE
        }
        return Module(
            id = id,
            name = props["name"] ?: id,
            version = props["version"] ?: "",
            versionCode = props["versionCode"]?.toIntOrNull() ?: -1,
            author = props["author"] ?: "",
            description = props["description"] ?: "",
            updateJson = props["updateJson"] ?: "",
            state = state,
        )
    }

    /** [action] is one of [STATE_ENABLE]/[STATE_DISABLE]/[STATE_REMOVE] -- matches MagiskModuleManager.enable/disable/remove's own file-touch logic. */
    fun setState(id: String, action: String): Boolean {
        val dir = moduleDir(id)
        if (!RootShell.exec("test -d '$dir'").ok) return false
        return when (action) {
            STATE_ENABLE -> RootShell.exec("rm -f '$dir/$REMOVE_FILE' '$dir/$DISABLE_FILE'").ok
            STATE_DISABLE -> RootShell.exec("rm -f '$dir/$REMOVE_FILE'; touch '$dir/$DISABLE_FILE'").ok
            STATE_REMOVE -> RootShell.exec("rm -f '$dir/$DISABLE_FILE'; touch '$dir/$REMOVE_FILE'").ok
            else -> false
        }
    }

    /** Each provider's own real module-install CLI (MagiskModuleManager.getInstallCommand and its KernelSU/APatch equivalents). */
    fun installCommand(provider: RootProvider, zipPath: String): String = when (provider) {
        RootProvider.MAGISK -> "magisk --install-module '$zipPath'"
        RootProvider.KERNEL_SU -> "ksud module install '$zipPath'"
        RootProvider.APATCH -> "apd module install '$zipPath'"
        RootProvider.NONE -> ""
    }
}
