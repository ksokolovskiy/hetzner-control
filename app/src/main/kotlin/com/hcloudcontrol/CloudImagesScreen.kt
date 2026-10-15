package com.hcloudcontrol

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
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

/** Project-scoped image inventory; aliases reuse the same immutable-ID detail/read owners. */
@Composable
internal fun CloudImagesScreen(repository: HCloudRepository, projectId: String, initialImageId: Long? = null,
    onBackFromInitial: (() -> Unit)? = null) {
    val selectionScope = "image:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    val inventory = com.hcloudcontrol.ui.state.rememberSectionLoader(selectionScope) { repository.getImages() }
    var selectedId by rememberContextualResourceId(selectionScope)
    var query by rememberSaveable(selectionScope) { mutableStateOf("") }
    var filterName by rememberSaveable(selectionScope) { mutableStateOf(CloudImageFilter.ALL.name) }
    val backFromDetail = {
        selectedId = null
        if (initialImageId != null) onBackFromInitial?.invoke()
        Unit
    }
    BackHandler(enabled = LocalNavigationChrome.current == null && selectedId != null) { backFromDetail() }
    LaunchedEffect(selectionScope, initialImageId) { if (initialImageId != null) selectedId = initialImageId }
    selectedId?.let { id ->
        CloudImageResourceDetail(id, repository, backFromDetail) { inventory.refresh(); backFromDetail() }
        return
    }
    HCloudPullToRefresh(inventory.state.refreshing, inventory::refresh, Modifier.fillMaxSize()) {
        if (inventory.state.hasSuccessfulResponse) CloudImagesList(
            images = inventory.state.data.orEmpty(), query = query, onQuery = { query = it },
            filter = CloudImageFilter.entries.firstOrNull { it.name == filterName } ?: CloudImageFilter.ALL,
            onFilter = { filterName = it.name },
            readHeader = { HCloudReadSection(inventory) {} },
            onImageClick = { selectedId = it.id },
        ) else LazyColumn(Modifier.fillMaxSize()) {
            item { HCloudReadSection(inventory) {} }
        }
    }
}

private enum class CloudImageFilter(val apiType: String?, val labelRes: Int) {
    ALL(null, R.string.images_filter_all),
    SYSTEM("system", R.string.images_filter_system),
    SNAPSHOT("snapshot", R.string.images_filter_snapshot),
    BACKUP("backup", R.string.images_filter_backup),
}

@Composable
private fun CloudImagesList(
    images: List<CloudImageInventory>,
    query: String, onQuery: (String) -> Unit,
    filter: CloudImageFilter, onFilter: (CloudImageFilter) -> Unit,
    readHeader: @Composable () -> Unit,
    onImageClick: (CloudImageInventory) -> Unit,
) {
    val filtered = remember(images, filter, query) {
        images.filter { filter.apiType == null || it.type.equals(filter.apiType, ignoreCase = true) }
            .filter { query.isBlank() || it.name.orEmpty().contains(query.trim(), true) || it.description.orEmpty().contains(query.trim(), true) }
            .sortedWith(compareBy<CloudImageInventory> { it.name.orEmpty().lowercase() }.thenBy { it.id })
    }
    LazyColumn(
        state = rememberRestorableLazyListState("images"),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.images_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.images_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            HCloudFormField(query, onQuery, stringResource(R.string.ds_search))
            HCloudSegmentedControl(CloudImageFilter.entries.map { ChoiceItem(it.name, stringResource(it.labelRes)) }, filter.name,
                { onFilter(CloudImageFilter.valueOf(it)) })
            readHeader()
        }
        if (filtered.isEmpty()) {
            item {
                HCloudSectionState(SectionPresentation(if (images.isEmpty()) SectionKind.EMPTY else SectionKind.NO_RESULTS))
                if (images.isNotEmpty()) OutlinedButton(onClick = { onQuery(""); onFilter(CloudImageFilter.ALL) }) { Text(stringResource(R.string.ds_clear_filters)) }
            }
        } else {
            items(filtered, key = { it.id }) { image -> CloudImageCard(image) { onImageClick(image) } }
        }
    }
}

@Composable
private fun CloudImageCard(image: CloudImageInventory, onClick: () -> Unit) = HCloudResourceCard(
    ResourceCardModel("image:${image.id}", image.name ?: stringResource(R.string.images_unnamed, image.id),
        image.status ?: stringResource(R.string.common_none),
        listOfNotNull(image.type, image.architecture, image.diskSize?.let { stringResource(R.string.images_disk_size, it) }).joinToString(" · ")), onClick)

@Composable
internal fun CloudImageDetailScreen(image: CloudImageInventory, repository: HCloudRepository, onBack: () -> Unit,
    onDeleted: () -> Unit = onBack) = CloudImageResourceDetail(image.id, repository, onBack, onDeleted)

