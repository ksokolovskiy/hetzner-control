package com.hcloudcontrol.core.domain.model

data class Server(
    val id: Long,
    val name: String,
    val status: ServerStatus,
    val ipv4Address: String?,
    val location: String,
    val serverType: String,
)

enum class ServerStatus { INITIALIZING, OFF, RUNNING, STARTING, STOPPING, UNKNOWN }
