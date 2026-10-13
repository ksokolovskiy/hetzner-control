#!/usr/bin/env sh
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || "$PROJECT_DIR/build.sh"
"$PROJECT_DIR/adb.sh" install -r "$APK"
"$PROJECT_DIR/adb.sh" shell am start -n com.hcloudcontrol/.MainActivity
