package dev.droidtop.plugins.mmrl

import org.json.JSONArray
import org.json.JSONObject

/**
 * What droidtop draws for this plugin, as facets (Droidtop/droidtop docs/plugin-api.md 1.10, "Facets first";
 * Droidtop/tracker#602): the plugin draws nothing. Its settings page is the provider, Check for updates, Install a
 * module from a link, and one group per module with its On switch and Remove; the Quick Menu shows one status
 * line. Pure: the module list and the ids with updates come in, JSON goes out.
 */
object ModuleFacets {
    const val POINT = "ui.facets"
    const val SETTINGS = "settings.page"
    const val QUICK_MENU = "quickmenu"

    const val OP_SET_ENABLED = "setEnabled"
    const val OP_REMOVE = "remove"
    const val OP_CHECK_UPDATES = "checkUpdates"
    const val OP_INSTALL = "install"

    /** The facets for [target]: none for a target this plugin does not use. */
    fun forTarget(target: String, provider: RootProvider, modules: List<ModuleManager.Module>, updates: Set<String>): JSONObject {
        val facets = when (target) {
            SETTINGS -> settings(provider, modules, updates)
            QUICK_MENU -> listOf(status(target, provider, modules, updates))
            else -> emptyList()
        }
        return JSONObject().put("facets", JSONArray(facets))
    }

    /** One line: the provider and how many modules, or why there is nothing to manage. */
    fun summary(provider: RootProvider, modules: List<ModuleManager.Module>, updates: Set<String>): String {
        if (provider == RootProvider.NONE) return "No Magisk, KernelSU or APatch here, or root not allowed for this plugin"
        val active = modules.count { it.state == ModuleManager.STATE_ENABLE }
        val update = updates.count { id -> modules.any { it.id == id } }
        return listOfNotNull(
            provider.displayName,
            "${modules.size} ${if (modules.size == 1) "module" else "modules"}, $active on",
            if (update > 0) "$update with an update" else null,
        ).joinToString(" · ")
    }

    private fun status(target: String, provider: RootProvider, modules: List<ModuleManager.Module>, updates: Set<String>): JSONObject =
        facet("status", "status", target)
            .put("label", "Root modules")
            .put("state", if (provider == RootProvider.NONE) "off" else "ok")
            .put("value", summary(provider, modules, updates))

    private fun settings(provider: RootProvider, modules: List<ModuleManager.Module>, updates: Set<String>): List<JSONObject> {
        val head = facet("provider", "info", SETTINGS).put("label", "Root modules").put("value", summary(provider, modules, updates))
        if (provider == RootProvider.NONE) return listOf(head)
        val actions = listOf(
            facet("check-updates", "action", SETTINGS)
                .put("label", "Check for updates")
                .put("sub", "Asks each module's update address for a newer version")
                .put("call", JSONObject().put("op", OP_CHECK_UPDATES).put("kind", "job")),
            facet("install", "input", SETTINGS)
                .put("label", "Install a module from a link")
                .put("value", "")
                .put("hint", "The module's .zip address")
                .put("kind", "url")
                .put("call", JSONObject().put("op", OP_INSTALL).put("kind", "job")),
        )
        val groups = modules.sortedBy { it.name.lowercase() }.map { module -> moduleGroup(module, module.id in updates) }
        val none = if (modules.isEmpty()) listOf(facet("none", "info", SETTINGS).put("label", "No modules installed")) else emptyList()
        return listOf(head) + actions + none + groups
    }

    private fun moduleGroup(module: ModuleManager.Module, hasUpdate: Boolean): JSONObject {
        val removing = module.state == ModuleManager.STATE_REMOVE
        val about = listOfNotNull(
            module.version.takeIf { it.isNotBlank() },
            module.author.takeIf { it.isNotBlank() }?.let { "by $it" },
            if (hasUpdate) "update available" else null,
            if (removing) "removed when the device restarts" else null,
            if (module.state == ModuleManager.STATE_UPDATE) "updated when the device restarts" else null,
        ).joinToString(", ")
        val inner = listOfNotNull(
            JSONObject().put("id", "module.${module.id}.about").put("kind", "info").put("v", 1)
                .put("label", module.description.ifBlank { module.id }).put("value", about),
            if (removing) {
                null
            } else {
                JSONObject().put("id", "module.${module.id}.on").put("kind", "toggle").put("v", 1)
                    .put("label", "On")
                    .put("sub", "Takes effect when the device restarts")
                    .put("value", module.state == ModuleManager.STATE_ENABLE || module.state == ModuleManager.STATE_UPDATE)
                    .put("call", JSONObject().put("op", OP_SET_ENABLED).put("args", JSONObject().put("id", module.id)))
            },
            if (removing) {
                null
            } else {
                JSONObject().put("id", "module.${module.id}.remove").put("kind", "action").put("v", 1)
                    .put("label", "Remove")
                    .put("sub", "Removes ${module.name.ifBlank { module.id }} when the device restarts")
                    .put("confirm", "danger")
                    .put("call", JSONObject().put("op", OP_REMOVE).put("args", JSONObject().put("id", module.id)))
            },
        )
        return facet("module.${module.id}", "group", SETTINGS)
            .put("title", module.name.ifBlank { module.id })
            .put("facets", JSONArray(inner))
    }

    private fun facet(id: String, kind: String, target: String): JSONObject =
        JSONObject().put("id", id).put("kind", kind).put("v", 1).put("targets", JSONArray().put(target))
}
