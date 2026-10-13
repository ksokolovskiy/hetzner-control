#!/usr/bin/env sh
set -eu

TOOLCHAINS=/home/ksoko/it/kite-reader-poc/.toolchains
WINDOWS_ADB="$TOOLCHAINS/windows/platform-tools/adb.exe"
LINUX_ADB="$TOOLCHAINS/debian-android/usr/bin/adb"

if [ -x "$WINDOWS_ADB" ]; then
    # Wi-Fi debugging ports rotate. Discover the current TLS endpoint and
    # connect automatically, reusing the trusted Windows ADB pairing.
    case " ${*:-} " in
        *" devices "*|*" mdns "*|*" connect "*) exec "$WINDOWS_ADB" "$@" ;;
    esac
    has_serial=false
    for arg in "$@"; do [ "$arg" = "-s" ] && has_serial=true; done
    if [ "$has_serial" = false ]; then
        serial="${HCLOUD_ADB_SERIAL:-}"
        if [ -z "$serial" ]; then
            serial=$("$WINDOWS_ADB" mdns services 2>/dev/null | tr -d '\r' | awk '$2 == "_adb-tls-connect._tcp" { print $3; exit }')
        fi
        if [ -n "$serial" ]; then
            "$WINDOWS_ADB" connect "$serial" >/dev/null 2>&1 || true
            set -- -s "$serial" "$@"
        fi
    fi
    exec "$WINDOWS_ADB" "$@"
fi
if [ -x "$LINUX_ADB" ]; then
    exec "$LINUX_ADB" "$@"
fi
echo "adb not found" >&2
exit 127
