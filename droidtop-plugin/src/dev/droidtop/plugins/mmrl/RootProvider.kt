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
        fun detect(): RootProvider {
            if (!RootShell.exec("id").ok) return NONE
            if (RootShell.exec("magisk -v").ok) return MAGISK
            if (RootShell.exec("ksud -V").ok) return KERNEL_SU
            if (RootShell.exec("apd -V").ok) return APATCH
            return NONE
        }
    }
}
