package com.hcloudcontrol.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

internal data class ServersResponse(val servers: List<ServerDto>, val meta: MetaDto? = null)
internal data class ServerResponse(val server: ServerDto)
internal data class ServerMetricsResponse(val metrics: ServerMetricsDto)
internal data class ServerMetricsDto(
    val start: String,
    val end: String,
    val step: Double,
    @Json(name = "time_series") val timeSeries: Map<String, ServerMetricsSeriesDto>,
)
internal data class ServerMetricsSeriesDto(val values: List<List<Any>>)
internal data class RootPasswordActionResponse(
    val action: ActionDto,
    @Json(name = "root_password") val rootPassword: String,
)

internal data class RebuildServerRequestDto(
    val image: String,
    @Json(name = "ssh_keys") val sshKeys: List<Long> = emptyList(),
)
internal data class EnableRescueRequestDto(
    val type: String = "linux64",
    @Json(name = "ssh_keys") val sshKeys: List<Long> = emptyList(),
)
internal data class MetaDto(val pagination: PaginationDto? = null)
internal data class PaginationDto(@Json(name = "next_page") val nextPage: Int? = null)
internal data class ServerDto(
    val id: Long,
    val name: String,
    val status: String,
    @Json(name = "public_net") val publicNet: PublicNetDto,
    // `datacenter` was removed from Server responses in July 2026. The API now
    // returns `location`; keep it optional so a partial response never prevents
    // the complete server list from rendering.
    val location: LocationDto? = null,
    @Json(name = "server_type") val serverType: ServerTypeDto,
    @Json(name = "backup_window") val backupWindow: String? = null,
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "placement_group") val placementGroup: PlacementGroupReferenceDto? = null,
    val iso: IsoDto? = null,
    val protection: ServerProtectionDto? = null,
    @Json(name = "private_net") val privateNet: List<ServerPrivateNetDto> = emptyList(),
    val volumes: List<Long> = emptyList(),
    @Json(name = "device_maps") val deviceMaps: List<ServerDeviceMapDto> = emptyList(),
)
internal data class ServerPrivateNetDto(
    val network: Long,
    val ip: String,
    @Json(name = "alias_ips") val aliasIps: List<String> = emptyList(),
    @Json(name = "mac_address") val macAddress: String? = null,
)
internal data class ServerDeviceMapDto(val volume: Long, val device: String)
internal data class PlacementGroupReferenceDto(val id: Long)
internal data class ServerProtectionDto(
    @Json(name = "delete") val delete: Boolean = false,
    val rebuild: Boolean = false,
)
/** Null fields are intentionally omitted by Moshi, preserving the untouched remote configuration. */
internal data class ServerConfigurationPatchDto(
    val name: String? = null,
    val labels: Map<String, String>? = null,
    @Json(name = "placement_group") val placementGroupId: Long? = null,
)
/** Null fields are omitted; the domain patch requires one non-null protection flag. */
internal data class ServerProtectionPatchDto(
    @Json(name = "delete") val delete: Boolean? = null,
    val rebuild: Boolean? = null,
)
internal data class PublicNetDto(
    val ipv4: Ipv4Dto?,
    val ipv6: Ipv6Dto? = null,
    @Json(name = "floating_ips") val floatingIps: List<Long> = emptyList(),
    @Json(name = "primary_ips") val primaryIps: List<Long> = emptyList(),
)
internal data class Ipv4Dto(
    val ip: String?,
    @Json(name = "dns_ptr") val dnsPtr: String? = null,
)
internal data class Ipv6Dto(
    val ip: String? = null,
    @Json(name = "dns_ptr") val dnsPointers: List<DnsPointerDto> = emptyList(),
)

internal data class ServerTypesResponse(val server_types: List<ServerTypeDto>, val meta: MetaDto? = null)
internal data class ServerTypeResponse(val server_type: ServerTypeDto)
internal data class ServerTypeDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val cores: Int? = null,
    val memory: Double? = null,
    val disk: Int? = null,
    val category: String? = null,
    val architecture: String? = null,
    val locations: List<ServerTypeLocationDto>? = null,
)
internal data class ServerTypeLocationDto(val name: String, val available: Boolean? = null)

