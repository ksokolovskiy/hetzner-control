package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    var refreshGeneration by remember(projectId) { mutableStateOf(0L) }
    var state by remember(projectId) { mutableStateOf<SshKeysState>(SshKeysState.Loading) }
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.ssh_inventory_load_error)
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var createDialog by remember(projectId) { mutableStateOf(false) }
    var deletingKey by remember(projectId) { mutableStateOf<SshKeyOption?>(null) }

    fun refresh() {
        val generation = refreshGeneration + 1
        refreshGeneration = generation
        state = SshKeysState.Loading
        scope.launch {
            val result = try {
                SshKeysState.Content(withContext(Dispatchers.IO) { repository.getSshKeys() })
            } catch (error: Throwable) {
                SshKeysState.Error(safeApiFailureMessage(error, fallback))
            }
            if (generation == refreshGeneration) state = result
        }
    }

    LaunchedEffect(projectId) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.ssh_inventory_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.ssh_inventory_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (highImpactAllowed) {
                Button(onClick = { createDialog = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.ssh_key_create))
                }
            } else {
                Text(
                    stringResource(R.string.ssh_key_operations_not_allowed),
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (val current = state) {
            SshKeysState.Loading -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ssh_inventory_loading))
                }
            }
            is SshKeysState.Error -> item {
                SshKeysError(current.message, onRetry = ::refresh)
            }
            is SshKeysState.Content -> {
                val keys = current.keys.sortedWith(compareBy<SshKeyOption> { it.name.lowercase() }.thenBy { it.id })
                if (keys.isEmpty()) {
                    item { SshKeysEmpty(onRetry = ::refresh) }
                } else {
                    items(keys, key = { it.id }) { key ->
                        SshKeyCard(key, canManage = highImpactAllowed, onDelete = { deletingKey = key })
                    }
                }
            }
        }
    }
    if (highImpactAllowed && createDialog) {
        CreateSshKeyDialog(
            repository = repository,
            onDismiss = { createDialog = false },
            onCreated = { createDialog = false; refresh() },
        )
    }
    deletingKey?.let { key ->
        if (highImpactAllowed) DeleteSshKeyDialog(
            key = key,
            repository = repository,
            onDismiss = { deletingKey = null },
            onDeleted = { deletingKey = null; refresh() },
        )
    }
}

private sealed interface SshKeysState {
    data object Loading : SshKeysState
    data class Content(val keys: List<SshKeyOption>) : SshKeysState
    data class Error(val message: String) : SshKeysState
}

@Composable
private fun SshKeyCard(key: SshKeyOption, canManage: Boolean, onDelete: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(key.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.ssh_inventory_id, key.id), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.ssh_keys_fingerprint, key.fingerprint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canManage) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.ssh_key_delete))
            }
        }
    }
}

@Composable
private fun CreateSshKeyDialog(repository: HCloudRepository, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var review by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failure = stringResource(R.string.ssh_key_create_failed)
    val valid = name.isNotBlank() && publicKey.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(if (review) R.string.ssh_key_create_review_title else R.string.ssh_key_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (review) {
                    Text(stringResource(R.string.ssh_key_create_review_message))
                    Text(stringResource(R.string.ssh_key_create_review_name, name.trim()), style = MaterialTheme.typography.bodyMedium)
                    Text(publicKey.trim(), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.ssh_key_no_private_key), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.ssh_key_create_intro))
                    OutlinedTextField(name, { name = it; error = null }, label = { Text(stringResource(R.string.ssh_key_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(publicKey, { publicKey = it; error = null }, label = { Text(stringResource(R.string.ssh_key_public_key)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!review) review = true else {
                    submitting = true; error = null
                    scope.launch {
                        try { withContext(Dispatchers.IO) { repository.createSshKey(CreateSshKeySpec(name.trim(), publicKey.trim())) }; onCreated() }
                        catch (t: Throwable) { error = safeApiFailureMessage(t, failure); submitting = false }
                    }
                }
            }, enabled = valid && !submitting) {
                Text(stringResource(if (submitting) R.string.common_in_progress else if (review) R.string.ssh_key_create_confirm else R.string.ssh_key_review))
            }
        },
        dismissButton = { TextButton(onClick = { if (!submitting) { if (review) review = false else onDismiss() } }, enabled = !submitting) { Text(stringResource(if (review) R.string.common_back else R.string.common_cancel)) } },
    )
}

@Composable
private fun DeleteSshKeyDialog(key: SshKeyOption, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var typedName by remember(key.id) { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failure = stringResource(R.string.ssh_key_delete_failed)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.ssh_key_delete_title, key.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ssh_key_delete_warning, key.name))
            Text(stringResource(R.string.ssh_key_delete_type_name, key.name))
            OutlinedTextField(typedName, { typedName = it; error = null }, label = { Text(stringResource(R.string.ssh_key_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            submitting = true; error = null
            scope.launch { try { withContext(Dispatchers.IO) { repository.deleteSshKey(key.id) }; onDeleted() } catch (t: Throwable) { error = safeApiFailureMessage(t, failure); submitting = false } }
        }, enabled = typedName == key.name && !submitting) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.ssh_key_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SshKeysError(message: String, onRetry: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
private fun SshKeysEmpty(onRetry: () -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ssh_inventory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}
