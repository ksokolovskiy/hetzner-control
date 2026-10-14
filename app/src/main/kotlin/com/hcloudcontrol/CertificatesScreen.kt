package com.hcloudcontrol

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private sealed interface CertificatesListState {
    data object Loading : CertificatesListState
    data class Content(val certificates: List<CertificateInventory>) : CertificatesListState
    data object Error : CertificatesListState
}

private sealed interface CertificateDetailState {
    data object Loading : CertificateDetailState
    data class Content(val certificate: CertificateInventory) : CertificateDetailState
    data object Error : CertificateDetailState
}

private sealed interface CertificateActionsState {
    data object Loading : CertificateActionsState
    data class Content(val actions: List<CloudAction>) : CertificateActionsState
    data object Error : CertificateActionsState
}

/** Inventory-only certificate view. No PEM, private key, import, renewal or deletion operation exists here. */
@Composable
fun CertificatesScreen(repository: HCloudRepository, projectId: String) {
    var refreshKey by remember(projectId) { mutableIntStateOf(0) }
    var state by remember(projectId) { mutableStateOf<CertificatesListState>(CertificatesListState.Loading) }
    var selected by remember(projectId) { mutableStateOf<CertificateInventory?>(null) }

    LaunchedEffect(projectId, refreshKey) {
        state = CertificatesListState.Loading
        state = try {
            CertificatesListState.Content(withContext(Dispatchers.IO) { repository.getCertificates() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            CertificatesListState.Error
        }
    }
    selected?.let { certificate ->
        CertificateDetailScreen(certificate, repository) { selected = null }
        return
    }
    when (val current = state) {
        CertificatesListState.Loading -> CertificateLoading(stringResource(R.string.certificates_loading))
        CertificatesListState.Error -> StateView(
            stringResource(R.string.certificates_error_title),
            stringResource(R.string.certificates_error_message),
            stringResource(R.string.certificates_retry),
        ) { refreshKey++ }
        is CertificatesListState.Content -> if (current.certificates.isEmpty()) {
            StateView(
                stringResource(R.string.certificates_empty_title),
                stringResource(R.string.certificates_empty_message),
                stringResource(R.string.certificates_retry),
            ) { refreshKey++ }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.certificates_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.certificates_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(current.certificates.sortedWith(compareBy<CertificateInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { certificate ->
                CertificateCard(certificate) { selected = certificate }
            }
        }
    }
}

@Composable
private fun CertificateCard(certificate: CertificateInventory, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(certificate.name, style = MaterialTheme.typography.titleMedium)
        Text(certificate.type, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.certificates_domains_count, certificate.domainNames.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        certificate.notValidAfter?.let { date ->
            Text(stringResource(R.string.certificates_valid_until, date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CertificateDetailScreen(
    initial: CertificateInventory,
    repository: HCloudRepository,
    onBack: () -> Unit,
) {
    var detailState by remember(initial.id) { mutableStateOf<CertificateDetailState>(CertificateDetailState.Loading) }
    var actionsState by remember(initial.id) { mutableStateOf<CertificateActionsState>(CertificateActionsState.Loading) }
    LaunchedEffect(initial.id) {
        coroutineScope {
            val detail = async(Dispatchers.IO) { runCatching { repository.getCertificate(initial.id) } }
            val actions = async(Dispatchers.IO) { runCatching { repository.getCertificateActions(initial.id) } }
            detailState = detail.await().fold(
                onSuccess = { CertificateDetailState.Content(it) },
                onFailure = { CertificateDetailState.Error },
            )
            actionsState = actions.await().fold(
                onSuccess = { CertificateActionsState.Content(it) },
                onFailure = { CertificateActionsState.Error },
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← ${stringResource(R.string.common_back)}") } }
        item {
            when (val current = detailState) {
                CertificateDetailState.Loading -> CertificateLoading(stringResource(R.string.certificates_detail_loading))
                CertificateDetailState.Error -> Text(stringResource(R.string.certificates_detail_load_error), color = MaterialTheme.colorScheme.error)
                is CertificateDetailState.Content -> CertificateMetadata(current.certificate)
            }
        }
        item { CertificateActionHistory(actionsState) }
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
private fun CertificateActionHistory(state: CertificateActionsState) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.certificates_action_history_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.certificates_action_history_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    when (state) {
        CertificateActionsState.Loading -> CertificateLoading(stringResource(R.string.certificates_action_history_loading))
        CertificateActionsState.Error -> Text(stringResource(R.string.certificates_action_history_load_error), color = MaterialTheme.colorScheme.error)
        is CertificateActionsState.Content -> if (state.actions.isEmpty()) {
            CertificateValue(stringResource(R.string.certificates_action_history_empty))
        } else state.actions.sortedByDescending { it.id }.forEach { action ->
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
}

@Composable
private fun CertificateLoading(message: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(12.dp))
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CertificateValue(value: String) = Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
