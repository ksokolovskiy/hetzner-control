#!/usr/bin/env sh
# Regression test for tools/check-repository-hygiene.sh. It creates only
# disposable local Git indexes and never contacts a network service.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
CHECKER="$PROJECT_DIR/tools/check-repository-hygiene.sh"
TEMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TEMP_DIR"; }
trap cleanup EXIT HUP INT TERM

expect_rejected() {
    fixture="$1"
    mkdir -p "$TEMP_DIR/$fixture"
    git -C "$TEMP_DIR/$fixture" init -q
    printf '%s\n' "$2" > "$TEMP_DIR/$fixture/$3"
    git -C "$TEMP_DIR/$fixture" add "$3"
    if HCLOUD_REPOSITORY_ROOT="$TEMP_DIR/$fixture" sh "$CHECKER" >/dev/null 2>&1; then
        echo "Expected hygiene checker to reject $fixture" >&2
        exit 1
    fi
}

private_ip_prefix="192"
private_ip="$private_ip_prefix.168.1.9:5555"
token_prefix="gh"
token="$token_prefix"p_abcdefghijklmnopqrstuvwxyz123456
expect_rejected "private-endpoint" "HCLOUD_ADB_SERIAL=$private_ip" ".env.example"
expect_rejected "credential" "token=$token" "sample.txt"
expect_rejected "signing-file" "placeholder" "release.jks"

echo "Repository hygiene regression test passed."
