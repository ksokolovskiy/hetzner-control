# Product IA review — Hetzner Cloud Control

Date: 2026-10-14
Method: independent product review of the current Android implementation,
`docs/feature-coverage.md`, and the official Hetzner Cloud API reference.

## Executive decision

The current **Catalog** is a disconnected, read-only list of server types and
locations.  It duplicates data that is already loaded by the server-provisioning
flow, but it has no next step.  It should **not** remain a primary destination
in that form.

Replace it with a primary **Create** destination whose first useful job is
**Create server**.  It must reuse the existing `ServerProvisioningScreen` as
the one and only order workflow, not create a second server-creation path.
The server-type catalogue becomes the first optional selection step of that
workflow: selecting a type and/or location continues to the same configurator
with those values prefilled.

This is supported by the public API: `POST /servers` provisions a server; the
documented server type and location resources supply the choices; and prices
are location-specific.  The API documentation explicitly says a server type
has both hourly and monthly cost and the customer pays the lower amount for
that server usage.  Sources: [Cloud API reference](https://docs.hetzner.cloud/reference/cloud),
[server types](https://docs.hetzner.cloud/reference/cloud#tag/server-types),
and [locations](https://docs.hetzner.cloud/reference/cloud#tag/locations).

## Findings

| Finding | Evidence in current product | Product consequence | Decision |
| --- | --- | --- | --- |
| Catalog has no outcome | `ServerCatalogScreen` only opens type/location details.  The actual create flow is separately reachable from the Servers FAB. | It looks like a store but cannot order anything. | Replace primary Catalog with Create; catalogue is a child/step, not an isolated destination. |
| Creation has one valid workflow already | `ServerProvisioningScreen` already obtains types, images, locations, SSH keys, availability and location-specific price. | A second form would diverge in validation, safety review and pricing. | Reuse it for every “Create server” entry point. |
| Bottom navigation has too many concepts | 13 destinations are flattened into four bottom items plus a textual-symbol overflow sheet. | Labels wrap and people cannot predict where an object lives. | Keep four task-oriented primary destinations plus More. |
| High-impact controls are intentionally hidden when disabled | The device-local capability is currently the safety boundary. | A user needs a discoverable explanation but must not see executable destructive/billable controls when it is off. | Show read-only inventory/rate data; hide executable CTAs; explain in-place that Changes are disabled and point to Settings. |
| Storage Box is a separate product | It has a separate API host, but uses the selected Console project's token. | It must not be presented as simply another Cloud Volume. | Keep it as a distinct advanced destination under More. |
| Billing data is limited | The public Cloud API exposes rate cards, not account balance, invoices, or accrued charges. | Calling Tariffs “Billing” creates a false expectation. | Name it Prices / Rate card; state its scope clearly. |
| Back navigation has no single route stack | Screen selection and several detail states are local Compose state. | A gesture can finish the activity from a nested detail rather than return to its parent. | Introduce one application navigation model before adding more screens. |

The product-surface claims above are verified against the official reference:
the documented API includes creation endpoints for Servers, Volumes, Networks,
Load Balancers and SSH Keys, as well as Storage Box endpoints on the separate
API.  It also describes Networks as optional private network interfaces and
documents distinct per-project API tokens.  See the [official API overview](https://docs.hetzner.cloud/)
and [Cloud API reference](https://docs.hetzner.cloud/reference/cloud).

## Recommended information architecture

### Primary navigation

| Destination | Primary job-to-be-done | First screen / primary action | Contents |
| --- | --- | --- | --- |
| **Servers** | “What is running, and what needs my attention?” | Server list and status; **Create server** FAB when changes are permitted. | Empty state also offers Create server.  Detail owns power, console/recovery, resize, images/backups, networking and protection. |
| **Create** | “I need to add capacity safely.” | Start **Create server**; type catalogue is a filterable first step. | Server types, location availability, official rate card, image, SSH key, network/IP options, final reviewed submission.  A type or location card has `Configure server` rather than merely `Open`. |
| **Storage** | “Where is my data and how do I protect/attach it?” | Volumes, snapshots and backups. | Create volume; volume lifecycle from detail; images/snapshots link to their detail.  Do not mix Storage Boxes here. |
| **Network** | “How is traffic addressed, protected and routed?” | A concise inventory with sections: Public IPs, Private Networks, Firewalls, Load Balancers. | Each section opens a resource list/detail.  Creation begins from its respective section after an explicit review; existing actions remain on detail. |
| **More** | “I need a less-frequent product or project capability.” | Categorized sheet/page, not an unstructured flat list. | See below. |

The Create destination is shown only as a **read-only catalogue** when the
device-level Changes capability is disabled: it may show prices and
availability but has no `Configure`/`Create` control.  Its explanatory state
links to **More → Settings → Changes**.  When enabled, Create is an executable
workflow with the existing final confirmation.  This keeps critical operations
unhidden in the *information architecture* but hidden as executable controls
until the user gives the device-level permission.

### More — grouped, not flat

| Group | Destinations | Reason |
| --- | --- | --- |
| **Security & access** | SSH Keys, Certificates | They control access/identity and are normally set up before provisioning. |
| **Operations & resilience** | Images, Placement Groups | Both are supporting configuration, not daily top-level destinations. |
| **DNS** | DNS (explicit read-only scope) | Separate scope and safety model; retain the read-only disclosure. |
| **Separate Hetzner product** | Storage Boxes | Separate API host and semantics, distinct from Cloud volumes and snapshots. |
| **Costs & project** | Prices / Rate card, Settings | Prices are not account billing; Settings owns projects, API profile, appearance, language and Changes permission. |

## Creation and transition map

No creating action should be a hidden feature of a static inventory card.

| Resource | Start location | One workflow / review boundary | Completion destination |
| --- | --- | --- | --- |
| Server | Servers FAB/empty state; Create; server-type/location detail | `ServerProvisioningScreen`, with selected type/location passed in; create acknowledgement and API result handling. | Newly created server detail, then server list on Back. |
| Volume | Storage header | Volume form + explicit size/location/cost acknowledgement. | Created volume detail. |
| Private network | Network → Private Networks header | Network/subnet/route review; traffic-interruption warning. | Created network detail. |
| Load balancer | Network → Load Balancers header | Full type/location/target/service review.  Do not expose a partial “quick create” with guessed networking defaults. | Created load-balancer detail. |
| SSH key | More → Security & access → SSH Keys | Public key only; do not display or store private material. | SSH key inventory. |
| Storage Box | More → Storage Boxes | Kept separate until a complete lifecycle and billing review exists. | Storage Box inventory/detail. |

The official API documents `POST /servers`, `POST /volumes`, `POST /networks`,
`POST /load_balancers`, and `POST /ssh_keys`; those endpoints justify the
entry points, but each remains behind the existing local Changes capability
and a resource-specific review.  The API should be the request-contract source
of truth, never the browser console.

## Back behaviour — product rule

Use a single navigation back stack (for example Navigation Compose routes),
not unrelated `selectedX` booleans.  Both the top-left Back affordance and the
Android back gesture must use the same dispatcher.

1. A modal, picker, confirmation or bottom sheet is dismissed first.
2. A detail returns to its exact parent list; it never finishes the activity.
3. A child of a detail (action history, image, provisioner step) returns to
   that detail or prior wizard step.
4. A Create-server flow returns to its source (Servers or Create) on cancel;
   success opens the created server detail.
5. A top-level destination is a root.  Android Back at a root may finish the
   app according to normal Android convention, but it must never do so from a
   nested route.  Switching bottom tabs does not manufacture a detail route.
6. Project changes clear project-scoped detail routes before the new project
   loads, preventing a stale-resource Back destination.

Acceptance test matrix: exercise the physical/gesture Back path for every
list → detail, list → create → picker/review, More → grouped child → detail,
and Settings → confirmation.  The Settings crash is a release-blocking defect,
not an expected navigation outcome, and must have a regression test before
this IA is shipped.

## Minimal implementation plan (highest priority first)

| Priority | Change | Why it comes first | Definition of done |
| --- | --- | --- | --- |
| P0 | Repair Settings crash and introduce a centralized back-stack model for the current screens. | Users cannot enable changes or safely leave details; new features would multiply broken paths. | Gesture/top Back succeeds for each current nested route; Settings opens, changes appearance/language/permission, returns without activity finish/crash. |
| P0 | Convert Catalog to Create and wire catalogue type/location `Configure server` actions into the existing provisioning screen. | Immediately makes the primary tab useful without duplicate API/mutation code. | All three entry points open the same provisioner; values prefill; no second create request/form; disabled permission hides executable CTA. |
| P1 | Replace More’s flat list with groups and move Load Balancers under Network. | Fixes discoverability and removes the misleading “everything is a tab” model. | Localized grouped labels fit at supported font scale; each resource has one canonical home. |
| P1 | Standardize numeric presentation via domain formatters (price, capacity, IP/metadata rows) and add visual/golden tests. | The user has already observed pricing and storage readability defects. | Monetary values have currency/locale/limited fractional digits; storage metadata has labelled spaced rows; no raw decimal strings. |
| P1 | In read-only resource detail, make protection state actionable when Changes is enabled; otherwise state why the control is absent. | “Protection disabled” is otherwise a dead-end status. | Exact action is in detail, behind a review and action polling; disabled-capability state gives a non-actionable explanation. |
| P2 | Build full reviewed workflows for Network → Load Balancer creation and any remaining resource creations. | They need more design choices and traffic/cost safety review than a server. | Official-contract tests, explicit preflight/review, action polling, error recovery and localized UI. |

## Explicit non-goals for this increment

- Do not call the rate card “Billing,” and do not scrape the Hetzner Console
  for invoices/balance/accrued spend.
- Do not add a second server form solely to make Catalog appear interactive.
- Do not expose a critical/billable control when the local Changes capability
  is off; navigation may explain availability, but must not execute it.
- Do not merge Storage Boxes with Cloud Volumes: they use different product
  semantics even though Storage Boxes authenticate with the active project token.
