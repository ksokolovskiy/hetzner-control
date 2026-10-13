package com.hcloudcontrol.core.network

import com.squareup.moshi.Json

internal data class ProfileResponse(val profile: ProfileDto)
internal data class ProfileDto(
    val id: Long,
    val name: String,
    val email: String,
)

internal data class ServersResponse(val servers: List<ServerDto>)
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
)
internal data class PublicNetDto(val ipv4: Ipv4Dto?)
internal data class Ipv4Dto(val ip: String?)
internal data class LocationDto(val name: String)
internal data class ServerTypeDto(val name: String)
