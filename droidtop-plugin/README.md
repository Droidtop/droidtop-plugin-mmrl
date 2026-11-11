# droidtop-plugin/

The droidtop `native_bundle` plugin built from this repo (see
`PLUGIN-PLAN.md`): facets (`ui.facets`: a settings page and a Quick Menu entry) for whatever
Magisk-module-compatible root solution (Magisk, KernelSU, APatch) is
present, installed through droidtop's Settings -> App integrations ->
Plugins screen like any other plugin bundle.

- `src/` -- five files:
  - `PrivilegedShell.kt` -- every root command, through droidtop's enhanced
    privileges provider (`priv.shell` at level root, Droidtop/tracker#892),
    as an argument list the provider starts with no shell; never `su` from
    this plugin's own process. A module install runs as a stream session
    (`exec_stream`), since it can outlast a quick call.
  - `RootProvider.kt` -- detects Magisk/KernelSU/APatch/none by asking
    each provider's own CLI to identify itself.
  - `ModuleManager.kt` -- module listing/enable/disable/remove/install,
    ported from MMRL's own `platform/manager/BaseModuleManager.kt` +
    `platform/model/ModId.kt` (GPL-3.0): the same `/data/adb/modules/<id>`
    layout and `disable`/`remove`/`update` marker-file semantics, run
    through `PrivilegedShell` instead of MMRL's bound service; a module
    id is used only in the shape Magisk requires of one.
  - `ModuleFacets.kt` -- the facets droidtop draws: the provider line, Check for
    updates, Install a module from a link, and one group per module with its On
    switch and Remove (docs/plugin-api.md 1.10).
  - `MmrlPlugin.kt` -- the `DroidtopPlugin` entry point: `ui.facets` `get`, the
    switch and Remove as quick calls, and check-updates/install as
    jobs (`startJob`) since a network fetch or install can run well past
    `invoke()`'s 15s watchdog.
- `manifest.template.json` -- everything about the manifest except
  `payload` (filled in by `build.sh`). It requires `priv.shell` (`minLevel` root, optional) and asks for
  `priv.shell.root`, so droidtop's approval screen offers root and the person
  can turn it off; without it the plugin says there is nothing to manage.
  `origin` is `"droidtop"`, same
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

## Signing

The bundle carries `manifest.jws`: a detached ES256 JWS over the exact bytes of `manifest.json`, with this repository's leaf certificate in `x5c` (the root itself is not included). The leaf comes from the repository secrets `PLUGIN_SIGNING_KEY` and `PLUGIN_SIGNING_CERT` and was issued by the droidtop root (pinned by the droidtop app); it names the plugin's origin and id. `sign_jws.py` writes the signature and checks it against the leaf before packing. `manifest.sig` stays in the bundle for older droidtop builds. Expiry dates are not checked.
