# Authorized mutation inventory

This document turns the user's authorization for powerful operations into a
bounded implementation plan. It is **not** an implementation of any mutation.
The permission is intentionally off by default and all controls below remain
hidden until the profile-level permission has been enabled in Settings.

The source of truth is the official [Hetzner Cloud API reference](https://docs.hetzner.cloud/reference/cloud), the [Cloud changelog](https://docs.hetzner.cloud/changelog), and the separate [Storage Box API reference](https://docs.hetzner.cloud/reference/hetzner#tag/storage-boxes), checked on 2026-10-14. Cloud requests use the selected project's `api.hetzner.cloud/v1` bearer token; Storage Box lifecycle requests use that same selected-project bearer token with `api.hetzner.com/v1`. An old separately stored Storage Box credential must never be silently reused.

## Permission and universal UX contract

### Profile setting

Add an encrypted-profile setting named **Allow resource-changing and billable
operations**. It applies only to the selected profile, defaults to `false`, and
does not grant API permissions: the server-side token remains authoritative.
Changing profile or enabling test/demo mode re-evaluates it. When disabled,
mutation controls are not merely disabled: they are absent from resource screens
and deep-link routes cannot invoke a mutation.

### Every mutation

1. Fetch/retain the resource detail immediately before opening its review UI.
2. Show exact resource name/ID, project/profile, endpoint effect, charge/data
   or connectivity consequence, and all changed fields. Never show a token,
   password, certificate private key, or raw request body in diagnostics.
3. Require a deliberately named confirmation control. For irreversible delete,
   rebuild, image restore, DNS replacement, and Storage Box rollback, require
   typed confirmation of the displayed resource name or ID.
4. The primary confirmation must stay on the originating detail screen. Keep a
   pending/error/success state there; never navigate back to the dashboard.
5. For `201`/`202` Action responses, poll the returned action by its known ID,
   then refresh only its affected scope. For `204`, re-fetch the resource list
   and report completion only after absence/presence is observed.
6. Block duplicate submissions while pending. Explain documented 4xx API error
   text safely; redact authorization and credentials from logs and UI.
7. Add a contract test for endpoint, JSON omission/null semantics, response
   status, action polling, and the permission gate before exposing a control.

## Cloud API: first implementation batches

`DELETE` below means an irreversible or potentially data-losing operation. All
contracts are to be re-read in the linked API operation immediately before code
is written; this table records only fields and preconditions that are stable and
needed to decide scope.

| Batch | Endpoint(s) | Contract / prerequisites | User impact and review copy |
| --- | --- | --- | --- |
| 1: delete unassigned address | `DELETE /floating_ips/{id}`; `DELETE /primary_ips/{id}` | Path ID only. The official May 2026 change requires the IP to be unassigned; otherwise API returns `must_be_unassigned`. Refresh list after `204`. | Stops billing only after deletion, permanently releases the address; it may not be recoverable. Display IP/type/location/assignee. Typed IP confirmation. |
| 1: delete image | `DELETE /images/{id}` | Path ID only; only user-managed image kinds are eligible; API enforces protection/resource constraints. Refresh list after `204`. | Permanently removes selected snapshot/backup image; snapshot storage charge ends. Backup is tied to server semantics. Display type, description, size and created time. Typed image ID confirmation. |
| 1: delete placement group | `DELETE /placement_groups/{id}` | Path ID only; API determines whether current membership/protection blocks it. Refresh list after `204`. | Removes placement policy, potentially changing future resilience intent. Typed group name/ID confirmation. |
| 1: delete SSH key | `DELETE /ssh_keys/{id}` | Path ID only. Existing server access is not automatically revoked; future injection is affected. Refresh after `204`. | Removes public key from project, can prevent future access. Typed key name/ID confirmation. |
| 2: delete server | `DELETE /servers/{id}` | Path ID only. Re-fetch detail first for name/status/volumes/protection; API is authoritative for protection/attachment failures. Refresh after `204`. | Permanent VM removal. Official docs say backups bound to the server are deleted too. Do **not** imply attached volumes are deleted unless API confirms it. Typed server name + explicit “delete server” acknowledgement. |
| 2: rebuild server | `POST /servers/{id}/actions/rebuild` | Requires image selector and optional SSH-key IDs per current operation schema. It is asynchronous: action response must be polled. Re-fetch server and show the potentially one-time password only in memory, if returned. | Replaces operating-system disk contents / downtime risk. Typed server name plus selected image review. Never log credentials or one-time root password. |
| 2: image from server | `POST /servers/{id}/actions/create_image` | Current schema supplies optional description, type and labels; returned Action is polled. | Creates snapshot/backup image and may incur storage charges. Display source server, selected type, description and current pricing caveat (price catalogue is not an invoice). |
| 2: backup toggle | `POST /servers/{id}/actions/enable_backup`; `.../disable_backup` | No request body for the basic action; poll Action. | Enable adds recurring backup cost (official docs describe seven backup slots at 20% of server price); disable stops future backups and affects recovery. Separate confirmations for each. |
| 3: volume create/delete | `POST /volumes`; `DELETE /volumes/{id}` | Creation needs `name`, `size` and location/server selection according to the current reference; optionally labels/format/automount fields where documented. Delete is path ID only; API must be allowed to reject attached/protected volumes. | New storage is billed by size/location. Delete permanently destroys volume data. Creation review shows size/location/attachment; deletion typed name/ID confirmation. |
| 3: volume resize | `POST /volumes/{id}/actions/resize` | `size` is required and must be greater than current size; poll Action. | Size grows only, changes cost and cannot be undone. Show old/new GiB; typed new size confirmation. |
| 3: volume protection | `POST /volumes/{id}/actions/change_protection` | Request only explicitly chosen protection members; poll Action. | Changes safeguards against delete. Normal explicit confirmation, no typed phrase. |
| 3: Floating/Primary IP create | `POST /floating_ips`; `POST /primary_ips` | Require type and location/assignment data exactly as current API schema requires. Primary IP is location-bound; Floating IP is globally assignable but should be created near intended use. | Starts IP billing (Primary hourly, Floating monthly in the official reference). Display price catalogue values and caveat. |
| 4: network create/delete and topology actions | `POST /networks`; `DELETE /networks/{id}`; `.../add_subnet`, `.../delete_subnet`, `.../add_route`, `.../delete_route`, `.../change_ip_range`, `.../change_protection` | Network creation requires `name`, RFC1918 `ip_range`, network zone and optional routes/subnets/labels per current schema. All topology actions return Action and require exact CIDR/gateway validation. | Can interrupt private routing. `change_ip_range` and delete operations are especially disruptive. Show before/after topology, require typed network name for delete/change-range, and never construct gateway/CIDR guesses. |
| 4: firewall create/delete/rules | `POST /firewalls`; `DELETE /firewalls/{id}`; `POST /firewalls/{id}/actions/set_rules` | Create has name, rules, labels and optional initial resources; rules use direction/protocol/port/source-or-destination CIDRs. CIDRs must not have host bits set (official Nov 2025 change). Poll actions. | Empty inbound rules mean inbound traffic is dropped; changes can expose or lock out systems. Present a diff, endpoint rule table, affected resource list, and typed firewall name for replace/delete. |
| 5: load balancer create/delete/configuration | `POST /load_balancers`; `DELETE /load_balancers/{id}`; actions `add_service`, `delete_service`, `update_service`, `add_target`, `remove_target`, `attach_to_network`, `detach_from_network`, `change_algorithm`, `change_type`, `enable_public_interface`, `disable_public_interface`, `change_protection`, `change_dns_ptr` | Create needs `name`, load-balancer type and location/network-zone data per current schema; API actions are async. Since Oct 2026 delete now returns `201` Action, not `204`. `update_service` has protocol-specific schema. | Starts LB billing and can route/drop production traffic. Implement each operation as an isolated review/diff, not a generic JSON editor; typed confirmation for delete/public-interface disable/type change. |
| 5: certificate write surface | `POST /certificates`, `PUT /certificates/{id}`, `DELETE /certificates/{id}`, `POST /certificates/{id}/actions/retry` | Uploaded certificates require secret private-key material. Managed certificates require domain/validation contract from the live official schema. | Do not implement uploaded private-key entry in this mobile client until a separate threat-model review. Managed certificate create/retry/delete can affect TLS issuance/availability; delete needs typed confirmation. |
| 6: DNS zones/RRSets | `POST|PUT|DELETE /zones/{id_or_name}`; `POST|PUT|DELETE /zones/{id_or_name}/rrsets/...`; RRSet actions for records, TTL and protection | DNS has an explicit scope acknowledgement in addition to general mutation permission. Zone mode is immutable; changing it means delete/recreate. RRSet URLs require safe segment encoding. From Sep 2026 RRSet TTL updates must supply `ttl`; PTR-style omission conventions must not be inferred. | DNS outage risk. First deliver one RRSet edit flow with exact before/after records, TTL, protection and a typed zone name. Do not implement BIND zone-file import/export in initial write batch. |

## Storage Box API: separately scoped batches

| Batch | Endpoint(s) | Contract / prerequisites | User impact and review copy |
| --- | --- | --- | --- |
| 1: snapshot update/delete | `PUT /storage_boxes/{id}/snapshots/{snapshot_id}`; `DELETE /storage_boxes/{id}/snapshots/{snapshot_id}` | Updates are partial since the October 2025 API change. Delete must not assume it is reversible; refresh snapshot list after success/action per current reference. | Metadata change versus permanent snapshot data removal. Typed snapshot name/ID required for delete. |
| 1: snapshot plan and rollback | Storage Box snapshot-plan action endpoints; rollback snapshot action | Current reference requires plan `hour` and `minute` (October 2025 breaking change). Rollback accepts snapshot name or ID (`snapshot` field); do not use removed `snapshot_id`. Poll returned Action. | Schedule changes can create recurring snapshots/cost. Rollback replaces current data: typed Storage Box + snapshot confirmation, show warning prominently. |
| 2: Storage Box create/update/delete/reset-password | `POST|PUT|DELETE /storage_boxes`; reset-password action | New Storage Box and reset-password must follow current password policy: at least 12 chars and a special character (official Aug 2025 change). Do not log/display returned passwords beyond one-time secure display. | New box incurs charges; deletion/data-access changes are high impact. Password response is secret. Separate independent confirmation review. |
| 2: subaccount create/update/delete/access/password/home | `/storage_boxes/{id}/subaccounts` and its documented actions | The API's partial updates and distinct home-directory action must be followed; do not use the removed home-directory field in access-settings update. Password policy above applies. | Grants/revokes data access. Mask secrets, display a generated/reset password once only, and use typed confirmation for delete/reset. |

## First vertical slice

Implement **delete an unassigned Floating IP** first.

Why this is the safest authorized vertical slice:

- It has no request JSON and no secret material.
- It is contained in the existing Floating IP inventory/detail/UI and action
  diagnostics; no new resource creation form is required.
- The official API now gives an explicit, checkable prerequisite: it must be
  unassigned (`must_be_unassigned` otherwise).
- The review can show a concrete resource (address, type and location), uses a
  typed confirmation, and verifies `204` by refreshing only the IP list.
- It validates the proposed profile permission gate end-to-end before any
  data-destroying server/volume/DNS action is attempted.

Acceptance criteria: hidden when permission is off; inaccessible via route;
visible only for unassigned IPs when on; typed IP confirmation; one-flight
submission; redacted diagnostics; `must_be_unassigned` shown safely; list and
detail refresh after `204`; EN/RU/DE strings and endpoint/permission tests.

## Explicitly deferred despite authorization

- No generic “run arbitrary API request” facility.
- No server console credentials, Storage Box credentials, private keys, or
  secret request/response logging.
- No Robot, legacy DNS Console, billing scraping, invoices, payment methods or
  account administration; they are not part of this API/token surface.
- No BIND zone-file import in the first DNS-write increment: it is a broad
  replacement operation and needs its own parser/preview/threat review.
