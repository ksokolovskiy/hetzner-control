package com.hcloudcontrol.core.domain.model

data class Server(
    val id: Long,
    val name: String,
    val status: ServerStatus,
    val ipv4Address: String?,
    val location: String,
    val serverType: String,
    /** Hetzner's assigned backup schedule, null when backups are disabled. */
    val backupWindow: String? = null,
    /** Current API labels; an update replaces labels only when explicitly requested. */
    val labels: Map<String, String> = emptyMap(),
    val placementGroupId: Long? = null,
    /** The ISO currently attached by the Cloud API, if any. */
    val attachedIso: IsoOption? = null,
    /** Protection state supplied by the Cloud API; null means it was not supplied. */
    val protection: ServerProtection? = null,
    /** Only public IP addresses returned by the Server API; used for server-scoped PTR changes. */
    val publicDnsPointers: List<DnsPointer> = emptyList(),
    /** Read-only interface and device metadata already returned by GET /servers/{id}. */
    val networkDevices: ServerNetworkDeviceInventory = ServerNetworkDeviceInventory(),
)

data class ServerNetworkDeviceInventory(
    val ipv4Address: String? = null,
    val ipv6Network: String? = null,
    val primaryIpIds: List<Long> = emptyList(),
    val floatingIpIds: List<Long> = emptyList(),
    val privateInterfaces: List<ServerPrivateNetworkInterface> = emptyList(),
    val attachedVolumeIds: List<Long> = emptyList(),
    val deviceMaps: List<ServerVolumeDeviceMap> = emptyList(),
)

data class ServerPrivateNetworkInterface(
    val networkId: Long,
    val ip: String,
    val aliasIps: List<String>,
    val macAddress: String?,
)

data class ServerVolumeDeviceMap(val volumeId: Long, val device: String)

enum class ServerStatus { INITIALIZING, OFF, RUNNING, STARTING, STOPPING, UNKNOWN }

data class ServerProtection(val delete: Boolean, val rebuild: Boolean)

/** Partial normal configuration update. Unset values are omitted from the HTTP body. */
data class ServerConfigurationPatch(
    val name: String? = null,
    val labels: Map<String, String>? = null,
    val placementGroupId: Long? = null,
) {
    init {
        require(name != null || labels != null || placementGroupId != null) { "At least one configuration field is required" }
        require(name?.isNotBlank() != false) { "Server name must not be blank" }
        require(placementGroupId == null || placementGroupId > 0) { "Placement group ID must be positive" }
    }
}

/** Partial protection action. At least one flag must be explicitly supplied. */
data class ServerProtectionPatch(
    val delete: Boolean? = null,
    val rebuild: Boolean? = null,
) {
    init {
        require(delete != null || rebuild != null) { "At least one protection flag is required" }
    }
}
