#!/usr/bin/env sh
# Build a signed Android App Bundle suitable for an upload to a Google Play
# closed-testing track. The upload key is intentionally supplied only by the
# ignored signing.properties file; no credential is accepted from source.
set -eu

PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SIGNING_FILE="$PROJECT_DIR/signing.properties"

if [ ! -r "$SIGNING_FILE" ]; then
    echo "Missing signing.properties. Copy signing.properties.example to that ignored path and fill it outside Git." >&2
    exit 1
fi

cd "$PROJECT_DIR"
sh tools/check-sensitive-data-boundaries.sh
sh tools/check-repository-hygiene.sh
./build.sh -PrequirePlaySigning=true :app:bundleRelease :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain

BUNDLE="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"
[ -f "$BUNDLE" ] || {
    echo "Signed AAB was not produced: $BUNDLE" >&2
    exit 1
}

sha256sum "$BUNDLE"
echo "Signed Play bundle is ready: $BUNDLE"
