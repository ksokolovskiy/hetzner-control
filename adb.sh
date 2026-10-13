#!/usr/bin/env sh
set -eu

TOOLCHAINS=/home/ksoko/it/kite-reader-poc/.toolchains
WINDOWS_ADB="$TOOLCHAINS/windows/platform-tools/adb.exe"
LINUX_ADB="$TOOLCHAINS/debian-android/usr/bin/adb"

if [ -x "$WINDOWS_ADB" ]; then
    exec "$WINDOWS_ADB" "$@"
fi
if [ -x "$LINUX_ADB" ]; then
    exec "$LINUX_ADB" "$@"
fi
echo "adb not found" >&2
exit 127
