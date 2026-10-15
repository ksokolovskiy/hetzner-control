#!/usr/bin/env sh
# Sourceable single-device selection shared by deployment and read-only diagnostics.
# The caller supplies the existing adb.sh wrapper; credentials are never accessed.
hcloud_select_adb_device() {
    hcloud_adb_helper="$1"
    hcloud_serial="${HCLOUD_ADB_SERIAL:-}"
    if [ -z "$hcloud_serial" ]; then
        hcloud_serials="$("$hcloud_adb_helper" mdns services 2>/dev/null | tr -d '\r' | awk '$2 == "_adb-tls-connect._tcp" { print $3 }' | sort -u)"
        hcloud_count="$(printf '%s\n' "$hcloud_serials" | awk 'NF { count++ } END { print count + 0 }')"
        if [ "$hcloud_count" -gt 1 ]; then
            echo 'Multiple Wi-Fi ADB devices found; set HCLOUD_ADB_SERIAL explicitly.' >&2
            return 1
        fi
        if [ "$hcloud_count" -eq 1 ]; then hcloud_serial="$hcloud_serials"; fi
    fi
    if [ -z "$hcloud_serial" ]; then
        hcloud_serials="$("$hcloud_adb_helper" devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }' | sort -u)"
        hcloud_count="$(printf '%s\n' "$hcloud_serials" | awk 'NF { count++ } END { print count + 0 }')"
        if [ "$hcloud_count" -ne 1 ]; then
            echo 'Expected exactly one ready ADB device; enable Wireless debugging or set HCLOUD_ADB_SERIAL.' >&2
            return 1
        fi
        hcloud_serial="$hcloud_serials"
    fi
    case "$hcloud_serial" in ''|*[[:space:]]*) echo 'Invalid ADB device selector.' >&2; return 1 ;; esac
    "$hcloud_adb_helper" connect "$hcloud_serial" >/dev/null 2>&1 || true
    hcloud_state="$("$hcloud_adb_helper" -s "$hcloud_serial" get-state 2>/dev/null | tr -d '\r' || true)"
    if [ "$hcloud_state" != device ]; then
        echo 'The selected ADB device is not ready.' >&2
        return 1
    fi
    printf '%s\n' "$hcloud_serial"
}
