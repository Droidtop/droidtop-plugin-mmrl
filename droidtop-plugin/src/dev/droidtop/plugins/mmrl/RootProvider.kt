package dev.droidtop.plugins.mmrl

/**
 * Which Magisk-module-compatible root solution (if any) this device has.
 * Detected the same way any Magisk-module manager tells them apart: ask
 * each provider's own CLI to identify itself (Magisk ships `magisk`,
 * KernelSU ships `ksud`, APatch ships `apd`) rather than parse `su`'s own
 * banner text, which none of the three standardise. This inverts the
 * usual "root optional" framing on purpose (PLUGIN-PLAN.md's "Root"
 * section): a device with none of these present simply has no modules to
 * manage, so [NONE] is not an error state, it is the ordinary answer for
 * most devices.
 */
enum class RootProvider(val displayName: String) {
    MAGISK("Magisk"),
    KERNEL_SU("KernelSU"),
    APATCH("APatch"),
    NONE("No root solution"),
    ;

    companion object {
        /** NONE also when root can't be used: no enhanced privileges provider at root level, or not allowed for this plugin. */
        fun detect(shell: PrivilegedShell): RootProvider {
            if (!shell.available()) return NONE
            if (shell.exec("magisk", "-v").ok) return MAGISK
            if (shell.exec("ksud", "-V").ok) return KERNEL_SU
            if (shell.exec("apd", "-V").ok) return APATCH
            return NONE
        }
    }
}
