# Redesign device acceptance

Scope: issues #2, #3, #5 and #9. This is a manual acceptance checklist, not evidence
that the scenarios passed. The checklist below remains unverified except for the
explicit dated observations recorded next. Build/unit/lint results are separate
from device acceptance.

## Physical observations — 2026-10-15, partial acceptance

The user subsequently deferred further visual phone testing. Do not resume UI
interaction, screenshots, theme/font changes or physical acceptance until asked.
Continue source and synthetic-test verification; remaining device rows are UNRUN.

Pixel 9, Android 17/API 37, ARM64; shared discovery helper connected successfully.
Installed debug APK came from the successful 350-test/lint/assemble checkpoint.
Later visual fixes require another build and installation before acceptance.

- MainActivity and Settings opened without a crash. More groups and nested
  Settings navigation rendered; nested Settings hides bottom navigation.
- Selecting German changed the UI and OS app locale to `[de]`; reopening Settings
  and restarting the app retained German. Actual edge Back from Settings returned
  to More. This restart does not prove saved-task process recreation.
- Test-profile inventory loaded an honest empty server list. Catalogue types and
  detail loaded, with readable location names and explicit unavailable pricing.
- Light synthetic gallery rendered; its status-bar inset defect was fixed in
  source, not yet rechecked. Duplicate root title, narrow navigation labels and
  unnecessary healthy-content retry buttons were found and fixed in source.
- Current reconnect succeeded, but the phone subsequently showed the lock screen.
  An attempted Back capture showed another foreground app, so it is NOT evidence
  of catalogue Back behavior. Further taps paused pending unlock.

Original preferences to restore after testing: screen timeout 60000 ms, font scale
1.0, app language System (`[]`), app appearance System. Screen timeout was restored
to 60000 ms while physical interaction is paused; app language still needs restoring
from German through Settings. No provider writes were confirmed.

## Prerequisites and safety

- Install the intended debug APK using the existing deployment workflow. Confirm
  the intended build before testing; do not assume an older installed APK is current.
- Enable previously paired Wi-Fi debugging and unlock the intended phone. When
  several devices are visible, explicitly set `HCLOUD_ADB_SERIAL`.
- Record the original app language, appearance, selected Cloud/S3 profiles and
  device-global changes permission. Restore them after testing.
- Use existing profiles and GET-only navigation. Do not confirm creation, deletion,
  assignment, power, recovery, PTR or protection operations, even on test profiles.
- Never collect onboarding/credential screenshots, UI hierarchy, app-private files
  or broad logcat. A masked input is not proof its accessibility XML is secret-free.
- Do not clear app data, remove profiles, revoke tokens or disable Wi-Fi ADB.

Commands run from the repository root:

```sh
# Local synthetic helper checks; no real device access.
sh tools/test-adb-device.sh

# Unique target selection reuses tools/adb-device.sh and the existing adb.sh.
# Output: five allowlisted hardware/Android properties, app version and PID only.
sh tools/device-acceptance-info.sh

# Optional explicit selector, if discovery reports more than one target.
HCLOUD_ADB_SERIAL='YOUR_PAIRED_ENDPOINT' sh tools/device-acceptance-info.sh

# Launch the already installed app; this does not build or install it.
./adb.sh shell am start -n com.hcloudcontrol/.MainActivity
```

The information helper does not launch the app, read credentials, dump the UI or
collect screenshots/logs. Its six synthetic discovery cases passed locally;
**the physical-device invocation is UNRUN**. No runtime Compose resource IDs are
promised: navigation currently has no `testTag`/`testTagsAsResourceId` contract.
Use visible localized labels, roles and bounds during manual interaction.

## Synthetic component previews — UNRUN on device

These debug-only fixtures do not contact a provider. The installed debug build
must contain Compose tooling's exported `PreviewActivity`; release builds must
not be assumed to contain it. The activity declaration was inspected in the
local tooling dependency, not resolved on the offline phone.

