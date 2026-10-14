# Security and privacy notes

This document describes the architecture in the current source tree. It is not
a legal privacy policy and must be reviewed again before a public release.

## Hetzner Cloud API tokens

- A Cloud project connection consists of a display name, a generated local ID,
  and its own API token. Tokens are intentionally not shared between projects.
- The connection metadata and each token are stored in AndroidX
  `EncryptedSharedPreferences` in the `hcloud_secure_credentials` local
  preference file. Preference keys use AES-256-SIV and values use AES-256-GCM.
- The master key is created with AndroidX `MasterKey` using AES-256-GCM and is
  held by Android Keystore. This protects the stored values at rest on the
  device; it is not a substitute for keeping the device itself secure.
- The app reads only the active project's token at request time. Its network
  interceptor adds that token to the HTTPS `Authorization: Bearer …` header
  for the Hetzner Cloud API endpoint (`https://api.hetzner.cloud/v1/`).
- Cloud DNS is a separately acknowledged, read-only screen. It uses only the
  selected Cloud project's token and the Cloud API host; it does not accept,
  store, or send a legacy DNS Console token. The DNS repository intentionally
  has no mutation, import, export, or zone-file operations.
- The current source contains no application backend, token synchronization,
  remote configuration, analytics SDK, crash-reporting SDK, or HTTP logging
  interceptor. API requests go directly from the device to Hetzner Cloud.
- Removing a project removes its token and metadata from this local encrypted
  preference store. Android backup is disabled in the manifest
  (`android:allowBackup="false"`).

Use a distinct Hetzner Cloud API token for each project and revoke it in
Hetzner Cloud when a device is lost, a project is removed, or access is no
longer required.

## Storage Box API tokens

- Storage Box connections use a separate encrypted preference file and a
  separate Android Keystore-backed credential boundary.
- A Storage Box token is never read from, copied into, or sent by the Cloud
  project connection flow. Likewise, a Cloud token is never reused for Storage
  Box requests.
- The Storage Box client talks to the documented Storage Box API host, not to
  the Cloud API host. It currently exposes inventory, folders, snapshots and
  subaccount identity. Creating a manual snapshot is an enabled mutation;
  deleting, restoring, or changing Storage Box accounts is not implemented.
- Storage Box subaccount passwords and other access secrets are neither
  requested nor shown. Names and usernames returned by the API are ordinary
  resource metadata and may be displayed locally while that screen is open.
- Do not put either kind of token in local environment files, sample
  configuration, source, issue reports, screenshots, or documentation.

## External test-mock profiles

- A test-mock profile is an explicit, separately stored Cloud connection kind.
  It has its own token and fixed test routing; selecting it never changes the
  real Cloud profile's token or route.
- The app does not bundle test credentials, provide a hidden endpoint switch,
  or turn a blank/legacy sandbox record into a usable connection. A test
  profile without its own credential is discarded during migration.
- A mock service is test infrastructure, not a security boundary or an
  accurate substitute for production authorization. Never enter a production
  Hetzner Cloud token into a test-mock profile.

## One-time root passwords

Hetzner may return a root password only in the successful server-creation
response when no suitable SSH key is selected. The app displays that value in a
confirmation dialog so the user can copy it into a password manager. It is
kept only in transient in-memory UI state and is not written to the token
store, files, logs, or analytics.

The user should save the password immediately and change it after the first
login. Prefer an existing SSH key where possible.

## Data handled locally

In addition to encrypted connection records, the running app temporarily holds
resource data returned by Hetzner APIs (for example server names, IDs,
locations, IP addresses, image metadata, SSH-key fingerprints, Storage Box
names, folder paths, snapshot metadata, and DNS record values) in memory to
render the UI. The current implementation does not define a local database or
backend upload for this resource data.

## Release artefacts and checks

Before an open-source or Google Play release, prepare and review:

1. A Git tag, changelog, source archive, chosen open-source `LICENSE`, and
   third-party notices/dependency inventory.
2. A signed Android App Bundle (`.aab`) for Play, its SHA-256 checksum, and a
   reproducible record of the build environment. A debug APK is not a release
   artefact.
3. The release signing key and passwords stored outside the repository. Keep
   any Play service-account credential outside the repository as well.
4. A user-facing privacy policy derived from the released binary and current
   data flows; Play Console Data safety answers must match that policy.
5. Store listing text, screenshots, high-resolution icon, feature graphic,
   content rating, support contact, and closed-test plan.
6. Release validation: fresh install, upgrade from the previous build, Cloud,
   Storage Box, and test-profile token removal, no-token behaviour, network
   failures, and an inspection that no token/root password appears in logs,
   screenshots, issue attachments, or build outputs.

## Repository hygiene

The repository ignores generated build output, local Android SDK settings, and
downloaded AAPT2 binaries. It must also ignore release-signing material and
local environment files. Ignore rules reduce accidental additions, but do not
remove a secret that was already committed: if that happens, revoke/rotate the
secret first and then remove it from history using an appropriate incident
process.

Before every public push, run a secret scan over tracked files and inspect
`git status`. Test fixtures may use placeholder values only; they must never
contain working Hetzner tokens, passwords, signing keys, or service-account
credentials.

The repository also has a source-level guardrail that checks the encrypted
per-project token boundary, prohibits direct production logging calls, and
rejects root-password persistence in the credential store. Run it explicitly:

```sh
sh tools/check-sensitive-data-boundaries.sh
```

This is a static check, not a replacement for runtime security testing, a
device compromise assessment, or a full secret scanner.
