#!/usr/bin/env sh
# Explicit future-release helper. This script is never called by build.sh.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SDK_ROOT="${HCLOUD_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
MODE="${1:---check}"

usage() {
    cat <<'EOF'
Usage:
  tools/provision-api36-prerequisites.sh --check
  HCLOUD_ANDROID_SDK_ROOT=/absolute/sdk/path tools/provision-api36-prerequisites.sh --install

--check   Report whether this SDK has Android API 36 prerequisites. It changes nothing.
--install Install only platforms;android-36 and build-tools;36.0.0 into the selected SDK.
          The Android SDK manager displays its own licence prompts; this script never accepts
          licences automatically and never uses sudo or modifies global shell configuration.

This helper does not upgrade AGP, Gradle, JDK, app source, or targetSdk.
EOF
}

case "$MODE" in
    --check|--install) ;;
    --help|-h) usage; exit 0 ;;
    *) usage >&2; exit 64 ;;
esac

if [ -z "$SDK_ROOT" ]; then
    echo "Set HCLOUD_ANDROID_SDK_ROOT (preferred), ANDROID_SDK_ROOT, or ANDROID_HOME to an existing local SDK." >&2
    exit 2
fi

if [ ! -d "$SDK_ROOT" ]; then
    echo "Android SDK directory does not exist: $SDK_ROOT" >&2
    exit 2
fi

SDK_MANAGER=""
for candidate in \
    "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
    "$SDK_ROOT/cmdline-tools/bin/sdkmanager"; do
    if [ -x "$candidate" ]; then
        SDK_MANAGER="$candidate"
        break
    fi
done

if [ -z "$SDK_MANAGER" ]; then
    echo "sdkmanager was not found under $SDK_ROOT/cmdline-tools. Install Android command-line tools into this SDK first." >&2
    exit 2
fi

agp_version="$(sed -n 's/^agp = "\([^"]*\)"/\1/p' "$PROJECT_DIR/gradle/libs.versions.toml" | head -n 1)"
platform_jar="$SDK_ROOT/platforms/android-36/android.jar"
build_tools_dir="$SDK_ROOT/build-tools/36.0.0"

echo "SDK root: $SDK_ROOT"
echo "AGP configured: ${agp_version:-unknown} (API 36 requires at least 8.9.1)"
echo "Gradle prerequisite after AGP upgrade: 8.11.1 or compatible newer version"
echo "JDK prerequisite: 17"

if [ "$MODE" = "--install" ]; then
    echo "Installing Android API 36 packages into the selected local SDK…" >&2
    "$SDK_MANAGER" --install "platforms;android-36" "build-tools;36.0.0"
fi

missing=0
if [ -f "$platform_jar" ]; then
    echo "OK: platforms;android-36"
else
    echo "MISSING: platforms;android-36" >&2
    missing=1
fi

if [ -d "$build_tools_dir" ]; then
    echo "OK: build-tools;36.0.0"
else
    echo "MISSING: build-tools;36.0.0" >&2
    missing=1
fi

if [ "$missing" -ne 0 ]; then
    echo "No project versions were changed. Re-run with --install only when the API 36 migration is approved." >&2
    exit 1
fi

echo "SDK prerequisites are installed. This alone does not make the project API 36-ready."
