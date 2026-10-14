package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.Server

/**
 * Read-only metadata already included in the documented Server response. It makes no additional
 * API calls and deliberately provides no attach/detach/reconfigure actions.
 */
@Composable
internal fun ServerNetworkDeviceSection(server: Server) {
    val details = server.networkDevices
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_network_device_title), style = MaterialTheme.typography.titleLarge)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.server_network_device_public), style = MaterialTheme.typography.titleMedium)
                ServerNetworkDeviceLine(R.string.server_network_device_ipv4, details.ipv4Address)
                ServerNetworkDeviceLine(R.string.server_network_device_ipv6, details.ipv6Network)
                ServerNetworkDeviceIds(R.string.server_network_device_primary_ips, details.primaryIpIds)
                ServerNetworkDeviceIds(R.string.server_network_device_floating_ips, details.floatingIpIds)

                Text(stringResource(R.string.server_network_device_private), style = MaterialTheme.typography.titleMedium)
                if (details.privateInterfaces.isEmpty()) {
                    Text(stringResource(R.string.server_network_device_private_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else details.privateInterfaces.sortedBy { it.networkId }.forEach { network ->
                    Text(stringResource(R.string.server_network_device_private_entry, network.networkId, network.ip))
                    if (network.aliasIps.isNotEmpty()) {
                        Text(stringResource(R.string.server_network_device_aliases, network.aliasIps.sorted().joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    network.macAddress?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.server_network_device_mac, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(stringResource(R.string.server_network_device_volumes), style = MaterialTheme.typography.titleMedium)
                if (details.attachedVolumeIds.isEmpty()) Text(stringResource(R.string.server_network_device_volumes_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Text(stringResource(R.string.server_network_device_volume_ids, details.attachedVolumeIds.sorted().joinToString()))
                if (details.deviceMaps.isEmpty()) Text(stringResource(R.string.server_network_device_maps_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else details.deviceMaps.sortedBy { it.volumeId }.forEach { map ->
                    Text(stringResource(R.string.server_network_device_map, map.volumeId, map.device), style = MaterialTheme.typography.bodySmall)
                }

                Text(stringResource(R.string.server_network_device_iso), style = MaterialTheme.typography.titleMedium)
                server.attachedIso?.let { iso ->
                    Text(stringResource(R.string.server_network_device_iso_entry, iso.id, iso.name))
                    listOfNotNull(iso.type, iso.architecture).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    iso.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } ?: Text(stringResource(R.string.server_network_device_iso_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.server_network_device_read_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ServerNetworkDeviceLine(labelRes: Int, value: String?) = Text(
    stringResource(labelRes, value ?: stringResource(R.string.common_none)),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ServerNetworkDeviceIds(labelRes: Int, ids: List<Long>) = Text(
    stringResource(labelRes, ids.sorted().joinToString().ifBlank { stringResource(R.string.common_none) }),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
