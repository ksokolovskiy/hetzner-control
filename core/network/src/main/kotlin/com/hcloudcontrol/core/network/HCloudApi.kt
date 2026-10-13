package com.hcloudcontrol.core.network

import retrofit2.http.GET

internal interface HCloudApi {
    @GET("profile")
    suspend fun getProfile(): ProfileResponse

    @GET("servers")
    suspend fun getServers(): ServersResponse
}
