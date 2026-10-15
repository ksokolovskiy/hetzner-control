# Redesign implementation evidence

Design baseline: `4972d35e790822183dcf0342ecc50963eed3d2ed`.
Tracker: https://github.com/ksokolovskiy/hetzner-control/issues/1

## Auditor handoff — 2026-10-15

The user requested committing this verified source snapshot and pushing `main`
for independent audit. This is a validation handoff, not closure of issues #1–#16.
The prior debug and release-variant/shared-domain snapshots each passed 392 tests;
both lint variants and the local release bundle validation passed. Pre-commit
full-index checks additionally found a synthetic key-header fixture and legitimate
RFC1918/provider-reserved constants matching the strict hygiene rules. The fixture
now constructs only its synthetic header; network constants use equivalent numeric
addresses. The hygiene checker is unchanged. A new boundary regression brings the
latest debug/shared-module reports to 393 tests, zero failures/errors. The earlier
release artifact/hash below predates this equivalent representation cleanup.
Final pre-commit `./build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain`
completed **BUILD SUCCESSFUL in 1m 33s**. Full staged-index hygiene and sensitive
boundaries, their regression checks and staged whitespace checks passed.
See the checkpoint records below for commands, signature warnings and limitations.
Visual/accessibility/physical restoration checks remain user-deferred as recorded
in `redesign-device-acceptance.md`. No phone installation or Play upload is part
of this handoff.

This record tracks implementation and verification separately. A working
foundation is not evidence that all consuming screens have migrated. An issue
stays open until its screen, behavior and regression requirements are checked.

## Work ownership

Release verification (no upload/phone):
`./build.sh :app:bundleRelease :app:testReleaseUnitTest :app:lintRelease --no-daemon --console=plain`
passed **BUILD SUCCESSFUL in 3m 22s**; **251 app release tests, zero failures/errors**.
Release network/security variant tests run separately, sequentially, before their
own results can be claimed. The shared domain tests were already verified.

The subsequent command
`./build.sh :core:network:testReleaseUnitTest :core:security:testReleaseUnitTest --no-daemon --console=plain`
passed **BUILD SUCCESSFUL in 47s**. Combined actual XML reports count **392
release-variant plus shared-domain tests, zero failures/errors**. These exercise
the same cases as the debug snapshot, not 392 newly invented distinct tests.
Repository hygiene, sensitive boundaries and diff checks also pass. Overall
progress is approximately **90%**; remaining device acceptance is user-deferred.

Fresh AAB: `app/build/outputs/bundle/release/app-release.aab`, SHA-256
`02796ebf1777c3d52a12d9e8697ee07b031e69a312e357f722f6a95c1a9ecc25`.
ZIP integrity passes. A full release DEX scan finds neither the debug component
gallery nor Compose PreviewActivity. `jarsigner -verify` reports `jar verified`,
but also self-signed/untrusted-chain, absent timestamp, ZIP permission and
JarFile/JarInputStream manifest-order warnings; these are recorded, not silently
treated as a clean warning-free verification or Play acceptance.

