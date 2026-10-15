package com.hcloudcontrol

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.StorageBox
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.core.security.LegacyStorageBoxCredentialStore
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.rememberContextualResourceId
import com.hcloudcontrol.ui.navigation.rememberContextualText
import com.hcloudcontrol.ui.state.rememberSectionLoader

/** Management retains the selected Cloud owner even though Storage Box uses another API host. */
@Composable
internal fun StorageBoxesInventory(project: CloudProjectConnection, legacyStore: LegacyStorageBoxCredentialStore,
    repository: StorageBoxRepository) {
    val scope = "${project.id}:${project.config.baseUrl}:storage-box"
    var selectedId by rememberContextualResourceId(scope)
    var query by rememberContextualText("$scope:query")
    var legacy by remember(legacyStore) { mutableStateOf(legacyStore.credentials()) }
    var forgetId by remember { mutableStateOf<String?>(null) }
    val boxes = rememberSectionLoader(scope, { it: List<StorageBox> -> it.isEmpty() }) { repository.getStorageBoxes() }
    selectedId?.let { id -> StorageBoxDetailScreen(id, repository, onBack = { selectedId = null }); return }
    val context = LocalContext.current
    val matches = boxes.state.data.orEmpty().filter { it.name.orEmpty().contains(query.trim(), true) || it.id.toString() == query.trim() }
        .sortedWith(compareBy<StorageBox> { it.name.orEmpty().lowercase() }.thenBy { it.id })
    HCloudPullToRefresh(boxes.state.refreshing, boxes::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = boxes.state.hasSuccessfulResponse),
            contentPadding = PaddingValues(HCloudDesignTokens.contentInset),
            verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
            item(key = "scope") {
                Text(project.displayName, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.storage_boxes_project_token))
            }
            if (legacy.isNotEmpty()) item(key = "legacy") {
                Text(stringResource(R.string.storage_boxes_legacy_credentials_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.storage_boxes_legacy_credentials_message))
                legacy.forEach { credential ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(credential.displayName)
                        TextButton(onClick = { forgetId = credential.id }) { Text(stringResource(R.string.storage_boxes_legacy_credentials_remove)) }
                    }
                }
            }
            item(key = "search") { HCloudFormField(query, { query = it }, stringResource(R.string.ds_search)) }
            item(key = "state") { HCloudReadSection(boxes) {
                if (matches.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
            } }
            items(matches, key = { it.id }) { box ->
                val summary = box.stats?.let {
                    stringResource(R.string.storage_boxes_usage_formatted,
                        Formatter.formatFileSize(context, it.sizeData), Formatter.formatFileSize(context, it.size))
                } ?: stringResource(R.string.storage_boxes_stats_unavailable)
                HCloudResourceCard(ResourceCardModel("$scope:${box.id}", box.name ?: stringResource(R.string.storage_boxes_unnamed),
                    box.status ?: stringResource(R.string.storage_boxes_status_unknown), summary, stale = boxes.state.isStale), { selectedId = box.id })
            }
        }
    }
    forgetId?.let { pinnedId ->
        HCloudDialog(stringResource(R.string.storage_boxes_legacy_credentials_forget_title),
            stringResource(R.string.storage_boxes_legacy_credentials_forget_confirm), {
                legacyStore.removeCredential(pinnedId)
                legacy = legacyStore.credentials()
                forgetId = null
            }, { forgetId = null }) { Text(stringResource(R.string.storage_boxes_legacy_credentials_forget_message)) }
    }
}
