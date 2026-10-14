# HCloud Control

An Android client for Hetzner Cloud, with project-token authenticated Storage Box
inventory and snapshot creation. Cloud DNS is available as an explicitly
acknowledged, read-only view through the Hetzner Cloud API. The app does not
use or manage Hetzner Robot or the legacy DNS Console API.

This project is released under the [MIT License](LICENSE). Its published
[privacy policy](https://ksokolovskiy.github.io/hetzner-control/privacy-policy/)
is maintained alongside the source.

## Modules

- `app` — Compose application and dependency wiring.
- `core:domain` — models, repository contracts, and use cases.
- `core:network` — Retrofit clients for the Cloud API and the Storage Box API.
- `core:security` — encrypted Cloud project credential persistence and a
  legacy-credential removal boundary.

Cloud project and test-mock credentials are never written to logs or exposed in
a resource UI state. Project credentials are stored using Jetpack Security's
`EncryptedSharedPreferences`, whose master key is backed by Android Keystore.
See [security and privacy notes](docs/security-and-privacy.md) for important
scope and test-profile boundaries.

## API contract

Cloud API work is implemented against the official [Hetzner Cloud API
Reference](https://docs.hetzner.cloud/reference/cloud) and its
[changelog](https://docs.hetzner.cloud/changelog); Storage Box work follows
the official [Storage Box reference](https://docs.hetzner.cloud/reference/hetzner#tag/storage-boxes).
In particular, server responses use `location` (not the removed `datacenter`
field), and list endpoints are followed through `meta.pagination.next_page`.

## Local Android build

Run `./build.sh`. It uses either an explicit `HCLOUD_TOOLCHAINS` root or the
ignored project-local `.toolchains` directory. The project never relies on a
toolchain belonging to another checkout. To make a project-local toolchain
from a compatible, explicitly selected source without `sudo` or downloads:

```sh
HCLOUD_TOOLCHAIN_SOURCE=/absolute/path/to/compatible/toolchains \
  sh tools/provision-build-toolchain.sh
```

Use `sh tools/provision-build-toolchain.sh --check` to validate it. Advanced
automation may instead set `HCLOUD_JAVA_HOME`, `HCLOUD_ANDROID_SDK_ROOT`, and
`HCLOUD_GRADLE_BIN` explicitly. On ARM64 WSL, `tools/aapt2` runs Android SDK AAPT2 `2.20-13193326`
through a project-local `qemu-user` runner. `build.sh` passes it to Gradle as
`-Pandroid.aapt2FromMavenOverride`; provisioning requires no `sudo` and avoids
the unsupported Windows-interop path.

## Install on a Wi-Fi debugging phone

Use one idempotent command after enabling Android's **Wireless debugging**:

```sh
sh tools/deploy-to-phone.sh
```

The script verifies the source-level credential boundaries, runs `build.sh`
(including its AAPT2 preparation), finds a single paired Wi-Fi ADB endpoint,
then installs and launches the debug APK only if the build succeeds. It never
reads or prints an API token or data stored by the app.

If more than one device is visible, choose it explicitly:

```sh
HCLOUD_ADB_SERIAL=<paired-wifi-adb-endpoint> sh tools/deploy-to-phone.sh
```

## Repository hygiene

Local credentials, signing material, Android SDK paths, Wi-Fi ADB endpoints,
and generated artifacts must remain untracked. Copy no values into source or
documentation; use placeholders such as those in [.env.example](.env.example).

Before a public push, run:

```sh
sh tools/check-sensitive-data-boundaries.sh
sh tools/check-repository-hygiene.sh
sh tools/test-repository-hygiene.sh
```

These are source-level guardrails. Run a maintained secret scanner against the
entire reachable Git history in CI before changing repository visibility.

## Google Play closed testing

The project targets Android API 36 and has a reproducible signed-bundle
entrypoint. Signing credentials are deliberately absent from source control:
copy `signing.properties.example` to the ignored `signing.properties`, store
the upload key outside the repository, then run:

```sh
sh tools/build-play-bundle.sh
```

The resulting signed AAB is the only artifact intended for Google Play. The
closed-test runbook, listing draft, and privacy-policy draft are in
[docs/play-console-closed-test.md](docs/play-console-closed-test.md) and
[docs/privacy-policy-draft.md](docs/privacy-policy-draft.md). The policy still
must be reviewed and published at a public HTTPS URL by the owner before a
Play submission.
