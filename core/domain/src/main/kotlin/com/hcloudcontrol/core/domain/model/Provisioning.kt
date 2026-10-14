package com.hcloudcontrol.core.domain.model

/** Values supplied by the Hetzner Cloud API for creating or resizing a Server. */
data class ServerTypeOption(
    val id: Long,
    val name: String,
    val description: String,
    val cores: Int,
    val memoryGb: Double,
    val diskGb: Int,
    val category: String?,
    val architecture: String?,
    val availableLocations: Set<String>,
)

/** Read-only server type detail and availability from the Cloud API. */
data class ServerTypeDetails(
    val id: Long,
    val name: String,
    val description: String,
    val cores: Int,
    val memoryGb: Double,
    val diskGb: Int,
    val category: String?,
    val architecture: String?,
    val locations: List<ServerTypeLocationAvailability>,
    /** Exact API pricing joined by type/location; it is never calculated locally. */
    val prices: List<ServerTypeLocationPrice>,
)

data class ServerTypeLocationAvailability(val name: String, val available: Boolean?)

data class ImageOption(
    val id: Long,
    val name: String,
    val description: String,
    val type: String,
    val architecture: String?,
)

/** Read-only inventory metadata for Hetzner backup and snapshot images. */
data class BackupSnapshotImage(
    val id: Long,
    val name: String?,
    val description: String?,
    val type: String,
    val createdAt: String?,
    val deprecatedAt: String?,
)

/** Read-only Cloud Image inventory. API timestamps and sizes are preserved as supplied. */
data class CloudImageInventory(
    val id: Long,
    val name: String?,
    val description: String?,
    val type: String?,
    val architecture: String? = null,
    val status: String?,
    val imageSize: Double?,
    val diskSize: Double?,
    val created: String?,
    val deleteProtected: Boolean?,
    /** Legacy API field, retained only for compatibility with older responses. */
    val deprecated: String?,
    /** Preferred deprecation information, if the API supplies it. */
    val deprecation: ImageDeprecation?,
)

data class ImageDeprecation(
    val announced: String?,
    val unavailableAfter: String?,
)

/** Changes only image deletion protection; the Cloud API does not expose partial image protection. */
data class ImageProtectionPatch(val delete: Boolean)

/** Read-only ISO inventory item. */
data class IsoOption(
    val id: Long,
    val name: String,
    val description: String?,
    val type: String?,
    val architecture: String?,
)

/** A user-selected ISO; name validation prevents ambiguous/blank selections before a mutation. */
data class IsoSelection(val id: Long, val name: String) {
    init {
        require(id > 0) { "ISO ID must be positive" }
        require(name.isNotBlank()) { "ISO name must not be blank" }
    }
}

/** Monetary values are API-provided decimal strings and must not be converted or rounded here. */
data class MoneyAmount(
    val net: String,
    val gross: String,
)

/** Price for one server type at one exact Hetzner location. */
data class ServerTypeLocationPrice(
    val serverTypeId: Long,
    val serverTypeName: String,
    val location: String,
    val currency: String,
    val vatRate: String,
    val hourly: MoneyAmount,
    val monthly: MoneyAmount,
)

/** Read-only Hetzner Cloud Volume inventory entry. */
data class VolumeInventory(
    val id: Long,
    val name: String,
    val sizeGb: Int,
    val location: String,
    val serverId: Long?,
    val status: String,
    val deleteProtected: Boolean,
    val labels: Map<String, String>,
)

/**
 * Explicit request to create an unattached Cloud Volume. A location is required by
 * the official Cloud API when no server is supplied. This app deliberately creates
 * the Volume unattached; attaching it is a separately reviewed action.
 */
data class CreateVolumeSpec(
    val name: String,
    val sizeGb: Int,
    val location: String,
    val labels: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Volume name cannot be blank" }
        require(sizeGb > 0) { "Volume size must be positive" }
        require(location.isNotBlank()) { "Volume location cannot be blank" }
    }
}

