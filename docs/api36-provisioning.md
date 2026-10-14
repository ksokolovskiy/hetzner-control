# Provisioning Android API 36 prerequisites

This helper prepares a **local SDK only** for a future API 36 migration. It is
not called by `build.sh`, does not run in CI, and does not upgrade AGP, Gradle,
the JDK, `compileSdk`, or `targetSdk`.

The project is not yet API 36-ready: see [target API 36 readiness](target-api-36-readiness.md)
for the required AGP/Gradle upgrade and verification sequence.

## Safe use

First run the read-only check. It changes nothing:

```sh
HCLOUD_ANDROID_SDK_ROOT=/absolute/path/to/android-sdk \
  sh tools/provision-api36-prerequisites.sh --check
```

After the API 36 migration has been approved, explicitly install into that same
local SDK:

```sh
HCLOUD_ANDROID_SDK_ROOT=/absolute/path/to/android-sdk \
  sh tools/provision-api36-prerequisites.sh --install
```

The script installs only `platforms;android-36` and `build-tools;36.0.0` in the
specified SDK directory. It does not use `sudo`, edit shell profiles, accept
licences automatically, or modify project source. Android's SDK manager will
show any licence prompts for the user to review and accept.

Run `--check` again afterward. A successful check only verifies SDK packages;
it does not replace the required AGP upgrade, AAPT2 wrapper update, clean
build, tests, and Android 16 device/emulator validation.

The compatibility requirements come from Android's [tool/API compatibility
table](https://developer.android.com/build/releases/about-agp): API 36 needs at
least AGP 8.9.1, with Gradle 8.11.1 for AGP 8.9.
