package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.Profile
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.CreateServerSpec
import com.hcloudcontrol.core.domain.model.CreatedServer
import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.IsoOption
import com.hcloudcontrol.core.domain.model.IsoSelection
import com.hcloudcontrol.core.domain.model.CloudImageInventory
import com.hcloudcontrol.core.domain.model.ImageDeprecation
import com.hcloudcontrol.core.domain.model.ImageProtectionPatch
import com.hcloudcontrol.core.domain.model.DeleteProtectionPatch
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.LocationDetails
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerNetworkDeviceInventory
import com.hcloudcontrol.core.domain.model.ServerPrivateNetworkInterface
import com.hcloudcontrol.core.domain.model.ServerVolumeDeviceMap
import com.hcloudcontrol.core.domain.model.ServerProtection
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.model.ServerTypeDetails
import com.hcloudcontrol.core.domain.model.ServerTypeLocationAvailability
import com.hcloudcontrol.core.domain.model.SshKeyOption
import com.hcloudcontrol.core.domain.model.CreateSshKeySpec
import com.hcloudcontrol.core.domain.model.BackupSnapshotImage
import com.hcloudcontrol.core.domain.model.MoneyAmount
import com.hcloudcontrol.core.domain.model.ServerTypeLocationPrice
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.model.CreateVolumeSpec
import com.hcloudcontrol.core.domain.model.CreatedVolume
import com.hcloudcontrol.core.domain.model.CreatePrimaryIpSpec
import com.hcloudcontrol.core.domain.model.CreatedPrimaryIp
import com.hcloudcontrol.core.domain.model.DnsPointer
import com.hcloudcontrol.core.domain.model.PrimaryIpInventory
import com.hcloudcontrol.core.domain.model.FloatingIpInventory
import com.hcloudcontrol.core.domain.model.CertificateInventory
import com.hcloudcontrol.core.domain.model.CertificateStatus
import com.hcloudcontrol.core.domain.model.CertificateUsage
import com.hcloudcontrol.core.domain.model.FirewallInventory
import com.hcloudcontrol.core.domain.model.FirewallRuleInventory
import com.hcloudcontrol.core.domain.model.FirewallAppliedResource
import com.hcloudcontrol.core.domain.model.CreateFirewallSpec
import com.hcloudcontrol.core.domain.model.CreatedFirewall
import com.hcloudcontrol.core.domain.model.FirewallRuleSpec
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.model.PrivateNetworkMember
import com.hcloudcontrol.core.domain.model.PrivateNetworkSubnet
import com.hcloudcontrol.core.domain.model.PrivateNetworkRoute
import com.hcloudcontrol.core.domain.model.CreatePrivateNetworkSpec
import com.hcloudcontrol.core.domain.model.CreatedPrivateNetwork
import com.hcloudcontrol.core.domain.model.PrivateNetworkSubnetSpec
import com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec
import com.hcloudcontrol.core.domain.model.LoadBalancerInventory
import com.hcloudcontrol.core.domain.model.LoadBalancerTypeInventory
import com.hcloudcontrol.core.domain.model.LoadBalancerPublicInterface
import com.hcloudcontrol.core.domain.model.LoadBalancerPrivateInterface
import com.hcloudcontrol.core.domain.model.LoadBalancerService
import com.hcloudcontrol.core.domain.model.LoadBalancerLocationPrice
import com.hcloudcontrol.core.domain.model.LoadBalancerTarget
import com.hcloudcontrol.core.domain.model.LoadBalancerTargetHealth
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.model.CreatePlacementGroupSpec
import com.hcloudcontrol.core.domain.model.CreatedPlacementGroup
import com.hcloudcontrol.core.domain.model.PlacementGroupPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.EnableRescueResult
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.OneTimeRootPassword
import com.hcloudcontrol.core.domain.model.ResetRootPasswordResult
import com.hcloudcontrol.core.domain.model.RebuiltServerResult
import com.hcloudcontrol.core.domain.model.ServerRebuildSpec

internal class RetrofitHCloudRepository(private val api: HCloudApi) : HCloudRepository {
    override suspend fun getProfile(): Profile = api.getProfile().profile.let {
        Profile(id = it.id, name = it.name, email = it.email)
    }

    override suspend fun getServers(): List<Server> = allServerPages().map { it.toDomain() }

    override suspend fun getServer(serverId: Long): Server = api.getServer(serverId).server.toDomain()

    override suspend fun updateServerConfiguration(serverId: Long, patch: ServerConfigurationPatch): Server =
        api.updateServerConfiguration(
            serverId,
            ServerConfigurationPatchDto(patch.name, patch.labels, patch.placementGroupId),
        ).server.toDomain()

    override suspend fun changeServerProtection(serverId: Long, patch: ServerProtectionPatch): CloudAction {
        require(serverId > 0) { "Server ID must be positive" }
        return api.changeServerProtection(serverId, ServerProtectionPatchDto(patch.delete, patch.rebuild)).action.toDomain()
    }

    override suspend fun deleteServer(serverId: Long) = safeHCloudApiCall {
        require(serverId > 0) { "Server ID must be positive" }
        api.deleteServer(serverId)
    }

    override suspend fun rebuildServer(serverId: Long, spec: ServerRebuildSpec): RebuiltServerResult = safeHCloudApiCall {
        require(serverId > 0) { "Server ID must be positive" }
        api.rebuildServer(serverId, RebuildServerRequestDto(spec.image, spec.sshKeyIds)).let { response ->
            RebuiltServerResult(
                action = response.action.toDomain(),
                password = response.rootPassword.takeIf { it.isNotBlank() }?.let(OneTimeRootPassword::fromApiResponse),
            )
        }
    }

