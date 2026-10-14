package com.hcloudcontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudImageInventory
import com.hcloudcontrol.core.domain.model.ImageProtectionPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only Cloud Images inventory; the exposed repository surface has no image mutation calls. */
@Composable
internal fun CloudImagesScreen(repository: HCloudRepository, projectId: String) {
    var state by remember(projectId) { mutableStateOf<CloudImagesState>(CloudImagesState.Loading) }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    var selectedImage by remember(projectId) { mutableStateOf<CloudImageInventory?>(null) }
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.images_load_error)
    fun load() {
        state = CloudImagesState.Loading
        scope.launch {
            state = try {
                CloudImagesState.Content(withContext(Dispatchers.IO) { repository.getImages() })
            } catch (error: Throwable) {
                CloudImagesState.Error(safeApiFailureMessage(error, loadError))
            }
        }
    }
    LaunchedEffect(projectId, refreshKey) { load() }

    selectedImage?.let { image ->
        CloudImageDetailScreen(image = image, repository = repository, onBack = { selectedImage = null })
        return
    }
    when (val current = state) {
        CloudImagesState.Loading -> LoadingView(stringResource(R.string.images_loading))
        is CloudImagesState.Error -> StateView(
            stringResource(R.string.images_title), current.message, stringResource(R.string.common_retry), { refreshKey++ },
        )
        is CloudImagesState.Content -> CloudImagesList(images = current.images, onImageClick = { selectedImage = it })
    }
}

private sealed interface CloudImagesState {
    data object Loading : CloudImagesState
    data class Content(val images: List<CloudImageInventory>) : CloudImagesState
    data class Error(val message: String) : CloudImagesState
}

private sealed interface CloudImageDetailState {
    data object Loading : CloudImageDetailState
    data class Content(val image: CloudImageInventory) : CloudImageDetailState
    data class Error(val message: String) : CloudImageDetailState
}

private enum class CloudImageFilter(val apiType: String?, val labelRes: Int) {
    ALL(null, R.string.images_filter_all),
    SYSTEM("system", R.string.images_filter_system),
    SNAPSHOT("snapshot", R.string.images_filter_snapshot),
    BACKUP("backup", R.string.images_filter_backup),
}

