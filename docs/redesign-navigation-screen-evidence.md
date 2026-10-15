# Navigation, connections and public IP screen evidence

Ownership source: `4972d35e790822183dcf0342ecc50963eed3d2ed:docs/design/hcloud-redesign/implementation-spec/SCREEN_MAP.md`.
The IDs/pages below match that pinned map exactly. This is source-to-test traceability,
not acceptance completion. **Every row is physical-device UNRUN**; the phone is offline.
Current integrated build results belong in the build report, not inferred from these
test names. A supporting unit/contract test does not prove a screen's rendering or Back behavior.

Source shorthand: app files are under `app/src/main/kotlin/com/hcloudcontrol/`;
shared UI under `ui/designsystem/`, routes/state under `ui/navigation/` and `ui/state/`.
Network contract tests are under `core/network/src/test/`; eligibility tests under
`core/domain/src/test/`; persistence tests under `core/security/src/test/`.

## Issue #2 — shared primitives and navigation

| ID | Exact screen / PDF page | Canonical implementation and shared ownership | Supporting test classes |
| --- | --- | --- | --- |
| T02 | Grouped More navigation / 58 | `MoreScreen.kt::MoreScreen`; `MainActivity.kt::moreDestinationGroups`, `CloudHome`; `DestinationNavigation.kt`; shared `HCloudAppScaffold`, `HCloudResourceCard`, `NavigationChromeHost` | `MoreDestinationGroupsTest`, `DestinationNavigationTest`, `CanonicalNavigationTest` |
| U01 | Offline and stale list / 59 | `SectionState`, `SectionLoader`, `sectionReadFailure`, `presentationKind`, `HCloudReadSection`/`HCloudSectionState`; server consumer `MainActivity.kt::ServersScreen` retains successful inventory | `SectionStateTest`, `SectionReadFailureTest`, `ServerInventoryReadPresentationTest`, `ReadFailureBoundaryContractTest` |
| U02 | Empty inventory / 59 | Shared `EmptyReason`, `SectionKind`, `HCloudSectionState`; `ServersScreen`, `CloudHome` root create affordance; consumers distinguish empty/no-results/no-eligible/no-connection/denied | `SectionStateTest`, `ServerInventoryReadPresentationTest`, `NetworkCategorySearchTest` |

Cross-screen route/restoration evidence: `CanonicalNavigation.kt`,
`RememberCanonicalNavigation.kt`, `NavigationChrome.kt`, `ContextualResourceSelection.kt`,
`ContextualTextDraft.kt`, `RestorableLazyListState.kt` and `DirtyFormExitGuard.kt`.
Supporting classes: `CanonicalNavigationTest`, `ContextualResourceSelectionTest`,
`ContextualTextDraftTest`, `ScrollPositionRegistryTest`, `DirtyFormExitGuardTest`.
Debug visual fixtures are in `app/src/debug/kotlin/com/hcloudcontrol/ui/designsystem/HCloudComponentGallery.kt`
(`LightGallery`, `DarkGallery`, `LargeTextGallery`); these are not production navigation routes.

## Issue #3 — verified connections, profiles and settings

| ID | Exact screen / PDF page | Canonical implementation / adapter | Supporting test classes |
| --- | --- | --- | --- |
| A01 | Connect a Cloud project / 9 | `MainActivity.kt::Onboarding`, `OnboardingConnectionRequest.kt`; shared fields/chips and dirty-exit prompt; token remains transient, outside saved draft | `OnboardingConnectionRequestTest`, `VerifiedCloudConnectionCoordinatorTest` |
| A02 | Check Cloud connection / 9 | Same `Onboarding`; `connections/VerifiedCloudConnectionCoordinator.kt` and repository factory read-check → secure-save; cancellation propagated | `VerifiedCloudConnectionCoordinatorTest` |
| A03 | Switch projects / 10 | `CloudHome` searchable `HCloudChoiceSheet`; `SettingsScreen` connection list; pinned `ProjectApiClients`/`PinnedCloudConnection`, canonical context reset and independent S3 context | `CanonicalNavigationTest`, `PinnedCloudConnectionTest`, `SettingsNavigationStateTest`, `PersistenceFailureGuardTest` |
| A04 | Connection failure / 10 | Same `Onboarding`; typed `ConnectionFailure` from coordinator, sanitized diagnostic fields, token focus and official Console link; no alternate save path | `VerifiedCloudConnectionCoordinatorTest`, `OnboardingConnectionRequestTest`, `CheckedPreferencesTest` |
| A05 | Settings index / 11 | `SettingsScreen.kt::SettingsScreen`, `SettingsNavigationState.kt`; grouped shared resource cards and choice/detail sheets | `SettingsNavigationStateTest` |
| A06 | Allow changes on this device / 11 | `SettingsScreen` shared `HCloudDialog`, unchecked local acknowledgement; `PersistentHighImpactOperationsStore`, process `PersistenceFailureLatch`; global capability supplied by `MainActivity` | `HighImpactOperationsStoreTest`, `CheckedPreferencesTest`, `PersistenceFailureGuardTest` |
| A07 | Cloud connection details / 12 | Same `SettingsScreen`, pinned inspected profile ID via `SettingsNavigationState`; inspection does not select; token never rendered | `SettingsNavigationStateTest`, `PinnedCloudConnectionTest` |
| A08 | Remove local Cloud connection / 12 | Same `SettingsScreen` pinned-profile confirmation and pending-operation warning; `EncryptedHCloudTokenStore.removeConnection`, shared checked persistence/quarantine | `SettingsNavigationStateTest`, `CheckedPreferencesTest`, `PersistenceFailureGuardTest` |
| A09 | Appearance and language / 13 | Same `SettingsScreen`, shared `HCloudChoiceSheet`; `MainActivity.kt::currentLanguageMode`/`applyLanguage`, appearance preference and privacy HTTPS link | No dedicated physical preference-persistence evidence; shared state tests do not verify locale/theme application |
| A10 | Dark settings / 13 | Same `SettingsScreen` and shared `HCloudDesignTheme` semantic token pairs; no separate dark-screen implementation | Debug `DarkGallery`/`LargeTextGallery` fixtures; contrast, focus and actual device appearance UNRUN |

