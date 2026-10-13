package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.Profile
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.repository.HCloudRepository

internal class RetrofitHCloudRepository(private val api: HCloudApi) : HCloudRepository {
    override suspend fun getProfile(): Profile = api.getProfile().profile.let {
        Profile(id = it.id, name = it.name, email = it.email)
    }

    override suspend fun getServers(): List<Server> = api.getServers().servers.map { server ->
        Server(
            id = server.id,
            name = server.name,
            status = server.status.toServerStatus(),
            ipv4Address = server.publicNet.ipv4?.ip,
            datacenter = server.datacenter.name,
            serverType = server.serverType.name,
        )
    }
}

/** Public construction boundary for the network module. */
object HCloudRepositoryFactory {
    fun create(tokenProvider: () -> String?): HCloudRepository =
        RetrofitHCloudRepository(HCloudApiFactory.create(tokenProvider))
}

private fun String.toServerStatus(): ServerStatus = when (lowercase()) {
    "initializing" -> ServerStatus.INITIALIZING
    "off" -> ServerStatus.OFF
    "running" -> ServerStatus.RUNNING
    "starting" -> ServerStatus.STARTING
    "stopping" -> ServerStatus.STOPPING
    else -> ServerStatus.UNKNOWN
}
