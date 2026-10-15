# HCloud Control: shared implementation contract

## Baseline and ownership

Implementation baseline: `ksokolovskiy/hetzner-control`, main commit `f2a244923186d3c55e4f3c8380fbce5d229f5d2c`. Kotlin Android/Compose Material 3; app and core domain/network/security modules. The source was statically inspected, not built or runtime-tested. MainActivity owns state-based routes and many long screens. Existing shared headers, detail rows, state views, pull refresh, project-namespaced list state, protection controls and safe error formatting should evolve, not be independently rewritten.

The 65-page Russian design has 106 synthetic mockups for 58 source families. English issue requirements govern implementation behavior; translate app text into EN/RU/DE string resources. A populated typed confirmation in a mockup illustrates user input: actual fields start blank and acknowledgements unchecked.

Issue 01 owns tokens, scaffold, canonical navigation and visual primitives. Issue 02 owns operation state, reviews/protection, permission-reason presentation and results. Feature tasks own data mapping, eligibility, validations and resource adapters; they never create another operation state machine or duplicate a canonical detail. Suggested new packages `ui/` and `operations/` may follow repository conventions. Preserve existing API/repository behavior and avoid unrelated backend changes.

State identity is `(projectId or s3ConnectionId, environment/endpoint, resourceType, resourceId)`, with operation intent/action identity where relevant. Mutable names never own state. Persist only nonsecret navigation, filters, scroll, eligible drafts and operation identity. On project switch clear prior descendants/selections, while pending operations retain their original context. S3 is independent.

## Shared component interfaces

- AppScaffold/ProjectContext: one title, Back/root control, project/environment badge, overflow and system/IME insets. Five roots: Servers, Create, Storage, Network, More. Nested forms/details hide bottom bar. All aliases navigate to one canonical destination accepting identity/prefill.
- ResourceList/Card: contextual stable key, resource type/ID, title, text status, summary, updatedAt, stale and capabilities. Whole row opens detail; menu never directly mutates. Search/filter/scroll belong to context plus destination.
- ResourceDetail/SectionState: independently loading/content/empty/error/stale metadata, metrics, related resources and history. Failed history never blanks successful metadata. Missing/deleted resource has explicit safe return.
- Exactly one Tabs, SegmentedControl, FilterChip, Menu, Dialog and Confirmation implementation. Data-driven labels/selection/callbacks/accessibility; no feature copies differentiated only by resource type/color.
- FormShell/fields: label, value, help, error, required, keyboardType and enabled. One scrollable IME-safe submit. Numeric sizes/ports, autocorrect off for IP/CIDR/token/key. Inline errors and focus first invalid input. Picker returns into same draft. Dependency change clears only incompatible values with explanation.
- ReviewSheet: immutable exact context/name/ID, old→new changes, fresh preconditions, effects, cost/unknown components and risk inputs. Danger/normal/local-disconnect reuse shell, not wording. Blank typed identity and unchecked boxes; edited intent or changed dependencies invalidate confirmation.
- OperationResult: original intent/action/resource identity, stage/time, sanitized error and allowed next step. SecretResult is a separate transient model; generic history never contains secret text.
- CostPresentation: loading/available/unavailable/incomplete/error, timestamp, included/excluded components, type/location, applicable hourly/monthly, currency, gross/net/VAT meaning. Unknown never becomes zero; rates are not account spending.

## Visual tokens

Centralize colors/spacing/type in theme code; platform sans, no per-screen hard-coded copies.

Light: primary #0059B8, ink #17212F, surface #FFFFFF, background #F3F5F8, border #DFE5ED. Dark: primary #9DC5FF, ink #E0E7F2, surface #161D27, background #10151D. Danger light/dark #BA1A1A/#FFB4AB. Reference muted #677489, soft #EAF1FA, danger-soft #FFF0EE, information #315AE8, confirmed green #147D58, warning #A65D00; derive/test remaining dark pairs centrally. Green means confirmed state, never mere request acceptance; color always has text.

Reference canvas 360×760; 16dp content inset; spacing 4/8/12/16/24dp; card radii 12–16dp. Reference title 17sp, compact body 13–14sp, metadata 10–11sp; allow enlargement/wrapping for accessibility. Reference DejaVu fonts do not require Android font bundling. Rows at least 62dp, primary action 48dp, every hit target at least 48×48dp. No tiny chevron-only tap targets. IDs/IPs remain verbatim; long IPv6/RRsets/names wrap or expand fully. Units remain with quantities; prices localize without excessive decimals.

