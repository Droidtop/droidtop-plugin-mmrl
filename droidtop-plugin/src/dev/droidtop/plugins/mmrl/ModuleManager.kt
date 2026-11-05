package dev.droidtop.plugins.mmrl

/**
 * Module listing/state, ported from MMRL's own
 * `platform/manager/BaseModuleManager.kt` + `platform/model/ModId.kt`
 * (GPL-3.0, github.com/MMRLApp/MMRL) -- the exact directory layout,
 * `module.prop` fields and `disable`/`remove`/`update` marker-file
 * semantics every Magisk-module-compatible provider (Magisk, KernelSU,
 * APatch) already shares, run here through droidtop's enhanced privileges
 * provider ([PrivilegedShell], Droidtop/tracker#892) instead of MMRL's own
 * bound root service, since a droidtop plugin has no manifest component of
 * its own to bind that to (see this repo's PLUGIN-PLAN.md).
 *
 * Every command is an argument list, never a shell string, and a module id
 * is used only when it has the shape Magisk itself requires of one
 * ([isModuleId]), so a folder under /data/adb/modules can never name a
 * command.
 */
class ModuleManager(private val shell: PrivilegedShell) {
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

    private fun moduleDir(id: String) = "$MODULES_PATH/$id"

    fun list(): List<Module> {
        val listing = shell.exec("ls", "-1", MODULES_PATH)
        if (!listing.ok) return emptyList()
        return listing.stdout.lines()
            .map { it.trim() }
            .filter(::isModuleId)
            .mapNotNull(::readModule)
    }

    private fun readModule(id: String): Module? {
        val dir = moduleDir(id)
        val propResult = shell.exec("cat", "$dir/$PROP_FILE")
        if (!propResult.ok || propResult.stdout.isBlank()) return null
        val props = propResult.stdout.lines().mapNotNull { line ->
            val parts = line.split("=", limit = 2).map { it.trim() }
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        // One listing of the module's folder says which marker files it has.
        val files = shell.exec("ls", "-1", dir).stdout.lines().map { it.trim() }.toSet()
        val state = when {
            REMOVE_FILE in files -> STATE_REMOVE
            DISABLE_FILE in files -> STATE_DISABLE
            UPDATE_FILE in files -> STATE_UPDATE
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
        if (!isModuleId(id)) return false
        val dir = moduleDir(id)
        if (!shell.exec("ls", "-d", dir).ok) return false
        return when (action) {
            STATE_ENABLE -> shell.exec("rm", "-f", "$dir/$REMOVE_FILE", "$dir/$DISABLE_FILE").ok
            STATE_DISABLE -> shell.exec("rm", "-f", "$dir/$REMOVE_FILE").ok && shell.exec("touch", "$dir/$DISABLE_FILE").ok
            STATE_REMOVE -> shell.exec("rm", "-f", "$dir/$DISABLE_FILE").ok && shell.exec("touch", "$dir/$REMOVE_FILE").ok
            else -> false
        }
    }

    /** Installs the module .zip at [zipPath] with the provider's own CLI (MagiskModuleManager.getInstallCommand and its KernelSU/APatch equivalents). */
    fun install(provider: RootProvider, zipPath: String): PrivilegedShell.Result {
        val argv = when (provider) {
            RootProvider.MAGISK -> listOf("magisk", "--install-module", zipPath)
            RootProvider.KERNEL_SU -> listOf("ksud", "module", "install", zipPath)
            RootProvider.APATCH -> listOf("apd", "module", "install", zipPath)
            RootProvider.NONE -> return PrivilegedShell.Result(-1, "", "No root solution to install with")
        }
        return shell.execLong(argv)
    }

    companion object {
        private const val MODULES_PATH = "/data/adb/modules"
        private const val PROP_FILE = "module.prop"
        private const val DISABLE_FILE = "disable"
        private const val REMOVE_FILE = "remove"
        private const val UPDATE_FILE = "update"

        const val STATE_ENABLE = "enable"
        const val STATE_DISABLE = "disable"
        const val STATE_REMOVE = "remove"
        const val STATE_UPDATE = "update"

        /** Magisk's own rule for a module id (its developer guide, `module.prop`): a letter, then letters, digits, `.`, `_` or `-`. */
        private val MODULE_ID = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

        fun isModuleId(id: String): Boolean = MODULE_ID.matches(id)
    }
}