Profile failure guard caches only public metadata, blocks credential dispatch after
an unconfirmed write, and is shared across store recreation within the process.
This does **not** assert durable protection after process death when storage itself
cannot persist. `CloudHome` receives the same immutable owner used to build clients.

## Issue #9 — network overview, public IPs and PTR

| ID | Exact screen / PDF page | Canonical implementation / shared adapter | Supporting test classes |
| --- | --- | --- | --- |
| N01 | Network overview / 31 | `NetworkWorkspaceScreen.kt::NetworkWorkspaceScreen`; independent shared section loaders; LB/placement/firewall/private-network entries delegate to their owners | `NetworkCategorySearchTest`, `SectionStateTest`, `ReadFailureBoundaryContractTest` |
| N02 | Public IP inventory / 31 | Same file `PublicIpInventoryScreen`; Primary/Floating segmented list, search, contextual IDs and shared resource cards; only Primary creation | `ContextualResourceSelectionTest`, `ContextualTextDraftTest`; full tab/search interaction remains device UNRUN |
| N03 | Primary IP detail / 32 | `PublicIpDetailScreen.kt::PublicIpDetailScreen(PRIMARY)`; independent metadata/history, shared `ResourceProtectionControls`/`ProtectionAdapter`; mutation adapter rereads stable ID | `PublicIpMutationAdapterTest`, `PublicIpEligibilityTest`, `ProtectionAdapterTest` |
| N04 | Create Primary IP / 32 | `PrimaryIpCreationFlow.kt::PrimaryIpCreationFlow`; shared fields/pickers, labels parser, explicit server choice, unknown cost presentation, review/session/result and created-ID handoff | `PrimaryIpLifecycleContractTest`, `PublicIpEligibilityTest`, `DirtyFormExitGuardTest`; no dedicated end-to-end creation UI test claimed |
| N05 | Assign Primary IP / 33 | `PublicIpDetailScreen.kt::PublicIpMutationDialog`; `PublicIpMutationAdapter`, shared `primaryIpAssignmentBlock`; same-location/OFF/family constraints and excluded-target reasons | `PublicIpEligibilityTest`, `PublicIpMutationAdapterTest` |
| N06 | Floating IP detail / 33 | Same `PublicIpDetailScreen(FLOATING)` and protection/history components; `PublicIpMutationAdapter`, official server-assignment mapping; no Floating creation path | `FloatingIpAndLoadBalancerApiContractTest`, `FloatingIpOperationsContractTest`, `PublicIpMutationAdapterTest` |
| N07 | Reverse DNS / PTR / 34 | `ReverseDnsDialog.kt::ReverseDnsDialog`, `ReverseDnsValidation.kt`; `ServerDnsPtrDialog.kt` is a thin Server adapter; IP entries use the same editor/review/session/result | `ReverseDnsValidationTest`, `ServerDnsPtrContractTest`, `FloatingIpOperationsContractTest` (explicit `dns_ptr:null`), `ContextualTextDraftTest` |
| N13 | Delete public IP / 37 | Same mutation dialog and `PublicIpMutationAdapter.DELETE`; fresh known protection/unassigned checks, blank typed identity, durable original operation, DELETE reconciliation, no auto-detach/unprotect | `PublicIpMutationAdapterTest`, `PrimaryIpLifecycleContractTest`, `FloatingIpOperationsContractTest`, `FloatingIpDeletionPreconditionsTest` |
| N14 | Reassign Floating IP / 37 | Same mutation dialog and `floatingIpAssignmentBlock`; named old→new server review and connectivity acknowledgement; same-zone/family eligibility, not copied Primary power/location rules | `PublicIpEligibilityTest`, `PublicIpMutationAdapterTest`, `FloatingIpOperationsContractTest` |

Shared mutation consumers above use `OperationReviewDialog`, `rememberOperationSession`,
`awaitOriginalOperationActions` and `OperationResultDialog`; test coverage also includes
the common operation coordinator/persistence classes. Those tests do not replace
feature UI/process-recreation checks. N08–N12/N15–N16 belong to issue #11, not #9;
U03–U06 belong to issue #4, not #2. Reusing their components does not duplicate ownership.

## Verification boundary

Legacy locale storage (API 26–32) uses `autoStoreLocales` metadata on the disabled,
nonexported `AppLocalesMetadataHolderService`, not on the application element.
`AppLocaleManifestContractTest` checks the merged manifest for that exact binding;
the unit test task depends on manifest processing and adds no Robolectric/SDK runtime.
This follows the official [AndroidX service contract](https://developer.android.com/reference/androidx/appcompat/app/AppLocalesMetadataHolderService)
and [per-app language setup](https://developer.android.com/guide/topics/resources/app-languages#androidx-impl).
Actual legacy-device locale persistence remains UNRUN; a modern-phone locale pass
does not cover AndroidX storage on API 32 and lower.

Manual steps and safe helper/preview commands are in
[`redesign-device-acceptance.md`](redesign-device-acceptance.md).
All physical light/dark, EN/RU/DE, TalkBack, 200% text, touch-target, real gesture,
scroll/Back and process-recreation checks remain **UNRUN**. This matrix does not
claim every mockup variant is finished, that issue acceptance boxes can be checked,
or that the current in-progress integrated build has passed.
