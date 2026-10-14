package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.ObjectStorageBucket
import com.hcloudcontrol.core.domain.model.ObjectStorageLocation
import com.hcloudcontrol.core.domain.model.isValidObjectStorageBucketName
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.network.ObjectStorageBucketNotEmptyException
import com.hcloudcontrol.core.network.ObjectStorageRequestException
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ObjectStorageState {
    data object Loading : ObjectStorageState
    data class Content(val buckets: List<ObjectStorageBucket>) : ObjectStorageState
    data class Error(val message: String) : ObjectStorageState
}

@Composable
internal fun ObjectStorageScreen(store: ObjectStorageCredentialStore, repository: ObjectStorageRepository) {
    val context = LocalContext.current
    // Do not rely solely on hiding controls. A setting may change while a confirmation
    // dialog is open, so create/delete must re-check this capability at execution time.
    val highImpactOperations = LocalHighImpactOperations.current
    var connections by remember { mutableStateOf(store.connections()) }
    var state by remember(store.activeConnectionId()) { mutableStateOf<ObjectStorageState>(ObjectStorageState.Loading) }
    var showConnect by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ObjectStorageBucket?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (store.activeCredentials() == null) { state = ObjectStorageState.Content(emptyList()); return }
        state = ObjectStorageState.Loading
        scope.launch {
            state = try {
                ObjectStorageState.Content(withContext(Dispatchers.IO) { repository.listBuckets() })
            } catch (error: Throwable) {
                ObjectStorageState.Error(objectStorageSafeMessage(context, error))
            }
        }
    }
    LaunchedEffect(store.activeConnectionId()) { refresh() }
    BackHandler(enabled = showConnect || showCreate || deleting != null) {
        showConnect = false; showCreate = false; deleting = null
    }

    if (showConnect) {
        ObjectStorageConnectionScreen(
            onCancel = { showConnect = false },
            onConnect = { displayName, location, accessKey, secretKey ->
                scope.launch {
                    // Verify in memory first. A failed pair is never persisted.
                    try {
                        val transient = com.hcloudcontrol.core.network.ObjectStorageRepositoryFactory.create {
                            com.hcloudcontrol.core.domain.model.ObjectStorageCredentials(accessKey, secretKey, location)
                        }
                        withContext(Dispatchers.IO) { transient.listBuckets() }
                        store.saveConnection(displayName, location, accessKey, secretKey)
                        connections = store.connections()
                        showConnect = false
                        refresh()
                    } catch (error: Throwable) {
                        state = ObjectStorageState.Error(objectStorageSafeMessage(context, error))
                    }
                }
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.object_storage_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.object_storage_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        ObjectStorageConnectionPicker(
            connections = connections,
            activeId = store.activeConnectionId(),
            onSelect = { id -> store.selectConnection(id); refresh() },
            onAdd = { showConnect = true },
            onRemove = { id -> store.removeConnection(id); connections = store.connections(); refresh() },
        )
        Spacer(Modifier.height(12.dp))
        if (highImpactOperations.isAllowed) {
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.object_storage_create_action)) }
        } else {
            Text(stringResource(R.string.object_storage_mutations_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        when (val current = state) {
            ObjectStorageState.Loading -> LoadingView(stringResource(R.string.object_storage_loading))
            is ObjectStorageState.Error -> StateView(stringResource(R.string.object_storage_title), current.message, stringResource(R.string.common_retry), ::refresh)
            is ObjectStorageState.Content -> if (current.buckets.isEmpty()) {
                StateView(stringResource(R.string.object_storage_title), stringResource(R.string.object_storage_empty), stringResource(R.string.common_retry), ::refresh)
            } else HCloudPullToRefresh(
                isRefreshing = false,
                onRefresh = ::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current.buckets, key = { it.name }) { bucket ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(bucket.name, style = MaterialTheme.typography.titleMedium)
                                Text(bucket.createdAt ?: stringResource(R.string.object_storage_created_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (highImpactOperations.isAllowed) TextButton(onClick = { deleting = bucket }) { Text(stringResource(R.string.object_storage_delete_action)) }
                        }
                    }
                }
            }
            }
        }
    }
    if (showCreate) ObjectStorageCreateDialog(
        onDismiss = { showCreate = false },
        onCreate = { name ->
            if (!highImpactOperations.isAllowed) {
                // The permission was revoked after the dialog opened. Close the stale
                // confirmation; recomposition exposes the read-only safe state.
                showCreate = false
                return@ObjectStorageCreateDialog
            }
            scope.launch {
                try { withContext(Dispatchers.IO) { repository.createBucket(name) }; showCreate = false; refresh() }
                catch (error: Throwable) { state = ObjectStorageState.Error(objectStorageSafeMessage(context, error)) }
            }
        },
    )
    deleting?.let { bucket -> ObjectStorageDeleteDialog(bucket, onDismiss = { deleting = null }) {
        if (!highImpactOperations.isAllowed) {
            // Never send a destructive S3 request from a stale confirmation dialog.
            deleting = null
            return@ObjectStorageDeleteDialog
        }
        scope.launch {
            try { withContext(Dispatchers.IO) { repository.deleteBucket(bucket.name) }; deleting = null; refresh() }
            catch (error: Throwable) { deleting = null; state = ObjectStorageState.Error(objectStorageSafeMessage(context, error)) }
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ObjectStorageConnectionScreen(
    onCancel: () -> Unit,
    onConnect: (String, ObjectStorageLocation, String, String) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }; var accessKey by remember { mutableStateOf("") }; var secretKey by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(ObjectStorageLocation.FALKENSTEIN) }; var expanded by remember { mutableStateOf(false) }; var submitted by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.object_storage_connect_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.object_storage_setup_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.object_storage_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.object_storage_profile_name)) }, isError = submitted && displayName.isBlank(), singleLine = true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField("${location.id} — ${location.host}", {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true, label = { Text(stringResource(R.string.object_storage_location)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { ObjectStorageLocation.entries.forEach { option -> DropdownMenuItem(text = { Text("${option.id} — ${option.host}") }, onClick = { location = option; expanded = false }) } }
        }
        OutlinedTextField(accessKey, { accessKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.object_storage_access_key)) }, visualTransformation = PasswordVisualTransformation(), isError = submitted && accessKey.isBlank(), singleLine = true)
        OutlinedTextField(secretKey, { secretKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.object_storage_secret_key)) }, visualTransformation = PasswordVisualTransformation(), isError = submitted && secretKey.isBlank(), singleLine = true)
        Button(onClick = { submitted = true; if (displayName.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()) onConnect(displayName, location, accessKey, secretKey) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.object_storage_connect_action)) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_cancel)) }
    }
}

