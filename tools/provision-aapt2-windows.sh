#!/usr/bin/env sh
set -eu

TOOL_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
AAPT2_BINARY="${HCLOUD_AAPT2_WINDOWS:-$TOOL_DIR/aapt2.exe}"
ARCHIVE="$TOOL_DIR/aapt2-windows.jar"
VERSION="8.7.3-12006047"
EXPECTED_SHA256="9406af8e4742da01e4ce65773e23bda40ed5f599917b38cacc261c9ba0952331"
URL="https://dl.google.com/android/maven2/com/android/tools/build/aapt2/$VERSION/aapt2-$VERSION-windows.jar"

if [ -f "$AAPT2_BINARY" ]; then
    exit 0
fi

if [ "$AAPT2_BINARY" != "$TOOL_DIR/aapt2.exe" ]; then
    echo "HCLOUD_AAPT2_WINDOWS points to a missing file: $AAPT2_BINARY" >&2
    exit 1
fi

command -v curl >/dev/null 2>&1 || { echo "curl is required to provision AAPT2." >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required to provision AAPT2." >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required to verify AAPT2." >&2; exit 1; }

tmp_archive="$(mktemp "$TOOL_DIR/aapt2-windows.XXXXXX.jar")"
tmp_binary="$(mktemp "$TOOL_DIR/aapt2.XXXXXX.exe")"
cleanup() { rm -f "$tmp_archive" "$tmp_binary"; }
trap cleanup EXIT HUP INT TERM

echo "Downloading official Android AAPT2 $VERSION for Windows…" >&2
curl --fail --location --retry 3 --silent --show-error "$URL" --output "$tmp_archive"
actual_sha256="$(sha256sum "$tmp_archive" | awk '{print $1}')"
if [ "$actual_sha256" != "$EXPECTED_SHA256" ]; then
    echo "AAPT2 download checksum did not match the expected official artifact." >&2
    exit 1
fi

unzip -p "$tmp_archive" aapt2.exe > "$tmp_binary"
chmod +x "$tmp_binary"
mv "$tmp_archive" "$ARCHIVE"
mv "$tmp_binary" "$AAPT2_BINARY"

echo "Provisioned Windows AAPT2 for ARM64 WSL." >&2
