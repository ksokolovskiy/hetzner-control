#!/usr/bin/env sh
# Build, verify, install, and launch the debug app on one Wi-Fi ADB device.
# This script never reads application tokens or other in-app credential data.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ADB="$PROJECT_DIR/adb.sh"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.hcloudcontrol"
ACTIVITY="$PACKAGE/.MainActivity"
NATIVE_AAPT2="$PROJECT_DIR/tools/aapt2"
EXPECTED_AAPT2_VERSION="Android Asset Packaging Tool (aapt) 2.20-13193326"

fail() {
    echo "Deploy failed: $1" >&2
    exit 1
}

[ -x "$ADB" ] || fail "ADB helper is missing or not executable: $ADB"

verify_known_build_toolchain() {
    [ -x "$NATIVE_AAPT2" ] || fail "AAPT2 runner wrapper is missing: $NATIVE_AAPT2"
    sh "$PROJECT_DIR/tools/provision-aapt2-runner.sh" || fail "could not provision local AAPT2 runner"
    aapt2_version="$("$NATIVE_AAPT2" version 2>&1 || true)"
    [ "$aapt2_version" = "$EXPECTED_AAPT2_VERSION" ] || fail "current Android SDK AAPT2 is unavailable (expected: $EXPECTED_AAPT2_VERSION)"
}

. "$PROJECT_DIR/tools/adb-device.sh"
serial="$(hcloud_select_adb_device "$ADB")"

echo "Verifying source boundaries…"
sh "$PROJECT_DIR/tools/check-sensitive-data-boundaries.sh"

echo "Verifying local ARM64 WSL AAPT2 runner…"
verify_known_build_toolchain

echo "Building debug APK…"
# This is the successful local build path recorded by Gradle: build.sh pins
# Gradle 8.13/JDK 21/Android SDK and passes
# -Pandroid.aapt2FromMavenOverride=$PROJECT_DIR/tools/aapt2. Do not accept an
# environment override here: deployment must use the verified local runner.
unset HCLOUD_AAPT2_WRAPPER HCLOUD_NATIVE_AAPT2 HCLOUD_X86_AAPT2 HCLOUD_TOOLCHAINS HCLOUD_QEMU_RUNNER_DIR
"$PROJECT_DIR/build.sh" :app:assembleDebug --console=plain

[ -f "$APK" ] || fail "successful build did not produce $APK"

echo "Installing debug APK on $serial…"
"$ADB" -s "$serial" install -r "$APK"
"$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null
echo "Installed and launched HCloud Control."
