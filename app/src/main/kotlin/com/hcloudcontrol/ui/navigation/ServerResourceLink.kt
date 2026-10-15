package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

enum class ServerLinkedResource { PRIMARY_IP, FLOATING_IP, PRIVATE_NETWORK, VOLUME }
data class ServerResourceLink(val type: ServerLinkedResource, val id: Long) {
    init { require(id > 0) }
    fun snapshot(scope: String): ArrayList<String> = arrayListOf("server-link-v1", scope, type.name, id.toString())
    companion object {
        fun restore(scope: String, saved: List<String>): ServerResourceLink? = runCatching {
            require(saved.size == 4 && saved[0] == "server-link-v1" && saved[1] == scope)
            ServerResourceLink(ServerLinkedResource.valueOf(saved[2]), saved[3].toLong())
        }.getOrNull()
    }
}

@Composable
fun rememberServerResourceLink(scope: String): MutableState<ServerResourceLink?> = rememberSaveable(scope,
    stateSaver = Saver<ServerResourceLink?, ArrayList<String>>(
        save = { it?.snapshot(scope) ?: arrayListOf("server-link-empty", scope) },
        restore = { ServerResourceLink.restore(scope, it) },
    )) { mutableStateOf(null) }