/** Result returned by POST /volumes. The action may be null when the API completed synchronously. */
data class CreatedVolume(
    val volume: VolumeInventory,
    val action: CloudAction?,
)

/** The non-server protection action is intentionally deletion-only. */
data class DeleteProtectionPatch(val delete: Boolean)

/**
 * Deliberate Primary IP creation request. A location is always selected by this client;
 * an optional assignee must be a server in that same location.
 */
data class CreatePrimaryIpSpec(
    val type: String,
    val name: String,
    val location: String,
    val assigneeId: Long? = null,
    val labels: Map<String, String> = emptyMap(),
) {
    init {
        require(type == "ipv4" || type == "ipv6") { "Primary IP type must be ipv4 or ipv6" }
        require(name.isNotBlank()) { "Primary IP name cannot be blank" }
        require(location.isNotBlank()) { "Primary IP location cannot be blank" }
        require(assigneeId == null || assigneeId > 0) { "Primary IP assignee ID must be positive" }
    }
}

/** The Cloud API returns the new IP and may return an asynchronous assignment action. */
data class CreatedPrimaryIp(
    val primaryIp: PrimaryIpInventory,
    val action: CloudAction?,
)

data class DnsPointer(
    val ip: String,
    val dnsPtr: String?,
)

/** Read-only Primary IP inventory for the network screen. */
data class PrimaryIpInventory(
    val id: Long,
    val name: String,
    val type: String,
    val address: String,
    val location: String,
    val assigneeId: Long?,
    val assigneeType: String?,
    val deleteProtected: Boolean,
    val labels: Map<String, String>,
    val dnsPointers: List<DnsPointer>,
)

data class FloatingIpInventory(
    val id: Long,
    val name: String?,
    val type: String,
    val address: String,
    val homeLocation: String?,
    val assigneeId: Long?,
    val assigneeType: String?,
    val deleteProtected: Boolean,
    val labels: Map<String, String>,
    val dnsPointers: List<DnsPointer>,
)

/** Read-only certificate metadata. Certificate/private-key material is deliberately excluded. */
data class CertificateInventory(
    val id: Long,
    val name: String,
    val type: String,
    val created: String?,
    val notValidBefore: String?,
    val notValidAfter: String?,
    val domainNames: List<String>,
    val fingerprint: String?,
    val status: CertificateStatus?,
    val usedBy: List<CertificateUsage>,
    val labels: Map<String, String>,
)

/** Managed-certificate issuance/renewal status, excluding any raw API error text. */
data class CertificateStatus(val issuance: String?, val renewal: String?, val errorCode: String?)
data class CertificateUsage(val id: Long, val type: String)

/** Read-only Firewall inventory for the network screen. */
data class FirewallInventory(
    val id: Long,
    val name: String,
    val rules: List<FirewallRuleInventory>,
    val appliedTo: List<FirewallAppliedResource>,
    val labels: Map<String, String>,
)

/** A rule as returned by Hetzner; an absent port is valid for protocols such as ICMP. */
data class FirewallRuleInventory(
    val direction: String,
    val protocol: String,
    val port: String?,
    val sourceIps: List<String>,
    val destinationIps: List<String>,
    val description: String?,
)

/**
 * Complete firewall definition used for an explicit create request.  Rules are intentionally
 * supplied as a reviewed list: an empty inbound-rule list means inbound traffic is dropped by
 * Hetzner Cloud's default policy.
 */
data class CreateFirewallSpec(
    val name: String,
    val rules: List<FirewallRuleSpec> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
) {
    init { require(name.isNotBlank()) { "Firewall name must not be blank" } }
}

/** A caller-owned rule representation for POST create and the set_rules action. */
data class FirewallRuleSpec(
    val direction: String,
    val protocol: String,
    val port: String? = null,
    val sourceIps: List<String> = emptyList(),
    val destinationIps: List<String> = emptyList(),
    val description: String? = null,
)

