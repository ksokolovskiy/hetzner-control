#!/usr/bin/env sh
# Read-only, allowlisted device/app metadata. Never collect UI, logs or app storage.
set -eu
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
case "${1:-}" in
    --help) echo 'Usage: sh tools/device-acceptance-info.sh (optional HCLOUD_ADB_SERIAL)'; exit 0 ;;
    '') [ "$#" -eq 0 ] || exit 2 ;;
    *) echo 'Unsupported argument.' >&2; exit 2 ;;
esac
ADB="$PROJECT_DIR/adb.sh"
. "$PROJECT_DIR/tools/adb-device.sh"
serial="$(hcloud_select_adb_device "$ADB")"
for property in ro.product.manufacturer ro.product.model ro.product.cpu.abi ro.build.version.release ro.build.version.sdk; do
    value="$("$ADB" -s "$serial" shell getprop "$property" | tr -d '\r\n')"
    printf '%s=%s\n' "$property" "$value"
done
package_metadata="$("$ADB" -s "$serial" shell dumpsys package com.hcloudcontrol)"
printf '%s\n' "$package_metadata" | tr -d '\r' | awk '
    $1 ~ /^versionCode=[0-9]+$/ { print $1; found=1 }
    $1 ~ /^versionName=/ { print $1; found=1 }
    END { if (!found) print "app_version=unavailable" }
'
pid="$("$ADB" -s "$serial" shell pidof com.hcloudcontrol 2>/dev/null | tr -d '\r' || true)"
case "$pid" in ''|*[!0-9\ ]*) echo 'app_pid=not_running_or_unavailable' ;; *) printf 'app_pid=%s\n' "$pid" ;; esac
