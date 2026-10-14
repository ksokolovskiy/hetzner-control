# Target API 36 readiness

Snapshot date: 2026-10-14. This is an assessment only; it does not change the
project SDK versions.

## Current project state

| Item | Current value | API 36-ready value |
| --- | --- | --- |
| `compileSdk` | 35 | 36 |
| `targetSdk` | 35 | 36 |
| `minSdk` | 26 | 26 can remain unchanged |
| Android Gradle Plugin | 8.7.3 | at least 8.9.1 |
| Gradle in CI | 8.9 | at least 8.11.1 after AGP upgrade |

The local SDK was rechecked on 2026-10-14 and has platforms `android-35`,
`android-37.0`, and `android-37.2`; it **does not have the exact
`android-36` platform**. Installed build tools include `36.0.0`, but build
tools alone are not a replacement for the compile platform. Therefore this
project must remain at API 35 until the exact API 36 platform is installed and
the Android Gradle Plugin upgrade is tested. Do not point `compileSdk` at a
preview/future platform merely because it is present locally.

## Official requirements

Android's tool compatibility table lists Android API 36 with a minimum Android
Gradle Plugin version of **8.9.1**. The project currently uses AGP 8.7.3, which
supports API 35 only. AGP 8.9 requires Gradle 8.11.1 and JDK 17. See Android's
[tool/API compatibility table](https://developer.android.com/build/releases/about-agp)
and the [AGP 8.9 release notes](https://developer.android.com/build/releases/agp-8-9-0-release-notes).

For Google Play, new apps and updates must target Android 16 / API 36 or later
from 31 August 2026. See the official [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk).

## Conclusion

It is **not safe to raise `compileSdk` or `targetSdk` to 36 today**: the exact
SDK platform is missing and the current AGP version is below Android's minimum
for API 36. The existing API 35 debug build remains the verified baseline.

## Safe migration sequence

1. Install `platforms;android-36` into the local Android SDK and accept its
   licenses. Keep Build Tools 36.0.0 available.
2. Upgrade the Android Gradle Plugin to at least 8.9.1 and use its compatible
   Gradle version (8.11.1 or newer within the official compatibility range).
   Update the repository CI workflow at the same time.
3. Revalidate the ARM64 WSL AAPT2 workaround. The current wrapper downloads a
   version-specific official Windows AAPT2 artifact for AGP 8.7.3; do not
   assume it remains compatible after an AGP upgrade. Use the AAPT2 version
   requested by the upgraded plugin and verify the checksum again.
4. Change `compileSdk` and `targetSdk` together to 36. Keep `minSdk = 26`
   unless a separately approved product decision changes device support.
5. Run a clean debug build, core/network tests, and the GitHub Actions build.
   Then install and exercise the app on an Android 16/API 36 device or
   emulator, focusing on network access, encrypted token storage, language
   preferences, notifications (when added), and server-operation confirmation
   flows.
6. Review Android 16 behavior changes applicable to the final feature set
   before creating the release AAB.

Do not merge any SDK/AGP/Gradle version change until the local debug build and
tests succeed. The target SDK migration should be an isolated, reviewable
change rather than being bundled with product functionality.
