package com.hcloudcontrol.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT
import retrofit2.http.DELETE

internal interface HCloudApi {
    @GET("servers")
    suspend fun getServers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ServersResponse

    @GET("servers/{id}")
    suspend fun getServer(@Path("id") serverId: Long): ServerResponse

    @PUT("servers/{id}")
    suspend fun updateServerConfiguration(
        @Path("id") serverId: Long,
        @Body patch: ServerConfigurationPatchDto,
    ): ServerResponse

    @GET("servers/{id}/actions")
    suspend fun getServerActions(
        @Path("id") serverId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @GET("servers/{id}/metrics")
    suspend fun getServerMetrics(
        @Path("id") serverId: Long,
        @Query("type") types: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("step") step: String? = null,
    ): ServerMetricsResponse

    @GET("server_types")
    suspend fun getServerTypes(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ServerTypesResponse

    /** Official Cloud API: read-only catalog detail. */
    @GET("server_types/{id}")
    suspend fun getServerType(@Path("id") serverTypeId: Long): ServerTypeResponse

    @GET("images")
    suspend fun getImages(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ImagesResponse

    @GET("images/{id}")
    suspend fun getImage(@Path("id") imageId: Long): ImageResponse

    @GET("images/{id}/actions")
    suspend fun getImageActions(@Path("id") imageId: Long, @Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): ActionsResponse

    @DELETE("images/{id}")
    suspend fun deleteImage(@Path("id") imageId: Long)

    @POST("images/{id}/actions/change_protection")
    suspend fun changeImageProtection(
        @Path("id") imageId: Long,
        @Body patch: ImageProtectionPatchDto,
    ): ActionResponse

    @GET("isos")
    suspend fun getIsos(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): IsosResponse

    @GET("isos/{id}")
    suspend fun getIso(@Path("id") isoId: Long): IsoResponse

    @GET("locations")
    suspend fun getLocations(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): LocationsResponse

    /** Official Cloud API: read-only location detail. */
    @GET("locations/{id}")
    suspend fun getLocation(@Path("id") locationId: Long): LocationResponse

    @GET("ssh_keys")
    suspend fun getSshKeys(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): SshKeysResponse

    @POST("ssh_keys")
    suspend fun createSshKey(@Body request: CreateSshKeyRequestDto): SshKeyResponse

    @DELETE("ssh_keys/{id}")
    suspend fun deleteSshKey(@Path("id") sshKeyId: Long)

    @GET("pricing")
    suspend fun getPricing(): PricingResponse

    @GET("volumes")
    suspend fun getVolumes(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): VolumesResponse

    @GET("volumes/{id}")
    suspend fun getVolume(@Path("id") volumeId: Long): VolumeResponse

    @GET("volumes/{id}/actions")
    suspend fun getVolumeActions(
        @Path("id") volumeId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @POST("volumes")
    suspend fun createVolume(@Body request: CreateVolumeRequestDto): CreateVolumeResponse

    @DELETE("volumes/{id}")
    suspend fun deleteVolume(@Path("id") volumeId: Long): ActionResponse

    @POST("volumes/{id}/actions/change_protection")
    suspend fun changeVolumeProtection(@Path("id") volumeId: Long, @Body patch: DeleteProtectionPatchDto): ActionResponse

    @GET("primary_ips")
    suspend fun getPrimaryIps(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): PrimaryIpsResponse

    @POST("primary_ips")
    suspend fun createPrimaryIp(@Body request: CreatePrimaryIpRequestDto): CreatePrimaryIpResponse

    @GET("primary_ips/{id}")
    suspend fun getPrimaryIp(@Path("id") primaryIpId: Long): PrimaryIpResponse

    /** API returns 204. Hetzner rejects assigned or delete-protected IPs. */
    @DELETE("primary_ips/{id}")
    suspend fun deletePrimaryIp(@Path("id") primaryIpId: Long)

    @GET("primary_ips/{id}/actions")
    suspend fun getPrimaryIpActions(
        @Path("id") primaryIpId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @GET("floating_ips")
    suspend fun getFloatingIps(@Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): FloatingIpsResponse

    @GET("floating_ips/{id}")
    suspend fun getFloatingIp(@Path("id") floatingIpId: Long): FloatingIpResponse

    @GET("floating_ips/{id}/actions")
    suspend fun getFloatingIpActions(
        @Path("id") floatingIpId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @POST("floating_ips/{id}/actions/assign")
    suspend fun assignFloatingIp(
        @Path("id") floatingIpId: Long,
        @Body request: AssignFloatingIpRequestDto,
    ): ActionResponse

    @POST("floating_ips/{id}/actions/unassign")
    suspend fun unassignFloatingIp(@Path("id") floatingIpId: Long): ActionResponse

    @POST("floating_ips/{id}/actions/change_dns_ptr")
    suspend fun changeFloatingIpReverseDns(
        @Path("id") floatingIpId: Long,
        @Body request: ChangeDnsPtrRequestDto,
    ): ActionResponse

    @POST("floating_ips/{id}/actions/change_protection")
    suspend fun changeFloatingIpProtection(@Path("id") floatingIpId: Long, @Body patch: DeleteProtectionPatchDto): ActionResponse

    /**
     * Official Cloud API returns 204 No Content. An assigned Floating IP is rejected by
     * Hetzner with `must_be_unassigned`; callers must also enforce that invariant locally.
     */
    @DELETE("floating_ips/{id}")
    suspend fun deleteFloatingIp(@Path("id") floatingIpId: Long)

    @GET("certificates")
    suspend fun getCertificates(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): CertificatesResponse

    @GET("certificates/{id}")
    suspend fun getCertificate(@Path("id") certificateId: Long): CertificateResponse

    @GET("certificates/{id}/actions")
    suspend fun getCertificateActions(
        @Path("id") certificateId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @GET("firewalls")
    suspend fun getFirewalls(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): FirewallsResponse

    @GET("firewalls/{id}")
    suspend fun getFirewall(@Path("id") firewallId: Long): FirewallResponse

    @GET("firewalls/{id}/actions")
    suspend fun getFirewallActions(
        @Path("id") firewallId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @POST("firewalls")
    suspend fun createFirewall(@Body request: CreateFirewallRequestDto): CreatedFirewallResponse

    @DELETE("firewalls/{id}")
    suspend fun deleteFirewall(@Path("id") firewallId: Long)

    @POST("firewalls/{id}/actions/set_rules")
    suspend fun setFirewallRules(@Path("id") firewallId: Long, @Body request: SetFirewallRulesRequestDto): ActionResponse

    @POST("firewalls/{id}/actions/apply_to_resources")
    suspend fun applyFirewallToResources(@Path("id") firewallId: Long, @Body request: FirewallResourcesRequestDto): ActionsResponse

    @POST("firewalls/{id}/actions/remove_from_resources")
    suspend fun removeFirewallFromResources(@Path("id") firewallId: Long, @Body request: FirewallRemoveResourcesRequestDto): ActionsResponse

    @GET("networks")
    suspend fun getNetworks(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): NetworksResponse

    @GET("networks/{id}")
    suspend fun getNetwork(@Path("id") networkId: Long): NetworkResponse

    @GET("networks/{id}/members")
    suspend fun getNetworkMembers(
        @Path("id") networkId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): NetworkMembersResponse

    @GET("networks/{id}/actions")
    suspend fun getNetworkActions(@Path("id") networkId: Long, @Query("page") page: Int = 1, @Query("per_page") perPage: Int = 50): ActionsResponse

    @POST("networks")
    suspend fun createNetwork(@Body request: CreateNetworkRequestDto): CreateNetworkResponse

    /** API returns the asynchronous action rather than a bare 204. */
    @DELETE("networks/{id}")
    suspend fun deleteNetwork(@Path("id") networkId: Long): ActionResponse

    @POST("networks/{id}/actions/add_subnet")
    suspend fun addNetworkSubnet(@Path("id") networkId: Long, @Body request: NetworkSubnetRequestDto): ActionResponse

    @POST("networks/{id}/actions/delete_subnet")
    suspend fun deleteNetworkSubnet(@Path("id") networkId: Long, @Body request: NetworkSubnetRequestDto): ActionResponse

    @POST("networks/{id}/actions/add_route")
    suspend fun addNetworkRoute(@Path("id") networkId: Long, @Body request: NetworkRouteRequestDto): ActionResponse

    @POST("networks/{id}/actions/delete_route")
    suspend fun deleteNetworkRoute(@Path("id") networkId: Long, @Body request: NetworkRouteRequestDto): ActionResponse

    @POST("networks/{id}/actions/change_protection")
    suspend fun changeNetworkProtection(@Path("id") networkId: Long, @Body patch: DeleteProtectionPatchDto): ActionResponse

    @POST("servers/{id}/actions/attach_to_network")
    suspend fun attachServerToNetwork(
        @Path("id") serverId: Long,
        @Body request: AttachServerToNetworkRequestDto,
    ): ActionResponse

    @POST("servers/{id}/actions/detach_from_network")
    suspend fun detachServerFromNetwork(
        @Path("id") serverId: Long,
        @Body request: DetachServerFromNetworkRequestDto,
    ): ActionResponse

    @GET("load_balancers")
    suspend fun getLoadBalancers(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): LoadBalancersResponse

    @GET("load_balancers/{id}")
    suspend fun getLoadBalancer(@Path("id") loadBalancerId: Long): LoadBalancerResponse

    /**
     * Hetzner Cloud now tracks deletion as an asynchronous action. The caller must poll the
     * returned action rather than assuming that the Load Balancer has already disappeared.
     */
    @DELETE("load_balancers/{id}")
    suspend fun deleteLoadBalancer(@Path("id") loadBalancerId: Long): ActionResponse

    @GET("load_balancers/{id}/actions")
    suspend fun getLoadBalancerActions(
        @Path("id") loadBalancerId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): ActionsResponse

    @GET("load_balancer_types")
    suspend fun getLoadBalancerTypes(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): LoadBalancerTypesResponse

    @GET("load_balancer_types/{id}")
    suspend fun getLoadBalancerType(@Path("id") loadBalancerTypeId: Long): LoadBalancerTypeResponse

    @GET("load_balancers/{id}/metrics")
    suspend fun getLoadBalancerMetrics(
        @Path("id") loadBalancerId: Long,
        @Query("type") types: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("step") step: String? = null,
    ): LoadBalancerMetricsResponse

    @POST("load_balancers/{id}/actions/change_protection")
    suspend fun changeLoadBalancerProtection(@Path("id") loadBalancerId: Long, @Body patch: DeleteProtectionPatchDto): ActionResponse

    @GET("placement_groups")
    suspend fun getPlacementGroups(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): PlacementGroupsResponse

    @POST("placement_groups")
    suspend fun createPlacementGroup(@Body request: CreatePlacementGroupRequestDto): CreatePlacementGroupResponse

    @GET("placement_groups/{id}")
    suspend fun getPlacementGroup(@Path("id") placementGroupId: Long): PlacementGroupResponse

    @PUT("placement_groups/{id}")
    suspend fun updatePlacementGroup(
        @Path("id") placementGroupId: Long,
        @Body request: UpdatePlacementGroupRequestDto,
    ): PlacementGroupResponse

    @DELETE("placement_groups/{id}")
    suspend fun deletePlacementGroup(@Path("id") placementGroupId: Long)

    @POST("servers/{id}/actions/add_to_placement_group")
    suspend fun addServerToPlacementGroup(
        @Path("id") serverId: Long,
        @Body request: PlacementGroupRequestDto,
    ): ActionResponse

    @POST("servers/{id}/actions/remove_from_placement_group")
    suspend fun removeServerFromPlacementGroup(
        @Path("id") serverId: Long,
        @Body request: PlacementGroupRequestDto,
    ): ActionResponse

    @POST("volumes/{id}/actions/attach")
    suspend fun attachVolume(
        @Path("id") volumeId: Long,
        @Body request: AttachVolumeRequestDto,
    ): ActionResponse

    @POST("volumes/{id}/actions/detach")
    suspend fun detachVolume(@Path("id") volumeId: Long): ActionResponse

    @POST("volumes/{id}/actions/resize")
    suspend fun resizeVolume(
        @Path("id") volumeId: Long,
        @Body request: ResizeVolumeRequestDto,
    ): ActionResponse

    @POST("primary_ips/{id}/actions/assign")
    suspend fun assignPrimaryIp(@Path("id") primaryIpId: Long, @Body request: AssignPrimaryIpRequestDto): ActionResponse

    @POST("primary_ips/{id}/actions/unassign")
    suspend fun unassignPrimaryIp(@Path("id") primaryIpId: Long): ActionResponse

    @POST("primary_ips/{id}/actions/change_dns_ptr")
    suspend fun changePrimaryIpReverseDns(@Path("id") primaryIpId: Long, @Body request: ChangeDnsPtrRequestDto): ActionResponse

    @POST("primary_ips/{id}/actions/change_protection")
    suspend fun changePrimaryIpProtection(@Path("id") primaryIpId: Long, @Body patch: DeleteProtectionPatchDto): ActionResponse

    @POST("servers")
    suspend fun createServer(@Body request: CreateServerRequestDto): CreateServerResponse

    @DELETE("servers/{id}")
    suspend fun deleteServer(@Path("id") serverId: Long)

    @POST("servers/{id}/actions/rebuild")
    suspend fun rebuildServer(
        @Path("id") serverId: Long,
        @Body request: RebuildServerRequestDto,
    ): RootPasswordActionResponse

    @POST("servers/{id}/actions/change_type")
    suspend fun changeServerType(
        @Path("id") serverId: Long,
        @Body request: ChangeServerTypeRequestDto,
    ): ActionResponse

    @POST("servers/{id}/actions/poweron")
    suspend fun powerOn(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/poweroff")
    suspend fun powerOff(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/shutdown")
    suspend fun shutdown(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/reboot")
    suspend fun reboot(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/reset")
    suspend fun reset(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/attach_iso")
    suspend fun attachIso(@Path("id") serverId: Long, @Body request: AttachIsoRequestDto): ActionResponse

    @POST("servers/{id}/actions/detach_iso")
    suspend fun detachIso(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/change_dns_ptr")
    suspend fun changeServerReverseDns(
        @Path("id") serverId: Long,
        @Body request: ServerDnsPtrRequestDto,
    ): ActionResponse

    @POST("servers/{id}/actions/change_protection")
    suspend fun changeServerProtection(
        @Path("id") serverId: Long,
        @Body request: ServerProtectionPatchDto,
    ): ActionResponse

    @POST("servers/{id}/actions/reset_password")
    suspend fun resetRootPassword(@Path("id") serverId: Long): RootPasswordActionResponse

    @POST("servers/{id}/actions/enable_rescue")
    suspend fun enableRescue(
        @Path("id") serverId: Long,
        @Body request: EnableRescueRequestDto,
    ): RootPasswordActionResponse

    @POST("servers/{id}/actions/disable_rescue")
    suspend fun disableRescue(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/enable_backup")
    suspend fun enableBackups(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/disable_backup")
    suspend fun disableBackups(@Path("id") serverId: Long): ActionResponse

    @POST("servers/{id}/actions/create_image")
    suspend fun createImage(
        @Path("id") serverId: Long,
        @Body request: CreateImageRequestDto,
    ): ActionResponse

    @GET("actions/{id}")
    suspend fun getAction(@Path("id") actionId: Long): ActionResponse
}
