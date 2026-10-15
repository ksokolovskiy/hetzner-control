#!/usr/bin/env sh
# Synthetic discovery tests only: no real ADB, phone, network or cloud access.
set -eu
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
. "$PROJECT_DIR/tools/adb-device.sh"
hcloud_fake_adb() {
    case "$*" in
        'mdns services')
            case "$fixture" in
                one) echo 'fixture _adb-tls-connect._tcp 192.0.2.1:5555' ;;
                many) printf '%s\n' 'one _adb-tls-connect._tcp 192.0.2.1:5555' 'two _adb-tls-connect._tcp 192.0.2.2:5555' ;;
            esac ;;
        devices) printf '%s\n' 'List of devices attached' 'usb-fixture device' ;;
        'connect '*) return 0 ;;
        '-s '*' get-state') if [ "$fixture" = offline ]; then echo offline; else echo device; fi ;;
        *) echo 'Unexpected command in synthetic test' >&2; return 99 ;;
    esac
}
unset HCLOUD_ADB_SERIAL
fixture=one
[ "$(hcloud_select_adb_device hcloud_fake_adb)" = '192.0.2.1:5555' ]
fixture=many
if hcloud_select_adb_device hcloud_fake_adb >/dev/null 2>&1; then exit 1; fi
fixture=fallback
[ "$(hcloud_select_adb_device hcloud_fake_adb)" = usb-fixture ]
fixture=offline
if hcloud_select_adb_device hcloud_fake_adb >/dev/null 2>&1; then exit 1; fi
fixture=many
HCLOUD_ADB_SERIAL='explicit-fixture'
[ "$(hcloud_select_adb_device hcloud_fake_adb)" = explicit-fixture ]
HCLOUD_ADB_SERIAL='bad selector'
if hcloud_select_adb_device hcloud_fake_adb >/dev/null 2>&1; then exit 1; fi
# An options-only Gradle invocation runs help and can silently install an old APK.
grep -Fq '"$PROJECT_DIR/build.sh" :app:assembleDebug --console=plain' "$PROJECT_DIR/tools/deploy-to-phone.sh"
echo 'PASS: six synthetic ADB selection cases and explicit APK build task; no device was accessed.'
