# Network topology, Storage Box and tariff evidence

Design ownership is pinned to `4972d35e790822183dcf0342ecc50963eed3d2ed`.
Source integration is not visual acceptance. Every row below still needs physical
device verification, including both themes, EN/RU/DE, 200% font and TalkBack.
No issue is closed by this table. Test counts apply only to the exact successful
snapshots recorded in `redesign-implementation-progress.md`.

| Screen | Canonical source entry | Adapter / shared implementation | Device |
| --- | --- | --- | --- |
| D01 storage inventory | `StorageInventoryRedesign` | independent volume/image loaders, scoped tabs/search/scroll | UNRUN |
| D02 volume detail | `VolumeDetailScreen` | independent metadata/history, shared protection | UNRUN |
| D03 create volume | `VolumeCreationFlow` | `VolumeOperationAdapter`, original multi-action bundle | UNRUN |
| D04 increase volume | `VolumeMutationFlow` RESIZE | fresh original size/attachment/protection checks | UNRUN |
| D05 delete volume | `VolumeMutationFlow` DELETE | known unprotected/unattached preconditions, HTTP 204 | UNRUN |
| D09 attach volume | `VolumeMutationFlow` ATTACH | exact original volume/server IDs and location eligibility | UNRUN |
| D10 detach volume | `VolumeMutationFlow` DETACH | original attachment review, one action coordinator | UNRUN |
| N08 private network detail | `PrivateNetworkDetailScreen` | independent SectionLoader metadata/members/history | UNRUN |
| N09 create network | `PrivateNetworkCreationFlow` | `NetworkTopologyAdapter`, shared review/result | UNRUN |
| N10 structured topology | `PrivateNetworkDetailScreen` topology page | shared cards, scoped page/scroll | UNRUN |
| N11 add subnet | `NetworkTopologyMutationFlow` ADD_SUBNET | `validCloudSubnet`, shared form/picker | UNRUN |
| N12 attach/detach member | `NetworkTopologyMutationFlow` ATTACH/DETACH | fresh original IDs, actual subnet/zone eligibility | UNRUN |
| N15 add/delete route | `NetworkTopologyMutationFlow` ADD_ROUTE/DELETE_ROUTE | `validNetworkRoute`, exact original route | UNRUN |
| N16 delete network | `NetworkTopologyMutationFlow` DELETE | known protection, fresh members, typed confirmation, HTTP 204 | UNRUN |
| B01 Box inventory | `StorageBoxesInventory` | original Cloud owner token, separate fixed API host | UNRUN |
| B02 Box detail | `StorageBoxDetailScreen` | independent metadata/folders/subaccounts/snapshot reads | UNRUN |
| B03 Box snapshot | `StorageBoxSnapshotOperation` | `BoxSnapshotAdapter`, original Hetzner-host action status | UNRUN |
| B04 forget legacy credential | `StorageBoxesInventory` legacy confirmation | local record removal only | UNRUN |
| T01 tariffs | `TariffsScreen` | shared cost model, independent pricing/location reads | UNRUN |

Synthetic coverage: `VolumeOperationAdapterTest`, `VolumeMutationsContractTest`,
`PrivateNetworkValidationTest`, `NetworkDraftStateTest`,
`NetworkTopologyAdapterTest`, `PrivateNetworkLifecycleContractTest`,
`BoxSnapshotAdapterTest`, `StorageBoxApiContractTest`, `CostPresentationTest`,
and shared operation/read-state/context tests. These do not simulate a real
TalkBack session or prove rendered layouts.

Provider constraints are intentional, not missing alternate implementations:

- Metadata and capacity counters arrive together in the documented Box response;
  no separate statistics endpoint is invented. Absent counters are not zero.
  Folders, subaccounts and snapshots have separate failure/retry owners.
- Box snapshot completion uses `GET /storage_boxes/actions/{id}` on the original
  Hetzner host. Merely seeing a snapshot in inventory is not completion evidence.
- Subnet gateways are provider assigned: the official add-subnet request does
  not accept a custom gateway. New subnets use `cloud`, not deprecated `server`.
- Route destinations and gateways follow the official private-range/default-route,
  overlap and reserved-address restrictions, not a local guessed topology.
- Network deletion warns about provider-side member detachment; the application
  does not silently send additional cleanup operations.
- Tariffs are published rates, not accrued spend, invoices or account balance.
  Unknown components stay unknown and never become a fake zero or monthly total.
