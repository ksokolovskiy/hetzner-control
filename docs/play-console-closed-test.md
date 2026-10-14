# Google Play: closed-test release runbook

This runbook is for the **HCloud Control** Android application. It separates
repository preparation from the account actions that only the Play Console
owner can perform.

## Prepared in this repository

- Application id: `com.hcloudcontrol`.
- Version: code `1`, name `0.1.0`. Every later upload must use a higher code.
- Android compatibility: min SDK 26, target and compile SDK 36.
- Artifact type: signed Android App Bundle (`app-release.aab`), never a debug
  APK.
- Local signing entrypoint: `sh tools/build-play-bundle.sh`. It refuses to
  produce an upload artifact without an ignored local signing configuration.
- Security evidence: Cloud project tokens and optional Object Storage
  credentials are encrypted at rest on-device, sent only to the selected
  Hetzner endpoint over HTTPS, and excluded from application diagnostics.

## Information to enter in Play Console

| Console field | Prepared value / decision |
| --- | --- |
| App name | HCloud Control |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |
| Contact email | Use the maintainer's monitored support address |
| Package name | Let the first uploaded AAB reserve `com.hcloudcontrol` |
| Privacy-policy URL | `https://ksokolovskiy.github.io/hetzner-control/privacy-policy/` after GitHub Pages is enabled |

Do not claim that the app is affiliated with Hetzner. It is an independent
client for Hetzner Cloud.

## Draft store listing

**Short description** (80-character limit):

> Manage Hetzner Cloud projects, servers, storage, networks and DNS securely.

**Full description:**

> HCloud Control is an independent Android client for managing Hetzner Cloud
> projects. Add each Cloud project with its own API token, then view and manage
> servers, volumes, snapshots, networks, Storage Boxes, Object Storage and
> Cloud DNS from one mobile app.
>
> The app stores each project token encrypted on your device and connects
> directly to the Hetzner APIs. It has no application backend, advertising,
> analytics, or token synchronization service. Some operations can create
> charges or delete resources; they remain hidden until you explicitly enable
> high-risk operations in Settings.
>
> HCloud Control is not affiliated with Hetzner.

The listing must be reviewed against the final release binary before upload.

## Closed-test procedure

1. In Play Console, create the app and accept Play App Signing. Keep the
   upload key outside the repository and make a backup according to the
   maintainer's key-management policy.
2. Copy `signing.properties.example` to the ignored `signing.properties`; add
   the upload-key path and passwords locally. Run `sh tools/build-play-bundle.sh`.
3. Create a **Closed testing** track, upload the resulting signed AAB, enter
   release notes, and save the release. Do not install that AAB directly with
   ADB.
4. Add testers through a Google Group or an email list, publish the opt-in
   link, and verify that every tester has opted in. For a personal developer
   account created after 13 November 2023, Google requires at least 12 testers
   opted in continuously for 14 days before production access can be applied
   for. Follow the current Play Console status as the authority.
5. Give testers concise scenarios: add/remove a test profile, connect a real
   project using a least-privilege disposable token, refresh inventories,
   change language/theme, create a non-production server, and verify that
   destructive controls require the opt-in setting.
6. Track crashes and functional reports. Before the next upload, increment
   `versionCode`, rebuild a signed AAB, and update the release notes.

Official references: [testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465),
[set up testing](https://support.google.com/googleplay/android-developer/answer/9845334), and
[target API policy](https://developer.android.com/google/play/requirements/target-sdk).

## Manual compliance questionnaire

The Play Console owner must answer these from the final binary, not from this
template:

- App access: tokens are entered by the tester; provide an active test profile
  and clear instructions rather than a production token.
- Data safety: review the current privacy policy and every SDK dependency. At
  the time of writing, the app has no analytics, ads, crash reporting, or
  backend. It handles user-provided Cloud API tokens locally and cloud resource
  metadata in memory for the active screen.
- Content rating: complete the IARC questionnaire honestly.
- Ads declaration: no ads in the current app.
- Target audience, country availability, and support contact: owner decisions.

Never attach API tokens, root passwords, keystores, or `signing.properties` to
tester instructions, screenshots, issue reports, or releases.