/** One metadata reader and one history reader; an alias never adds another GET path. */
@Composable
private fun CloudImageResourceDetail(imageId: Long, repository: HCloudRepository, onBack: () -> Unit,
    onDeleted: () -> Unit) {
    val contextKey = "image:${LocalNavigationContext.current}:$imageId"
    val metadata = com.hcloudcontrol.ui.state.rememberSectionLoader("$contextKey:metadata") { repository.getImage(imageId) }
    val history = com.hcloudcontrol.ui.state.rememberSectionLoader("$contextKey:history") { repository.getImageActions(imageId) }
    var deleting by rememberContextualFlag("$contextKey:delete")
    val image = metadata.state.data
    val fresh = metadata.state.hasSuccessfulResponse && !metadata.state.isStale &&
        !metadata.state.refreshing && metadata.state.error == null
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.IMAGE, imageId.toString(),
        image?.reviewIdentity() ?: stringResource(R.string.images_title), onBack)
    HCloudPullToRefresh(metadata.state.refreshing || history.state.refreshing,
        { metadata.refresh(); history.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(contextKey, contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { HCloudReadSection(metadata) { CloudImageDetailsCard(it) } }
            if (image != null) item {
                if (image.canProtectImage()) ResourceProtectionControls(image.reviewIdentity(),
                    checkNotNull(image.deleteProtected),
                    com.hcloudcontrol.operations.ProtectionAdapter(com.hcloudcontrol.operations.ProtectionResource.IMAGE,
                        image.id, com.hcloudcontrol.operations.ProtectionField.DELETE, repository),
                    onCompleted = metadata::refresh, enabled = fresh)
                if (highImpactAllowed && fresh) {
                    if (image.canDeleteImage()) Button(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.images_delete_action))
                    } else if (image.type in setOf("snapshot", "backup") && image.deleteProtected == true)
                        Text(stringResource(R.string.images_delete_protected))
                }
            }
            item {
                Text(stringResource(R.string.image_actions_title), style = MaterialTheme.typography.titleMedium)
                HCloudReadSection(history) { ImageActionHistory(it) }
            }
        }
    }
    if (deleting && image != null) ImageDeleteDialog(image, repository, { deleting = false }, onDeleted)
}

@Composable
private fun ImageDeleteDialog(image: CloudImageInventory, repository: HCloudRepository,
    onDismiss: () -> Unit, onCompleted: () -> Unit) {
    val adapter = remember(repository, image) { ImageDeletionAdapter(repository, image) }
    val session = com.hcloudcontrol.operations.rememberOperationSession("image", image.id.toString(), "delete_image",
        listOf(image.name, image.description, image.type, image.status, image.deleteProtected).hashCode().toLong() and 0xffffffffL)
    val activeContext by androidx.compose.runtime.rememberUpdatedState(com.hcloudcontrol.operations.LocalOperationContext.current)
    val allowed by androidx.compose.runtime.rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val scope = rememberCoroutineScope()
    var busy by remember(session) { mutableStateOf(false) }
    var failure by remember(session) { mutableStateOf(false) }
    var reviewedAt by remember(session) { mutableStateOf(0L) }
    var progress by remember(session) { mutableStateOf(0) }
    val record = remember(session, progress, busy) { session.record }
    LaunchedEffect(session) {
        if (session.record == null) {
            busy = true
            try { withContext(Dispatchers.IO) { adapter.validateFresh() }; reviewedAt = System.currentTimeMillis() }
            catch (_: Throwable) { failure = true }
            busy = false
        }
    }
    if (record != null) {
        com.hcloudcontrol.operations.OperationResultDialog(stringResource(R.string.images_delete_title), record,
            record.intent.resource.context.connectionId, "${image.reviewIdentity()} · #${image.id}",
            onClose = { if (record.stage == com.hcloudcontrol.operations.OperationStage.SUCCEEDED) onCompleted() else onDismiss() },
            busy = busy, session = session, onCheckStatus = {
                if (!busy && activeContext == session.context) {
                    busy = true
                    scope.launch {
                        try { session.reconcileExistingEffect(withContext(Dispatchers.IO) { adapter.deleted() }) }
                        catch (_: Throwable) { failure = true }
                        busy = false; progress++
                    }
                }
            }) {
            if (failure) Text(stringResource(R.string.images_action_failed), color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (reviewedAt > 0 && session.intent != null && !failure) {
        val model = com.hcloudcontrol.operations.OperationReviewContent(checkNotNull(session.intent), reviewedAt,
            stringResource(R.string.images_delete_title), session.context?.connectionId.orEmpty(),
            "${image.reviewIdentity()} · #${image.id}", listOf(com.hcloudcontrol.operations.ReviewChange(
                stringResource(R.string.images_type), null, image.type.orEmpty()),
                com.hcloudcontrol.operations.ReviewChange(stringResource(R.string.images_delete_protection), null,
                    stringResource(R.string.common_protected_disabled)),
                com.hcloudcontrol.operations.ReviewChange(stringResource(R.string.images_delete_action), null,
                    stringResource(R.string.images_delete_warning))), typedIdentity = image.reviewIdentity(),
            acknowledgements = listOf(com.hcloudcontrol.operations.RiskAcknowledgement("image-delete", stringResource(R.string.images_delete_acknowledgement))))
        com.hcloudcontrol.operations.OperationReviewDialog(model, { proof ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { adapter.validateFresh() }
                        session.beginReviewed(proof, allowed, activeContext)
                        withContext(Dispatchers.IO) { check(activeContext == session.context && allowed); adapter.delete() }
                        session.succeed()
                    } catch (error: Throwable) { session.rejectOrUnknown(error); failure = true }
                    busy = false; progress++
                }
            }
        }, onDismiss, submitEnabled = allowed && !busy)
    } else HCloudDialog(stringResource(R.string.images_delete_title), stringResource(R.string.ds_close), onDismiss, onDismiss) {
        HCloudSectionState(SectionPresentation(if (busy) SectionKind.LOADING else SectionKind.ERROR,
            detail = if (failure) stringResource(R.string.images_action_failed) else null))
    }
}

@Composable
private fun ImageActionHistory(actions: List<com.hcloudcontrol.core.domain.model.CloudAction>?) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun ImageDetailRow(label: String, value: String) = Column(Modifier.fillMaxWidth()) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}
