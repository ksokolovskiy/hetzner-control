# Localization plan: English, Russian, German

> Historical planning document, snapshot 2026-10-13. The three resource sets,
> Android locale configuration, and the in-app/System/English/Russian/German
> selection are now implemented. Use the current `app/src/main/res` resources
> and regression checks as the source of truth; retain this document only for
> migration rationale.

## Original inventory (2026-10-13)

The app UI currently contains **154 Kotlin string-literal occurrences** in the
two app UI files:

| File | Candidate literals | Primary areas |
| --- | ---: | --- |
| `MainActivity.kt` | 88 | connection setup, navigation, server list/details, power and storage actions, settings |
| `ServerProvisioningScreen.kt` | 66 | create-server flow, SSH keys, resize flow, confirmations and operation status |

About 120 of those occurrences are used directly by UI components (`Text`,
field labels, buttons, dialogs, loading/error states). The remainder includes
Hetzner API statuses, icons, and developer-only exception messages. Some
visible text is composed dynamically; it must become formatted or plural
resources rather than concatenated text.

The original UI was intentionally mixed English/Russian. The resource default
will be English; Russian and German will be complete equivalents before they
are exposed as selectable application languages.

## Resource breakdown

Create these resource sets during the migration:

```
app/src/main/res/values/strings.xml       # complete English fallback
app/src/main/res/values-ru/strings.xml    # complete Russian translation
app/src/main/res/values-de/strings.xml    # complete German translation
app/src/main/res/resources.properties     # unqualifiedResLocale=en
```

Use stable key prefixes:

| Prefix | Scope | Estimated keys |
| --- | --- | ---: |
| `app_`, `nav_`, `settings_` | app name, navigation, project connection and settings | 25 |
| `servers_`, `server_detail_`, `power_` | lists, server properties, power actions and results | 35 |
| `storage_`, `backup_`, `snapshot_` | image inventory, backups and snapshots | 25 |
| `provision_`, `resize_`, `ssh_`, `network_` | create-server and resize flows | 45 |
| `action_`, `error_`, `common_` | shared confirmation, retry/cancel and safe errors | 20 |

Estimated first migration: **about 150 resource values per locale** (including
short labels, formatted messages and plurals). This is an estimate of resource
values, not source lines: repeated labels should share keys.

### Formatting rules

- Use `stringResource()` in composables and `context.getString()` outside a
  composable; do not localize API values such as server type, location, image
  name, project name, fingerprint or an API status code.
- Replace string interpolation with format resources (`%1$s`) and use
  `plurals` for counts such as configured projects and servers.
- Keep technical failure details out of translated UI unless they are safe and
  actionable. Map API/action statuses to resource keys.
- Preserve provider/API terms that must remain recognisable (`Hetzner Cloud`,
  `SSH`, `IPv4`, `IPv6`, `root password`, `snapshot`, `backup`, `vCPU`).

## Per-app language design

Follow Android's per-app language API rather than maintaining a separate
preference. The Android documentation recommends this because the in-app
picker and Android 13+ system settings stay synchronized:

1. Add AndroidX AppCompat and migrate `MainActivity` from `ComponentActivity`
   to `AppCompatActivity`.
2. Enable `autoStoreLocales` via
   `AppLocalesMetadataHolderService` for Android 12 and below. This is the
   persisted local choice; no extra token/settings storage is needed.
3. Add a Settings row: **System default / English / Русский / Deutsch**. It
   reads `AppCompatDelegate.getApplicationLocales()` and applies the selection
   with `AppCompatDelegate.setApplicationLocales(LocaleListCompat...)`.
   Selecting System default passes an empty locale list.
4. Once all three resource sets are complete, enable AGP automatic locale
   configuration in the app module:

   ```kotlin
   android {
       androidResources { generateLocaleConfig = true }
       defaultConfig { resourceConfigurations += listOf("en", "ru", "de") }
   }
   ```

   This exposes the same languages in Android 13+ system app-language
   settings. It must not be enabled before the translations are complete.
5. Verify locale changes by changing both the in-app selector and system
   Settings, then restarting on API 26–32 and API 33+ devices.

References: [Android resource localization](https://developer.android.com/guide/topics/resources/localization)
and [official per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages).

## Delivery slices

1. **Foundation:** create complete English resource inventory and replace
   shared/navigation/settings literals. Add Russian and German translations in
   the same pull request.
2. **Server lifecycle:** list, details, power, storage, backup and snapshot
   strings plus plural/format tests.
3. **Provisioning:** creation, SSH, networking, resize and all billable or
   irreversible-operation confirmations.
4. **Exposure:** only after coverage review, enable `LocaleConfig`, add the
   Settings selector, run locale regression tests, and validate overflow for
   German.

## Placement Groups: next resource slice

The read-only Network section needs the following `network_placement_group_*`
keys in all three resource sets. Server names, IDs, group types, and label
values remain API arguments and are not translated.

| Key | English | Russian | German | Kind |
| --- | --- | --- | --- | --- |
| `network_placement_groups_title` | Placement Groups | Placement Groups | Placement Groups | string |
| `network_placement_groups_empty` | None | Нет | Keine | string |
| `network_placement_group_type` | Type: %1$s | Тип: %1$s | Typ: %1$s | format |
| `network_placement_group_servers` | Servers in group: %1$d (%2$s) | Серверов в группе: %1$d (%2$s) | Server in der Gruppe: %1$d (%2$s) | format/plural review |
| `network_placement_group_servers_empty` | Servers in group: 0 | Серверов в группе: 0 | Server in der Gruppe: 0 | string or zero plural |
| `network_placement_group_delete_protected` | Deletion protection enabled | Защита от удаления включена | Löschschutz aktiviert | string |
| `network_placement_group_delete_unprotected` | Deletion protection disabled | Защита от удаления выключена | Löschschutz deaktiviert | string |
| `network_labels` | Labels: %1$s | Метки: %1$s | Labels: %1$s | format |

`deleteProtected == null` means the API did not provide a protection state;
omit both protection strings in that case. During migration, use a plural for
the server count rather than composing `#id` strings inside a translated
sentence.
