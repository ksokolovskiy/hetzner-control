# Server and shared-operation screen evidence

Scope: GitHub issues #4, #5, #6 and #7; screen ownership is the design revision `4972d35` and the issue bodies, not a claim of pixel-perfect or device acceptance.

**Physical verification: UNRUN for every screen and variant below.** This document records source paths and synthetic/model/HTTP contract tests. It does not convert unit-test success into TalkBack, touch, Back-gesture, process-death, theme, translation, IME, or 200% font verification.

Source paths below are relative to the repository. App symbols are under `app/src/main/kotlin/com/hcloudcontrol/`, operation helpers under its `operations/` directory. Test class names denote actual files in `app/src/test/kotlin/com/hcloudcontrol/`, `operations/`, or `core/network/src/test/kotlin/com/hcloudcontrol/core/network/`.

## Issue #4: shared operation contract

| Screen | Canonical presentation / execution owner | Synthetic evidence |
| --- | --- | --- |
| S07 Protection review | `ResourceProtectionControls`; `OperationReviewDialog`; typed `ProtectionAdapter`; `OperationCoordinator` | `ProtectionAdapterTest`, `ServerProtectionContractTest`, `ResourceProtectionContractTest`, `ProtectionPresenceContractTest`, `OperationCoordinatorTest` |
| S13 One-time secret | `SecretResultDialog`; transient `SecretResult` / `consumeSecretResult`; provisioning, recovery and rebuild consume this same result | `SecretResultLifecycleTest`, secret cases in `OperationCoordinatorTest`, nullable rebuild secret in `ServerDestructiveOperationsContractTest` |
| U03 Changes unavailable | device-global `LocalHighImpactOperations`; provider HTTP rejection becomes `OperationDiagnostic`; separate protection availability/known state | `MutationRejectionStateTest`, `MutationFailureBoundaryContractTest`, gate/context cases in `OperationCoordinatorTest` |
| U04 Unknown outcome | `OperationResultPresentation` / `OperationResultDialog`; `OperationCoordinator`; `OperationSession`; `OperationPersistence` / `SharedPreferencesOperationStore` | `OperationCoordinatorTest`: lost response, sending restore, accepted restore, original context, duplicate/create scope lock, ambiguous evidence and exact action bundle |
| U05 Confirmed result | same shared result presentation; `startNewAttemptAfterTerminalClose`; read-refresh failure remains success | `OperationResultPolicyTest`, `OperationAttemptTest`, success-plus-refresh-error cases in `OperationCoordinatorTest` |
| U06 Rejected operation | same shared result; `OperationSession.rejectOrUnknown`; `safeHCloudApiCall` / `HCloudApiErrorMapper`; no raw exception text in durable journal | `MutationFailureBoundaryContractTest`, `MutationRejectionStateTest`, `CreateServerFailureMessageTest`, `OperationCoordinatorTest` |

Durable operation data is allowlisted: original context/resource/action identity, stage, provider action IDs, returned resource ID and vetted diagnostics. Credentials, generated passwords, arbitrary exceptions and mutation payloads are not journal fields. `SENDING` restores as unknown. Reconciliation never attributes an existing same-name resource to a lost create request.

Generated-password screen security and clipboard ownership/grace cleanup are implemented in source, but their Android lifecycle behavior is **physically UNRUN**. The pure secret tests cover masking state, acknowledgement, independent copies, destruction and already-consumed input; they are not clipboard instrumentation tests.

## Issue #5: one catalogue and provisioning workflow

