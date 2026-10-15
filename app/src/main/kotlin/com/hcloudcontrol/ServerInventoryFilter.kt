package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerStatus

internal enum class ServerInventoryFilter { ALL, RUNNING, OFF }

/** One localized status vocabulary for inventory, details and operation review. */
internal fun serverStatusLabelResource(status: ServerStatus): Int = when (status) {
    ServerStatus.INITIALIZING -> R.string.inventory_status_initializing
    ServerStatus.OFF -> R.string.inventory_status_off
    ServerStatus.RUNNING -> R.string.inventory_status_running
    ServerStatus.STARTING -> R.string.inventory_status_starting
    ServerStatus.STOPPING -> R.string.inventory_status_stopping
    ServerStatus.UNKNOWN -> R.string.inventory_status_unknown
}

/** The same local filter drives displayed rows and the no-results state. */
internal fun filterServers(servers: List<Server>, query: String, filter: ServerInventoryFilter): List<Server> {
    val search = query.trim()
    return servers.asSequence().filter { server ->
        when (filter) {
            ServerInventoryFilter.ALL -> true
            ServerInventoryFilter.RUNNING -> server.status == ServerStatus.RUNNING
            ServerInventoryFilter.OFF -> server.status == ServerStatus.OFF
        }
    }.filter { server ->
        search.isEmpty() || server.name.contains(search, ignoreCase = true) ||
            server.ipv4Address.orEmpty().contains(search, ignoreCase = true) ||
            server.networkDevices.ipv6Network.orEmpty().contains(search, ignoreCase = true) ||
            server.labels.any { (key, value) -> "$key=$value".contains(search, ignoreCase = true) }
    }.sortedWith(compareBy<Server> { it.name.lowercase() }.thenBy { it.id }).toList()
}
