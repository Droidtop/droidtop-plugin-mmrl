# MMRL as a droidtop plugin

Private plan. Upstream: https://github.com/MMRLApp/MMRL (GPL-3.0). Manages
Magisk/KernelSU/APatch modules: browse repos, install/enable/disable/update
modules, run each module's own WebUI config screen.

## What it would contribute

A status tile in Desktop mode ("root provider: Magisk n, N modules active, M
updates available") and settings rows to enable/disable/update a module by
id. This is read-mostly from droidtop's side: MMRL already owns the real
work (talking to whichever root provider is present); droidtop just wants
the module list and update state back, and a way to fire an
enable/disable/update action per module — squarely the subplugin's "search
that returns results, state droidtop polls" shape from SPEC §12a.
Module-specific WebUI config screens stay inside the real MMRL app; droidtop
has no business rendering third-party module UI.

## Reuse as a native bundle

Good candidate. `platform/` and `compat/` are the root-provider abstraction
(Magisk vs KernelSU vs APatch talked to through one interface) with no UI
dependency — this is the part a subplugin actually needs, and it's already
factored out as separate Gradle modules upstream, which makes stripping
straightforward rather than a grep-and-guess job. Must be stripped: `ui/`
(Compose screens for the module list, repo browser, settings), the WebUI-X
interface (`MMRLWebUIInterface.kt`, `pathHandler/*`) since that's for
rendering a module's own config page and droidtop has no surface for that,
and the `playstore` source set (Play-specific build flavor, irrelevant here).

## Root

This one inverts the usual framing: MMRL's entire purpose is root/module
management, so there's no "non-root path" for the *feature itself* — a
device with no Magisk/KernelSU/APatch present simply has no modules to
manage. What stays true to droidtop's "root optional, never required" rule
is at the UI layer: the status tile and settings rows only appear when a
root provider is actually present and MMRL (or its subplugin) can see it;
their absence is silent, not an error state or a nag to root the device.
Nothing about the rest of droidtop ever depends on this plugin being active.

## What it needs from droidtop's plugin API

- A polling/status capability (module list + update flags) — the same shape
  SPEC §12a already anticipates for "a now-playing state it polls".
- An action capability keyed by module id (enable/disable/update), returning
  success/failure droidtop can show inline on the row that fired it, per
  §12a's "malformed answer is that call's failure, shown on the row that
  asked" rule — MMRL's plugin fits this rule directly, no change needed.
- Because the subplugin needs a live root-provider connection (a bound
  service call, not just file reads), the plugins agent should confirm
  enginehost's subplugin host process is allowed to hold that kind of
  connection open, or whether every root-provider call has to be
  request/response per call like everything else on the capabilities
  provider.