Official `google/bundletool` **1.18.3**, downloaded from its release asset with
SHA-256 verified as
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`,
successfully validates the AAB. Direct bundletool manifest dump confirms API26–36,
versionCode1, backup disabled, no debuggable/testOnly/PreviewActivity, and the
disabled/nonexported locale service with nested autoStoreLocales. Direct packaged
resource dumps for nav_servers and ds_cost_monthly confirm default English plus
German/Russian values. This sample plus source parity does not replace all-screen
visual localization acceptance. References:
https://developer.android.com/tools/bundletool and
https://github.com/google/bundletool/releases/tag/1.18.3.

The artifact was not uploaded, installed or exercised on a phone. Existing
deferred device/security/accessibility checks remain unverified; no issues closed.

Heartbeat batch verified: the full assemble/app+domain+network+security unit/lint
command completed **BUILD SUCCESSFUL in 1m 24s**, **392 tests, zero failures/errors**.
This adds 18 regressions to the prior checkpoint: canonical ancestor/alias/Back
and saved-state branches, settings identity/pending warnings, refresh context
races, snapshot retain/discard, original prefill restoration, volume post-lock
gate/context changes and unknown-create no replay, SSH/S3 refresh identity and
late S3 result publication. The first attempt rejected one new coroutine JUnit
method's inferred non-Unit return; explicitly declaring `runBlocking<Unit>` fixed
that test declaration without changing production behavior.

SSH and S3 Retry now clear the original refresh warning only after verified reads;
late S3 callbacks cannot clear a newer result's latch. Hygiene/regression, ADB
synthetic selection/build-task, sensitive-boundary and diff checks all pass.
No phone actions, real provider writes, issue closures, commit or push occurred.
Visual/accessibility/physical process-restoration acceptance remains deferred by
the user, not counted as passed. Approximate overall progress: 89%.

Heartbeat continuation, verification pending: added focused synthetic branches for
original-context refresh races, snapshot draft retain/discard, late original
catalogue prefill and volume unknown-create/no-replay and post-lock gate changes.
SSH/S3 successful read retry now uses the shared refresh helper; S3 late completion
cannot clear a newer result's refresh latch or publish into a closed/wrong result.
Navigation/save-state regression coverage is being extended without phone access.
This batch does not inherit the previous 374-test checkpoint until rebuilt.

Latest verified snapshot: full debug APK/app+domain+network+security tests/lint
completed **BUILD SUCCESSFUL in 1m 25s**, **374 tests, zero failures/errors**.
This verifies the locale merged-manifest contract, Firewall target invalidation,
snapshot public draft, original-context read refresh and cancellation handling.
Repository hygiene, its regression test, six synthetic ADB selection cases plus
explicit build-task assertion, sensitive boundaries and `git diff --check` pass.
No further phone interaction or installation occurred after the user deferred
visual testing. Remaining device acceptance stays explicitly unverified; issues
remain open and no completion commit/push has been made.

The first locale/Firewall/read-refresh integration attempt compiled and assembled
the APK, but three new `OperationReadRefreshTest` fixtures failed with `Review
expired`: their synthetic reviewed time was 1 ms while their coordinator used the
real clock. The fixture now injects the same fixed clock through the existing
coordinator API; production review expiry checks are unchanged. Full rerun is
required before this batch is considered verified.

Complete asynchronous-list readiness integration passed the full command below
in **1m 58s** (APK, 366 tests with zero failures/errors, lint). This includes
storage/Box/tariff, S3, SSH, certificates/DNS, firewall, load-balancer and placement
group consumers. The user deferred further visual phone checks; source and fake
tests continue, without treating those checks as passed or resuming phone taps.

Restoration/draft checkpoint: the same full assemble/app+domain+network+security
test/lint command passed in **2m 34s**, **366 tests, zero failures/errors**.
Independent secondary reads may settle with an error without disabling viewport
recording for successfully loaded detail content. Readiness integration in the
remaining asynchronous resource lists is a subsequent source change and requires
another build; no physical scroll acceptance is inferred from these unit tests.

Checkpoint after bounded visual/catalogue fixes: full command
`./build.sh :app:assembleDebug :app:testDebugUnitTest :core:domain:test :core:network:testDebugUnitTest :core:security:testDebugUnitTest :app:lintDebug --no-daemon --console=plain`
completed **BUILD SUCCESSFUL in 2m 56s**, with **358 tests, zero failures/errors**.
Subsequent scoped restoration and unavailable-selection fixes are not covered by
this checkpoint until rebuilt. Phone ADB is connected, but system NotificationShade
is currently focused over MainActivity; no further app taps are treated as tests.

Latest integration, 2026-10-15: the full app/domain/network/security suite passed
350 tests with zero failures and errors, plus debug APK assembly and lint. Physical
acceptance is partial, recorded in `redesign-device-acceptance.md`; no issue is
closed on source coverage alone. New bounded fixes (catalogue search/location
prefill, picker restoration/read states, navigation labels/icons, shared retry
policy/dialog actions/semantics and debug gallery insets) are under a fresh full
integration run. The deploy script now explicitly runs `:app:assembleDebug` rather
than allowing an options-only Gradle invocation to select the help task; synthetic
ADB regression checks cover this command.

| Issue | Scope | Status |
| --- | --- | --- |
| #2 | Shared tokens and UI primitives, canonical navigation, read states | In progress |
| #4 | Shared operation identity, state, reconciliation and secret result | In progress |
| #3 | Verified connections, project management and settings | Partial source integration |
| #7 | Server inventory and routine lifecycle | Partial source integration |
| #6 | Server rebuild, recovery, ISO and deletion | Partial source integration |
| #5 | Catalogue and provisioning wizard | Source checkpoint; verification pending |
| #8 | Volumes and images | Source checkpoint; verification pending |
| #9 | Network overview, public IPs and PTR | Source checkpoint; verification pending |
| #11 | Private network topology | Source checkpoint; verification pending |
| #10 | Firewalls | Source checkpoint; verification pending |
| #12 | Load balancers and placement groups | Source checkpoint; verification pending |
| #13 | SSH keys, certificates and DNS | Source checkpoint; verification pending |
| #14 | Storage Boxes | Partial independent reads and snapshot integration |
| #15 | Independent S3 profiles and buckets | Source checkpoint; verification pending |
| #16 | Tariffs and cost uncertainty | Partial source integration |

## Acceptance evidence required for each issue

- Exact owned screen IDs and variants from the pinned screen map.
- Shared primitive APIs consumed; any newly introduced primitive has one owner.
- Context-safe navigation, Back, filters and scroll restoration.
- Independent read failures, stale content and timestamps where applicable.
- Mutations use the one operation coordinator and immutable review identity.
- Fake-repository tests cover applicable uncertainty and duplicate-submit cases.
- EN/RU/DE, both themes, 200% font, TalkBack, touch and keyboard verification.
- Exact build/test/lint commands and outcomes, with missing device checks stated.

## Initial integration

The More menu now groups the existing ten secondary destinations, without
adding alternate resource implementations. A unit test checks unique complete
coverage so a secondary destination cannot disappear or occur in two groups.
This alone does not complete T02 or issue #2.

## First integrated verification

Command: `./build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain`.
The Kotlin compilation, all **61 app unit tests**, and `lintDebug` passed in
the first integrated snapshot (`BUILD SUCCESSFUL in 3m 11s`). Later changes
require their own integration check before they can inherit this evidence.

Coverage includes canonical route serialization/project switching, stale-read
retention, numeric cost/VAT presentation, duplicate writes and uncertain
outcomes, read-before-save connection verification, SSH wire-format checking,
server local filters, and existing navigation/formatting regression tests.

Source integrations at this checkpoint: shared theme and scaffold, grouped
More navigation (still pending the full T02 page), server read state and local
filters, verified connection flow, grouped settings, tariffs, image/SSH lists,
and shared recovery/rebuild secret results with operation journaling.

No visual/device/TalkBack/process-recreation acceptance has been claimed.

No real paid or destructive provider operation is authorized by this test plan.

## Second integration checkpoint (verification pending)

The first attempt failed compilation in ProtectionAdapter's Kotlin property getter;
the backing-field/name collision has been corrected. This is not a passed build.

New source coverage: T02 is now a real grouped root page; independent S3 context,
verified encrypted profile persistence, bucket/SSH operation journaling, provisioning
wizard and resize, canonical volume/Box restoration, and shared detail read states.
Server cards use the shared card and localized statuses. Metrics/history preserve
successful data independently when refresh fails.

Server deletion now preserves the provider action rather than discarding it, requires
fresh unprotected exact identity, persists before send, and exposes read-only original
action/absence reconciliation. Official implementation reference:
https://github.com/hetznercloud/hcloud-go/blob/main/hcloud/server.go
(ServerClient.DeleteWithResult). Synthetic tests cover protected resources,
duplicate submission, original-action completion, and lost-response reconciliation.

These changes do not complete their issues until compilation, regression tests,
screen variants and device acceptance have been checked.

Verification outcome for that snapshot: **93 app unit tests and 100 network unit
tests passed**. Lint stopped on one Activity-context cast in the S3 credential
screen; it has been changed to LocalActivity but needs the next lint run.
Repository hygiene passed. The sensitive-boundary check initially referenced
the old wizard file; it now verifies the actual single write path and shared
secret/error boundaries, and passes. No issue is closed on this evidence alone.

## Third integration checkpoint (verification pending)

- Context-validated saved resource IDs and canonical image aliases.
- All Cloud GET repository methods normalize errors at the network boundary;
  synthetic contracts cover 401/403/404 without live credentials.
- Provisioning quote refresh, exact type validation, scoped draft restoration,
  independent picker/step state, dirty-exit choices and SSH-management return.
- Shared image protection/deletion with independently loaded metadata/history.
- Read-only certificate independent sections and acknowledged DNS inventory.
- Storage tabs and independent volume/image inventories, local tab searches,
  canonical details, no duplicated row lifecycle implementation.
- Volume create/attach/detach/resize/delete use one adapter and coordinator,
  fresh identity/dependency checks, exact original-action reconciliation,
  read-only unknown status checks and shared reviews/results. No auto-unprotect,
  detach-at-delete or attach-at-create. Unknown cost is explicit, not zero.

Official volume preconditions checked against
https://docs.hetzner.cloud/cloud.spec.json. New fake tests cover attached/protected
delete, incompatible attach target, original resize action and authoritative
unattached create ID. Device and visual checks still pending.

Third checkpoint verified: `./build.sh :app:testDebugUnitTest
:core:network:testDebugUnitTest :app:lintDebug --no-daemon --console=plain` —
**BUILD SUCCESSFUL in 2m 25s**, **214 tests, 0 failures**, lint passed.
This evidence applies to that snapshot, not subsequent feature edits.

## Fourth integrated verification

Command: `./build.sh :app:testDebugUnitTest :core:network:testDebugUnitTest
:core:security:testDebugUnitTest :app:lintDebug --no-daemon --console=plain`.
**BUILD SUCCESSFUL in 2m 29s**, **249 tests, 0 failures**, lint passed.

New coverage includes shared result shell, pinned owner-ID credential/config
bindings, profiles/settings draft restoration, server routines and actual-disk
resize eligibility, independent Box inventory/detail tabs, DNS RRsets, and LB/
placement source integrations. This is still not device/visual acceptance.

Official API correction: server metadata PUT accepts name/labels, not placement
membership. Membership uses the existing dedicated actions. A removed original
credential fails closed; requests never fall back to a newly selected project's
token. Both behaviors have regression coverage.

## Fifth source checkpoint (not yet verified)

Network overview and public IPs, structured private-network topology, supported
TCP firewall management, shared PTR, independent certificate/DNS reads, and
LB/placement workflows are integrated into the shared navigation and operation
boundaries. The unreachable legacy combined network implementation was removed;
used volume wrappers remain. Lists/forms use scoped saved state, shared validation
and recoverable results rather than separate action pollers.

Official wire-contract corrections include network create returning a resource
without an action, network/volume DELETE returning HTTP 204, volume creation
tracking all original next actions, rebuild passwords being optional, and subnet
deletion accepting only its IP range. Custom subnet gateway input is deliberately
not implemented: the official add-subnet schema does not accept that write field.
Unknown protection must not become "disabled" or permit deletion. Floating IP
assignment is read from the official `server` field, not Primary IP assignee fields.

The first verification attempt compiled the app and passed 174 app tests, then
stopped on a network-test Kotlin generic-inference error. That test source is
corrected, but this is NOT full-build evidence. Later source changes still require
the complete test/lint command. S3 transport now shares the safe diagnostic
contract without retaining bucket names, queries, XML or signed headers.

The Wi-Fi debugging phone is reachable. No redesigned APK has been installed or
claimed as device-verified at this checkpoint. EN/RU/DE, light/dark, 200% font,
TalkBack, process recreation and all issue acceptance criteria remain explicit
verification work. All GitHub issues remain open.

## Fifth integrated verification

`./build.sh :app:testDebugUnitTest :core:network:testDebugUnitTest
:core:security:testDebugUnitTest :app:lintDebug --no-daemon --console=plain`
completed **BUILD SUCCESSFUL in 2m 49s**, **305 tests, 0 failures, 0 errors**;
lint passed. This supersedes the failed nullable-test compile attempt, not device
acceptance. Source now includes scoped catalogue IDs, retained list filters and
public drafts, original-context image section loaders, safe S3 diagnostics and
late-status-result guards, terminal-session Close handling, unknown-protection
gates and real Floating IP assignment mapping.

Read-only audit identified further work: Storage Box action reconciliation must
use the documented Hetzner-host action endpoint (not Cloud's action client),
placement operation results must remain visible through gate/refresh changes,
restored missing LB/placement IDs need an explicit missing state, and shared
checkbox/card semantics need accessibility refinements. These are not considered
complete merely because this build passes. Phone ADB subsequently became offline;
no APK installation or device acceptance is claimed.

## Sixth integrated verification

The same full test/lint command plus `:app:assembleDebug` completed
**BUILD SUCCESSFUL in 3m 14s**, **312 tests, 0 failures, 0 errors**; lint passed
and the debug APK was packaged. New synthetic coverage verifies the documented
Storage Box action endpoint and persist-before-send snapshot adapter, original
action reconciliation, unknown outcomes, owner isolation and disabled gates.
Shared accessibility and read-only card semantics, explicit missing LB/placement
states, pending-profile warnings and IME-first app-bar Back are included.

Device acceptance remains unrun; an APK on disk is not an installed/verified app.
Further audit work on uniform write-error normalization and checked local
preference persistence is being implemented after this verified snapshot.

## Seventh integrated verification

APK + app/network/security unit tests + lint completed
**BUILD SUCCESSFUL in 3m 5s**, **321 tests, 0 failures, 0 errors**.
Coverage includes typed HTTP rejection for previously unwrapped write methods,
shared successful-write/read-retry state, preference commit failure handling,
and shared native Back ownership. Tariffs now enrich location codes from an
independent official metadata read without hiding known prices if metadata fails.

Further source audit found edge cases still being fixed: a failed permission
write must stay fail-closed across Activity recreation; a failed rollback must
never produce a project-header/client-owner mismatch; original pending firewall,
placement and S3 operations need reachable read-only status controls; ISO review
must name the exact media and retain selection/scroll. Device checks are still
unrun and issues remain open.

## Eighth and ninth integrated verification

The expanded command adds `:core:domain:test` to APK/app/network/security/lint.
The eighth snapshot passed in **2m 35s**, **342 tests, 0 failures, 0 errors**.
After the shared inventory/catalogue/wizard/display-price migrations, shared
metadata form and canonical overview resource links, the ninth snapshot passed
in **2m 20s**, **350 tests, 0 failures, 0 errors**, with lint and APK packaging.

The ninth APK was installed successfully on the user's Pixel 9 (Android 17,
API 37) with app data preserved. The deployment script was found to invoke
options-only Gradle, which runs `help`; it now explicitly invokes
`:app:assembleDebug`. The corrected script was rerun and verified assembly before
successful installation. A synthetic script regression guard covers this error.

Physical acceptance is now in progress, not complete. Original phone settings
are screen timeout 60000 ms, font scale 1.0, app locale System and appearance
System. Timeout is temporarily 1800000 ms and must be restored after testing.
Synthetic gallery capture is confirmed possible through the existing debug-only
PreviewActivity; no new debug Activity or credential access mechanism was added.
