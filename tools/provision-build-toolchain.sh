#!/usr/bin/env sh
# Create the project-local, ignored build toolchain from an explicitly chosen
# compatible source. This never uses sudo, downloads nothing and never guesses
# another checkout's cache. It deliberately refuses to overwrite a directory.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DESTINATION="${HCLOUD_TOOLCHAINS:-$PROJECT_DIR/.toolchains}"
SOURCE="${HCLOUD_TOOLCHAIN_SOURCE:-}"

fail() {
    echo "Toolchain provisioning failed: $1" >&2
    exit 1
}

check_toolchain() {
    root="$1"
    [ -x "$root/jdk21/bin/java" ] || return 1
    [ -x "$root/gradle-9.4.1/bin/gradle" ] || return 1
    [ -d "$root/android-sdk" ] || return 1
    [ -d "$root/x86_64-linux/usr/lib/x86_64-linux-gnu" ] || return 1
    [ -r "$root/x86_64-linux/usr/lib64/ld-linux-x86-64.so.2" ] || return 1
}

case "${1:-}" in
    --check)
        check_toolchain "$DESTINATION" || fail "incomplete project toolchain at $DESTINATION"
        echo "Project build toolchain is ready: $DESTINATION"
        exit 0
        ;;
    "") ;;
    *)
        echo "Usage: HCLOUD_TOOLCHAIN_SOURCE=/compatible/toolchains sh tools/provision-build-toolchain.sh [--check]" >&2
        exit 2
        ;;
esac

[ -n "$SOURCE" ] || fail "HCLOUD_TOOLCHAIN_SOURCE must name a compatible source toolchain"
check_toolchain "$SOURCE" || fail "source toolchain is incomplete: $SOURCE"
[ ! -e "$DESTINATION" ] || fail "destination already exists: $DESTINATION (run --check or choose HCLOUD_TOOLCHAINS)"

mkdir -p "$(dirname -- "$DESTINATION")"
mkdir "$DESTINATION"
for component in jdk21 gradle-9.4.1 android-sdk x86_64-linux; do
    cp -a "$SOURCE/$component" "$DESTINATION/"
done

check_toolchain "$DESTINATION" || fail "copied toolchain did not pass validation: $DESTINATION"
echo "Project build toolchain provisioned: $DESTINATION"
