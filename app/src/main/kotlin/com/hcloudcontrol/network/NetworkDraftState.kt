package com.hcloudcontrol.network

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.core.domain.model.*

internal fun networkDraftSnapshot(scope: String, draft: NetworkMutationDraft?): ArrayList<String> = arrayListOf(
    "network-draft-v1", scope, draft?.kind?.name.orEmpty(), draft?.name.orEmpty(), draft?.range.orEmpty(),
    draft?.subnet?.ipRange.orEmpty(), draft?.subnet?.networkZone.orEmpty(), draft?.subnet?.gateway.orEmpty(),
    draft?.route?.destination.orEmpty(), draft?.route?.gateway.orEmpty(), draft?.serverId?.toString().orEmpty())

internal fun restoreNetworkDraft(scope: String, saved: List<String>): NetworkMutationDraft? {
    if (saved.size != 11 || saved[0] != "network-draft-v1" || saved[1] != scope || saved.any { it.length > 512 }) return null
    val kind = NetworkMutationKind.entries.firstOrNull { it.name == saved[2] } ?: return null
    val serverId = saved[10].takeIf { it.isNotEmpty() }?.toLongOrNull()
    if (saved[10].isNotEmpty() && (serverId == null || serverId <= 0)) return null
    val subnet = if (saved[5].isNotEmpty() && saved[6].isNotEmpty()) PrivateNetworkSubnetSpec(saved[5], saved[6], saved[7].ifEmpty { null }) else null
    val route = if (saved[8].isNotEmpty() && saved[9].isNotEmpty()) PrivateNetworkRouteSpec(saved[8], saved[9]) else null
    if (kind == NetworkMutationKind.DELETE_SUBNET && subnet == null) return null
    if (kind == NetworkMutationKind.DELETE_ROUTE && route == null) return null
    if (kind == NetworkMutationKind.DETACH && serverId == null) return null
    return NetworkMutationDraft(kind, saved[3], saved[4], subnet, route, serverId)
}

@Composable
internal fun rememberNetworkDraft(scope: String): MutableState<NetworkMutationDraft?> {
    val saver = remember(scope) { Saver<MutableState<NetworkMutationDraft?>, ArrayList<String>>(
        save = { networkDraftSnapshot(scope, it.value) }, restore = { mutableStateOf(restoreNetworkDraft(scope, it)) }) }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(null) }
}
