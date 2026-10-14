# Target API 36 readiness

Snapshot date: 2026-10-14. The migration is complete and locally verified.

## Current project state

| Item | Current value | API 36-ready value |
| --- | --- | --- |
| `compileSdk` | 36 | 36 |
| `targetSdk` | 36 | 36 |
| `minSdk` | 26 | 26 remains unchanged |
| Android Gradle Plugin | 8.12.2 | supports API 36 |
| Gradle local/CI | 8.13 | compatible with AGP 8.12.2 |

The local SDK includes the exact `platforms;android-36` platform and Build
Tools `36.0.0`. Build Tools alone are not a replacement for a compile platform;
the platform is explicitly checked by the provisioning script.

## Official requirements

AGP 8.12.2 supports API 36 and uses Gradle 8.13. See the official
[AGP 8.12 release notes](https://developer.android.com/build/releases/agp-8-12-0-release-notes).

For Google Play, new apps and updates must target Android 16 / API 36 or later
from 31 August 2026. See the official [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk).

## Evidence and remaining device validation

The ARM64 WSL release bundle completed successfully after upgrading the
toolchain. `build.sh` uses the audited local AAPT2 runner on ARM64, while CI
uses its native x86_64 AAPT2. Before the first closed-test upload, complete a
fresh-install smoke test on a real Android 16 device and cover connection,
token removal, localization, refresh, and destructive-action confirmation
flows.