@Composable
private fun CloudImagesList(images: List<CloudImageInventory>, onImageClick: (CloudImageInventory) -> Unit) {
    var filter by remember { mutableStateOf(CloudImageFilter.ALL) }
    val filtered = remember(images, filter) {
        images.filter { filter.apiType == null || it.type.equals(filter.apiType, ignoreCase = true) }
            .sortedWith(compareBy<CloudImageInventory> { it.name.orEmpty().lowercase() }.thenBy { it.id })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.images_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.images_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudImageFilter.entries.forEach { choice ->
                    FilterChip(
                        selected = filter == choice,
                        onClick = { filter = choice },
                        label = { Text(stringResource(choice.labelRes)) },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    stringResource(if (images.isEmpty()) R.string.images_empty else R.string.images_filter_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered, key = { it.id }) { image -> CloudImageCard(image) { onImageClick(image) } }
        }
    }
}

@Composable
private fun CloudImageCard(image: CloudImageInventory, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(image.name ?: stringResource(R.string.images_unnamed, image.id), style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(image.type, image.status, image.architecture).joinToString(" · ").ifBlank { stringResource(R.string.common_none) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        image.diskSize?.let { Text(stringResource(R.string.images_disk_size, it), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun CloudImageDetailScreen(image: CloudImageInventory, repository: HCloudRepository, onBack: () -> Unit) {
    var refreshKey by remember(image.id) { mutableStateOf(0) }
    var state by remember(image.id) { mutableStateOf<CloudImageDetailState>(CloudImageDetailState.Loading) }
    var actions by remember(image.id) { mutableStateOf<List<com.hcloudcontrol.core.domain.model.CloudAction>?>(null) }
    var pendingOperation by remember(image.id) { mutableStateOf<ImageHighImpactOperation?>(null) }
    val loadError = stringResource(R.string.image_load_error)
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    LaunchedEffect(image.id, refreshKey) {
        state = CloudImageDetailState.Loading
        state = try {
            CloudImageDetailState.Content(withContext(Dispatchers.IO) { repository.getImage(image.id) })
        } catch (_: Throwable) {
            CloudImageDetailState.Error(loadError)
        }
    }
    LaunchedEffect(image.id, refreshKey) { actions = runCatching { withContext(Dispatchers.IO) { repository.getImageActions(image.id) } }.getOrNull() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { Text(image.name ?: stringResource(R.string.images_unnamed, image.id), style = MaterialTheme.typography.headlineMedium) }
        when (val current = state) {
            CloudImageDetailState.Loading -> item { ImageLoading(stringResource(R.string.image_loading)) }
            is CloudImageDetailState.Error -> item {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) }
            }
            is CloudImageDetailState.Content -> {
                item { CloudImageDetailsCard(current.image) }
                if (highImpactAllowed) {
                    item {
                        ImageHighImpactControls(
                            image = current.image,
                            onChangeProtection = { pendingOperation = ImageHighImpactOperation.ChangeProtection(it) },
                            onDelete = { pendingOperation = ImageHighImpactOperation.Delete },
                        )
                    }
                }
                item { ImageActionHistory(actions) }
            }
        }
    }
    pendingOperation?.let { operation ->
        when (operation) {
            ImageHighImpactOperation.Delete -> ImageDeleteDialog(
                image = (state as? CloudImageDetailState.Content)?.image ?: image,
                repository = repository,
                onDismiss = { pendingOperation = null },
                onCompleted = { pendingOperation = null; refreshKey++ },
            )
            is ImageHighImpactOperation.ChangeProtection -> ImageProtectionDialog(
                image = (state as? CloudImageDetailState.Content)?.image ?: image,
                desiredState = operation.enabled,
                repository = repository,
                onDismiss = { pendingOperation = null },
                onCompleted = { pendingOperation = null; refreshKey++ },
            )
        }
    }
}

private sealed interface ImageHighImpactOperation {
    data object Delete : ImageHighImpactOperation
    data class ChangeProtection(val enabled: Boolean) : ImageHighImpactOperation
}

private fun CloudImageInventory.canManageLifecycle(): Boolean =
    type.equals("snapshot", ignoreCase = true) && status.equals("available", ignoreCase = true)

private fun CloudImageInventory.confirmationValues(): Set<String> =
    setOfNotNull(description?.trim()?.takeIf { it.isNotEmpty() }, name?.trim()?.takeIf { it.isNotEmpty() })

@Composable
private fun ImageHighImpactControls(
    image: CloudImageInventory,
    onChangeProtection: (Boolean) -> Unit,
    onDelete: () -> Unit,
) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.images_high_impact_title), style = MaterialTheme.typography.titleMedium)
        if (!image.canManageLifecycle()) {
            Text(stringResource(R.string.images_high_impact_unavailable), color = MaterialTheme.colorScheme.onErrorContainer)
        } else {
            val protected = image.deleteProtected == true
            Text(
                stringResource(if (protected) R.string.images_protection_enabled_note else R.string.images_protection_disabled_note),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = { onChangeProtection(!protected) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (protected) R.string.images_protection_disable else R.string.images_protection_enable))
            }
            if (image.confirmationValues().isEmpty()) {
                Text(stringResource(R.string.images_delete_identity_missing), color = MaterialTheme.colorScheme.onErrorContainer)
            } else if (protected) {
                Text(stringResource(R.string.images_delete_protected), color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.images_delete_action)) }
            }
        }
    }
}

