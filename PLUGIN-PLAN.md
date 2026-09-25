# MMRL as a droidtop plugin

Private plan. Upstream: https://github.com/MMRLApp/MMRL (GPL-3.0). Manages
Magisk/KernelSU/APatch modules: browse repos, install/enable/disable/update
modules, run each module's own WebUI config screen.

**Plugin model this plan is written against (2026-09-25):** droidtop plugins
run in **droidtop's own process/context**, never through enginehost. A
plugin is Python, a native Kotlin/`.so` bundle (arm64-v8a + x86_64), or
another kind droidtop's host adds support for. The sandbox exists for
stability, not security. Root is an optional enhancement only, never
required, never the default path.

## What it would contribute

A status tile in Desktop mode ("root provider: Magisk n, N modules active, M
updates available") and settings rows to enable/disable/update a module by
id. This is read-mostly from droidtop's side: MMRL already owns the real
work (talking to whichever root provider is present); droidtop just wants
the module list and update state back, and a way to fire an
enable/disable/update action per module. Module-specific WebUI config
screens stay inside the real MMRL app; droidtop has no business rendering
third-party module UI.

## Reuse as a native bundle

Good candidate for droidtop's native Kotlin/`.so` plugin kind. `platform/`
and `compat/` are the root-provider abstraction (Magisk vs KernelSU vs
APatch talked to through one interface) with no UI dependency -- this is the
part a plugin actually needs, and it's already factored out as separate
Gradle modules upstream, which makes stripping straightforward rather than a
grep-and-guess job. Must be stripped: `ui/` (Compose screens for the module
list, repo browser, settings), the WebUI-X interface
(`MMRLWebUIInterface.kt`, `pathHandler/*`) since that's for rendering a
module's own config page and droidtop has no surface for that, and the
`playstore` source set (Play-specific build flavor, irrelevant here).

## Root

This one inverts the usual framing: MMRL's entire purpose is root/module
management, so there's no "non-root path" for the *feature itself* -- a
device with no Magisk/KernelSU/APatch present simply has no modules to
manage. What stays true to droidtop's "root optional, never required" rule
is at the UI layer: the status tile and settings rows only appear when a
root provider is actually present and the plugin can see it; their absence
is silent, not an error state or a nag to root the device. Nothing about the
rest of droidtop ever depends on this plugin being active, and since
plugins now run in droidtop's own context rather than a separate host, this
is purely a runtime check the plugin makes for itself, not something that
needs a separate discovery mechanism.

## What it needs from droidtop's plugin API

- A polling/status capability (module list + update flags), read back into
  droidtop's status-tile/settings-row rendering.
- An action capability keyed by module id (enable/disable/update), returning
  success/failure droidtop can show inline on the row that fired it.
- Since the plugin needs a live root-provider connection (a bound service
  call, not just a file read), and plugins now run directly in droidtop's
  own process, the plugins agent should confirm whether a plugin is allowed
  to hold that kind of persistent connection open itself, or whether every
  root-provider call still has to go through a request/response shape at
  the plugin-host boundary. This question is shared with the Shizuku and
  ReVanced Manager plans -- worth settling once.
