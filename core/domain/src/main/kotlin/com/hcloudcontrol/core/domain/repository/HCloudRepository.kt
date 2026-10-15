package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.CreateServerSpec
import com.hcloudcontrol.core.domain.model.CreatedServer
import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.IsoOption
import com.hcloudcontrol.core.domain.model.IsoSelection
import com.hcloudcontrol.core.domain.model.CloudImageInventory
import com.hcloudcontrol.core.domain.model.ImageProtectionPatch
import com.hcloudcontrol.core.domain.model.DeleteProtectionPatch
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.LocationDetails
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.model.ServerTypeDetails
import com.hcloudcontrol.core.domain.model.SshKeyOption
import com.hcloudcontrol.core.domain.model.CreateSshKeySpec
import com.hcloudcontrol.core.domain.model.BackupSnapshotImage
import com.hcloudcontrol.core.domain.model.ServerTypeLocationPrice
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.model.CreateVolumeSpec
import com.hcloudcontrol.core.domain.model.CreatedVolume
import com.hcloudcontrol.core.domain.model.PrimaryIpInventory
import com.hcloudcontrol.core.domain.model.CreatePrimaryIpSpec
import com.hcloudcontrol.core.domain.model.CreatedPrimaryIp
import com.hcloudcontrol.core.domain.model.FloatingIpInventory
import com.hcloudcontrol.core.domain.model.CertificateInventory
import com.hcloudcontrol.core.domain.model.FirewallInventory
import com.hcloudcontrol.core.domain.model.CreateFirewallSpec
import com.hcloudcontrol.core.domain.model.CreatedFirewall
import com.hcloudcontrol.core.domain.model.FirewallRuleSpec
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.model.PrivateNetworkMember
import com.hcloudcontrol.core.domain.model.CreatePrivateNetworkSpec
import com.hcloudcontrol.core.domain.model.CreatedPrivateNetwork
import com.hcloudcontrol.core.domain.model.PrivateNetworkSubnetSpec
import com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec
import com.hcloudcontrol.core.domain.model.LoadBalancerInventory
import com.hcloudcontrol.core.domain.model.LoadBalancerTypeInventory
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.model.CreatePlacementGroupSpec
import com.hcloudcontrol.core.domain.model.CreatedPlacementGroup
import com.hcloudcontrol.core.domain.model.PlacementGroupPatch
import com.hcloudcontrol.core.domain.model.EnableRescueResult
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.ResetRootPasswordResult
import com.hcloudcontrol.core.domain.model.ServerRebuildSpec
import com.hcloudcontrol.core.domain.model.RebuiltServerResult

