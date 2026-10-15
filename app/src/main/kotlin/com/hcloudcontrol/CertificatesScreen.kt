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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CertificateInventory
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Inventory-only certificate view. No PEM, private key, import, renewal or deletion operation exists here. */
@Composable
fun CertificatesScreen(repository: HCloudRepository, projectId: String) {
    val scope = "certificates:$projectId:${com.hcloudcontrol.ui.navigation.LocalNavigationContext.current?.endpoint.orEmpty()}"
    val loader = com.hcloudcontrol.ui.state.rememberSectionLoader(scope, isEmpty = { it: List<CertificateInventory> -> it.isEmpty() }) { repository.getCertificates() }
    var query by androidx.compose.runtime.saveable.rememberSaveable(scope) { mutableStateOf("") }
    var selectedId by com.hcloudcontrol.ui.navigation.rememberContextualResourceId(scope)
    BackHandler(enabled = com.hcloudcontrol.ui.navigation.LocalNavigationChrome.current == null && selectedId != null) { selectedId = null }
    val selected = loader.state.data?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        com.hcloudcontrol.ui.navigation.RegisterResourceChildNavigation(
            com.hcloudcontrol.ui.navigation.RootDestination.MORE, com.hcloudcontrol.ui.navigation.ResourceType.CERTIFICATE,
            selected.id.toString(), selected.name) { selectedId = null }
        CertificateDetailScreen(selected, repository) { selectedId = null }
        return
    }
    HCloudPullToRefresh(loader.state.refreshing, loader::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = loader.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.certificates_title), style = MaterialTheme.typography.titleLarge)
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(loader) {}
            }
            if (selectedId != null && loader.state.hasSuccessfulResponse) item {
                HCloudSectionState(SectionPresentation(SectionKind.MISSING))
                TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.common_back)) }
            }
            val certificates = loader.state.data.orEmpty()
            val filtered = certificates.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.domainNames.any { domain -> domain.contains(query.trim(), true) } }
            if (certificates.isNotEmpty() && filtered.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(filtered.sortedWith(compareBy<CertificateInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { certificate ->
                val status = listOfNotNull(certificate.status?.issuance?.let { stringResource(R.string.certificates_issuance, it) },
                    certificate.status?.renewal?.let { stringResource(R.string.certificates_renewal, it) },
                    certificate.status?.errorCode?.let { stringResource(R.string.certificates_status_error_code, it) })
                HCloudResourceCard(ResourceCardModel("certificate:${certificate.id}", certificate.name,
                    status.joinToString(" · ").ifBlank { certificate.type },
                    listOf(certificate.domainNames.joinToString(" · "), stringResource(R.string.certificates_used_by_title, certificate.usedBy.size)).joinToString("\n")),
                    { selectedId = certificate.id })
            }
        }
    }
}

@Composable
private fun CertificateDetailScreen(initial: CertificateInventory, repository: HCloudRepository, onBack: () -> Unit) {
    val scope = "certificate:${com.hcloudcontrol.ui.navigation.LocalNavigationContext.current}:${initial.id}"
    val metadata = com.hcloudcontrol.ui.state.rememberSectionLoader(scope + ":metadata") { repository.getCertificate(initial.id) }
    val history = com.hcloudcontrol.ui.state.rememberSectionLoader(scope + ":history") { repository.getCertificateActions(initial.id) }
    HCloudPullToRefresh(metadata.state.refreshing || history.state.refreshing, { metadata.refresh(); history.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { HCloudReadSection(metadata) { CertificateMetadata(it) } }
            item {
                HCloudReadSection(history) { CertificateActionHistory(it) }
            }
        }
    }
}

@Composable
private fun CertificateMetadata(certificate: CertificateInventory) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(certificate.name, style = MaterialTheme.typography.headlineMedium)
    CertificateValue(stringResource(R.string.common_id, certificate.id))
    CertificateValue(stringResource(R.string.certificates_type, certificate.type))
    certificate.created?.let { CertificateValue(stringResource(R.string.certificates_created, it)) }
    certificate.notValidBefore?.let { CertificateValue(stringResource(R.string.certificates_valid_from, it)) }
    certificate.notValidAfter?.let { CertificateValue(stringResource(R.string.certificates_valid_until, it)) }
    certificate.fingerprint?.let { CertificateValue(stringResource(R.string.certificates_fingerprint, it)) }
    Text(stringResource(R.string.certificates_domains_title), style = MaterialTheme.typography.titleMedium)
    if (certificate.domainNames.isEmpty()) CertificateValue(stringResource(R.string.common_none))
    certificate.domainNames.sorted().forEach { domain -> CertificateValue(domain) }
    certificate.status?.let { status ->
        Text(stringResource(R.string.certificates_status_title), style = MaterialTheme.typography.titleMedium)
        status.issuance?.let { CertificateValue(stringResource(R.string.certificates_issuance, it)) }
        status.renewal?.let { CertificateValue(stringResource(R.string.certificates_renewal, it)) }
        status.errorCode?.let { CertificateValue(stringResource(R.string.certificates_status_error_code, it)) }
    }
    Text(stringResource(R.string.certificates_used_by_title, certificate.usedBy.size), style = MaterialTheme.typography.titleMedium)
    if (certificate.usedBy.isEmpty()) CertificateValue(stringResource(R.string.common_none))
    certificate.usedBy.sortedWith(compareBy({ it.type }, { it.id })).forEach { usage ->
        CertificateValue(stringResource(R.string.certificates_used_by_item, usage.type, usage.id))
    }
    Text(stringResource(R.string.certificates_safety_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CertificateActionHistory(actions: List<CloudAction>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.certificates_action_history_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.certificates_action_history_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    if (actions.isEmpty()) {
            CertificateValue(stringResource(R.string.certificates_action_history_empty))
        } else actions.sortedByDescending { it.id }.forEach { action ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(action.command ?: stringResource(R.string.certificates_action_unknown), style = MaterialTheme.typography.titleSmall)
                    CertificateValue(stringResource(R.string.certificates_action_status, action.status))
                    action.progress?.let { CertificateValue(stringResource(R.string.certificates_action_progress, it)) }
                    action.started?.let { CertificateValue(stringResource(R.string.certificates_action_started, it)) }
                    action.finished?.let { CertificateValue(stringResource(R.string.certificates_action_finished, it)) }
                    // Never display server-provided raw error text in a shareable history.
                    action.errorCode?.let { CertificateValue(stringResource(R.string.certificates_action_error_code, it)) }
                }
            }
        }
}

@Composable
private fun CertificateValue(value: String) = Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