| Screen | Canonical presentation / execution owner | Synthetic evidence |
| --- | --- | --- |
| C01 Create and catalogue | `ServerCatalogScreen.kt: ServerCatalogScreen`, `CatalogConfigureAction`, `CatalogChangesDisabled`; root configure route enters the same `ServerProvisioningScreen` wrapper | `CanonicalNavigationTest`, `ContextualResourceSelectionTest`, `DestinationNavigationTest` (route/state models, not UI execution) |
| C02 Type detail | `ServerCatalogScreen.kt: ServerTypeDetailScreen`; repository type/pricing reads; same type-prefilled wizard | `ServerMetadataContractTest`, catalogue cases in `RetrofitHCloudRepositoryPaginationTest`, `CostPresentationTest` |
| C03 Location detail | `ServerCatalogScreen.kt: LocationDetailScreen`; `LocationPresentation`; same location-prefilled wizard | `LocationPresentationTest`, location/network-zone mapping in `ServerMetadataContractTest`, `ContextualResourceSelectionTest` |
| C04 Resources | `UnifiedProvisioningWizard.kt: UnifiedServerProvisioningScreen`; `ProvisioningDraft`; shared form, choices and dirty-form exit guard | `ProvisioningDraftTest`, `DirtyFormExitGuardTest` |
| C05 Type selection | `UnifiedProvisioningWizard.kt: WizardTypePicker`; `ServerTypeFilters`; numeric CPU/RAM/disk ordering | filter/order/reset cases in `ProvisioningDraftTest` |
| C06 Access | same wizard Access step; existing `SshKeySelector`; existing SSH add route; public IPv4/IPv6 booleans in the same draft | draft round-trip/context cases in `ProvisioningDraftTest`; SSH contract/adapter tests are owned by the shared SSH flow |
| C07 Paid creation review | same wizard; `OperationReviewDialog`; `HCloudCostPresentation`; fresh type/image/location/SSH ID and quote reads; `OperationSession.beginReviewed` | `CostPresentationTest`, quote comparison in `ProvisioningDraftTest`, `OperationCoordinatorTest` |
| C08 Track creation | same wizard result branch; one `repository.createServer` path; `awaitOriginalOperationActions`; original returned resource route; shared `SecretResultDialog` | duplicate/create lock, original action bundle, accepted recreation, lost secret and ambiguous create cases in `OperationCoordinatorTest`; `OperationAttemptTest` |

`ServerProvisioningScreen.kt` is a compatibility entry wrapper, not a second creation implementation. All catalogue/server-list prefills use the same ID-only draft and mutation call. The submitted intent is not replayed on UI restoration. Type/architecture or location changes invalidate incompatible dependent choices rather than preserving an invalid image/type pair silently.

Wizard display inventory now has four independent shared `SectionLoader` owners (types, images, locations, SSH keys), rather than one all-or-nothing request state. Each picker retains its own data/error/retry state, with stale choices not confirmable. Resize type inventory uses the same shared owner. Both creation and resize display prices consume the same `rememberWizardPrice`/`WizardPriceSection` shared-loader helper; explicit fresh review/submission quote reads remain separate safety preconditions. This checkpoint awaits the next integrated build and physical verification.

Catalogue Types/Locations share a context-scoped saved local search with case-insensitive term matching against codes/names/human labels. Type details offer each explicitly available location as a prefill into the same existing wizard callback, alongside type-only configure. Picker empty-state presentation distinguishes read loading/failure, true inventory empty, no eligible targets and filtered no results; uncommitted picker IDs use saveable context-scoped state. `CatalogueSelectionPresentationTest` exercises the search and empty-state rules; these latest fixes still await aggregate build/device verification.

Refreshed successful inventories reconcile restored draft IDs without selecting replacement IDs: removed resources are cleared with a localized replacement notice, changed architecture/availability clears incompatible dependent choices, and pending prefills remain unapplied until their original IDs actually arrive. An active numeric CPU filter always has an explicit reset button even if its original value disappears. `ProvisioningDraftTest` covers removed IDs, changed architecture and withdrawn location availability.

Snapshot description now uses a context/server/action-scoped saveable draft under the existing shared state holder and dirty-exit decision; confirmations and generated secrets are not saved. Server result read retries use shared `refreshOperationResource` with an actual original-resource read before clearing `refreshFailed`; callback scheduling alone is not read proof. Recovery review status is localized, and type availability explicitly disclaims capacity guarantees in EN/RU/DE. `OperationReadRefreshTest` covers failed read then verified retry with unchanged successful action identity and rejects refresh as evidence for a pending write. These latest source changes await the next aggregate checks.

Paid creation and resize re-read the quoted rate before send. Known quote changes invalidate review; unavailable/failed price remains explicitly unknown and requires the shared unchecked acknowledgement. Server prices are provider decimal hourly/monthly rates, not a locally multiplied monthly estimate. Public IPv4 extra cost is not invented when unavailable.