internal data class ImagesResponse(val images: List<ImageDto>, val meta: MetaDto? = null)
internal data class ImageResponse(val image: ImageDto)
internal data class ImageDto(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val architecture: String? = null,
    val status: String? = null,
    @Json(name = "image_size") val imageSize: Double? = null,
    @Json(name = "disk_size") val diskSize: Double? = null,
    val created: String? = null,
    val protection: ImageProtectionDto? = null,
    val deprecated: String? = null,
    val deprecation: ImageDeprecationDto? = null,
)

internal data class ImageProtectionDto(@Json(name = "delete") val deleteProtected: Boolean? = null)
/** The documented image protection payload has one explicit `delete` flag. */
internal data class ImageProtectionPatchDto(@Json(name = "delete") val delete: Boolean)
internal data class ImageDeprecationDto(
    val announced: String? = null,
    @Json(name = "unavailable_after") val unavailableAfter: String? = null,
)
internal data class IsosResponse(val isos: List<IsoDto>, val meta: MetaDto? = null)
internal data class IsoResponse(val iso: IsoDto)
internal data class IsoDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val architecture: String? = null,
)

internal data class LocationsResponse(val locations: List<LocationDto>, val meta: MetaDto? = null)
internal data class LocationResponse(val location: LocationDto)
internal data class LocationDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val city: String? = null,
    val country: String? = null,
    @Json(name = "network_zone") val networkZone: String? = null,
)

internal data class SshKeysResponse(val ssh_keys: List<SshKeyDto>, val meta: MetaDto? = null)
internal data class SshKeyResponse(val ssh_key: SshKeyDto)

/** Deliberately excludes the API's public_key value: selection requires only public metadata. */
internal data class SshKeyDto(
    val id: Long,
    val name: String,
    val fingerprint: String,
)

/** Only the public half is serialized; no private-key field exists in this DTO. */
internal data class CreateSshKeyRequestDto(
    val name: String,
    @Json(name = "public_key") val publicKey: String,
)

internal data class PricingResponse(val pricing: PricingDto)
internal data class PricingDto(
    val currency: String,
    @Json(name = "vat_rate") val vatRate: String,
    @Json(name = "server_types") val serverTypes: List<PricingServerTypeDto>,
)
internal data class PricingServerTypeDto(
    val id: Long,
    val name: String,
    val prices: List<PricingServerTypePriceDto>,
)
internal data class PricingServerTypePriceDto(
    val location: String,
    @Json(name = "price_hourly") val priceHourly: MoneyDto,
    @Json(name = "price_monthly") val priceMonthly: MoneyDto,
)
internal data class MoneyDto(
    val net: String,
    val gross: String,
)

