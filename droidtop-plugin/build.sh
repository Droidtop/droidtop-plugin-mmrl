#!/usr/bin/env bash
# Compiles, dexes and hashes droidtop.mmrl-modules's payload (three
# source files: RootShell.kt, RootProvider.kt, ModuleManager.kt,
# MmrlPlugin.kt) in the exact shape PluginBundleInstaller.install()
# expects (Droidtop/droidtop docs/SPEC.md 12a). Same build.sh shape as
# droidtop's own samples/plugin-sample-statustile and this project's
# droidtop-plugin-shizuku sibling.
#
# Split from signing (see sign.sh) on purpose: this script never touches
# the plugin origin's private key and is safe to run in CI. Only
# droidtop-dev, which holds the private half at
# /root/coordination/keys/droidtop-plugins/droidtop-origin-private.pem,
# ever runs sign.sh.
#
# Prerequisites:
#   - kotlinc on PATH (matching Droidtop/droidtop's gradle/libs.versions.toml
#     "kotlin" entry)
#   - d8 on PATH (Android SDK build-tools)
#   - PLUGIN_HOST_CLASSPATH pointing at a compiled :plugin-host classes.jar
#     (from a Droidtop/droidtop checkout, e.g. its plugin-host-debug.aar's
#     classes.jar)
#   - ANDROID_JAR pointing at android.jar for :plugin-host's compileSdk
#
# Optionally, for a one-shot local build+sign (droidtop-dev only): also set
# PLUGIN_SIGNING_KEY and this script calls sign.sh itself at the end.

set -euo pipefail
cd "$(dirname "$0")"

: "${PLUGIN_HOST_CLASSPATH:?set to a jar/dir containing dev.droidtop.pluginhost.* compiled classes}"
: "${ANDROID_JAR:?set ANDROID_JAR to android.jar for the target compileSdk}"

rm -rf build
mkdir -p build/classes


# org.json (used for the modules_json row encoding) lives in android.jar
# itself -- a platform framework API on Android, not a separate library
# -- so kotlinc needs it on ITS OWN classpath too, not just d8's --lib
# below; a plugin with no org.json usage (the sample) never surfaced
# this.
kotlinc -cp "$PLUGIN_HOST_CLASSPATH:$ANDROID_JAR" -d build/classes \
  src/dev/droidtop/plugins/mmrl/RootShell.kt \
  src/dev/droidtop/plugins/mmrl/RootProvider.kt \
  src/dev/droidtop/plugins/mmrl/ModuleManager.kt \
  src/dev/droidtop/plugins/mmrl/MmrlPlugin.kt

d8 --output build --lib "$ANDROID_JAR" \
  $(find build/classes -name '*.class')

# classes.jar is a zip containing classes.dex at its root -- what
# DexClassLoader (PluginRuntimeService) expects.
(cd build && zip -q classes.jar classes.dex)

CLASSES_SHA=$(sha256sum build/classes.jar | cut -d' ' -f1)

python3 - "$CLASSES_SHA" <<'PY'
import json, sys
sha = sys.argv[1]
manifest = json.load(open("manifest.template.json"))
manifest["payload"] = [{"path": "classes.jar", "sha256": sha}]
json.dump(manifest, open("build/manifest.json", "w"), indent=2, sort_keys=True)
PY

echo "Built build/classes.jar and build/manifest.json (unsigned)"
sha256sum build/classes.jar

if [ -n "${PLUGIN_SIGNING_KEY:-}" ]; then
  PLUGIN_SIGNING_KEY="$PLUGIN_SIGNING_KEY" ./sign.sh
else
  echo "PLUGIN_SIGNING_KEY not set -- stopping here, unsigned."
  echo "Run ./sign.sh with PLUGIN_SIGNING_KEY set (droidtop-dev only; the key never leaves that host) to produce droidtop.mmrl-modules.droidplugin.tar.xz."
fi