## Issue #6: rebuild, recovery, ISO and deletion

| Screen | Canonical presentation / execution owner | Synthetic evidence |
| --- | --- | --- |
| S04 Rebuild | `ServerDestructiveOperationsSection.kt: RebuildServerDialog`; shared image/read/review/result/secret components; fresh rebuild protection and image architecture checks | `ServerDestructiveOperationsContractTest` includes nullable password and unsupported SSH-field rejection; `OperationCoordinatorTest`; `SecretResultLifecycleTest` |
| S11 Server ISO catalogue | `ServerIsoSection`; independent current attachment; shared section states/cards/dialog; original-context saved selected ID, query and catalogue scroll | ISO API cases in `RetrofitHCloudRepositoryPaginationTest`; `ServerRoutineAdapterTest` |
| S12 Recovery actions | `ServerRecoveryDialog`, distinct `ServerRecoveryOperation` variants; same shared review/result/secret journal; reset-password requires running server | recovery cases in `RetrofitHCloudRepositoryPaginationTest`; `OperationCoordinatorTest`; `SecretResultLifecycleTest` |
| S14 Delete server | `ServerDeletionDialog`; `ServerDeletionAdapter`; shared typed-name/keyword/risk review and result | `ServerDeletionAdapterTest`, `ServerDestructiveOperationsContractTest`, `OperationCoordinatorTest` (acknowledgement/review model, not typed-field UI instrumentation) |
| S15 ISO detail | same `ServerIsoSection` selection/detail flow; saved IDs/scroll; `isoReviewIdentity`; same `ServerRoutineFlow` / `ServerRoutineAdapter` attach/detach | exact identity case in `ServerRoutineAdapterTest`; ISO contract cases in `RetrofitHCloudRepositoryPaginationTest` |
| S16 Rescue review | same `ServerRecoveryDialog`; `OperationReviewDialog`; separate enable/disable/reset copy; shared terminal result | recovery cases in `RetrofitHCloudRepositoryPaginationTest`; accepted/recreation/unknown cases in `OperationCoordinatorTest`; `SecretResultLifecycleTest` |

Rebuild, recovery, routine actions and resize pin their original server snapshot through result Close, so a parent read refresh does not erase the accepted result or transient secret. ISO review includes the selected name and immutable ISO ID, not only an encoded action string. No attach path silently detaches/replaces existing media. No rebuild/delete path disables protection automatically.

## Issue #7: server inventory and routine lifecycle

| Screen | Canonical presentation / execution owner | Synthetic evidence |
| --- | --- | --- |
| S01 Inventory | `MainActivity.kt: ServersScreen`; `ServerInventoryFilter`; shared cards, section states and restorable list state | `ServerInventoryFilterTest`, `ui.state.ServerInventoryReadPresentationTest`, `CanonicalNavigationTest`, `ScrollPositionRegistryTest` |
| S02 Overview | `MainActivity.kt: ServerDetails`, `DetailsCard`; shared Overview/Metrics/Actions tabs; real `ServerHardware`, `ServerImageInfo`, `LocationDetails`; readonly protection flags; `ServerNetworkDeviceSection` supplies IPv4/IPv6 | `ServerMetadataContractTest`, `ProtectionPresenceContractTest`, `CanonicalNavigationTest` |
| S03 Grouped actions | `ServerDetails` Actions tab; `PowerActions`; `ServerManagementSection`; `ServerDestructiveOperationsSection`; recovery/ISO/protection/PTR canonical owners | `ServerRoutineAdapterTest`, `ServerDeletionAdapterTest`, `ProtectionAdapterTest`; device menu/layout eligibility remains UNRUN |
| S05 Metrics/events | `ServerMetricsSummary` and `ServerActionHistory` under independent `SectionLoader`s; shared `HCloudMetricSeriesCard` also consumed by Load Balancer metrics; existing fixed six-hour query | `MetricSeriesTest`, `ServerMetricsContractTest`, `ServerActionsContractTest`, `SectionStateTest`, `SectionReadFailureTest` |
| S06 Metadata edit | `ServerManagementSection.kt: ServerMetadataDialog` delegates immutable patch to `ServerRoutineFlow` / `ServerRoutineAdapter`; one official labels validator | label cases in `ServerRoutineAdapterTest`, `ServerConfigurationContractTest`, `MutationFailureBoundaryContractTest` |
| S08 Power review | `PowerConfirmation` only maps five variants into `ServerRoutineFlow` / `ServerRoutineAdapter`; fresh eligibility, one journal and original-action tracking | `ServerRoutineAdapterTest`, power HTTP case in `MutationFailureBoundaryContractTest`, `MutationRejectionStateTest` |
| S09 Resize | `UnifiedResizeServerScreen`; `serverResizeEligible`; shared review/cost/result and original-action tracking | `ServerResizeEligibilityTest`, `ProvisioningDraftTest`, relevant server contract/pagination tests, `OperationCoordinatorTest` |
| S10 Backups/snapshot | `StorageActionConfirmation` delegates enable/disable/snapshot to the same routine adapter and shared review/result | `ServerRoutineAdapterTest`; server action/backup/snapshot contract cases in `RetrofitHCloudRepositoryPaginationTest`; `MutationFailureBoundaryContractTest` |

