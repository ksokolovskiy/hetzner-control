# Feature coverage matrix

This is an engineering scope matrix, not a promise that every browser-console
control is available in the app. It is reviewed against the official
[Hetzner Cloud API reference](https://docs.hetzner.cloud/reference/cloud), its
[changelog](https://docs.hetzner.cloud/changelog), and the separate official
[Storage Box reference](https://docs.hetzner.cloud/reference/hetzner#tag/storage-boxes).
The supported Cloud API surface is `api.hetzner.cloud/v1`; Storage Boxes use
the selected project's bearer token with their own `api.hetzner.com/v1` host
and client. Robot and the legacy DNS
Console are deliberately out of scope.

## Status legend

- **Available** — implemented in the repository and reachable in the app.
- **Read-only** — available without a resource mutation.
- **Next safe** — a documented, non-destructive gap suitable for a later
  increment after contract tests and UI error states are added.
- **Explicit confirmation** — do not add a caller-facing action until its
  cost, outage, data-loss, or security consequences are designed and approved.
- **Out of scope** — not the Cloud/Storage Box product surface supported by
  this app.

## Cloud API resources

| Official resource | Current coverage | Safe missing capability | Confirmation boundary |
| --- | --- | --- | --- |
| Profile | Credential validation plus a read-only profile summary in Settings. | None currently identified. | None. |
| Servers | List/detail; create; power actions; resize; labels/name; protection; backups/snapshots; ISO; PTR; root-password reset/rescue; per-server actions and metrics. | Richer read-only network/device detail. A global action feed is not available. | Delete/rebuild, rescue boot changes, reset/password changes, snapshot/backup and server creation need clear per-action confirmation; creation is billable. |
| Server types, locations, pricing | Available in provisioning, filtering and official rate-card display. | Documented per-type and per-location read-only detail/availability/deprecation fields. | None. |
| Images | Read-only list/detail for system, backup and snapshot images, plus scoped action history. | Read-only filtering/search refinements. | Create/delete image, protection changes and image restore can change cost or data availability. |
| ISOs | Read-only list/detail plus attach/detach on a server. | ISO metadata/search refinements. | Attach/detach changes boot/install media and remains explicitly reviewed. |
| SSH keys | Read-only selection for server creation and inventory screen. | Read-only filtering/search refinements. | Create/update/delete keys affect access to machines. |
| Volumes | List, read-only detail and scoped action history; attach, detach and monotonic resize with acknowledgement. | Read-only filtering/search refinements. | Create/delete, resize (irreversible), and attachment changes need explicit review. |
| Primary IPs | List/detail/action history; assign/unassign; PTR set/clear. | Read-only filtering/search refinements. | Create/delete, assignment and PTR changes alter public connectivity. |
| Floating IPs | List/detail/action history; assign/unassign; PTR set/clear. | Read-only filtering/search refinements. | Create/delete, assignment and PTR changes alter public connectivity. |
| Firewalls | Inventory/detail/action history; direct server apply/remove, with action polling. | Read-only filtering/search refinements. | Create/delete/rule edits can expose or block traffic. |
| Private Networks | Inventory, detail/member reads and non-mutating topology visualization; attach/detach an existing server using API-managed addressing. | Documented scoped action history; read-only filtering/search refinements. | Create/delete, route/subnet edits, aliases and explicit addressing can interrupt traffic. |
| Load Balancers | Read-only list/detail, type inventory, metrics and action history. | Read-only filtering/search refinements. | Create/delete, targets/services, algorithms, public interface and network attachment all affect traffic/cost. |
| Placement Groups | List/detail and add/remove existing server membership. | Action-history if introduced by the API. | Create/delete/type/label edits can change placement and resilience. |
| Certificates | Read-only list/detail and action history; no private-key material. | Read-only filtering/search. | Create/import/delete/renew/retry can affect TLS availability and must never expose private keys. |
| DNS | Explicitly acknowledged, read-only zones and RRsets, using the selected Cloud-project token only. | Read-only pagination/filter refinements. | Zone/RRset create/update/delete, import/export and zone-file workflows can cause outages and need a separate confirmation model. |
| Generic actions | Individual action lookup and scoped server/load-balancer/certificate/image/volume/firewall/Primary-IP/Floating-IP histories. | No project-wide feed: `GET /actions` now accepts only already-known action IDs. | None for read-only history. |

## Storage Box API resources

| Official resource | Current coverage | Safe missing capability | Confirmation boundary |
| --- | --- | --- | --- |
| Storage Boxes | Active-project inventory/detail, statistics and folder names via the selected project token. | Read-only filters/search. | Create/update/delete Storage Boxes changes billing or data access. |
| Snapshots | Read-only list/detail and manually create a snapshot with explicit UI state. | Read-only action history if exposed by the API. | Delete, restore/rollback and schedule changes can lose or replace data/cost; require explicit approval. |
| Subaccounts | Read-only identity metadata (name/username); no passwords displayed. | Read-only pagination/filter refinements. | Create/update/delete/reset-password operations alter access and need explicit confirmation. |

## Deliberate product boundaries

- **Accounts, invoices, accrued spend, balance and payment methods:** not exposed
  by the documented public Cloud API. The app shows only the official public
  rate card; it does not scrape the Hetzner Console or embed an authenticated
  browser session. See the current billing notes in the official
  [Cloud billing FAQ](https://docs.hetzner.com/cloud/billing/faq/).
- **Robot, dedicated servers, vSwitch management, legacy DNS Console and
  Object Storage:** separate products/credentials and out of the current app
  scope. They must not be silently routed through a Cloud-project token.

## Next implementation order

1. Add remaining resource **detail/action-history reads** before any new
   mutation surface.
2. For each action in the confirmation column, document the user impact,
   require a review screen, keep the result on the originating detail screen,
   poll the returned action, and refresh only that resource scope.

Before implementing any endpoint in this matrix, re-check its current request
and response contract in the official reference and add a contract test. The
Hetzner changelog has changed resource fields and endpoints in the past, so
browser-console behaviour is never used as an API specification.

### Important action-feed correction

The official reference now says `GET /actions` accepts one or more known `id`
parameters only. Its former project-wide listing was deprecated in July 2023
and removed on 30 January 2025. Therefore this app must **not** invent a
paginated global action feed. The closest supported read-only capability is a
resource-scoped history such as `GET /servers/{id}/actions`, which is the
direction used by the existing detail screens. Source:
[official actions reference](https://docs.hetzner.cloud/reference/cloud) and
[removal announcement](https://docs.hetzner.cloud/changelog#2025-01-30-listing-arbitrary-actions-in-the-actions-list-endpoint-is-removed).