/** Result of creating a Firewall; resource assignment actions, if any, remain observable. */
data class CreatedFirewall(
    val firewall: FirewallInventory,
    val actions: List<CloudAction>,
)

/**
 * A Firewall can be applied directly to a server or via a label selector. For selectors,
 * Hetzner may include the currently matching resources in [appliedToResources].
 */
data class FirewallAppliedResource(
    val type: String,
    val serverId: Long?,
    val labelSelector: String?,
    val appliedToResources: List<FirewallAppliedResource>,
)

/** Read-only Private Network inventory for the network screen. */
data class PrivateNetworkInventory(
    val id: Long,
    val name: String,
    val ipRange: String,
    val subnets: List<PrivateNetworkSubnet>,
    val routes: List<PrivateNetworkRoute>,
    val serverIds: List<Long>,
    val deleteProtected: Boolean,
    val labels: Map<String, String>,
)

data class PrivateNetworkSubnet(
    val type: String,
    val ipRange: String,
    val networkZone: String,
    val gateway: String?,
    val vswitchId: Long?,
)

data class PrivateNetworkRoute(
    val destination: String,
    val gateway: String,
)

/**
 * Explicit input for the billable/private topology boundary.  A network is created without
 * implicit subnets or routes: those are reviewed and added as separate actions.
 */
data class CreatePrivateNetworkSpec(
    val name: String,
    val ipRange: String,
    val labels: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Network name must not be blank" }
        require(ipRange.isNotBlank()) { "Network IP range must not be blank" }
    }
}

/** Only the documented cloud subnet fields are accepted; vSwitch topology is deliberately excluded. */
data class PrivateNetworkSubnetSpec(
    val ipRange: String,
    val networkZone: String,
    val gateway: String? = null,
) {
    init {
        require(ipRange.isNotBlank()) { "Subnet IP range must not be blank" }
        require(networkZone.isNotBlank()) { "Network zone must not be blank" }
    }
}

data class PrivateNetworkRouteSpec(
    val destination: String,
    val gateway: String,
) {
    init {
        require(destination.isNotBlank()) { "Route destination must not be blank" }
        require(gateway.isNotBlank()) { "Route gateway must not be blank" }
    }
}

data class CreatedPrivateNetwork(
    val network: PrivateNetworkInventory,
    val action: CloudAction,
)

/** Read-only attachment returned by GET /networks/{id}/members. */
data class PrivateNetworkMember(
    val type: String,
    val id: Long,
    val ip: String,
    val aliasIps: List<String>,
    val subnet: String,
    val status: String,
)

/** Read-only Load Balancer inventory. Target health is the available status signal. */
data class LoadBalancerInventory(
    val id: Long,
    val name: String,
    val type: String,
    val location: String,
    val publicInterface: LoadBalancerPublicInterface,
    val privateInterfaces: List<LoadBalancerPrivateInterface>,
    val services: List<LoadBalancerService>,
    val targets: List<LoadBalancerTarget>,
    val deleteProtected: Boolean,
    val labels: Map<String, String>,
    /** API-provided price strings for this type; no currency conversion or rounding is performed. */
    val prices: List<LoadBalancerLocationPrice>,
)

data class LoadBalancerPublicInterface(
    val enabled: Boolean,
    val ipv4Address: String?,
    val ipv4DnsPtr: String?,
    val ipv6Address: String?,
    val ipv6DnsPtr: String?,
)

data class LoadBalancerPrivateInterface(val networkId: Long, val ip: String)

data class LoadBalancerService(
    val protocol: String,
    val listenPort: Int?,
    val destinationPort: Int?,
    val proxyProtocol: Boolean,
)

data class LoadBalancerTarget(
    val type: String,
    val serverId: Long?,
    val labelSelector: String?,
    val ip: String?,
    val usePrivateIp: Boolean,
    val healthStatuses: List<LoadBalancerTargetHealth>,
    val targets: List<LoadBalancerTarget>,
)