@Composable private fun ObjectStorageConnectionPicker(connections: List<com.hcloudcontrol.core.security.ObjectStorageConnection>, activeId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    Column {
        Text(stringResource(R.string.object_storage_connection), style = MaterialTheme.typography.titleSmall)
        connections.forEach { connection -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilterChip(selected = connection.id == activeId, onClick = { onSelect(connection.id) }, label = { Text("${connection.displayName} · ${connection.location.id}") })
            if (connections.size > 1) TextButton(onClick = { onRemove(connection.id) }) { Text(stringResource(R.string.object_storage_remove_connection)) }
        } }
        TextButton(onClick = onAdd) { Text(stringResource(R.string.object_storage_add_connection)) }
    }
}

@Composable private fun ObjectStorageCreateDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.object_storage_create_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.object_storage_create_warning))
            Text(stringResource(R.string.object_storage_create_cost_warning), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(name, { name = it.lowercase() }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.object_storage_bucket_name)) }, isError = name.isNotEmpty() && !isValidObjectStorageBucketName(name), singleLine = true)
        }
    }, confirmButton = { Button(onClick = { onCreate(name) }, enabled = isValidObjectStorageBucketName(name)) { Text(stringResource(R.string.object_storage_create_confirm)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } })
}

@Composable private fun ObjectStorageDeleteDialog(bucket: ObjectStorageBucket, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.object_storage_delete_title, bucket.name)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.object_storage_delete_warning))
            Text(stringResource(R.string.object_storage_delete_protection_warning), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.object_storage_delete_type_name, bucket.name))
            OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.object_storage_bucket_name)) }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = onDelete, enabled = confirmation == bucket.name) { Text(stringResource(R.string.object_storage_delete_confirm)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } })
}

private fun objectStorageSafeMessage(context: android.content.Context, error: Throwable): String = when (error) {
    is ObjectStorageBucketNotEmptyException -> context.getString(R.string.object_storage_bucket_not_empty)
    // Network-layer wording is deliberately never raw S3 XML, but localization belongs to UI.
    is ObjectStorageRequestException -> context.getString(R.string.object_storage_request_failed)
    else -> context.getString(R.string.object_storage_request_failed)
}