internal data class VolumesResponse(val volumes: List<VolumeDto>, val meta: MetaDto? = null)
internal data class VolumeResponse(val volume: VolumeDto)
internal data class CreateVolumeResponse(
    val volume: VolumeDto,
    val action: ActionDto? = null,
)
internal data class CreateVolumeRequestDto(
    val name: String,
    val size: Int,
    val location: String,
    val labels: Map<String, String> = emptyMap(),
)
internal data class VolumeDto(
    val id: Long,
    val name: String,
    val size: Int,
    val location: LocationDto,
    val server: Long? = null,
    val status: String,
    val protection: VolumeProtectionDto = VolumeProtectionDto(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class VolumeProtectionDto(
    @Json(name = "delete") val deleteProtected: Boolean = false,
)
internal data class DeleteProtectionPatchDto(@Json(name = "delete") val delete: Boolean)

internal data class PrimaryIpsResponse(val primary_ips: List<PrimaryIpDto>, val meta: MetaDto? = null)
internal data class PrimaryIpResponse(@Json(name = "primary_ip") val primaryIp: PrimaryIpDto)
internal data class CreatePrimaryIpResponse(
    @Json(name = "primary_ip") val primaryIp: PrimaryIpDto,
    val action: ActionDto? = null,
)
internal data class CreatePrimaryIpRequestDto(
    val type: String,
    val name: String,
    val location: String,
    @Json(name = "assignee_type") val assigneeType: String? = null,
    @Json(name = "assignee_id") val assigneeId: Long? = null,
    val labels: Map<String, String> = emptyMap(),
)
internal data class FloatingIpsResponse(@Json(name = "floating_ips") val floatingIps: List<FloatingIpDto>, val meta: MetaDto? = null)
internal data class FloatingIpResponse(@Json(name = "floating_ip") val floatingIp: FloatingIpDto)
internal data class FloatingIpDto(
    val id: Long, val name: String? = null, val type: String, val ip: String,
    @Json(name = "home_location") val homeLocation: LocationDto? = null,
    @Json(name = "assignee_id") val assigneeId: Long? = null,
    @Json(name = "assignee_type") val assigneeType: String? = null,
    val protection: FloatingIpProtectionDto = FloatingIpProtectionDto(),
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "dns_ptr") val dnsPointers: List<DnsPointerDto> = emptyList(),
)
internal data class FloatingIpProtectionDto(@Json(name = "delete") val deleteProtected: Boolean = false)

internal data class CertificatesResponse(
    val certificates: List<CertificateDto>,
    val meta: MetaDto? = null,
)
internal data class CertificateResponse(val certificate: CertificateDto)
/** Omits certificate chain/key payloads on purpose; the app consumes only public metadata. */
internal data class CertificateDto(
    val id: Long,
    val name: String,
    val type: String,
    val created: String? = null,
    @Json(name = "not_valid_before") val notValidBefore: String? = null,
    @Json(name = "not_valid_after") val notValidAfter: String? = null,
    @Json(name = "domain_names") val domainNames: List<String> = emptyList(),
    val fingerprint: String? = null,
    val status: CertificateStatusDto? = null,
    @Json(name = "used_by") val usedBy: List<CertificateUsageDto> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class CertificateStatusDto(
    val issuance: String? = null,
    val renewal: String? = null,
    val error: CertificateStatusErrorDto? = null,
)
internal data class CertificateStatusErrorDto(val code: String? = null)
internal data class CertificateUsageDto(val id: Long, val type: String)
internal data class PrimaryIpDto(
    val id: Long,
    val name: String,
    val type: String,
    val ip: String,
    val location: LocationDto,
    @Json(name = "assignee_id") val assigneeId: Long? = null,
    @Json(name = "assignee_type") val assigneeType: String? = null,
    val protection: PrimaryIpProtectionDto = PrimaryIpProtectionDto(),
    val labels: Map<String, String> = emptyMap(),
    @Json(name = "dns_ptr") val dnsPointers: List<DnsPointerDto> = emptyList(),
)
internal data class PrimaryIpProtectionDto(
    @Json(name = "delete") val deleteProtected: Boolean = false,
)
internal data class DnsPointerDto(
    val ip: String,
    @Json(name = "dns_ptr") val dnsPtr: String? = null,
)

internal data class FirewallsResponse(val firewalls: List<FirewallDto>, val meta: MetaDto? = null)
internal data class FirewallResponse(val firewall: FirewallDto)
internal data class CreatedFirewallResponse(
    val firewall: FirewallDto,
    val actions: List<ActionDto> = emptyList(),
)
internal data class CreateFirewallRequestDto(
    val name: String,
    val rules: List<FirewallRuleRequestDto> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class SetFirewallRulesRequestDto(val rules: List<FirewallRuleRequestDto>)
internal data class FirewallRuleRequestDto(
    val direction: String,
    val protocol: String,
    val port: String? = null,
    @Json(name = "source_ips") val sourceIps: List<String> = emptyList(),
    @Json(name = "destination_ips") val destinationIps: List<String> = emptyList(),
    val description: String? = null,
)
internal data class FirewallResourcesRequestDto(val apply_to: List<FirewallResourceRequestDto>)

internal data class FirewallRemoveResourcesRequestDto(val remove_from: List<FirewallResourceRequestDto>)
internal data class FirewallResourceRequestDto(val type: String = "server", val server: FirewallServerReferenceDto)
internal data class FirewallServerReferenceDto(val id: Long)
internal data class ActionsResponse(val actions: List<ActionDto>, val meta: MetaDto? = null)
internal data class FirewallDto(
    val id: Long,
    val name: String,
    val rules: List<FirewallRuleDto> = emptyList(),
    @Json(name = "applied_to") val appliedTo: List<FirewallAppliedResourceDto> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class FirewallRuleDto(
    val direction: String,
    val protocol: String,
    val port: String? = null,
    @Json(name = "source_ips") val sourceIps: List<String> = emptyList(),
    @Json(name = "destination_ips") val destinationIps: List<String> = emptyList(),
    val description: String? = null,
)
internal data class FirewallAppliedResourceDto(
    val type: String,
    val server: FirewallServerDto? = null,
    @Json(name = "label_selector") val labelSelector: FirewallLabelSelectorDto? = null,
    @Json(name = "applied_to_resources") val appliedToResources: List<FirewallAppliedResourceDto> = emptyList(),
)
internal data class FirewallServerDto(val id: Long)
internal data class FirewallLabelSelectorDto(val selector: String)

internal data class NetworksResponse(val networks: List<NetworkDto>, val meta: MetaDto? = null)
internal data class NetworkResponse(val network: NetworkDto)
internal data class CreateNetworkResponse(val network: NetworkDto, val action: ActionDto)
internal data class NetworkMembersResponse(val members: List<NetworkMemberDto>, val meta: MetaDto? = null)
internal data class NetworkMemberDto(
    val type: String,
    val id: Long,
    val ip: String,
    @Json(name = "alias_ips") val aliasIps: List<String> = emptyList(),
    val subnet: String,
    val status: String,
)
internal data class NetworkDto(
    val id: Long,
    val name: String,
    @Json(name = "ip_range") val ipRange: String,
    val subnets: List<NetworkSubnetDto> = emptyList(),
    val routes: List<NetworkRouteDto> = emptyList(),
    val servers: List<Long> = emptyList(),
    val protection: NetworkProtectionDto = NetworkProtectionDto(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class NetworkSubnetDto(
    val type: String,
    @Json(name = "ip_range") val ipRange: String,
    @Json(name = "network_zone") val networkZone: String,
    val gateway: String? = null,
    @Json(name = "vswitch_id") val vswitchId: Long? = null,
)
internal data class NetworkRouteDto(
    val destination: String,
    val gateway: String,
)
internal data class NetworkProtectionDto(
    @Json(name = "delete") val deleteProtected: Boolean = false,
)
internal data class CreateNetworkRequestDto(
    val name: String,
    @Json(name = "ip_range") val ipRange: String,
    val labels: Map<String, String> = emptyMap(),
)
/** The Cloud API's subnet action identifies a subnet by type, range and zone. */
internal data class NetworkSubnetRequestDto(
    val type: String = "cloud",
    @Json(name = "ip_range") val ipRange: String,
    @Json(name = "network_zone") val networkZone: String,
    val gateway: String? = null,
)
internal data class NetworkRouteRequestDto(
    val destination: String,
    val gateway: String,
)

internal data class LoadBalancersResponse(
    @Json(name = "load_balancers") val loadBalancers: List<LoadBalancerDto>,
    val meta: MetaDto? = null,
)
internal data class LoadBalancerResponse(@Json(name = "load_balancer") val loadBalancer: LoadBalancerDto)
internal data class LoadBalancerTypesResponse(
    @Json(name = "load_balancer_types") val loadBalancerTypes: List<LoadBalancerTypeDto>,
    val meta: MetaDto? = null,
)
internal data class LoadBalancerTypeResponse(@Json(name = "load_balancer_type") val loadBalancerType: LoadBalancerTypeDto)
internal data class LoadBalancerMetricsResponse(val metrics: LoadBalancerMetricsDto)
internal data class LoadBalancerMetricsDto(
    val start: String,
    val end: String,
    val step: Double,
    @Json(name = "time_series") val timeSeries: Map<String, LoadBalancerMetricsSeriesDto>,
)
internal data class LoadBalancerMetricsSeriesDto(val values: List<List<Any>>)
internal data class LoadBalancerDto(
    val id: Long,
    val name: String,
    @Json(name = "load_balancer_type") val loadBalancerType: LoadBalancerTypeDto,
    val location: LocationDto,
    @Json(name = "public_net") val publicNet: LoadBalancerPublicNetDto = LoadBalancerPublicNetDto(),
    @Json(name = "private_net") val privateNet: List<LoadBalancerPrivateNetDto> = emptyList(),
    val services: List<LoadBalancerServiceDto> = emptyList(),
    val targets: List<LoadBalancerTargetDto> = emptyList(),
    val protection: LoadBalancerProtectionDto = LoadBalancerProtectionDto(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class LoadBalancerTypeDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    @Json(name = "max_services") val maxServices: Int? = null,
    @Json(name = "max_connections") val maxConnections: Int? = null,
    @Json(name = "max_targets") val maxTargets: Int? = null,
    @Json(name = "max_assignments") val maxAssignments: Int? = null,
    @Json(name = "max_open_requests") val maxOpenRequests: Int? = null,
    @Json(name = "max_routing_rules") val maxRoutingRules: Int? = null,
    @Json(name = "max_tcp_connections") val maxTcpConnections: Int? = null,
    @Json(name = "max_http_requests_per_second") val maxHttpRequestsPerSecond: Int? = null,
    @Json(name = "max_requests_per_second") val maxRequestsPerSecond: Int? = null,
    @Json(name = "max_ssl_certificates") val maxSslCertificates: Int? = null,
    val prices: List<LoadBalancerTypePriceDto> = emptyList(),
)
internal data class LoadBalancerTypePriceDto(
    val location: String,
    @Json(name = "price_hourly") val priceHourly: MoneyDto,
    @Json(name = "price_monthly") val priceMonthly: MoneyDto,
)
internal data class LoadBalancerPublicNetDto(
    val enabled: Boolean = false,
    val ipv4: LoadBalancerPublicIpDto? = null,
    val ipv6: LoadBalancerPublicIpDto? = null,
)
internal data class LoadBalancerPublicIpDto(
    val ip: String,
    @Json(name = "dns_ptr") val dnsPtr: String? = null,
)
internal data class LoadBalancerPrivateNetDto(val network: Long, val ip: String)
internal data class LoadBalancerServiceDto(
    val protocol: String,
    @Json(name = "listen_port") val listenPort: Int? = null,
    @Json(name = "destination_port") val destinationPort: Int? = null,
    @Json(name = "proxyprotocol") val proxyProtocol: Boolean = false,
)
internal data class LoadBalancerTargetDto(
    val type: String,
    val server: LoadBalancerTargetServerDto? = null,
    @Json(name = "label_selector") val labelSelector: LoadBalancerTargetLabelSelectorDto? = null,
    val ip: LoadBalancerTargetIpDto? = null,
    @Json(name = "use_private_ip") val usePrivateIp: Boolean = false,
    @Json(name = "health_status") val healthStatuses: List<LoadBalancerTargetHealthDto> = emptyList(),
    val targets: List<LoadBalancerTargetDto> = emptyList(),
)
internal data class LoadBalancerTargetServerDto(val id: Long)
internal data class LoadBalancerTargetLabelSelectorDto(val selector: String)
internal data class LoadBalancerTargetIpDto(val ip: String)
internal data class LoadBalancerTargetHealthDto(
    @Json(name = "listen_port") val listenPort: Int,
    val status: String,
)
internal data class LoadBalancerProtectionDto(
    @Json(name = "delete") val deleteProtected: Boolean = false,
)

internal data class PlacementGroupsResponse(
    @Json(name = "placement_groups") val placementGroups: List<PlacementGroupDto>,
    val meta: MetaDto? = null,
)
internal data class PlacementGroupResponse(@Json(name = "placement_group") val placementGroup: PlacementGroupDto)
internal data class CreatePlacementGroupResponse(
    @Json(name = "placement_group") val placementGroup: PlacementGroupDto,
    val action: ActionDto? = null,
)
/** The documented response has no `protection` member for Placement Groups. */
internal data class PlacementGroupDto(
    val id: Long,
    val name: String,
    val type: String,
    val servers: List<Long> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)

internal data class AttachVolumeRequestDto(val server: Long)
internal data class ResizeVolumeRequestDto(val size: Int)
internal data class AssignPrimaryIpRequestDto(
    @Json(name = "assignee_id") val assigneeId: Long,
    @Json(name = "assignee_type") val assigneeType: String = "server",
)
internal data class AssignFloatingIpRequestDto(val server: Long)
internal data class ChangeDnsPtrRequestDto(val ip: String, @Json(name = "dns_ptr") val dnsPtr: String?)
/** Deliberately leaves ip, aliases and ip_range absent so Hetzner selects a valid subnet/IP. */
internal data class AttachServerToNetworkRequestDto(val network: Long)
internal data class DetachServerFromNetworkRequestDto(val network: Long)
internal data class PlacementGroupRequestDto(@Json(name = "placement_group") val placementGroupId: Long)
internal data class CreatePlacementGroupRequestDto(
    val name: String,
    val type: String,
    val labels: Map<String, String>? = null,
)
internal data class UpdatePlacementGroupRequestDto(
    val name: String? = null,
    val labels: Map<String, String>? = null,
)
internal data class AttachIsoRequestDto(val iso: Long)

/** dns_ptr is mandatory on this API operation even when clearing it to null. */
internal data class ServerDnsPtrRequestDto(val ip: String, val dnsPtr: String?)

internal class ServerDnsPtrRequestDtoJsonAdapter {
    @ToJson
    fun toJson(writer: JsonWriter, value: ServerDnsPtrRequestDto) {
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        try {
            writer.beginObject()
            writer.name("ip").value(value.ip)
            writer.name("dns_ptr")
            if (value.dnsPtr == null) writer.nullValue() else writer.value(value.dnsPtr)
            writer.endObject()
        } finally {
            writer.serializeNulls = previousSerializeNulls
        }
    }
}

/** dns_ptr is required for all PTR changes; a null value explicitly clears the record. */
internal class ChangeDnsPtrRequestDtoJsonAdapter {
    @ToJson
    fun toJson(writer: JsonWriter, value: ChangeDnsPtrRequestDto) {
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        try {
            writer.beginObject()
            writer.name("ip").value(value.ip)
            writer.name("dns_ptr")
            if (value.dnsPtr == null) writer.nullValue() else writer.value(value.dnsPtr)
            writer.endObject()
        } finally {
            writer.serializeNulls = previousSerializeNulls
        }
    }
}

internal data class CreateServerRequestDto(
    val name: String,
    @Json(name = "server_type") val serverType: String,
    val image: String,
    val location: String,
    @Json(name = "start_after_create") val startAfterCreate: Boolean = true,
    @Json(name = "public_net") val publicNet: PublicNetRequestDto,
    @Json(name = "ssh_keys") val sshKeys: List<Long> = emptyList(),
)
internal data class PublicNetRequestDto(
    @Json(name = "enable_ipv4") val enableIpv4: Boolean,
    @Json(name = "enable_ipv6") val enableIpv6: Boolean,
)
internal data class CreateServerResponse(
    val server: ServerDto,
    @Json(name = "root_password") val rootPassword: String? = null,
    val action: ActionDto? = null,
    @Json(name = "next_actions") val nextActions: List<ActionDto> = emptyList(),
)

internal data class ChangeServerTypeRequestDto(
    @Json(name = "server_type") val serverType: String,
    @Json(name = "upgrade_disk") val upgradeDisk: Boolean,
)
internal data class CreateImageRequestDto(
    val description: String,
    /** Kept fixed to snapshot by the repository's public contract. */
    val type: String = "snapshot",
)
internal data class ActionResponse(val action: ActionDto)
internal data class ActionDto(
    val id: Long,
    val status: String,
    val command: String? = null,
    val started: String? = null,
    val finished: String? = null,
    val progress: Int? = null,
    val error: ActionErrorDto? = null,
)
internal data class ActionErrorDto(val code: String? = null, val message: String? = null)

/** Official Hetzner non-2xx response shape. These DTOs are never logged or exposed raw. */
internal data class HCloudErrorResponseDto(val error: HCloudErrorDto? = null)
internal data class HCloudErrorDto(
    val code: String? = null,
    val message: String? = null,
    val details: HCloudErrorDetailsDto? = null,
)
internal data class HCloudErrorDetailsDto(val fields: List<HCloudErrorFieldDto>? = null)
internal data class HCloudErrorFieldDto(val name: String? = null)
