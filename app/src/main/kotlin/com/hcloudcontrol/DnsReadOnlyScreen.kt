package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.DnsRrSet
import com.hcloudcontrol.core.domain.model.DnsZone
import com.hcloudcontrol.core.domain.repository.DnsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cloud DNS is deliberately a separate, acknowledgement-gated read-only view.
 * This file depends on [DnsRepository], whose interface has no mutation methods.
 */
@Composable
internal fun DnsReadOnlyScreen(projectId: String, repository: DnsRepository) {
    var acknowledged by remember(projectId) { mutableStateOf(false) }
    var selectedZone by remember(projectId) { mutableStateOf<DnsZone?>(null) }
    BackHandler(enabled = selectedZone != null) { selectedZone = null }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    var state by remember(projectId) { mutableStateOf<DnsZonesState>(DnsZonesState.Idle) }
    val scope = rememberCoroutineScope()
    val loadFailure = stringResource(R.string.dns_zones_load_error)

    fun loadZones() {
        state = DnsZonesState.Loading
        scope.launch {
            state = try {
                DnsZonesState.Content(withContext(Dispatchers.IO) { repository.getZones() })
            } catch (error: Throwable) {
                DnsZonesState.Error(safeApiFailureMessage(error, loadFailure))
            }
        }
    }

    LaunchedEffect(projectId, refreshKey, acknowledged) {
        if (acknowledged) loadZones()
    }

    selectedZone?.let { zone ->
        DnsZoneDetailScreen(
            zone = zone,
            repository = repository,
            onBack = { selectedZone = null },
        )
        return
    }

    LazyColumn(
        state = rememberRestorableLazyListState("dns-zones"),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.dns_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.dns_read_only_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!acknowledged) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = false, onCheckedChange = { acknowledged = it })
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Column {
                            Text(stringResource(R.string.dns_acknowledge_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.dns_acknowledge_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else when (val current = state) {
            DnsZonesState.Idle, DnsZonesState.Loading -> item { DnsLoading(stringResource(R.string.dns_zones_loading)) }
            is DnsZonesState.Error -> item {
                DnsErrorState(current.message) { refreshKey++ }
            }
            is DnsZonesState.Content -> {
                if (current.zones.isEmpty()) {
                    item { DnsEmptyState(stringResource(R.string.dns_zones_empty)) { refreshKey++ } }
                } else {
                    item {
                        Text(
                            stringResource(R.string.dns_pagination_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(current.zones.sortedWith(compareBy<DnsZone> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { zone ->
                        DnsZoneCard(zone) { selectedZone = zone }
                    }
                }
            }
        }
    }
}

private sealed interface DnsZonesState {
    data object Idle : DnsZonesState
    data object Loading : DnsZonesState
    data class Content(val zones: List<DnsZone>) : DnsZonesState
    data class Error(val message: String) : DnsZonesState
}

private sealed interface DnsZoneDetailState {
    data object Loading : DnsZoneDetailState
    data class Content(val zone: DnsZone, val rrsets: List<DnsRrSet>) : DnsZoneDetailState
    data class Error(val message: String) : DnsZoneDetailState
}

@Composable
private fun DnsZoneCard(zone: DnsZone, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(zone.name, style = MaterialTheme.typography.titleMedium)
        zone.mode?.let { Text(stringResource(R.string.dns_mode, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        zone.ttl?.let { Text(stringResource(R.string.dns_ttl, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        DnsLabels(zone.labels)
    }
}

@Composable
private fun DnsZoneDetailScreen(zone: DnsZone, repository: DnsRepository, onBack: () -> Unit) {
    var refreshKey by remember(zone.id) { mutableStateOf(0) }
    var state by remember(zone.id) { mutableStateOf<DnsZoneDetailState>(DnsZoneDetailState.Loading) }
    val loadFailure = stringResource(R.string.dns_zone_load_error)
    LaunchedEffect(zone.id, refreshKey) {
        state = DnsZoneDetailState.Loading
        state = try {
            coroutineScope {
                val detail = async(Dispatchers.IO) { repository.getZone(zone.id.toString()) }
                val rrsets = async(Dispatchers.IO) { repository.getRrSets(zone.id.toString()) }
                DnsZoneDetailState.Content(detail.await(), rrsets.await())
            }
        } catch (_: Throwable) {
            DnsZoneDetailState.Error(loadFailure)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { Text(zone.name, style = MaterialTheme.typography.headlineMedium) }
        when (val current = state) {
            DnsZoneDetailState.Loading -> item { DnsLoading(stringResource(R.string.dns_zone_loading)) }
            is DnsZoneDetailState.Error -> item { DnsErrorState(current.message) { refreshKey++ } }
            is DnsZoneDetailState.Content -> {
                item { DnsZoneDetailCard(current.zone) }
                item { Text(stringResource(R.string.dns_rrsets_title), style = MaterialTheme.typography.titleLarge) }
                if (current.rrsets.isEmpty()) {
                    item { DnsEmptyState(stringResource(R.string.dns_rrsets_empty)) { refreshKey++ } }
                } else {
                    items(current.rrsets.sortedWith(compareBy<DnsRrSet> { it.name.lowercase() }.thenBy { it.type }), key = { "${it.name}/${it.type}" }) { rrset ->
                        DnsRrSetCard(rrset)
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsZoneDetailCard(zone: DnsZone) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(stringResource(R.string.dns_zone_details), style = MaterialTheme.typography.titleMedium)
        DnsDetailRow(stringResource(R.string.dns_zone_id), zone.id.toString())
        zone.mode?.let { DnsDetailRow(stringResource(R.string.dns_zone_mode), it) }
        zone.ttl?.let { DnsDetailRow(stringResource(R.string.dns_zone_ttl), it.toString()) }
        DnsLabels(zone.labels)
    }
}

@Composable
private fun DnsRrSetCard(rrset: DnsRrSet) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("${rrset.name} · ${rrset.type}", style = MaterialTheme.typography.titleSmall)
        rrset.ttl?.let { DnsDetailRow(stringResource(R.string.dns_rrset_ttl), it.toString()) }
        if (rrset.records.isEmpty()) {
            Text(stringResource(R.string.dns_records_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rrset.records.forEach { record ->
                Text(record.value, style = MaterialTheme.typography.bodyMedium)
                record.comment?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DnsLabels(rrset.labels)
    }
}

@Composable
private fun DnsLabels(labels: Map<String, String>) {
    if (labels.isNotEmpty()) {
        Text(
            stringResource(R.string.common_labels_format, labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DnsLoading(message: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    androidx.compose.material3.CircularProgressIndicator(Modifier.padding(2.dp))
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DnsDetailRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}

@Composable
private fun DnsErrorState(message: String, retry: () -> Unit) {
    Text(message, color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = retry) { Text(stringResource(R.string.common_retry)) }
}

@Composable
private fun DnsEmptyState(message: String, retry: () -> Unit) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = retry) { Text(stringResource(R.string.common_retry)) }
}
