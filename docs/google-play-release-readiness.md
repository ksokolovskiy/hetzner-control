# Google Play release-readiness audit

Snapshot: 2026-10-14. This document describes local build evidence only. It
does not authorize creating a signing key, signing an artifact, creating a
Play Console listing, or uploading a build.

## Evidence collected

On the ARM64 WSL development machine, the project's canonical build entrypoint
completed the following without installing anything on a device:

```sh
sh tools/provision-aapt2-runner.sh
./build.sh :app:bundleRelease :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain
```

The resulting unsigned bundle is:

```
app/build/outputs/bundle/release/app-release.aab
```

The release unit-test task and `lintRelease` completed successfully. The
release bundle is deliberately **unsigned**: Gradle's `signingReport` reports
`Config: null` for the `release` variant. This is appropriate for source-tree
verification, but is not uploadable to Google Play.

The same command also produces the debug APK because `build.sh` always adds
`assembleDebug`; this is harmless but should be kept in mind when estimating
CI time.

## Current build configuration

| Setting | Current value | Release implication |
| --- | --- | --- |
| Application id | `com.hcloudcontrol` | Must be reserved in Play Console before first production upload. |
| Version code/name | `1` / `0.1.0` | Increase `versionCode` for every later Play upload. |
| min / target / compile SDK | 26 / 35 / 35 | Does not meet the current new-app Play target requirement. |
| Release signing | Not configured | A signing key and an injected signing configuration are required. |
| Minification / resource shrinking | Not configured | The default release behavior is no minification or shrinking; no R8/ProGuard compatibility claim has been made. |

## Blocking requirements before the first Play submission

1. **API level:** Google Play requires new apps and updates submitted from
   31 August 2026 to target Android 16 / API 36 or higher. The project
   currently targets 35. Follow the isolated migration plan in
   [target-api-36-readiness.md](target-api-36-readiness.md), then rerun the
   complete release verification on API 36. Official requirement:
   <https://developer.android.com/google/play/requirements/target-sdk>.
2. **Signing:** Create and retain the upload key outside the repository; do
   not commit a keystore, passwords, or Play service-account JSON. Add a
   release signing configuration which accepts those values only from local or
   CI secrets, then verify the signed AAB's certificate fingerprint before
   upload. Play App Signing enrollment is a separate Play Console decision.
3. **Store compliance:** Complete the Play listing, privacy-policy URL, Data
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

After the target-SDK and signing work has been reviewed, run this on the exact
commit intended for upload:

```sh
sh tools/provision-aapt2-runner.sh
./build.sh :app:bundleRelease :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain
./build.sh :app:signingReport --no-daemon --console=plain
```

Then confirm the release signing report names the expected non-debug upload
certificate; inspect the final signed AAB using the JDK from the configured
toolchain; and retain its SHA-256 checksum with the release record. Never put
the keystore path, passwords, token values, or certificate private key in this
document, source control, or build logs.
