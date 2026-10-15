package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.operations.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.SshKeyOption
import com.hcloudcontrol.core.domain.model.CreateSshKeySpec
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Project-scoped public SSH-key inventory. The domain model deliberately has
 * no private-key material. Key lifecycle controls stay unavailable until the
 * device-local resource-changing capability is explicitly enabled in Settings.
 */
@Composable
internal fun SshKeysScreen(projectId: String, repository: HCloudRepository) {
    val contextKey = "ssh-keys:$projectId:${com.hcloudcontrol.ui.navigation.LocalNavigationContext.current?.endpoint.orEmpty()}"
    val loader = com.hcloudcontrol.ui.state.rememberSectionLoader(contextKey,
        isEmpty = { it: List<SshKeyOption> -> it.isEmpty() }) { repository.getSshKeys() }
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var createDialog by com.hcloudcontrol.ui.navigation.rememberContextualFlag("$contextKey:create")
    val coordinator = LocalOperationCoordinator.current
    val operationContext = LocalOperationContext.current
    var pendingId by remember(contextKey) { mutableStateOf<String?>(null) }
    var deletingKey by remember(contextKey) { mutableStateOf<SshKeyOption?>(null) }
    var query by rememberSaveable(contextKey) { mutableStateOf("") }
    var expandedId by com.hcloudcontrol.ui.navigation.rememberContextualResourceId(contextKey)
    val fresh = loader.state.hasSuccessfulResponse && !loader.state.isStale && !loader.state.refreshing && loader.state.error == null

    HCloudPullToRefresh(loader.state.refreshing, loader::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(contextKey, contentReady = loader.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.ssh_inventory_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.ssh_inventory_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (highImpactAllowed) Button(onClick = { createDialog = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.ssh_key_create))
                } else Text(stringResource(R.string.ssh_key_operations_not_allowed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(loader) {}
            }
            coordinator?.all()?.filter { it.isPending && it.intent.resource.context == operationContext && it.intent.resource.resourceType == "ssh_key" }
                ?.forEach { pending -> item(key = pending.intent.intentId) {
                    OutlinedButton(onClick = { pendingId = pending.intent.intentId }) { Text(stringResource(R.string.ssh_redesign_pending)) }
                } }
            val all = loader.state.data.orEmpty()
            val keys = all.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.fingerprint.contains(query.trim(), true) }
                .sortedWith(compareBy<SshKeyOption> { it.name.lowercase() }.thenBy { it.id })
            if (all.isNotEmpty() && keys.isEmpty()) item {
                HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
                OutlinedButton(onClick = { query = "" }) { Text(stringResource(R.string.ds_clear_filters)) }
            }
            items(keys, key = { it.id }) { key ->
                SshKeyCard(key, expanded = expandedId == key.id,
                    onExpand = { expandedId = if (expandedId == key.id) null else key.id },
                    canManage = highImpactAllowed && fresh, onDelete = { deletingKey = key })
            }
        }
    }
    pendingId?.let { id -> coordinator?.get(id)?.takeIf { it.intent.resource.context == operationContext }?.let { pending ->
        SshKeyMutationFlow(repository, null, pending, onDismiss = { pendingId = null },
            onChanged = { pendingId = null; loader.refresh() })
    } }
    if (highImpactAllowed && createDialog) CreateSshKeyDialog(repository,
        { createDialog = false }, { createDialog = false; loader.refresh() })
    deletingKey?.let { key ->
        if (highImpactAllowed) DeleteSshKeyDialog(key, repository,
            { deletingKey = null }, { deletingKey = null; loader.refresh() })
    }
}

@Composable
private fun SshKeyCard(key: SshKeyOption, expanded: Boolean, onExpand: () -> Unit, canManage: Boolean, onDelete: () -> Unit) {
    var menu by remember(key.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HCloudResourceCard(ResourceCardModel("ssh_key:${key.id}", key.name,
            stringResource(R.string.ssh_keys_fingerprint, key.fingerprint), stringResource(R.string.ssh_key_no_private_key)), onExpand,
            menu = if (canManage) ({
                Box {
                    TextButton(onClick = { menu = true }) { Text(stringResource(R.string.ds_actions)) }
                    HCloudMenu(menu, listOf(MenuAction("delete", stringResource(R.string.ssh_key_delete))),
                        { menu = false }, { onDelete() })
                }
            }) else null)
        if (expanded) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.ssh_inventory_id, key.id), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.ssh_keys_fingerprint, key.fingerprint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun CreateSshKeyDialog(repository: HCloudRepository, onDismiss: () -> Unit, onCreated: () -> Unit) =
    SshKeyMutationFlow(repository, null, onDismiss = onDismiss, onChanged = onCreated)

@Composable
private fun DeleteSshKeyDialog(key: SshKeyOption, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) =
    SshKeyMutationFlow(repository, key, onDismiss = onDismiss, onChanged = onDeleted)
