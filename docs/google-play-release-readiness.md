# Google Play release-readiness audit

Snapshot: 2026-10-14. This document describes the reproducible preparation
for a closed Google Play test. It does not authorize an upload or create a
signing key on the maintainer's behalf.

## Evidence collected

On the ARM64 WSL development machine, the project's canonical build entrypoint
completed the following without installing anything on a device:

```sh
sh tools/provision-aapt2-runner.sh
./build.sh :app:bundleRelease --rerun-tasks --no-daemon --console=plain
```

The resulting (intentionally unsigned) verification bundle is:

```
app/build/outputs/bundle/release/app-release.aab
```

API 36 and AGP 8.12.2 were verified with a clean local release bundle. The
bundle is deliberately **unsigned** until an upload key is supplied from an
ignored local file. An unsigned bundle is appropriate for source-tree
verification, but is not uploadable to Google Play.

`build.sh` builds `assembleDebug` only when no task is specified; an explicit
release request no longer compiles an unnecessary debug APK.

## Current build configuration

| Setting | Current value | Release implication |
| --- | --- | --- |
| Application id | `com.hcloudcontrol` | Must be reserved in Play Console before first production upload. |
| Version code/name | `1` / `0.1.0` | Increase `versionCode` for every later Play upload. |
| min / target / compile SDK | 26 / 36 / 36 | Meets the current new-app target requirement. |
| Release signing | External `signing.properties` supported | An upload key is still required locally or in CI secrets. |
| Minification / resource shrinking | Not configured | The default release behavior is no minification or shrinking; no R8/ProGuard compatibility claim has been made. |

## Blocking requirements before the first Play submission

1. **API level:** Google Play requires new apps and updates submitted from
   31 August 2026 to target Android 16 / API 36 or higher. The project now
   targets 36. Official requirement:
   <https://developer.android.com/google/play/requirements/target-sdk>.
2. **Signing:** Create and retain the upload key outside the repository; do
   not commit a keystore, passwords, or Play service-account JSON. Copy
   `signing.properties.example` to the ignored `signing.properties`, fill it
   with the local upload-key values, and use `tools/build-play-bundle.sh`.
   The script refuses to make an unsigned upload bundle. Play App Signing
   enrollment is a separate Play Console decision.
3. **Store compliance:** Complete the Play listing, a public privacy-policy URL, Data
   safety form, content rating, app-access declaration (if applicable),
   country distribution, and the required closed-test track before production.
   These must describe the final binary, especially encrypted per-project API
   tokens and any diagnostic logging behavior.
4. **Release hardening decision:** If R8/resource shrinking is enabled later,
   add explicit release rules and run a real-device smoke test of every
   network and Compose screen. It is deliberately not enabled merely to make
   a release build smaller.
5. **Reproducibility:** `build.sh` uses either `HCLOUD_TOOLCHAINS` or the
   ignored project-local `.toolchains` directory. Provision the latter only
   through the explicit `HCLOUD_TOOLCHAIN_SOURCE` procedure documented in the
   README; the build must never infer another checkout's toolchain or cache.
   CI remains the portable x86_64 build reference. Before release, validate a
   clean ARM64 WSL project-local provisioning and build as well.

## Safe pre-upload verification sequence

After the signing work has been reviewed, run this on the exact commit
intended for upload:

```sh
sh tools/build-play-bundle.sh
```

Then confirm the release signing report names the expected non-debug upload
certificate; inspect the final signed AAB using the JDK from the configured
toolchain; and retain its SHA-256 checksum with the release record. Never put
the keystore path, passwords, token values, or certificate private key in this
document, source control, or build logs.
