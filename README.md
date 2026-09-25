# MMRL


![GitHub Release](https://img.shields.io/github/v/release/MMRLApp/MMRL?label=Latest%20Release)
![Google Play Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.dergoogler.mmrl%26l%3DInstalls%26m%3D%24shortinstalls&label=Google%20Play&color=red)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/MMRLApp/MMRL/total?label=GitHub%20Downloads)

An app for managing, sharing, and exploring Magisk modules across repositories.

## Features

- **Comprehensive Root Manager Support**: Full compatibility with Magisk, KernelSU, and APatch for diverse root management options.
- **WebUI with Advanced Features**: Includes [Edge-to-Edge](https://developer.android.com/develop/ui/compose/layouts/insets) support and dynamic app themes for a seamless user experience.
- **Bulk Installation Support**: Effortlessly install modules in bulk, both online and locally.
- **Module Dependency Management**: Simplifies module installation for end-users by automatically handling module dependencies.
- **Magisk Modules Repositories**: A community-driven platform that allows anyone to contribute and access Magisk modules.
- **User Transparency with Blacklist**: Clear visibility into which modules or actions are restricted, ensuring users can make informed decisions.
- **Customizable Module Pages**: Personalize module pages with categories, screenshots, icons, covers, and other content for a tailored presentation.
- **Transparency of Included Files**: Detailed breakdown of all files within modules, including APKs, service files, and more, for full user awareness.
- **Analytics Dashboard**: Display module-related analytics on the homepage, offering valuable insights into module usage and trends.

## Preview

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="32%" />
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="32%" />
</p>

## Supported Versions

- Android 8.0 ~ 14
- Magisk 24.0 ~ latest
- KernelSU 0.5.1 ~ latest
- APatch 10253 ~ latest

## Translate

Get involved with MMRL by translating it into your language!

[![Translation status](https://hosted.weblate.org/widget/mmrlapp/mmrl-main/multi-auto.svg)](https://hosted.weblate.org/engage/mmrlapp/)

## Modules Repository

- [MMRLApp/MMRL-Util](https://github.com/MMRLApp/MMRL-Util): A command line interface for building modules repository

## Credits

- [tabler/tabler-icons](https://github.com/tabler/tabler-icons.git)
- [MRepoApp/MRepo](https://github.com/MRepoApp/MRepo)
- [topjohnwu/libsu](https://github.com/topjohnwu/libsu)

## This is a private personal fork

This repo mirrors upstream [MMRLApp/MMRL](https://github.com/MMRLApp/MMRL)
for droidtop plugin work (see `PLUGIN-PLAN.md`). Private, personal use only —
never mention it in public droidtop docs or repos.

Branches:
- `upstream-main` tracks upstream's `master` exactly (fast-forward only,
  synced daily by `.github/workflows/sync-upstream.yml`).
- `main` is where plugin/integration work happens; the sync workflow merges
  `upstream-main` into it automatically when there's no conflict, and opens
  an issue here instead of forcing anything when there is one.

### Licence

MMRL is GPL-3.0 (see `LICENSE`). Same as ReVanced Manager: private
modification and personal-device use carries no extra obligation beyond
keeping `LICENSE` and copyright notices, since installing a personal build on
the owner's own device is not distribution. Sharing a modified build with
anyone else would require offering that modified source under GPL-3.0.

### Why MMRL over other Magisk module managers

Picked over alternatives found in the same search: `yunncheng/MMRL` is a
differently-scoped, far smaller project sharing only the name; the various
"Alt-Repo"/"Rikj000" results are module *repositories* (JSON indexes of
modules), not manager apps, so they're not a fit for this slot at all. MMRL
(MMRLApp/MMRL, 2.1k+ stars) is the most actively maintained manager app in
this space: it supports Magisk, KernelSU, and APatch as interchangeable root
providers (`platform/`, `compat/`), ships its own CLI and WebUI-X companion
projects, and has a template-repository project for anyone hosting a module
repo — evidence of a real ecosystem around it rather than a one-person tool
that might go stale.
