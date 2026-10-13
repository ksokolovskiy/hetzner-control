package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.Profile
import com.hcloudcontrol.core.domain.model.Server

/** Boundary for Hetzner Cloud data. Implementations must never expose the API token. */
interface HCloudRepository {
    suspend fun getProfile(): Profile
    suspend fun getServers(): List<Server>
}