    override suspend fun getServerActions(serverId: Long): List<CloudAction> {
        val result = mutableListOf<CloudAction>()
        var page = 1
        do {
            val response = api.getServerActions(serverId, page = page)
            result += response.actions.map { it.toDomain() }
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    override suspend fun getServerTypes(): List<ServerTypeOption> = allServerTypePages().map { type ->
        ServerTypeOption(
            id = type.id,
            name = type.name,
            description = type.description.orEmpty(),
            cores = type.cores ?: 0,
            memoryGb = type.memory ?: 0.0,
            diskGb = type.disk ?: 0,
            category = type.category,
            architecture = type.architecture,
            // A missing availability flag must never be interpreted as permission to create.
            // The UI only offers a type after the API explicitly marks the location available.
            availableLocations = type.locations.orEmpty().filter { it.available == true }.map { it.name }.toSet(),
        )
    }

    override suspend fun getServerType(serverTypeId: Long): ServerTypeDetails {
        require(serverTypeId > 0) { "Server type ID must be positive" }
        val type = api.getServerType(serverTypeId).server_type
        // Currency/VAT live in the official pricing resource. Values are preserved verbatim.
        val pricing = api.getPricing().pricing
        val prices = pricing.serverTypes.firstOrNull { it.id == type.id }?.prices.orEmpty().map { price ->
            ServerTypeLocationPrice(
                serverTypeId = type.id,
                serverTypeName = type.name,
                location = price.location,
                currency = pricing.currency,
                vatRate = pricing.vatRate,
                hourly = price.priceHourly.toDomain(),
                monthly = price.priceMonthly.toDomain(),
            )
        }
        return ServerTypeDetails(
            id = type.id, name = type.name, description = type.description.orEmpty(),
            cores = type.cores ?: 0, memoryGb = type.memory ?: 0.0, diskGb = type.disk ?: 0,
            category = type.category, architecture = type.architecture,
            locations = type.locations.orEmpty().map { ServerTypeLocationAvailability(it.name, it.available) },
            prices = prices,
        )
    }

    override suspend fun getSystemImages(): List<ImageOption> = allImagePages("system").mapNotNull { image ->
        val name = image.name ?: return@mapNotNull null
        ImageOption(image.id, name, image.description.orEmpty(), image.type ?: "system", image.architecture)
    }

    override suspend fun getBackupAndSnapshotImages(): List<BackupSnapshotImage> =
        allImagePages("backup").plus(allImagePages("snapshot")).map { image ->
            BackupSnapshotImage(
                id = image.id,
                name = image.name,
                description = image.description,
                type = image.type ?: "unknown",
                createdAt = image.created,
                deprecatedAt = image.deprecated,
            )
        }

    override suspend fun getImages(): List<CloudImageInventory> = allImagePages().map { it.toInventory() }

    override suspend fun getImage(imageId: Long): CloudImageInventory = api.getImage(imageId).image.toInventory()
    override suspend fun getImageActions(imageId: Long): List<CloudAction> {
        require(imageId > 0) { "Image ID must be positive" }
        val result = mutableListOf<ActionDto>(); var page = 1
        do { val response = api.getImageActions(imageId, page); result += response.actions; page = response.meta?.pagination?.nextPage ?: 0 } while (page > 0)
        return result.map { it.toDomain() }
    }

    override suspend fun deleteImage(imageId: Long) = safeHCloudApiCall {
        require(imageId > 0) { "Image ID must be positive" }
        api.deleteImage(imageId)
    }

    override suspend fun changeImageProtection(imageId: Long, patch: ImageProtectionPatch): CloudAction = safeHCloudApiCall {
        require(imageId > 0) { "Image ID must be positive" }
        api.changeImageProtection(imageId, ImageProtectionPatchDto(delete = patch.delete)).action.toDomain()
    }

    override suspend fun getIsos(): List<IsoOption> = allIsoPages().map { it.toDomain() }

    override suspend fun getIso(isoId: Long): IsoOption = api.getIso(isoId).iso.toDomain()

    override suspend fun getServerTypePrice(serverTypeId: Long, location: String): ServerTypeLocationPrice? {
        val pricing = api.getPricing().pricing
        val serverType = pricing.serverTypes.firstOrNull { it.id == serverTypeId } ?: return null
        val price = serverType.prices.firstOrNull { it.location == location } ?: return null
        return ServerTypeLocationPrice(
            serverTypeId = serverType.id,
            serverTypeName = serverType.name,
            location = price.location,
            currency = pricing.currency,
            vatRate = pricing.vatRate,
            hourly = price.priceHourly.toDomain(),
            monthly = price.priceMonthly.toDomain(),
        )
    }

    override suspend fun getVolumes(): List<VolumeInventory> = allVolumePages().map { it.toInventory() }

    override suspend fun getVolume(volumeId: Long): VolumeInventory {
        require(volumeId > 0) { "Volume ID must be positive" }
        return api.getVolume(volumeId).volume.toInventory()
    }

    override suspend fun getVolumeActions(volumeId: Long): List<CloudAction> {
        require(volumeId > 0) { "Volume ID must be positive" }
        return allVolumeActionPages(volumeId).map { it.toDomain() }
    }

    override suspend fun createVolume(spec: CreateVolumeSpec): CreatedVolume = safeHCloudApiCall {
        val response = api.createVolume(
            CreateVolumeRequestDto(
                name = spec.name.trim(),
                size = spec.sizeGb,
                location = spec.location.trim(),
                labels = spec.labels,
            ),
        )
        CreatedVolume(response.volume.toInventory(), response.action?.toDomain())
    }

    override suspend fun deleteVolume(volumeId: Long): CloudAction = safeHCloudApiCall {
        require(volumeId > 0) { "Volume ID must be positive" }
        api.deleteVolume(volumeId).action.toDomain()
    }

    override suspend fun changeVolumeProtection(volumeId: Long, patch: DeleteProtectionPatch): CloudAction = safeHCloudApiCall {
        require(volumeId > 0) { "Volume ID must be positive" }
        api.changeVolumeProtection(volumeId, DeleteProtectionPatchDto(patch.delete)).action.toDomain()
    }

    override suspend fun getPrimaryIps(): List<PrimaryIpInventory> = allPrimaryIpPages().map { it.toInventory() }

    override suspend fun createPrimaryIp(spec: CreatePrimaryIpSpec): CreatedPrimaryIp = safeHCloudApiCall {
        val response = api.createPrimaryIp(
            CreatePrimaryIpRequestDto(
                type = spec.type,
                name = spec.name.trim(),
                location = spec.location.trim(),
                assigneeType = spec.assigneeId?.let { "server" },
                assigneeId = spec.assigneeId,
                labels = spec.labels,
            ),
        )
        CreatedPrimaryIp(response.primaryIp.toInventory(), response.action?.toDomain())
    }

    override suspend fun getPrimaryIp(primaryIpId: Long): PrimaryIpInventory {
        require(primaryIpId > 0) { "Primary IP ID must be positive" }
        return api.getPrimaryIp(primaryIpId).primaryIp.toInventory()
    }

    override suspend fun getPrimaryIpActions(primaryIpId: Long): List<CloudAction> {
        require(primaryIpId > 0) { "Primary IP ID must be positive" }
        return allPrimaryIpActionPages(primaryIpId).map { it.toDomain() }
    }

    override suspend fun deletePrimaryIp(primaryIpId: Long) {
        require(primaryIpId > 0) { "Primary IP ID must be positive" }
        api.deletePrimaryIp(primaryIpId)
    }

    override suspend fun getFloatingIps(): List<FloatingIpInventory> = allFloatingIpPages().map { it.toDomain() }

    override suspend fun getFloatingIp(floatingIpId: Long): FloatingIpInventory =
        api.getFloatingIp(floatingIpId).floatingIp.toDomain()

    override suspend fun getFloatingIpActions(floatingIpId: Long): List<CloudAction> {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        return allFloatingIpActionPages(floatingIpId).map { it.toDomain() }
    }

    override suspend fun assignFloatingIp(floatingIpId: Long, serverId: Long): CloudAction {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        require(serverId > 0) { "Server ID must be positive" }
        return api.assignFloatingIp(floatingIpId, AssignFloatingIpRequestDto(serverId)).action.toDomain()
    }

    override suspend fun unassignFloatingIp(floatingIpId: Long): CloudAction {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        return api.unassignFloatingIp(floatingIpId).action.toDomain()
    }

    override suspend fun changeFloatingIpReverseDns(floatingIpId: Long, ip: String, dnsPtr: String?): CloudAction {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        require(ip.isNotBlank()) { "IP address must not be blank" }
        return api.changeFloatingIpReverseDns(floatingIpId, ChangeDnsPtrRequestDto(ip, dnsPtr)).action.toDomain()
    }

    override suspend fun changeFloatingIpProtection(floatingIpId: Long, patch: DeleteProtectionPatch): CloudAction = safeHCloudApiCall {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        api.changeFloatingIpProtection(floatingIpId, DeleteProtectionPatchDto(patch.delete)).action.toDomain()
    }

    override suspend fun deleteFloatingIp(floatingIpId: Long) {
        require(floatingIpId > 0) { "Floating IP ID must be positive" }
        api.deleteFloatingIp(floatingIpId)
    }

    override suspend fun getCertificates(): List<CertificateInventory> = allCertificatePages().map { it.toDomain() }

    override suspend fun getCertificate(certificateId: Long): CertificateInventory {
        require(certificateId > 0) { "Certificate ID must be positive" }
        return api.getCertificate(certificateId).certificate.toDomain()
    }

    override suspend fun getCertificateActions(certificateId: Long): List<CloudAction> {
        require(certificateId > 0) { "Certificate ID must be positive" }
        return allCertificateActionPages(certificateId).map { it.toDomain() }
    }

    override suspend fun assignPrimaryIp(primaryIpId: Long, serverId: Long): CloudAction =
        api.assignPrimaryIp(primaryIpId, AssignPrimaryIpRequestDto(serverId)).action.toDomain()
    override suspend fun unassignPrimaryIp(primaryIpId: Long): CloudAction = api.unassignPrimaryIp(primaryIpId).action.toDomain()
    override suspend fun changePrimaryIpReverseDns(primaryIpId: Long, ip: String, dnsPtr: String?): CloudAction =
        api.changePrimaryIpReverseDns(primaryIpId, ChangeDnsPtrRequestDto(ip, dnsPtr)).action.toDomain()

    override suspend fun changePrimaryIpProtection(primaryIpId: Long, patch: DeleteProtectionPatch): CloudAction = safeHCloudApiCall {
        require(primaryIpId > 0) { "Primary IP ID must be positive" }
        api.changePrimaryIpProtection(primaryIpId, DeleteProtectionPatchDto(patch.delete)).action.toDomain()
    }

    override suspend fun getFirewalls(): List<FirewallInventory> = allFirewallPages().map { it.toInventory() }
    override suspend fun getFirewall(firewallId: Long): FirewallInventory {
        require(firewallId > 0) { "Firewall ID must be positive" }
        return api.getFirewall(firewallId).firewall.toInventory()
    }
    override suspend fun getFirewallActions(firewallId: Long): List<CloudAction> {
        require(firewallId > 0) { "Firewall ID must be positive" }
        return allFirewallActionPages(firewallId).map { it.toDomain() }
    }
    override suspend fun createFirewall(spec: CreateFirewallSpec): CreatedFirewall {
        validateFirewallSpec(spec.rules)
        val response = api.createFirewall(spec.toRequest())
        return CreatedFirewall(response.firewall.toInventory(), response.actions.map { it.toDomain() })
    }
    override suspend fun deleteFirewall(firewallId: Long) {
        require(firewallId > 0) { "Firewall ID must be positive" }
        api.deleteFirewall(firewallId)
    }
    override suspend fun setFirewallRules(firewallId: Long, rules: List<FirewallRuleSpec>): CloudAction {
        require(firewallId > 0) { "Firewall ID must be positive" }
        validateFirewallSpec(rules)
        return api.setFirewallRules(firewallId, SetFirewallRulesRequestDto(rules.map { it.toRequest() })).action.toDomain()
    }
    override suspend fun applyFirewallToServer(firewallId: Long, serverId: Long): List<CloudAction> =
        api.applyFirewallToResources(firewallId, firewallServerRequest(serverId)).actions.map { it.toDomain() }
    override suspend fun removeFirewallFromServer(firewallId: Long, serverId: Long): List<CloudAction> =
        api.removeFirewallFromResources(firewallId, firewallRemoveServerRequest(serverId)).actions.map { it.toDomain() }

    override suspend fun getPrivateNetworks(): List<PrivateNetworkInventory> = allNetworkPages().map { it.toInventory() }
    override suspend fun createPrivateNetwork(spec: CreatePrivateNetworkSpec): CreatedPrivateNetwork = safeHCloudApiCall {
        api.createNetwork(CreateNetworkRequestDto(spec.name, spec.ipRange, spec.labels)).let { response ->
            CreatedPrivateNetwork(response.network.toInventory(), response.action.toDomain())
        }
    }
    override suspend fun getPrivateNetwork(networkId: Long): PrivateNetworkInventory {
        require(networkId > 0) { "Network ID must be positive" }
        return api.getNetwork(networkId).network.toInventory()
    }
    override suspend fun getPrivateNetworkMembers(networkId: Long): List<PrivateNetworkMember> {
        require(networkId > 0) { "Network ID must be positive" }
        val result = mutableListOf<NetworkMemberDto>()
        var page = 1
        do {
            val response = api.getNetworkMembers(networkId, page)
            result += response.members
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page > 0)
        return result.map { PrivateNetworkMember(it.type, it.id, it.ip, it.aliasIps, it.subnet, it.status) }
    }
    override suspend fun getPrivateNetworkActions(networkId: Long): List<CloudAction> {
        require(networkId > 0) { "Network ID must be positive" }
        val result = mutableListOf<ActionDto>(); var page = 1
        do { val response = api.getNetworkActions(networkId, page); result += response.actions; page = response.meta?.pagination?.nextPage ?: 0 } while (page > 0)
        return result.map { it.toDomain() }
    }
    override suspend fun deletePrivateNetwork(networkId: Long): CloudAction = safeHCloudApiCall {
        require(networkId > 0) { "Network ID must be positive" }
        api.deleteNetwork(networkId).action.toDomain()
    }
    override suspend fun addPrivateNetworkSubnet(networkId: Long, spec: PrivateNetworkSubnetSpec): CloudAction =
        privateNetworkSubnetAction(networkId, spec, add = true)
    override suspend fun deletePrivateNetworkSubnet(networkId: Long, spec: PrivateNetworkSubnetSpec): CloudAction =
        privateNetworkSubnetAction(networkId, spec, add = false)
    private suspend fun privateNetworkSubnetAction(networkId: Long, spec: PrivateNetworkSubnetSpec, add: Boolean): CloudAction = safeHCloudApiCall {
        require(networkId > 0) { "Network ID must be positive" }
        val request = NetworkSubnetRequestDto(ipRange = spec.ipRange, networkZone = spec.networkZone, gateway = spec.gateway)
        (if (add) api.addNetworkSubnet(networkId, request) else api.deleteNetworkSubnet(networkId, request)).action.toDomain()
    }
    override suspend fun addPrivateNetworkRoute(networkId: Long, spec: PrivateNetworkRouteSpec): CloudAction =
        privateNetworkRouteAction(networkId, spec, add = true)
    override suspend fun deletePrivateNetworkRoute(networkId: Long, spec: PrivateNetworkRouteSpec): CloudAction =
        privateNetworkRouteAction(networkId, spec, add = false)
    private suspend fun privateNetworkRouteAction(networkId: Long, spec: PrivateNetworkRouteSpec, add: Boolean): CloudAction = safeHCloudApiCall {
        require(networkId > 0) { "Network ID must be positive" }
        val request = NetworkRouteRequestDto(spec.destination, spec.gateway)
        (if (add) api.addNetworkRoute(networkId, request) else api.deleteNetworkRoute(networkId, request)).action.toDomain()
    }
    private fun NetworkDto.toInventory() =
        PrivateNetworkInventory(
            id = id,
            name = name,
            ipRange = ipRange,
            subnets = subnets.map { subnet ->
                PrivateNetworkSubnet(
                    type = subnet.type,
                    ipRange = subnet.ipRange,
                    networkZone = subnet.networkZone,
                    gateway = subnet.gateway,
                    vswitchId = subnet.vswitchId,
                )
            },
            routes = routes.map { route ->
                PrivateNetworkRoute(destination = route.destination, gateway = route.gateway)
            },
            serverIds = servers,
            deleteProtected = protection.deleteProtected,
            labels = labels,
        )
    override suspend fun attachServerToPrivateNetwork(networkId: Long, serverId: Long): CloudAction =
        api.attachServerToNetwork(serverId, AttachServerToNetworkRequestDto(networkId)).action.toDomain()
    override suspend fun detachServerFromPrivateNetwork(networkId: Long, serverId: Long): CloudAction =
        api.detachServerFromNetwork(serverId, DetachServerFromNetworkRequestDto(networkId)).action.toDomain()

    override suspend fun changePrivateNetworkProtection(networkId: Long, patch: DeleteProtectionPatch): CloudAction = safeHCloudApiCall {
        require(networkId > 0) { "Network ID must be positive" }
        api.changeNetworkProtection(networkId, DeleteProtectionPatchDto(patch.delete)).action.toDomain()
    }

    override suspend fun getLoadBalancers(): List<LoadBalancerInventory> = allLoadBalancerPages().map { loadBalancer ->
        LoadBalancerInventory(
            id = loadBalancer.id,
            name = loadBalancer.name,
            type = loadBalancer.loadBalancerType.name,
            location = loadBalancer.location.name,
            publicInterface = LoadBalancerPublicInterface(
                enabled = loadBalancer.publicNet.enabled,
                ipv4Address = loadBalancer.publicNet.ipv4?.ip,
                ipv4DnsPtr = loadBalancer.publicNet.ipv4?.dnsPtr,
                ipv6Address = loadBalancer.publicNet.ipv6?.ip,
                ipv6DnsPtr = loadBalancer.publicNet.ipv6?.dnsPtr,
            ),
            privateInterfaces = loadBalancer.privateNet.map { LoadBalancerPrivateInterface(it.network, it.ip) },
            services = loadBalancer.services.map {
                LoadBalancerService(it.protocol, it.listenPort, it.destinationPort, it.proxyProtocol)
            },
            targets = loadBalancer.targets.map { it.toDomain() },
            deleteProtected = loadBalancer.protection.deleteProtected,
            labels = loadBalancer.labels,
            prices = loadBalancer.loadBalancerType.prices.map {
                LoadBalancerLocationPrice(it.location, it.priceHourly.toDomain(), it.priceMonthly.toDomain())
            },
        )
    }

    override suspend fun getLoadBalancer(loadBalancerId: Long): LoadBalancerInventory =
        api.getLoadBalancer(loadBalancerId).loadBalancer.toInventory()

    override suspend fun deleteLoadBalancer(loadBalancerId: Long): CloudAction = safeHCloudApiCall {
        require(loadBalancerId > 0) { "Load Balancer ID must be positive" }
        api.deleteLoadBalancer(loadBalancerId).action.toDomain()
    }

    override suspend fun getLoadBalancerActions(loadBalancerId: Long): List<CloudAction> {
        require(loadBalancerId > 0) { "Load Balancer ID must be positive" }
        return allLoadBalancerActionPages(loadBalancerId).map { it.toDomain() }
    }

    override suspend fun getLoadBalancerTypes(): List<LoadBalancerTypeInventory> =
        allLoadBalancerTypePages().map { it.toInventory() }

    override suspend fun getLoadBalancerType(loadBalancerTypeId: Long): LoadBalancerTypeInventory {
        require(loadBalancerTypeId > 0) { "Load Balancer type ID must be positive" }
        return api.getLoadBalancerType(loadBalancerTypeId).loadBalancerType.toInventory()
    }

    override suspend fun changeLoadBalancerProtection(loadBalancerId: Long, patch: DeleteProtectionPatch): CloudAction = safeHCloudApiCall {
        require(loadBalancerId > 0) { "Load Balancer ID must be positive" }
        api.changeLoadBalancerProtection(loadBalancerId, DeleteProtectionPatchDto(patch.delete)).action.toDomain()
    }

    override suspend fun getPlacementGroups(): List<PlacementGroupInventory> = allPlacementGroupPages().map { it.toDomain() }

    override suspend fun getPlacementGroup(placementGroupId: Long): PlacementGroupInventory =
        api.getPlacementGroup(placementGroupId).placementGroup.toDomain()

    override suspend fun createPlacementGroup(spec: CreatePlacementGroupSpec): CreatedPlacementGroup {
        require(spec.name.isNotBlank()) { "Placement group name must not be blank" }
        require(spec.type == "spread") { "Unsupported placement group type" }
        val response = api.createPlacementGroup(
            CreatePlacementGroupRequestDto(
                name = spec.name.trim(),
                type = spec.type,
                labels = spec.labels.takeIf { it.isNotEmpty() },
            ),
        )
        return CreatedPlacementGroup(response.placementGroup.toDomain(), response.action?.toDomain())
    }

    override suspend fun updatePlacementGroup(placementGroupId: Long, patch: PlacementGroupPatch): PlacementGroupInventory {
        require(placementGroupId > 0) { "Placement group ID must be positive" }
        val response = api.updatePlacementGroup(
            placementGroupId,
            UpdatePlacementGroupRequestDto(name = patch.name?.trim(), labels = patch.labels),
        )
        return response.placementGroup.toDomain()
    }

    override suspend fun deletePlacementGroup(placementGroupId: Long) {
        require(placementGroupId > 0) { "Placement group ID must be positive" }
        api.deletePlacementGroup(placementGroupId)
    }

    override suspend fun addServerToPlacementGroup(serverId: Long, placementGroupId: Long): CloudAction {
        validatePlacementGroupIds(serverId, placementGroupId)
        return api.addServerToPlacementGroup(serverId, PlacementGroupRequestDto(placementGroupId)).action.toDomain()
    }

    override suspend fun removeServerFromPlacementGroup(serverId: Long, placementGroupId: Long): CloudAction {
        validatePlacementGroupIds(serverId, placementGroupId)
        return api.removeServerFromPlacementGroup(serverId, PlacementGroupRequestDto(placementGroupId)).action.toDomain()
    }

    override suspend fun attachVolume(volumeId: Long, serverId: Long): CloudAction =
        api.attachVolume(volumeId, AttachVolumeRequestDto(serverId)).action.toDomain()

    override suspend fun detachVolume(volumeId: Long): CloudAction =
        api.detachVolume(volumeId).action.toDomain()

    override suspend fun resizeVolume(volumeId: Long, currentSizeGb: Int, newSizeGb: Int): CloudAction {
        require(currentSizeGb > 0) { "Current volume size must be positive" }
        require(newSizeGb > currentSizeGb) { "New volume size must be greater than the current size" }
        return api.resizeVolume(volumeId, ResizeVolumeRequestDto(newSizeGb)).action.toDomain()
    }

    override suspend fun getLocations(): List<LocationOption> = allLocationPages().map { location ->
        LocationOption(location.id, location.name, location.description.orEmpty(), location.city, location.country)
    }

    override suspend fun getLocation(locationId: Long): LocationDetails {
        require(locationId > 0) { "Location ID must be positive" }
        val location = api.getLocation(locationId).location
        return LocationDetails(
            id = location.id, name = location.name, description = location.description.orEmpty(),
            city = location.city, country = location.country, networkZone = location.networkZone,
        )
    }

    override suspend fun getSshKeys(): List<SshKeyOption> = allSshKeyPages().map { key ->
        SshKeyOption(id = key.id, name = key.name, fingerprint = key.fingerprint)
    }

    override suspend fun createSshKey(spec: CreateSshKeySpec): SshKeyOption {
        val created = api.createSshKey(CreateSshKeyRequestDto(spec.name.trim(), spec.publicKey.trim())).ssh_key
        return SshKeyOption(id = created.id, name = created.name, fingerprint = created.fingerprint)
    }

    override suspend fun deleteSshKey(sshKeyId: Long) {
        require(sshKeyId > 0) { "SSH key ID must be positive" }
        api.deleteSshKey(sshKeyId)
    }

    override suspend fun createServer(spec: CreateServerSpec): CreatedServer = safeHCloudApiCall {
        api.createServer(
            CreateServerRequestDto(
                name = spec.name,
                serverType = spec.serverType,
                image = spec.image,
                location = spec.location,
                publicNet = PublicNetRequestDto(spec.enableIpv4, spec.enableIpv6),
                sshKeys = spec.sshKeyIds,
            ),
        )
    }.let { response ->
        CreatedServer(
            server = response.server.toDomain(),
            rootPassword = response.rootPassword,
            actions = listOfNotNull(response.action, *response.nextActions.toTypedArray())
                .distinctBy(ActionDto::id)
                .map { CloudAction(it.id, it.status) },
        )
    }

    override suspend fun changeServerType(serverId: Long, serverType: String, upgradeDisk: Boolean): CloudAction = api.changeServerType(
        serverId,
        ChangeServerTypeRequestDto(serverType, upgradeDisk),
    ).action.let { CloudAction(it.id, it.status) }

    override suspend fun getAction(actionId: Long): CloudAction = api.getAction(actionId).action.let {
        CloudAction(it.id, it.status)
    }

    override suspend fun powerOn(serverId: Long): CloudAction = api.powerOn(serverId).action.toDomain()

    override suspend fun powerOff(serverId: Long): CloudAction = api.powerOff(serverId).action.toDomain()

    override suspend fun shutdown(serverId: Long): CloudAction = api.shutdown(serverId).action.toDomain()

    override suspend fun reboot(serverId: Long): CloudAction = api.reboot(serverId).action.toDomain()

    override suspend fun reset(serverId: Long): CloudAction = api.reset(serverId).action.toDomain()

    override suspend fun attachIso(serverId: Long, iso: IsoSelection): CloudAction =
        api.attachIso(serverId, AttachIsoRequestDto(iso.id)).action.toDomain()

    override suspend fun detachIso(serverId: Long): CloudAction = api.detachIso(serverId).action.toDomain()

    override suspend fun changeServerReverseDns(serverId: Long, ip: String, dnsPtr: String?): CloudAction {
        require(serverId > 0) { "Server ID must be positive" }
        require(ip.isNotBlank()) { "IP address must not be blank" }
        return api.changeServerReverseDns(serverId, ServerDnsPtrRequestDto(ip, dnsPtr)).action.toDomain()
    }
    override suspend fun resetRootPassword(serverId: Long): ResetRootPasswordResult =
        api.resetRootPassword(serverId).toResetRootPasswordResult()
    override suspend fun enableRescue(serverId: Long, spec: EnableRescueSpec): EnableRescueResult =
        api.enableRescue(serverId, EnableRescueRequestDto(spec.type.apiValue, spec.sshKeyIds)).toEnableRescueResult()
    override suspend fun disableRescue(serverId: Long): CloudAction = api.disableRescue(serverId).action.toDomain()

    override suspend fun enableBackups(serverId: Long): CloudAction = api.enableBackups(serverId).action.toDomain()

    override suspend fun disableBackups(serverId: Long): CloudAction = api.disableBackups(serverId).action.toDomain()

    override suspend fun createSnapshot(serverId: Long, description: String): CloudAction {
        require(description.isNotBlank()) { "Snapshot description must not be blank" }
        return api.createImage(serverId, CreateImageRequestDto(description = description.trim())).action.toDomain()
    }

    private fun ServerDto.toDomain() = Server(
        id = id,
        name = name,
        status = status.toServerStatus(),
        ipv4Address = publicNet.ipv4?.ip,
        location = location?.name ?: "Location unavailable",
        serverType = serverType.name,
        backupWindow = backupWindow,
        labels = labels,
        placementGroupId = placementGroup?.id,
        attachedIso = iso?.toDomain(),
        protection = protection?.let { ServerProtection(it.delete, it.rebuild) },
        publicDnsPointers = buildList {
            publicNet.ipv4?.ip?.let { add(DnsPointer(it, publicNet.ipv4?.dnsPtr)) }
            publicNet.ipv6?.dnsPointers?.forEach { pointer -> add(DnsPointer(pointer.ip, pointer.dnsPtr)) }
        }.distinctBy { it.ip },
        networkDevices = ServerNetworkDeviceInventory(
            ipv4Address = publicNet.ipv4?.ip,
            ipv6Network = publicNet.ipv6?.ip,
            primaryIpIds = publicNet.primaryIps,
            floatingIpIds = publicNet.floatingIps,
            privateInterfaces = privateNet.map { ServerPrivateNetworkInterface(it.network, it.ip, it.aliasIps, it.macAddress) },
            attachedVolumeIds = volumes,
            deviceMaps = deviceMaps.map { ServerVolumeDeviceMap(it.volume, it.device) },
        ),
    )

    private suspend fun allServerPages(): List<ServerDto> {
        val result = mutableListOf<ServerDto>()
        var page = 1
        do {
            val response = api.getServers(page)
            result += response.servers
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allServerTypePages(): List<ServerTypeDto> {
        val result = mutableListOf<ServerTypeDto>(); var page = 1
        do { val response = api.getServerTypes(page); result += response.server_types; page = response.meta?.pagination?.nextPage ?: 0 } while (page != 0)
        return result
    }

    private suspend fun allImagePages(type: String? = null): List<ImageDto> {
        val result = mutableListOf<ImageDto>(); var page = 1
        do { val response = api.getImages(type = type, page = page); result += response.images; page = response.meta?.pagination?.nextPage ?: 0 } while (page != 0)
        return result
    }

    private suspend fun allIsoPages(): List<IsoDto> {
        val result = mutableListOf<IsoDto>()
        var page = 1
        do {
            val response = api.getIsos(page = page)
            result += response.isos
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private fun ImageDto.toInventory() = CloudImageInventory(
        id = id,
        name = name,
        description = description,
        type = type,
        architecture = architecture,
        status = status,
        imageSize = imageSize,
        diskSize = diskSize,
        created = created,
        deleteProtected = protection?.deleteProtected,
        deprecated = deprecated,
        deprecation = deprecation?.let { ImageDeprecation(it.announced, it.unavailableAfter) },
    )

    private fun VolumeDto.toInventory() = VolumeInventory(
        id = id,
        name = name,
        sizeGb = size,
        location = location.name,
        serverId = server,
        status = status,
        deleteProtected = protection.deleteProtected,
        labels = labels,
    )

    private fun PrimaryIpDto.toInventory() = PrimaryIpInventory(
        id = id,
        name = name,
        type = type,
        address = ip,
        location = location.name,
        assigneeId = assigneeId,
        assigneeType = assigneeType,
        deleteProtected = protection.deleteProtected,
        labels = labels,
        dnsPointers = dnsPointers.map { DnsPointer(it.ip, it.dnsPtr) },
    )

    private fun FirewallDto.toInventory() = FirewallInventory(
        id = id,
        name = name,
        rules = rules.map { rule ->
            FirewallRuleInventory(
                direction = rule.direction,
                protocol = rule.protocol,
                port = rule.port,
                sourceIps = rule.sourceIps,
                destinationIps = rule.destinationIps,
                description = rule.description,
            )
        },
        appliedTo = appliedTo.map { it.toDomain() },
        labels = labels,
    )

    private fun IsoDto.toDomain() = IsoOption(id, name, description, type, architecture)

    private fun PlacementGroupDto.toDomain() = PlacementGroupInventory(
        id = id,
        name = name,
        type = type,
        serverIds = servers,
        labels = labels,
        deleteProtected = null,
    )

    private fun FloatingIpDto.toDomain() = FloatingIpInventory(
        id = id, name = name, type = type, address = ip, homeLocation = homeLocation?.name,
        assigneeId = assigneeId, assigneeType = assigneeType, deleteProtected = protection.deleteProtected,
        labels = labels, dnsPointers = dnsPointers.map { DnsPointer(it.ip, it.dnsPtr) },
    )

    private fun CertificateDto.toDomain() = CertificateInventory(
        id = id,
        name = name,
        type = type,
        created = created,
        notValidBefore = notValidBefore,
        notValidAfter = notValidAfter,
        domainNames = domainNames,
        fingerprint = fingerprint,
        status = status?.let { CertificateStatus(it.issuance, it.renewal, it.error?.code) },
        usedBy = usedBy.map { CertificateUsage(it.id, it.type) },
        labels = labels,
    )

    private fun LoadBalancerDto.toInventory() = LoadBalancerInventory(
        id = id, name = name, type = loadBalancerType.name, location = location.name,
        publicInterface = LoadBalancerPublicInterface(publicNet.enabled, publicNet.ipv4?.ip, publicNet.ipv4?.dnsPtr, publicNet.ipv6?.ip, publicNet.ipv6?.dnsPtr),
        privateInterfaces = privateNet.map { LoadBalancerPrivateInterface(it.network, it.ip) },
        services = services.map { LoadBalancerService(it.protocol, it.listenPort, it.destinationPort, it.proxyProtocol) },
        targets = targets.map { it.toDomain() }, deleteProtected = protection.deleteProtected, labels = labels,
        prices = loadBalancerType.prices.map { LoadBalancerLocationPrice(it.location, it.priceHourly.toDomain(), it.priceMonthly.toDomain()) },
    )

    private fun LoadBalancerTypeDto.toInventory() = LoadBalancerTypeInventory(
        id = id,
        name = name,
        description = description,
        maxServices = maxServices,
        maxConnections = maxConnections,
        maxTargets = maxTargets,
        maxAssignments = maxAssignments,
        maxOpenRequests = maxOpenRequests,
        maxRoutingRules = maxRoutingRules,
        maxTcpConnections = maxTcpConnections,
        maxHttpRequestsPerSecond = maxHttpRequestsPerSecond,
        maxRequestsPerSecond = maxRequestsPerSecond,
        maxSslCertificates = maxSslCertificates,
        prices = prices.map { LoadBalancerLocationPrice(it.location, it.priceHourly.toDomain(), it.priceMonthly.toDomain()) },
    )

    private fun validatePlacementGroupIds(serverId: Long, placementGroupId: Long) {
        require(serverId > 0) { "Server ID must be positive" }
        require(placementGroupId > 0) { "Placement group ID must be positive" }
    }

    private suspend fun allLocationPages(): List<LocationDto> {
        val result = mutableListOf<LocationDto>(); var page = 1
        do { val response = api.getLocations(page); result += response.locations; page = response.meta?.pagination?.nextPage ?: 0 } while (page != 0)
        return result
    }

    private suspend fun allSshKeyPages(): List<SshKeyDto> {
        val result = mutableListOf<SshKeyDto>()
        var page = 1
        do {
            val response = api.getSshKeys(page)
            result += response.ssh_keys
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allVolumePages(): List<VolumeDto> {
        val result = mutableListOf<VolumeDto>()
        var page = 1
        do {
            val response = api.getVolumes(page)
            result += response.volumes
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allVolumeActionPages(volumeId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getVolumeActions(volumeId, page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allPrimaryIpPages(): List<PrimaryIpDto> {
        val result = mutableListOf<PrimaryIpDto>()
        var page = 1
        do {
            val response = api.getPrimaryIps(page)
            result += response.primary_ips
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allPrimaryIpActionPages(primaryIpId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getPrimaryIpActions(primaryIpId, page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allFloatingIpPages(): List<FloatingIpDto> {
        val result = mutableListOf<FloatingIpDto>(); var page = 1
        do { val response = api.getFloatingIps(page); result += response.floatingIps; page = response.meta?.pagination?.nextPage ?: 0 } while (page != 0)
        return result
    }

    private suspend fun allFloatingIpActionPages(floatingIpId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getFloatingIpActions(floatingIpId, page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allCertificatePages(): List<CertificateDto> {
        val result = mutableListOf<CertificateDto>()
        var page = 1
        do {
            val response = api.getCertificates(page)
            result += response.certificates
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allCertificateActionPages(certificateId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getCertificateActions(certificateId, page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allFirewallPages(): List<FirewallDto> {
        val result = mutableListOf<FirewallDto>()
        var page = 1
        do {
            val response = api.getFirewalls(page)
            result += response.firewalls
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allFirewallActionPages(firewallId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getFirewallActions(firewallId, page = page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allNetworkPages(): List<NetworkDto> {
        val result = mutableListOf<NetworkDto>()
        var page = 1
        do {
            val response = api.getNetworks(page)
            result += response.networks
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allLoadBalancerPages(): List<LoadBalancerDto> {
        val result = mutableListOf<LoadBalancerDto>()
        var page = 1
        do {
            val response = api.getLoadBalancers(page)
            result += response.loadBalancers
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allLoadBalancerTypePages(): List<LoadBalancerTypeDto> {
        val result = mutableListOf<LoadBalancerTypeDto>()
        var page = 1
        do {
            val response = api.getLoadBalancerTypes(page)
            result += response.loadBalancerTypes
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allLoadBalancerActionPages(loadBalancerId: Long): List<ActionDto> {
        val result = mutableListOf<ActionDto>()
        var page = 1
        do {
            val response = api.getLoadBalancerActions(loadBalancerId, page)
            result += response.actions
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allPlacementGroupPages(): List<PlacementGroupDto> {
        val result = mutableListOf<PlacementGroupDto>()
        var page = 1
        do {
            val response = api.getPlacementGroups(page)
            result += response.placementGroups
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }
}

private fun firewallServerRequest(serverId: Long) = FirewallResourcesRequestDto(
    apply_to = listOf(FirewallResourceRequestDto(server = FirewallServerReferenceDto(serverId))),
)

private fun firewallRemoveServerRequest(serverId: Long) = FirewallRemoveResourcesRequestDto(
    remove_from = listOf(FirewallResourceRequestDto(server = FirewallServerReferenceDto(serverId))),
)

private fun CreateFirewallSpec.toRequest() = CreateFirewallRequestDto(
    name = name.trim(),
    rules = rules.map { it.toRequest() },
    labels = labels,
)

private fun FirewallRuleSpec.toRequest() = FirewallRuleRequestDto(
    direction = direction,
    protocol = protocol,
    port = port?.trim()?.takeIf { it.isNotEmpty() },
    sourceIps = sourceIps.map { it.trim() },
    destinationIps = destinationIps.map { it.trim() },
    description = description?.trim()?.takeIf { it.isNotEmpty() },
)

/**
 * Keeps invalid or ambiguous firewall requests off the wire. Hetzner itself remains the final
 * authority for CIDR and port syntax, but this prevents accidental direction/address inversion
 * and the common unsafe TCP/UDP rule without a declared port policy.
 */
private fun validateFirewallSpec(rules: List<FirewallRuleSpec>) {
    val supportedProtocols = setOf("tcp", "udp", "icmp", "esp", "gre", "ah")
    rules.forEach { rule ->
        require(rule.direction in setOf("in", "out")) { "Firewall direction must be in or out" }
        require(rule.protocol in supportedProtocols) { "Unsupported Firewall protocol" }
        if (rule.protocol == "tcp" || rule.protocol == "udp") {
            require(!rule.port.isNullOrBlank()) { "TCP and UDP Firewall rules require an explicit port or any" }
        }
        if (rule.direction == "in") {
            require(rule.destinationIps.isEmpty()) { "Inbound Firewall rules cannot declare destination IPs" }
            require(rule.sourceIps.isNotEmpty() && rule.sourceIps.none { it.isBlank() }) { "Inbound Firewall rules require source IPs" }
        } else {
            require(rule.sourceIps.isEmpty()) { "Outbound Firewall rules cannot declare source IPs" }
            require(rule.destinationIps.isNotEmpty() && rule.destinationIps.none { it.isBlank() }) { "Outbound Firewall rules require destination IPs" }
        }
    }
}

private fun ActionDto.toDomain() = CloudAction(
    id = id,
    status = status,
    command = command,
    started = started,
    finished = finished,
    progress = progress,
    errorCode = error?.code,
    errorMessage = error?.message,
)

private fun RootPasswordActionResponse.toResetRootPasswordResult() = ResetRootPasswordResult(
    action = action.toDomain(),
    password = OneTimeRootPassword.fromApiResponse(rootPassword),
)

private fun RootPasswordActionResponse.toEnableRescueResult() = EnableRescueResult(
    action = action.toDomain(),
    password = OneTimeRootPassword.fromApiResponse(rootPassword),
)

private fun MoneyDto.toDomain() = MoneyAmount(net, gross)

private fun FirewallAppliedResourceDto.toDomain(): FirewallAppliedResource = FirewallAppliedResource(
    type = type,
    serverId = server?.id,
    labelSelector = labelSelector?.selector,
    appliedToResources = appliedToResources.map { it.toDomain() },
)

private fun LoadBalancerTargetDto.toDomain(): LoadBalancerTarget = LoadBalancerTarget(
    type = type,
    serverId = server?.id,
    labelSelector = labelSelector?.selector,
    ip = ip?.ip,
    usePrivateIp = usePrivateIp,
    healthStatuses = healthStatuses.map { LoadBalancerTargetHealth(it.listenPort, it.status) },
    targets = targets.map { it.toDomain() },
)

/** Public construction boundary for the network module. */
object HCloudRepositoryFactory {
    fun create(tokenProvider: () -> String?, configProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real): HCloudRepository =
        RetrofitHCloudRepository(HCloudApiFactory.create(tokenProvider, configProvider))
}

private fun String.toServerStatus(): ServerStatus = when (lowercase()) {
    "initializing" -> ServerStatus.INITIALIZING
    "off" -> ServerStatus.OFF
    "running" -> ServerStatus.RUNNING
    "starting" -> ServerStatus.STARTING
    "stopping" -> ServerStatus.STOPPING
    else -> ServerStatus.UNKNOWN
}
