# HCloud Control

An Android client for Hetzner Cloud. The initial architecture deliberately scopes
the app to the **Hetzner Cloud API**; it does not attempt to manage Hetzner
Robot, Storage Box, DNS Console, or other Hetzner products.

## Modules

- `app` — Compose application and dependency wiring.
- `core:domain` — models, repository contracts, and use cases.
- `core:network` — Retrofit implementation of the Hetzner Cloud API.
- `core:security` — encrypted API-token persistence abstraction.

The API token is never written to logs or exposed in a UI state. It is stored
using Jetpack Security's `EncryptedSharedPreferences`, whose master key is
backed by Android Keystore.