data class LoadBalancerTargetHealth(val listenPort: Int, val status: String)

data class LoadBalancerLocationPrice(
    val location: String,
    val hourly: MoneyAmount,
    val monthly: MoneyAmount,
)

/**
 * A catalog entry returned by the Cloud Load Balancer Types API. This is not a
 * Load Balancer instance and the app deliberately exposes no creation path
 * from this catalog.
 */
data class LoadBalancerTypeInventory(
    val id: Long,
    val name: String,
    val description: String?,
    val maxServices: Int?,
    val maxConnections: Int?,
    val maxTargets: Int?,
    val maxAssignments: Int?,
    val maxOpenRequests: Int?,
    val maxRoutingRules: Int?,
    val maxTcpConnections: Int?,
    val maxHttpRequestsPerSecond: Int?,
    val maxRequestsPerSecond: Int?,
    val maxSslCertificates: Int?,
    val prices: List<LoadBalancerLocationPrice>,
)

/** Read-only Placement Group inventory. The Cloud API currently exposes no protection object. */
data class PlacementGroupInventory(
    val id: Long,
    val name: String,
    val type: String,
    val serverIds: List<Long>,
    val labels: Map<String, String>,
    /** Null means that the Cloud API did not provide a deletion-protection state. */
    val deleteProtected: Boolean?,
)

/** A reviewed request to create a Placement Group. Only the documented `spread` type is accepted. */
data class CreatePlacementGroupSpec(
    val name: String,
    val type: String = "spread",
    val labels: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "Placement group name must not be blank" }
        require(type == "spread") { "Unsupported placement group type" }
    }
}

/** A Placement Group is returned immediately; creation may also carry an asynchronous Action. */
data class CreatedPlacementGroup(
    val placementGroup: PlacementGroupInventory,
    val action: CloudAction?,
)

/** Partial normal configuration. A null member is omitted, while an empty labels map clears labels. */
data class PlacementGroupPatch(
    val name: String? = null,
    val labels: Map<String, String>? = null,
) {
    init {
        require(name == null || name.isNotBlank()) { "Placement group name must not be blank" }
        require(name != null || labels != null) { "At least one placement group field is required" }
    }
}

data class LocationOption(
    val id: Long,
    val name: String,
    val description: String,
    val city: String?,
    val country: String?,
)

/** Read-only Location detail from GET /locations/{id}. */
data class LocationDetails(
    val id: Long,
    val name: String,
    val description: String,
    val city: String?,
    val country: String?,
    val networkZone: String?,
)

/** Public SSH-key metadata only. Private key material is never handled by the app. */
data class SshKeyOption(
    val id: Long,
    val name: String,
    val fingerprint: String,
)

/**
 * A public key submitted to Hetzner Cloud. The app never generates, receives,
 * persists, or logs a private key. The caller must show these values for review
 * immediately before the API request.
 */
data class CreateSshKeySpec(
    val name: String,
    val publicKey: String,
) {
    init {
        require(name.isNotBlank()) { "SSH key name must not be blank" }
        require(publicKey.isNotBlank()) { "Public SSH key must not be blank" }
    }
}

data class CreateServerSpec(
    val name: String,
    val serverType: String,
    val image: String,
    val location: String,
    val enableIpv4: Boolean,
    val enableIpv6: Boolean,
    val sshKeyIds: List<Long> = emptyList(),
)

/** `rootPassword` is supplied by Hetzner only for initial display and must not be persisted. */
data class CreatedServer(
    val server: Server,
    val rootPassword: String?,
    /** Provisioning continues asynchronously; callers must track these actions before declaring success. */
    val actions: List<CloudAction>,
)

/** Generic asynchronous Cloud API action; all optional metadata is read-only API state. */
data class CloudAction(
    val id: Long,
    val status: String,
    val command: String? = null,
    val started: String? = null,
    val finished: String? = null,
    val progress: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
