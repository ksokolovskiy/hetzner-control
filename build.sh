#!/usr/bin/env sh
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
TOOLCHAINS=/home/ksoko/it/kite-reader-poc/.toolchains
export JAVA_HOME="$TOOLCHAINS/jdk21"
export ANDROID_HOME="$TOOLCHAINS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle-user-home}"

exec "$TOOLCHAINS/gradle-9.4.1/bin/gradle" --project-dir "$PROJECT_DIR" assembleDebug "$@"
