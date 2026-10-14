#!/usr/bin/env sh
# Provision an unprivileged x86_64 runner for Android's current AAPT2.
# ARM64 WSL does not need a system QEMU installation: qemu-user is downloaded
# and unpacked under tools/.qemu-user, which is intentionally gitignored.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
RUNNER_DIR="${HCLOUD_QEMU_RUNNER_DIR:-$PROJECT_DIR/tools/.qemu-user}"
QEMU="$RUNNER_DIR/usr/bin/qemu-x86_64"
TOOLCHAINS="${HCLOUD_TOOLCHAINS:-$PROJECT_DIR/.toolchains}"
X86_ROOT="$TOOLCHAINS/x86_64-linux"
X86_LIB_DIR="$X86_ROOT/usr/lib/x86_64-linux-gnu"
X86_LOADER_DIR="$X86_ROOT/usr/lib64"
SYSROOT="$RUNNER_DIR/sysroot"

if [ "$(uname -m)" != "aarch64" ] && [ "$(uname -m)" != "arm64" ]; then
    exit 0
fi

if [ ! -x "$QEMU" ]; then
    command -v apt-get >/dev/null 2>&1 || {
        echo "Cannot provision AAPT2 runner: apt-get is unavailable." >&2
        exit 1
    }
    command -v dpkg-deb >/dev/null 2>&1 || {
        echo "Cannot provision AAPT2 runner: dpkg-deb is unavailable." >&2
        exit 1
    }

    temp_dir="$(mktemp -d)"
    cleanup() { rm -rf "$temp_dir"; }
    trap cleanup EXIT HUP INT TERM
    (
        cd "$temp_dir"
        # This downloads a package only; it never invokes sudo or apt install.
        apt-get download qemu-user >/dev/null
        package="$(find . -maxdepth 1 -type f -name 'qemu-user_*.deb' -print -quit)"
        [ -n "$package" ] || {
            echo "qemu-user download did not produce a Debian package." >&2
            exit 1
        }
        mkdir -p "$RUNNER_DIR"
        dpkg-deb -x "$package" "$RUNNER_DIR"
    )
fi

[ -x "$QEMU" ] || {
    echo "AAPT2 runner was not provisioned: $QEMU" >&2
    exit 1
}
[ -d "$TOOLCHAINS" ] || {
    echo "Build toolchain is unavailable: $TOOLCHAINS. Set HCLOUD_TOOLCHAINS or run tools/provision-build-toolchain.sh." >&2
    exit 1
}
[ -d "$X86_LIB_DIR" ] || {
    echo "x86_64 AAPT2 sysroot is missing: $X86_LIB_DIR" >&2
    exit 1
}
[ -r "$X86_LOADER_DIR/ld-linux-x86-64.so.2" ] || {
    echo "x86_64 AAPT2 dynamic loader is missing: $X86_LOADER_DIR/ld-linux-x86-64.so.2" >&2
    exit 1
}

mkdir -p "$SYSROOT"
# qemu's -L/QEMU_LD_PREFIX resolves the ELF interpreter /lib64/ld-linux-x86-64.so.2
# beneath this directory. The loader is deliberately kept distinct from the
# shared-library directory in the pinned toolchain.
ln -sfn "$X86_LOADER_DIR" "$SYSROOT/lib64"
ln -sfn "$X86_LIB_DIR" "$SYSROOT/usr-lib-x86_64"