```sh
./adb.sh shell cmd package resolve-activity --brief -n com.hcloudcontrol/androidx.compose.ui.tooling.PreviewActivity

./adb.sh shell am start -n com.hcloudcontrol/androidx.compose.ui.tooling.PreviewActivity --es composable com.hcloudcontrol.ui.designsystem.HCloudComponentGalleryKt.LightGallery
./adb.sh shell am start -n com.hcloudcontrol/androidx.compose.ui.tooling.PreviewActivity --es composable com.hcloudcontrol.ui.designsystem.HCloudComponentGalleryKt.DarkGallery
./adb.sh shell am start -n com.hcloudcontrol/androidx.compose.ui.tooling.PreviewActivity --es composable com.hcloudcontrol.ui.designsystem.HCloudComponentGalleryKt.LargeTextGallery

# Return to the application after reviewing fixtures.
./adb.sh shell am start -n com.hcloudcontrol/.MainActivity
```

Inspect readable semantic colors, long text, all section states, incomplete costs,
unchecked review acknowledgements, blank typed confirmation and 48dp controls.
`LargeTextGallery` requests 200% through an Android Studio `@Preview` annotation.
A direct PreviewActivity launch does not apply that annotation's font scale, so
it cannot prove runtime 200% rendering. Verify actual font scale independently
when physical testing resumes. Preview startup/reflection was observed for Light;
Dark and actual 200% rendering remain **UNRUN**.

## App acceptance — every row UNRUN

| Area | Steps and expected result |
| --- | --- |
| Roots / More (#2) | Visit all five roots and every grouped More entry. One scaffold; nested details/forms hide bottom navigation. |
| Back (#2) | For Server, catalog type/location, IP detail and settings child: app-bar Back and system Back return exactly one level. Test a real edge gesture separately; `KEYCODE_BACK` alone does not prove gesture behavior. |
| Keyboard / overlay (#2) | Focus a field, open IME, use app-bar Back: keyboard hides before leaving. For system Back, first IME then picker/dialog then detail/root; verify actual device ordering rather than infer it from source. |
| Server filters / scroll (#2) | Search/filter, scroll, open detail, return: query, filter and list position unchanged. Switch project: no old resource selection or filter leaks. |
| Catalog (#5) | Scroll, select type/location, return and rotate/recreate: same safe selected ID and list position; missing IDs show missing state, not another resource. |
| Wizard (#5) | Enter a public synthetic name, choose resources/access, visit Review without submitting. Numeric filters sorted; type-first/location-first availability coherent; exact API cost or explicit unknown, never invented zero. Back retains/discards as selected; confirmations reset. |
| Cloud profiles (#3) | Search picker; inspect an inactive profile without selecting it. Explicit switch changes Cloud context and clears old descendants/drafts. S3 context stays independent. Open removal confirmation, verify pinned name/pending warning, then Cancel. |
| Global gate (#3) | Open permission sheet: acknowledgement starts unchecked. Cancel leaves state unchanged. If testing enable/disable locally, restore the original flag; do not use this to execute provider writes. |
| Language / theme (#3) | Select EN, RU, DE and System; navigate away/reopen/restart. Preferences persist and UI follows them. Check System/Light/Dark, disabled/error/focus contrast and privacy HTTPS link. Restore originals. |
| Network (#9) | Search nonexistent category: explicit no-results. Enter a category and Back: overview query retained. Visit Primary/Floating tabs and details; inspect metadata, history and protection independently. |
| IP / PTR forms (#9) | Open forms only: no arbitrary server/address selection; eligible/excluded reasons visible. Back/reopen preserves public draft, never acknowledgement. PTR invalid hostname/no-public-address states are clear. Do not confirm. |
| Accessibility | TalkBack labels/selected states/headings; long names/IPs, dark/light and 200% text. No clipped actions, inaccessible fields or tiny targets. Synthetic preview alone does not verify production screens. |

## Process recreation — UNRUN

Use only safe list/detail/form state, with no submitted operation or token input.
Record PID with the information helper, press HOME, then:

```sh
./adb.sh shell am kill com.hcloudcontrol
```

Reopen the existing task from Recents; run the information helper again and verify
the process actually died/restarted. Check selected ID, filters, scroll and public
draft; typed confirmations/acknowledgements must not restore. Test another Cloud
context to ensure no old ID/draft is rebound to it. `force-stop` and clearing data
are not substitutes for saved-task process recreation.

Offline/401/403/404, partial reads, secure-persistence failure and operation
sending/running/unknown outcomes require controlled fake tests or a separately
approved isolated test environment. They remain **UNRUN on the physical device**
under this no-provider-writes checklist. Do not report source inspection or unit
tests as device verification. Restore original local preferences and return to
the user's original profile/screen when finished.
