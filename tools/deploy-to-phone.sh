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

discover_serial() {
    if [ -n "${HCLOUD_ADB_SERIAL:-}" ]; then
        printf '%s\n' "$HCLOUD_ADB_SERIAL"
        return
    fi

    # Prefer the TLS endpoint announced by Wireless debugging. adb.sh delegates
    # this unchanged to the Windows ADB client when that is the active client.
    mdns_serials="$($ADB mdns services 2>/dev/null | tr -d '\r' | awk '$2 == "_adb-tls-connect._tcp" { print $3 }' | sort -u)"
    mdns_count="$(printf '%s\n' "$mdns_serials" | awk 'NF { count++ } END { print count + 0 }')"
    if [ "$mdns_count" -eq 1 ]; then
        printf '%s\n' "$mdns_serials"
        return
    fi
    if [ "$mdns_count" -gt 1 ]; then
        fail "multiple Wi-Fi debugging devices found; set HCLOUD_ADB_SERIAL to one endpoint"
    fi

    # A previously connected device might not be advertising mDNS right now.
    online_serials="$($ADB devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }' | sort -u)"
    online_count="$(printf '%s\n' "$online_serials" | awk 'NF { count++ } END { print count + 0 }')"
    if [ "$online_count" -eq 1 ]; then
        printf '%s\n' "$online_serials"
        return
    fi
    if [ "$online_count" -gt 1 ]; then
        fail "multiple ADB devices are online; set HCLOUD_ADB_SERIAL explicitly"
    fi
    fail "no Wi-Fi ADB device found; enable Wireless debugging or set HCLOUD_ADB_SERIAL"
}

serial="$(discover_serial)"
case "$serial" in
    *"\n"*|"") fail "ADB device serial is invalid" ;;
esac

# Connecting is harmless for an already connected endpoint and lets an explicit
# HCLOUD_ADB_SERIAL work for a freshly discovered Wi-Fi debugging port.
"$ADB" connect "$serial" >/dev/null 2>&1 || true
state="$("$ADB" -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
[ "$state" = "device" ] || fail "ADB endpoint is not ready: $serial"

echo "Verifying source boundaries…"
sh "$PROJECT_DIR/tools/check-sensitive-data-boundaries.sh"

echo "Verifying local ARM64 WSL AAPT2 runner…"
verify_known_build_toolchain

echo "Building debug APK…"
# This is the successful local build path recorded by Gradle: build.sh pins
# Gradle 9.4.1/JDK 21/Android SDK and passes
# -Pandroid.aapt2FromMavenOverride=$PROJECT_DIR/tools/aapt2. Do not accept an
# environment override here: deployment must use the verified local runner.
unset HCLOUD_AAPT2_WRAPPER HCLOUD_NATIVE_AAPT2 HCLOUD_X86_AAPT2 HCLOUD_TOOLCHAINS HCLOUD_QEMU_RUNNER_DIR
"$PROJECT_DIR/build.sh" --console=plain

[ -f "$APK" ] || fail "successful build did not produce $APK"

echo "Installing debug APK on $serial…"
"$ADB" -s "$serial" install -r "$APK"
"$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null
echo "Installed and launched HCloud Control."