## Navigation, forms and read states

One back policy: close keyboard when applicable, close top picker/menu/review, return to preceding detail/list or wizard step, root returns to Servers before exiting. Preserve nonsecret fields; leaving dirty forms offers retain/discard. After acceptance Close only leaves presentation. Reopening pending operation restores status, not a second mutation form. Rotation/process recreation restores safe identity without resubmission.

Lists distinguish initial loading, content, real empty, filtered no-results, refreshing over content, timestamped stale/offline, permission denial and safe read retry. Pickers distinguish no eligible target; no connection differs from connected empty. Each detail section retries independently. Stale data does not prove current resource state and cannot justify unsafe mutation.

## Operation state machine

idle → validating → review → sending → accepted → running → succeeded / failed / outcomeUnknown. Immediate authoritative success and acceptance-only endpoints need appropriate typed adapters. Fresh-review protection, dependencies and state; recheck local gate/context at execution. App lock, API denial and resource protection are separate. Never auto-unprotect, auto-detach or alter unrelated dependencies.

Only one request per reviewed intent while pending. Double tap, recomposition, navigation, retry and process recreation cannot duplicate mutation. Persist nonsecret action/resource identity as soon as known. Timeout/lost response/process loss after possible submission is not failure. Unknown offers Check status and safe reconciliation of original intent, never automatic POST/DELETE retry. Without ID, reconcile resource state and remain unknown if ambiguity persists. Confirmed failure can permit new review only when safe. Read retry never replays writes.

Cancel before send can discard a draft/check. After acceptance use Close and state provider work continues. Confirmed success remains success if refresh fails; retry only read. Done cannot stay disabled by stale busy state. Storage Box snapshot acceptance is not completion without evidence.

## Authentication, secrets and boundaries

Cloud and Storage Box management share active Cloud token across API hosts. S3 uses separate access/secret profiles and endpoint. Cloud save needs visible read-only check; S3 already listBuckets-checks before save but lacks visible form busy/errors. Local credential removal neither revokes keys nor deletes resources. Legacy Box cleanup is distinct from Cloud disconnect.

Changes is one device-global local flag, off by default, not per-project/backend rights/provider protection. No automatic read-only-token detector is assumed. Secrets default masked; explicit reveal/copy only; never navigation state, ordinary drafts, operation identity, logs, analytics, notifications, screenshots or error text. Generated credentials use protected transient screen and lifecycle-aware clipboard; losing a secret must not replay mutation. Safe diagnostics only vetted field names/code/HTTP/request ID, no raw response/headers.

Excluded: SSH terminal/web console, LB creation/config editor, Floating IP creation, arbitrary firewall editor, certificate mutation/private keys, DNS writes/import/export, Storage Box lifecycle/rollback/snapshot-delete/scheduling, S3 objects/policies/versioning, account billing API. Server/IP PTR remains allowed, distinct from DNS zones. Firewall scope remains empty create plus additive inbound TCP preserving rules.

## No-duplication merge gate

Every PR lists reused components, new primitives and their owner. Reject copied tabs/menus/dialogs/confirmation/forms/status/polling, duplicate provisioning paths/details and feature-local token constants. Extend shared APIs with typed adapters. Necessary exception requires explicit rationale and migration/removal plan reviewed before merge. Extract incrementally and retain old entry-point regression coverage.

## Verification contract

Each feature supplies validation/state unit tests, fake-repository/API tests and Compose/navigation tests. Exercise pre-send offline, provider denial, stale review, double submit, lost response, accepted recreation, unknown reconciliation and success-plus-refresh-failure. Use synthetic fixtures; no paid/destructive live-provider requests for UI testing. Run repository-supported build/unit/lint and record exact results, including unavailable checks; never claim unexecuted tests passed.

Tracker closure requires all 58 families tested for entry/Back, switch project, rotation/process recreation, light/dark, EN/RU/DE, 200% font, TalkBack/focus, long names/IPv6/RRsets, contrast/tap targets/IME-safe fields and partial failures. Visual comparison does not replace behavioral assertions.
