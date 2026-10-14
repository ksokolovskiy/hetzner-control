package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerRebuildSpec
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.launch

/** These controls exist only behind the user's global high-impact operations setting. */
@Composable
internal fun ServerDestructiveOperationsSection(
    server: Server,
    repository: HCloudRepository,
    readOnly: Boolean,
    onDeleted: () -> Unit,
    onCompleted: () -> Unit,
) {
    var deleteOpen by remember(server.id) { mutableStateOf(false) }
    var rebuildOpen by remember(server.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_high_impact_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.server_high_impact_note), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { rebuildOpen = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.server_rebuild_action))
        }
        OutlinedButton(onClick = { deleteOpen = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.server_delete_action), color = MaterialTheme.colorScheme.error)
        }
    }
    if (deleteOpen) DeleteServerDialog(server, repository, { deleteOpen = false }, onDeleted)
    if (rebuildOpen) RebuildServerDialog(server, repository, { rebuildOpen = false }, onCompleted)
}

@Composable private fun DeleteServerDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var name by remember(server.id) { mutableStateOf("") }
    var acknowledgement by remember(server.id) { mutableStateOf("") }
    var submitting by remember(server.id) { mutableStateOf(false) }
    var error by remember(server.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errorText = stringResource(R.string.server_delete_error)
    val canDelete = name == server.name && acknowledgement == "DELETE"
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.server_delete_title, server.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.server_delete_warning), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.server_delete_type_name, server.name))
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_delete_name_label)) }, enabled = !submitting, singleLine = true)
            Text(stringResource(R.string.server_delete_type_word))
            OutlinedTextField(acknowledgement, { acknowledgement = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_delete_word_label)) }, enabled = !submitting, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            submitting = true; error = null
            scope.launch { try { repository.deleteServer(server.id); onDeleted() } catch (_: Throwable) { error = errorText; submitting = false } }
        }, enabled = canDelete && !submitting) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.server_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable private fun RebuildServerDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var images by remember(server.id) { mutableStateOf<List<ImageOption>?>(null) }
    var imageId by remember(server.id) { mutableStateOf<Long?>(null) }
    var expanded by remember(server.id) { mutableStateOf(false) }
    var typedName by remember(server.id) { mutableStateOf("") }
    var submitting by remember(server.id) { mutableStateOf(false) }
    var error by remember(server.id) { mutableStateOf<String?>(null) }
    var progress by remember(server.id) { mutableStateOf<String?>(null) }
    var oneTimePassword by remember(server.id) { mutableStateOf<String?>(null) }
    var loadingFailed by remember(server.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val errorText = stringResource(R.string.server_rebuild_error)
    LaunchedEffect(server.id) { try { images = repository.getSystemImages() } catch (_: Throwable) { loadingFailed = true } }
    val image = images?.firstOrNull { it.id == imageId }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.server_rebuild_title, server.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.server_rebuild_warning), color = MaterialTheme.colorScheme.error)
            when { loadingFailed -> Text(stringResource(R.string.server_rebuild_images_error), color = MaterialTheme.colorScheme.error)
                images == null -> Row { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.size(10.dp)); Text(stringResource(R.string.server_rebuild_images_loading)) }
                else -> androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(image?.let { "${it.name} — ${it.description}" } ?: stringResource(R.string.server_rebuild_select_image)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { images!!.forEach { candidate -> DropdownMenuItem(text = { Text("${candidate.name} — ${candidate.description}") }, onClick = { imageId = candidate.id; expanded = false }) } }
                }
            }
            Text(stringResource(R.string.server_rebuild_type_name, server.name))
            OutlinedTextField(typedName, { typedName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_delete_name_label)) }, enabled = !submitting, singleLine = true)
            progress?.let { Text(it) }
            oneTimePassword?.let { password ->
                Text(stringResource(R.string.server_rebuild_password_warning), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(password, {}, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_rebuild_password_label)) }, readOnly = true, singleLine = true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            val selected = image ?: return@Button
            submitting = true; error = null; progress = context.getString(R.string.common_sending_request)
            scope.launch { try {
                val result = repository.rebuildServer(server.id, ServerRebuildSpec(selected.id.toString()))
                awaitServerManagementAction(repository, result.action.id) { progress = context.getString(R.string.action_status_format, it) }
                oneTimePassword = result.password?.revealOnce()
                if (oneTimePassword == null) onCompleted()
            } catch (_: Throwable) { error = errorText; submitting = false } }
        }, enabled = image != null && typedName == server.name && !submitting && oneTimePassword == null) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.server_rebuild_confirm)) } },
        dismissButton = { TextButton(onClick = { if (oneTimePassword != null) onCompleted() else onDismiss() }, enabled = !submitting) { Text(stringResource(if (oneTimePassword != null) R.string.server_rebuild_done else R.string.common_cancel)) } },
    )
}
