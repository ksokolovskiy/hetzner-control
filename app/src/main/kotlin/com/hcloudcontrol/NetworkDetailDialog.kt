package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.model.PrivateNetworkMember
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private sealed interface NetworkDetailState { data object Loading : NetworkDetailState; data class Content(val network: PrivateNetworkInventory, val members: List<PrivateNetworkMember>, val actions: List<com.hcloudcontrol.core.domain.model.CloudAction>) : NetworkDetailState; data object Error : NetworkDetailState }

@Composable
internal fun NetworkDetailDialog(network: PrivateNetworkInventory, repository: HCloudRepository, onDismiss: () -> Unit) {
    var state by remember(network.id) { mutableStateOf<NetworkDetailState>(NetworkDetailState.Loading) }
    var refreshKey by remember(network.id) { mutableStateOf(0) }
    LaunchedEffect(network.id, refreshKey) { coroutineScope {
        val detail = async(Dispatchers.IO) { runCatching { repository.getPrivateNetwork(network.id) } }
        val members = async(Dispatchers.IO) { runCatching { repository.getPrivateNetworkMembers(network.id) } }
        val actions = async(Dispatchers.IO) { runCatching { repository.getPrivateNetworkActions(network.id) } }
        val detailResult = detail.await(); val memberResult = members.await(); val actionResult = actions.await()
        state = if (detailResult.isSuccess && memberResult.isSuccess && actionResult.isSuccess) NetworkDetailState.Content(detailResult.getOrThrow(), memberResult.getOrThrow(), actionResult.getOrThrow()) else NetworkDetailState.Error
    } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.network_detail_title, network.name)) }, text = {
        when (val current = state) {
            NetworkDetailState.Loading -> CircularProgressIndicator()
            NetworkDetailState.Error -> Text(stringResource(R.string.network_detail_error), color = MaterialTheme.colorScheme.error)
            is NetworkDetailState.Content -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.network_cidr, current.network.ipRange));
                ResourceProtectionControls(
                    resourceName = current.network.name,
                    deleteProtected = current.network.deleteProtected,
                    repository = repository,
                    onChange = { patch -> repository.changePrivateNetworkProtection(current.network.id, patch) },
                    onCompleted = { refreshKey++ },
                )
                Text(stringResource(R.string.network_detail_topology), style = MaterialTheme.typography.titleMedium)
                if (current.members.isEmpty()) Text(stringResource(R.string.network_detail_members_empty))
                current.members.sortedWith(compareBy<PrivateNetworkMember> { it.type }.thenBy { it.id }).forEach { member ->
                    Text(stringResource(R.string.network_detail_member, member.type, member.id, member.ip, member.subnet, member.status))
                    if (member.aliasIps.isNotEmpty()) Text(stringResource(R.string.network_detail_aliases, member.aliasIps.joinToString()), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.network_actions_title), style = MaterialTheme.typography.titleMedium)
                if (current.actions.isEmpty()) Text(stringResource(R.string.network_actions_empty))
                current.actions.sortedByDescending { it.id }.forEach { action -> Text("${action.command ?: stringResource(R.string.network_actions_unknown)} · ${action.status}") }
                Text(stringResource(R.string.network_detail_read_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } })
}
