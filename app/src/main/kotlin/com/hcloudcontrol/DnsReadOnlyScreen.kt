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
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import com.hcloudcontrol.ui.navigation.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val scopeKey = "dns:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    var acknowledged by remember(scopeKey) { mutableStateOf(false) }
    var selectedZone by remember(scopeKey) { mutableStateOf<DnsZone?>(null) }
    var query by rememberSaveable(scopeKey) { mutableStateOf("") }
    BackHandler(enabled = LocalNavigationChrome.current == null && selectedZone != null) { selectedZone = null }
    // The loader is not composed until consent: even its initial automatic read is gated.
    val loader = if (acknowledged) rememberSectionLoader(scopeKey, isEmpty = { it: List<DnsZone> -> it.isEmpty() }) {
        readAcknowledgedDnsZones(acknowledged, repository)
    } else null

    selectedZone?.let { zone ->
        RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.DNS_ZONE, zone.id.toString(), zone.name) { selectedZone = null }
        DnsZoneDetailScreen(
            zone = zone,
            repository = repository,
            onBack = { selectedZone = null },
        )
        return
    }

    HCloudPullToRefresh(
        isRefreshing = loader?.state?.refreshing == true,
        onRefresh = { loader?.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = rememberRestorableLazyListState(scopeKey, contentReady = loader?.state?.data != null),
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
        } else {
            item {
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(checkNotNull(loader)) {}
            }
            val zones = loader?.state?.data.orEmpty()
            val filtered = zones.filter { query.isBlank() || it.name.contains(query.trim(), true) }
            if (zones.isNotEmpty() && filtered.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(filtered.sortedWith(compareBy<DnsZone> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { zone ->
                HCloudResourceCard(ResourceCardModel("dns-zone:${zone.id}", zone.name, zone.mode.orEmpty(),
                    zone.ttl?.let { stringResource(R.string.dns_ttl, it) }.orEmpty()), { selectedZone = zone })
            }
        }
    }
    }
}

@Composable
private fun DnsZoneDetailScreen(zone: DnsZone, repository: DnsRepository, onBack: () -> Unit) {
    val scopeKey = "dns-zone:${LocalNavigationContext.current}:${zone.id}"
    val metadata = rememberSectionLoader(scopeKey + ":metadata") { repository.getZone(zone.id.toString()) }
    val records = rememberSectionLoader(scopeKey + ":records") { repository.getRrSets(zone.id.toString()) }
    var query by rememberSaveable(scopeKey) { mutableStateOf("") }
    var selected by rememberDnsRrSetIdentity(scopeKey)
    val closeRecord = { selected = null }
    BackHandler(enabled = LocalNavigationChrome.current == null && selected != null) { closeRecord() }
    if (selected != null) {
        DnsRrSetValueScreen(zone, checkNotNull(selected).name, checkNotNull(selected).type, repository, closeRecord)
        return
    }
    HCloudPullToRefresh(metadata.state.refreshing || records.state.refreshing, { metadata.refresh(); records.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scopeKey, contentReady = metadata.state.data != null && records.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { Text(zone.name, style = MaterialTheme.typography.titleLarge) }
            item { HCloudReadSection(metadata) { DnsZoneDetailCard(it) } }
            item {
                Text(stringResource(R.string.dns_rrsets_title), style = MaterialTheme.typography.titleMedium)
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(records) {}
            }
            val all = records.state.data.orEmpty()
            val filtered = all.filter { dnsRrSetMatches(it, query) }
            if (records.state.hasSuccessfulResponse && all.isEmpty()) item {
                HCloudSectionState(SectionPresentation(SectionKind.EMPTY, message = stringResource(R.string.dns_rrsets_empty)))
            } else if (all.isNotEmpty() && filtered.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(filtered.sortedWith(compareBy<DnsRrSet> { it.name.lowercase() }.thenBy { it.type }), key = { "${it.name}/${it.type}" }) { rrset ->
                HCloudResourceCard(ResourceCardModel("${zone.id}:${rrset.name}:${rrset.type}", "${rrset.name} · ${rrset.type}",
                    rrset.ttl?.let { stringResource(R.string.dns_ttl, it) }.orEmpty(),
                    rrset.records.firstOrNull()?.value ?: stringResource(R.string.dns_records_empty)), {
                    selected = DnsRrSetIdentity(rrset.name, rrset.type)
                })
            }
        }
    }
}

@Composable
private fun DnsRrSetValueScreen(zone: DnsZone, name: String, type: String, repository: DnsRepository, onBack: () -> Unit) {
    val scopeKey = "dns-value:${LocalNavigationContext.current}:${zone.id}:$name:$type"
    val loader = rememberSectionLoader(scopeKey) { repository.getRrSet(zone.id.toString(), name, type) }
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    var copyFailure by remember(scopeKey) { mutableStateOf(false) }
    val title = stringResource(R.string.dns_value_detail_title)
    RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.DNS_ZONE, zone.id.toString(), title, onBack)
    HCloudPullToRefresh(loader.state.refreshing, loader::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scopeKey, contentReady = loader.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { Text(zone.name, style = MaterialTheme.typography.titleMedium) }
            item { HCloudReadSection(loader) { rrset ->
                Text("${rrset.name} · ${rrset.type}", style = MaterialTheme.typography.titleLarge)
                rrset.ttl?.let { DnsDetailRow(stringResource(R.string.dns_rrset_ttl), it.toString()) }
                if (rrset.records.isEmpty()) Text(stringResource(R.string.dns_records_empty))
                rrset.records.forEach { record ->
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(record.value, style = MaterialTheme.typography.bodyLarge) }
                    record.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = {
                        copyFailure = runCatching {
                            val clipboard = androidContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DNS", record.value))
                        }.isFailure
                    }) { Text(stringResource(R.string.dns_copy_value)) }
                }
                DnsLabels(rrset.labels)
            } }
            if (copyFailure) item { Text(stringResource(R.string.dns_copy_failed), color = MaterialTheme.colorScheme.error) }
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
private fun DnsDetailRow(label: String, value: String) = Column(Modifier.fillMaxWidth()) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}