@Composable
private fun ImageProtectionDialog(
    image: CloudImageInventory,
    desiredState: Boolean,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var submitting by remember(image.id, desiredState) { mutableStateOf(false) }
    var failure by remember(image.id, desiredState) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val actionFailure = stringResource(R.string.images_action_failed)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.images_protection_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.images_protection_review_message, image.name ?: image.id.toString(), stringResource(if (desiredState) R.string.images_protected else R.string.images_not_protected)))
                Text(stringResource(R.string.images_protection_review_warning), color = MaterialTheme.colorScheme.error)
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(2.dp))
                    Text(stringResource(R.string.common_sending_request))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true; failure = null
                    scope.launch {
                        try {
                            val action = withContext(Dispatchers.IO) { repository.changeImageProtection(image.id, ImageProtectionPatch(desiredState)) }
                            awaitImageAction(repository, action.id)
                            onCompleted()
                        } catch (error: Throwable) {
                            failure = safeApiFailureMessage(error, actionFailure)
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.images_protection_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Keeps the destructive-operation dialog open until the submitted Image action is terminal. */
private suspend fun awaitImageAction(repository: HCloudRepository, actionId: Long) {
    repeat(600) {
        when (val current = repository.getAction(actionId).status.lowercase()) {
            "success" -> return
            "error" -> throw IllegalStateException("Image action failed")
            else -> delay(1_000)
        }
    }
    throw IllegalStateException("Image action timed out")
}

@Composable
private fun ImageDeleteDialog(
    image: CloudImageInventory,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var typedIdentity by remember(image.id) { mutableStateOf("") }
    var acknowledged by remember(image.id) { mutableStateOf(false) }
    var submitting by remember(image.id) { mutableStateOf(false) }
    var failure by remember(image.id) { mutableStateOf<String?>(null) }
    val allowedValues = image.confirmationValues()
    val scope = rememberCoroutineScope()
    val actionFailure = stringResource(R.string.images_action_failed)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.images_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.images_delete_warning), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.images_delete_type_identity, allowedValues.joinToString(" / ")))
                OutlinedTextField(
                    value = typedIdentity,
                    onValueChange = { typedIdentity = it },
                    enabled = !submitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.images_delete_identity_label)) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !submitting) { acknowledged = !acknowledged },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !submitting)
                    Text(stringResource(R.string.images_delete_acknowledgement))
                }
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(2.dp))
                    Text(stringResource(R.string.common_sending_request))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true; failure = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.deleteImage(image.id) }
                            onCompleted()
                        } catch (error: Throwable) {
                            failure = safeApiFailureMessage(error, actionFailure)
                            submitting = false
                        }
                    }
                },
                enabled = !submitting && acknowledged && typedIdentity.trim() in allowedValues && image.canManageLifecycle() && image.deleteProtected != true,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.images_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ImageActionHistory(actions: List<com.hcloudcontrol.core.domain.model.CloudAction>?) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(stringResource(R.string.image_actions_title), style = MaterialTheme.typography.titleLarge)
    when {
        actions == null -> Text(stringResource(R.string.image_actions_load_error), color = MaterialTheme.colorScheme.error)
        actions.isEmpty() -> Text(stringResource(R.string.image_actions_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> actions.sortedByDescending { it.id }.forEach { action ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text(action.command ?: stringResource(R.string.image_actions_unknown))
                    Text(action.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    action.errorCode?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun CloudImageDetailsCard(image: CloudImageInventory) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        image.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ImageDetailRow(stringResource(R.string.images_id), image.id.toString())
        ImageDetailRow(stringResource(R.string.images_type), image.type ?: stringResource(R.string.common_none))
        ImageDetailRow(stringResource(R.string.images_status), image.status ?: stringResource(R.string.common_none))
        ImageDetailRow(stringResource(R.string.images_architecture), image.architecture ?: stringResource(R.string.common_none))
        image.imageSize?.let { ImageDetailRow(stringResource(R.string.images_image_size), stringResource(R.string.images_size_gib, it)) }
        image.diskSize?.let { ImageDetailRow(stringResource(R.string.images_disk_size_label), stringResource(R.string.images_size_gib, it)) }
        image.created?.let { ImageDetailRow(stringResource(R.string.images_created), it) }
        ImageDetailRow(
            stringResource(R.string.images_delete_protection),
            stringResource(if (image.deleteProtected == true) R.string.images_protected else R.string.images_not_protected),
        )
        image.deprecated?.let { ImageDetailRow(stringResource(R.string.images_deprecated), it) }
        image.deprecation?.announced?.let { ImageDetailRow(stringResource(R.string.images_deprecation_announced), it) }
        image.deprecation?.unavailableAfter?.let { ImageDetailRow(stringResource(R.string.images_unavailable_after), it) }
        Text(stringResource(R.string.images_read_only_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImageLoading(message: String) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    androidx.compose.material3.CircularProgressIndicator()
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ImageDetailRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}
