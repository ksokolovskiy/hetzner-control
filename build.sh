#!/usr/bin/env sh
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# The project never assumes another checkout's toolchain. Use an explicitly
# selected toolchain root or the provisioned, ignored project-local directory.
TOOLCHAINS="${HCLOUD_TOOLCHAINS:-$PROJECT_DIR/.toolchains}"
export JAVA_HOME="${HCLOUD_JAVA_HOME:-${JAVA_HOME:-$TOOLCHAINS/jdk21}}"
export ANDROID_HOME="${HCLOUD_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$TOOLCHAINS/android-sdk}}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/.gradle-user-home}"
GRADLE_BIN="${HCLOUD_GRADLE_BIN:-$TOOLCHAINS/gradle-8.13/bin/gradle}"

[ -x "$JAVA_HOME/bin/java" ] || {
    echo "JDK is unavailable: $JAVA_HOME. Set HCLOUD_JAVA_HOME/HCLOUD_TOOLCHAINS or run tools/provision-build-toolchain.sh." >&2
    exit 1
}
[ -d "$ANDROID_HOME" ] || {
    echo "Android SDK is unavailable: $ANDROID_HOME. Set HCLOUD_ANDROID_SDK_ROOT/HCLOUD_TOOLCHAINS or run tools/provision-build-toolchain.sh." >&2
    exit 1
}
[ -x "$GRADLE_BIN" ] || {
    echo "Gradle 8.13 is unavailable: $GRADLE_BIN. Set HCLOUD_GRADLE_BIN/HCLOUD_TOOLCHAINS or run tools/provision-build-toolchain.sh." >&2
    exit 1
}

# Android's Maven AAPT2 binary is x86_64 Linux. On ARM64 WSL this project uses
# the local QEMU runner behind tools/aapt2; it needs no sudo or Windows path.
if [ "$#" -eq 0 ]; then
    set -- assembleDebug
fi

if [ "$(uname -m)" = "aarch64" ] || [ "$(uname -m)" = "arm64" ]; then
    AAPT2_WRAPPER="${HCLOUD_AAPT2_WRAPPER:-$PROJECT_DIR/tools/aapt2}"
    if [ ! -x "$AAPT2_WRAPPER" ]; then
        echo "Missing ARM64 WSL AAPT2 wrapper: $AAPT2_WRAPPER" >&2
        exit 1
    fi
    set -- "-Pandroid.aapt2FromMavenOverride=$AAPT2_WRAPPER" "$@"
else
    set -- "$@"
fi

exec "$GRADLE_BIN" --project-dir "$PROJECT_DIR" "$@"
