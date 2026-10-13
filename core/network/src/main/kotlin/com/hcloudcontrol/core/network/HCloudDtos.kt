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
    val datacenter: DatacenterDto,
    @Json(name = "server_type") val serverType: ServerTypeDto,
)
internal data class PublicNetDto(val ipv4: Ipv4Dto?)
internal data class Ipv4Dto(val ip: String?)
internal data class DatacenterDto(val name: String)
internal data class ServerTypeDto(val name: String)
