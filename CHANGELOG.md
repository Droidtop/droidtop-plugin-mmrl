# Changelog

All notable changes this fork makes are documented in this file. The format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Upstream's own
changes arrive through the upstream sync and are recorded in MMRLApp/MMRL's history; the
fork does not cut versioned releases of its own, so its changes are recorded
under Unreleased.

## [Unreleased]

### Added

- The droidtop plugin: a root-module status tile and settings rows (2026-09-26), the plugin plan and its rewrite so plugins run in droidtop's own context (2026-09-25), the licence note and the daily upstream sync (2026-09-25).
- The plugin bundle workflow signs the bundle itself (2026-10-07), now through the shared workflow in `Droidtop/droidtop-platforms`; every signed push publishes a pre-release.
- A commit-hygiene check and the upstream sync, both called from `Droidtop/droidtop-platforms`.

### Changed

- `MmrlPlugin` follows `DroidtopPlugin.startJob` gaining a job id (2026-09-26).
- `build.sh` puts `android.jar` on the kotlinc classpath as well as d8's (2026-09-26).
- The private-fork framing is dropped now that the plugin is public (2026-09-28).
