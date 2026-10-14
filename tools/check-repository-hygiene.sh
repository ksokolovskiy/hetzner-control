#!/usr/bin/env sh
# Guard tracked source against common accidental publication hazards.
set -eu

PROJECT_DIR="${HCLOUD_REPOSITORY_ROOT:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"

fail() {
    echo "Repository hygiene check failed: $1" >&2
    exit 1
}

[ -d "$PROJECT_DIR/.git" ] || fail "a Git worktree is required"

tracked_files="$(git -C "$PROJECT_DIR" ls-files)"
prohibited_file_pattern='(^|/)(local\.properties|\.env($|[^/]*$)|keystore\.properties|signing\.properties|google-services\.json|.*\.(jks|keystore|p12|pfx|pem|key)|.*service-account.*\.json|credentials.*\.json)$'
prohibited_files="$(printf '%s\n' "$tracked_files" | rg -i "$prohibited_file_pattern" | rg -v '(^|/)\.env\.example$' || true)"
if [ -n "$prohibited_files" ]; then
    printf '%s\n' "$prohibited_files" >&2
    fail "a local credential, signing, or environment file is tracked"
fi

# Private LAN/loopback endpoints and credential-shaped strings must not be in
# tracked text. Documentation must use placeholders, not a developer's phone
# or server address. The pattern is intentionally conservative; this is a
# guardrail, not a replacement for a maintained secret scanner in CI.
credential_content_pattern='(-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|Bearer[[:space:]]+[A-Za-z0-9._~-]{20,})'
if git -C "$PROJECT_DIR" grep -n -I -E "$credential_content_pattern" -- .; then
    fail "a credential-shaped value is present in tracked text"
fi

# RFC1918 ranges are valid contract fixtures for Cloud Networks, so do not
# reject them under src/test. Credentials above are still scanned everywhere,
# including tests. Outside tests, private endpoints remain forbidden.
private_endpoint_pattern='(^|[^0-9])(10|127)\.[0-9]{1,3}\.[0-9]{1,3}|192\.168\.[0-9]{1,3}\.[0-9]{1,3}|172\.(1[6-9]|2[0-9]|3[0-1])\.[0-9]{1,3}\.[0-9]{1,3}'
if git -C "$PROJECT_DIR" grep -n -I -E "$private_endpoint_pattern" -- . ':(exclude)**/src/test/**'; then
    fail "a private endpoint is present outside tracked test fixtures"
fi

echo "Repository hygiene check passed."