/** Boundary for Hetzner Cloud data. Implementations must never expose the API token. */
interface HCloudRepository {
    suspend fun getServers(): List<Server>
    suspend fun getServer(serverId: Long): Server
    /** Permanently removes a server. UI callers must require an explicit typed confirmation. */
    suspend fun deleteServer(serverId: Long) {
        throw UnsupportedOperationException("Deleting servers is unavailable")
    }
    /** Preserve the provider's original asynchronous delete action when available. */
    suspend fun deleteServerWithAction(serverId: Long): CloudAction? {
        deleteServer(serverId)
        return null
    }
    /** Reinstalls the selected image. No implicit/default image is ever permitted. */
    suspend fun rebuildServer(serverId: Long, spec: ServerRebuildSpec): RebuiltServerResult {
        throw UnsupportedOperationException("Rebuilding servers is unavailable")
    }
    /** Partial normal configuration only: name, labels, placement group. */
    suspend fun updateServerConfiguration(serverId: Long, patch: ServerConfigurationPatch): Server
    suspend fun changeServerProtection(serverId: Long, patch: ServerProtectionPatch): CloudAction
    /** Paginated read-only history for one server. Individual nested action GET is deprecated. */
    suspend fun getServerActions(serverId: Long): List<CloudAction>
    suspend fun getServerTypes(): List<ServerTypeOption>
    /** Read-only type metadata, availability and exact API-returned prices. */
    suspend fun getServerType(serverTypeId: Long): ServerTypeDetails
    suspend fun getSystemImages(): List<ImageOption>
    suspend fun getBackupAndSnapshotImages(): List<BackupSnapshotImage>
    /** Full Cloud Image inventory. Mutations require the device-local high-impact capability in UI. */
    suspend fun getImages(): List<CloudImageInventory>
    suspend fun getImage(imageId: Long): CloudImageInventory
    /** Paginated, read-only action history for one Cloud Image. */
    suspend fun getImageActions(imageId: Long): List<CloudAction>
    /** Permanently deletes an Image. Callers must confirm its displayed description or name. */
    suspend fun deleteImage(imageId: Long)
    /** Changes the Image's deletion protection through the documented asynchronous action. */
    suspend fun changeImageProtection(imageId: Long, patch: ImageProtectionPatch): CloudAction
    suspend fun getIsos(): List<IsoOption>
    suspend fun getIso(isoId: Long): IsoOption
    /** Returns null when the API has no price for this exact type/location pair. */
    suspend fun getServerTypePrice(serverTypeId: Long, location: String): ServerTypeLocationPrice?
    suspend fun getVolumes(): List<VolumeInventory>
    /** Read-only Volume detail as returned by the Cloud API. */
    suspend fun getVolume(volumeId: Long): VolumeInventory
    /** Paginated, read-only action history scoped to one Volume. */
    suspend fun getVolumeActions(volumeId: Long): List<CloudAction>
    /** Creates an unattached, billable Volume at a named Cloud location. */
    suspend fun createVolume(spec: CreateVolumeSpec): CreatedVolume
    /** Deletes a Volume. Callers must make the data-loss confirmation explicit. */
    /** Null denotes the documented authoritative 204 response, not an invented Action. */
    suspend fun deleteVolume(volumeId: Long): CloudAction?
    /** Changes deletion protection through the documented asynchronous Volume action. */
    suspend fun changeVolumeProtection(volumeId: Long, patch: DeleteProtectionPatch): CloudAction {
        throw UnsupportedOperationException("Changing Volume protection is unavailable")
    }
    suspend fun getPrimaryIps(): List<PrimaryIpInventory>
    /** Creation is billable; callers must present an explicit reviewed confirmation. */
    suspend fun createPrimaryIp(spec: CreatePrimaryIpSpec): CreatedPrimaryIp
    /** Read-only Primary IP detail. */
    suspend fun getPrimaryIp(primaryIpId: Long): PrimaryIpInventory
    /** Paginated, read-only action history scoped to one Primary IP. */
    suspend fun getPrimaryIpActions(primaryIpId: Long): List<CloudAction>
    suspend fun getFloatingIps(): List<FloatingIpInventory>
    suspend fun getFloatingIp(floatingIpId: Long): FloatingIpInventory
    /** Paginated, read-only action history scoped to one Floating IP. */
    suspend fun getFloatingIpActions(floatingIpId: Long): List<CloudAction>
    /** Reassigns an existing Floating IP to an existing server; no IP creation is exposed. */
    suspend fun assignFloatingIp(floatingIpId: Long, serverId: Long): CloudAction
    suspend fun unassignFloatingIp(floatingIpId: Long): CloudAction
    /** Sets or explicitly clears a PTR record for a Floating IP address. */
    suspend fun changeFloatingIpReverseDns(floatingIpId: Long, ip: String, dnsPtr: String?): CloudAction
    /**
     * Permanently releases an unassigned Floating IP. This returns only after the official
     * 204 response; callers must refresh inventory and make data/billing confirmation explicit.
     */
    suspend fun deleteFloatingIp(floatingIpId: Long)
    /** Changes deletion protection through the documented asynchronous Floating IP action. */
    suspend fun changeFloatingIpProtection(floatingIpId: Long, patch: DeleteProtectionPatch): CloudAction {
        throw UnsupportedOperationException("Changing Floating IP protection is unavailable")
    }
    suspend fun getCertificates(): List<CertificateInventory>
    suspend fun getCertificate(certificateId: Long): CertificateInventory
    /** Paginated, read-only action history for one certificate. */
    suspend fun getCertificateActions(certificateId: Long): List<CloudAction>
    /** Permanently releases an unassigned, unprotected Primary IP. */
    suspend fun deletePrimaryIp(primaryIpId: Long)
    /** Assign/unassign existing Primary IPs. */
    suspend fun assignPrimaryIp(primaryIpId: Long, serverId: Long): CloudAction
    suspend fun unassignPrimaryIp(primaryIpId: Long): CloudAction
    /** Sets or clears a PTR record for an address belonging to this Primary IP. */
    suspend fun changePrimaryIpReverseDns(primaryIpId: Long, ip: String, dnsPtr: String?): CloudAction
    /** Changes deletion protection through the documented asynchronous Primary IP action. */
    suspend fun changePrimaryIpProtection(primaryIpId: Long, patch: DeleteProtectionPatch): CloudAction {
        throw UnsupportedOperationException("Changing Primary IP protection is unavailable")
    }
    suspend fun getFirewalls(): List<FirewallInventory>
    /** Read-only Firewall detail from GET /firewalls/{id}. */
    suspend fun getFirewall(firewallId: Long): FirewallInventory
    /** Paginated, read-only action history from GET /firewalls/{id}/actions. */
    suspend fun getFirewallActions(firewallId: Long): List<CloudAction>
    /** Creates a Firewall. Callers must clearly review that no inbound rules implies DROP. */
    suspend fun createFirewall(spec: CreateFirewallSpec): CreatedFirewall
    /** Permanently deletes a Firewall. Callers must require a typed confirmation. */
    suspend fun deleteFirewall(firewallId: Long)
    /** Replaces the whole Firewall ruleset via the documented asynchronous set_rules action. */
    suspend fun setFirewallRules(firewallId: Long, rules: List<FirewallRuleSpec>): CloudAction
    suspend fun applyFirewallToServer(firewallId: Long, serverId: Long): List<CloudAction>
    suspend fun removeFirewallFromServer(firewallId: Long, serverId: Long): List<CloudAction>
    suspend fun getPrivateNetworks(): List<PrivateNetworkInventory>
    /** Creates a private network. Callers must expose this only behind the local high-impact gate. */
    suspend fun createPrivateNetwork(spec: CreatePrivateNetworkSpec): CreatedPrivateNetwork {
        throw UnsupportedOperationException("Creating private networks is unavailable")
    }
    suspend fun getPrivateNetwork(networkId: Long): PrivateNetworkInventory
    suspend fun getPrivateNetworkMembers(networkId: Long): List<PrivateNetworkMember>
    suspend fun getPrivateNetworkActions(networkId: Long): List<CloudAction>
    /** Removes the entire private network and all of its topology. Require typed-name confirmation. */
    suspend fun deletePrivateNetwork(networkId: Long): CloudAction? {
        throw UnsupportedOperationException("Deleting private networks is unavailable")
    }
    /** Topology changes can interrupt traffic and must be individually reviewed. */
    suspend fun addPrivateNetworkSubnet(networkId: Long, spec: PrivateNetworkSubnetSpec): CloudAction {
        throw UnsupportedOperationException("Changing private network topology is unavailable")
    }
    suspend fun deletePrivateNetworkSubnet(networkId: Long, spec: PrivateNetworkSubnetSpec): CloudAction {
        throw UnsupportedOperationException("Changing private network topology is unavailable")
    }
    suspend fun addPrivateNetworkRoute(networkId: Long, spec: PrivateNetworkRouteSpec): CloudAction {
        throw UnsupportedOperationException("Changing private network topology is unavailable")
    }
    suspend fun deletePrivateNetworkRoute(networkId: Long, spec: PrivateNetworkRouteSpec): CloudAction {
        throw UnsupportedOperationException("Changing private network topology is unavailable")
    }
    /** Connects an existing Cloud server to an existing private network using API-managed addressing. */
    suspend fun attachServerToPrivateNetwork(networkId: Long, serverId: Long): CloudAction
    /** Removes an existing private-network interface from a Cloud server. */
    suspend fun detachServerFromPrivateNetwork(networkId: Long, serverId: Long): CloudAction
    /** Changes deletion protection through the documented asynchronous Network action. */
    suspend fun changePrivateNetworkProtection(networkId: Long, patch: DeleteProtectionPatch): CloudAction {
        throw UnsupportedOperationException("Changing private network protection is unavailable")
    }
    suspend fun getLoadBalancers(): List<LoadBalancerInventory>
    suspend fun getLoadBalancer(loadBalancerId: Long): LoadBalancerInventory
    /** Permanently removes a Load Balancer. UI callers must require a typed-name confirmation. */
    suspend fun deleteLoadBalancer(loadBalancerId: Long): CloudAction {
        throw UnsupportedOperationException("Deleting Load Balancers is unavailable")
    }
    /** Paginated read-only history for one Load Balancer. */
    suspend fun getLoadBalancerActions(loadBalancerId: Long): List<CloudAction>
    /** Read-only catalog from GET /load_balancer_types. */
    suspend fun getLoadBalancerTypes(): List<LoadBalancerTypeInventory>
    /** Read-only catalog entry from GET /load_balancer_types/{id}. */
    suspend fun getLoadBalancerType(loadBalancerTypeId: Long): LoadBalancerTypeInventory
    /** Changes deletion protection through the documented asynchronous Load Balancer action. */
    suspend fun changeLoadBalancerProtection(loadBalancerId: Long, patch: DeleteProtectionPatch): CloudAction {
        throw UnsupportedOperationException("Changing Load Balancer protection is unavailable")
    }
    suspend fun getPlacementGroups(): List<PlacementGroupInventory>
    suspend fun getPlacementGroup(placementGroupId: Long): PlacementGroupInventory
    /** Creates a billable-placement policy object; callers must present a reviewed confirmation. */
    suspend fun createPlacementGroup(spec: CreatePlacementGroupSpec): CreatedPlacementGroup {
        throw UnsupportedOperationException("Creating placement groups is unavailable")
    }
    /** Updates only name and/or the complete reviewed label set. */
    suspend fun updatePlacementGroup(placementGroupId: Long, patch: PlacementGroupPatch): PlacementGroupInventory {
        throw UnsupportedOperationException("Updating placement groups is unavailable")
    }
    /** Permanently deletes a Placement Group; callers must require a typed confirmation. */
    suspend fun deletePlacementGroup(placementGroupId: Long) {
        throw UnsupportedOperationException("Deleting placement groups is unavailable")
    }
    suspend fun addServerToPlacementGroup(serverId: Long, placementGroupId: Long): CloudAction
    suspend fun removeServerFromPlacementGroup(serverId: Long, placementGroupId: Long): CloudAction
    suspend fun attachVolume(volumeId: Long, serverId: Long): CloudAction
    suspend fun detachVolume(volumeId: Long): CloudAction
    /** Resizing is irreversible; callers must supply the currently known size and a larger target. */
    suspend fun resizeVolume(volumeId: Long, currentSizeGb: Int, newSizeGb: Int): CloudAction
    suspend fun getLocations(): List<LocationOption>
    /** Read-only location metadata. */
    suspend fun getLocation(locationId: Long): LocationDetails
    suspend fun getSshKeys(): List<SshKeyOption>
    /** Creates only a public SSH key. Private key material is never accepted or stored. */
    suspend fun createSshKey(spec: CreateSshKeySpec): SshKeyOption {
        throw UnsupportedOperationException("Creating SSH keys is unavailable")
    }
    /** Permanently removes an SSH key. Callers must require a typed key-name confirmation. */
    suspend fun deleteSshKey(sshKeyId: Long) {
        throw UnsupportedOperationException("Deleting SSH keys is unavailable")
    }
    suspend fun createServer(spec: CreateServerSpec): CreatedServer
    suspend fun changeServerType(serverId: Long, serverType: String, upgradeDisk: Boolean): CloudAction
    suspend fun getAction(actionId: Long): CloudAction
    suspend fun powerOn(serverId: Long): CloudAction
    suspend fun powerOff(serverId: Long): CloudAction
    suspend fun shutdown(serverId: Long): CloudAction
    suspend fun reboot(serverId: Long): CloudAction
    suspend fun reset(serverId: Long): CloudAction
    suspend fun attachIso(serverId: Long, iso: IsoSelection): CloudAction
    suspend fun detachIso(serverId: Long): CloudAction
    /** Sets a PTR record or clears it when dnsPtr is null; null is sent explicitly to the API. */
    suspend fun changeServerReverseDns(serverId: Long, ip: String, dnsPtr: String?): CloudAction
    /** One-time secret is in-memory only; caller must reveal it once and never persist it. */
    suspend fun resetRootPassword(serverId: Long): ResetRootPasswordResult
    suspend fun enableRescue(serverId: Long, spec: EnableRescueSpec = EnableRescueSpec()): EnableRescueResult
    suspend fun disableRescue(serverId: Long): CloudAction
    suspend fun enableBackups(serverId: Long): CloudAction
    suspend fun disableBackups(serverId: Long): CloudAction
    /** Creates a snapshot image only; no backup conversion or image deletion is exposed. */
    suspend fun createSnapshot(serverId: Long, description: String): CloudAction
}