Metric plots use actual provider samples, preserve their timestamp/value table, do not manufacture zeroes and break invalid/large gaps. Missing actual server disk size is never replaced by nominal plan size for resize eligibility. Disk growth begins disabled and has its own unchecked irreversible acknowledgement. Disable-backup review explicitly warns that existing automatic backups are immediately deleted; standalone snapshots are billed per GB and running-disk consistency must be considered.

## Verification limits and remaining acceptance work

- Latest aggregate build/unit/lint evidence is maintained by the root agent in `docs/redesign-implementation-progress.md`; do not infer a new successful build merely from this source map. The most recent ISO presentation/scroll and readonly-overview changes were source-ready when this document was written and still required the next aggregate compile/test/lint run.
- Every physical screen/variant remains **UNRUN** in this evidence file: light/dark/automatic theme, EN/RU/DE, TalkBack/focus, 200% font, long names and addresses, 48dp targets, IME/keyboard Back, swipe refresh, actual navigation return, scroll restoration, rotation/process loss and secure clipboard lifecycle. Synthetic test names above do not claim these checks.
- Rebuild/recovery secrets and unknown/accepted results must be exercised with synthetic/mock responses only; no live paid or destructive Hetzner calls were made for this work.
- Catalogue list/detail now use independently keyed shared `SectionLoader` owners for types, locations, detail metadata and pricing. Refresh preserves prior data with stale/error/timestamp presentation; metadata remains visible if pricing fails. Scope includes the original project/endpoint/environment, and detail/list scroll and numeric CPU filtering remain separate. This source migration awaits the next integrated build and physical UI matrix.
- Server metadata edit now uses the shared dialog/form fields, inline validation, original-context safe draft and dirty-exit guard, then the same shared review/journal execution. This root-owned source change awaits the next integrated build and physical UI verification. Grouped server action layout and canonical resource-link parity still require their final screen-by-screen acceptance pass, not just API tests.
- Query/picker/detail preservation has model/source coverage, not Compose instrumentation coverage. Catalogue prefills and every IPv4/IPv6/SSH/unknown-cost combination still need the specified UI matrix.

## Official API corrections verified during this work

- `PUT /servers/{id}` contains name/labels only; placement membership uses its documented actions. Removing a server from a placement group sends no invented body.
- Rebuild accepts image (and documented optional user data), not SSH-key selection; its returned root password may be null without losing the original action.
- `DELETE /volumes/{id}` and network deletion require authoritative 204, not fabricated asynchronous actions. Volume creation requires its primary action and `next_actions`; every original action is retained and reconciled as a bundle.
- Missing protection or an incomplete protection object is unknown, not disabled. Floating IP assignment is the official `server` field rather than Primary-IP `assignee_*` fields.
- Storage Box action status uses the original `api.hetzner.com` repository and `storage_boxes/actions/{id}`, not the Cloud action host. This is adjacent #14 integration evidence, not additional screen ownership in this map.

Primary schemas: <https://docs.hetzner.cloud/cloud.spec.json> and <https://docs.hetzner.cloud/hetzner.spec.json>.
