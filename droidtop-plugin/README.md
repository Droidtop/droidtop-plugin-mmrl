# droidtop-plugin/

The droidtop `native_bundle` plugin built from this repo (see
`PLUGIN-PLAN.md`): a `status_tile` + `settings_rows` surface for whatever
Magisk-module-compatible root solution (Magisk, KernelSU, APatch) is
present, installed through droidtop's Settings -> App integrations ->
Plugins screen like any other plugin bundle.

- `src/` -- four files:
  - `RootShell.kt` -- one `su -c <command>` invocation at a time. This
    plugin has no manifest component of its own to bind MMRL's real
    `libsu`-backed `RootService` to, so it forgoes that persistent
    connection rather than fake it.
  - `RootProvider.kt` -- detects Magisk/KernelSU/APatch/none by asking
    each provider's own CLI to identify itself.
  - `ModuleManager.kt` -- module listing/enable/disable/remove/install,
    ported from MMRL's own `platform/manager/BaseModuleManager.kt` +
    `platform/model/ModId.kt` (GPL-3.0): the same `/data/adb/modules/<id>`
    layout and `disable`/`remove`/`update` marker-file semantics, run
    through `RootShell` instead of MMRL's bound service.
  - `MmrlPlugin.kt` -- the `DroidtopPlugin` entry point: `status_tile`,
    `settings_rows`, and enable/disable/remove/check-updates/install as
    jobs (`startJob`) since a network fetch or install can run well past
    `invoke()`'s 15s watchdog.
- `manifest.template.json` -- everything about the manifest except
  `payload` (filled in by `build.sh`). `requestsRoot: true` -- this
  plugin's whole purpose needs it, gated at every call site on
  `PluginContext.hasRootApproval()` (both "device has root" AND "user
  approved this plugin's root request"). `origin` is `"droidtop"`, same
  reasoning as the sibling `droidtop-plugin-shizuku` repo: this bundle is
  signed with droidtop's own pinned sample-plugin origin key, since
  droidtop's public repo pins only that one origin.
- `build.sh` / `sign.sh` / `.github/workflows/plugin-bundle.yml` -- same
  split and CI shape as `droidtop-plugin-shizuku`: CI builds the payload
  against a `Droidtop/droidtop` checkout's `:plugin-host`, then signs it with
  `sign.sh` using the `PLUGIN_SIGNING_KEY` repo secret (optional `PLUGIN_SIGNING_CERT`
  becomes `origin.cert`); without the secret it uploads unsigned. A `plugin-v*` tag
  attaches the signed bundle to a release. Locally, `sign.sh` runs on droidtop-dev.

To produce an installable bundle: download the `plugin-bundle` CI
artifact's `build/manifest.json` + `build/classes.jar` (or run
`build.sh` locally against a `:plugin-host:assembleDebug` classpath from
a `Droidtop/droidtop` checkout), then run
`PLUGIN_SIGNING_KEY=/root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem ./sign.sh`.
