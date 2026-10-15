package com.hcloudcontrol

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

private data class ServerIsoCatalogue(val options: List<IsoOption>, val inventorySize: Int)

/** Current media is independent of catalogue reads; selection/detail/review share one mutation path. */
@Composable
internal fun ServerIsoSection(server: Server, repository: HCloudRepository, readOnly: Boolean,
    onActionCompleted: () -> Unit) {
    val context = LocalOperationContext.current
    val allowed = LocalHighImpactOperations.current.isAllowed && !readOnly
    var managing by rememberSaveable(context, server.id) { mutableStateOf(false) }
    var selectedId by rememberSaveable(context, server.id) { mutableStateOf<Long?>(null) }
    var detailOpen by rememberSaveable(context, server.id) { mutableStateOf(false) }
    var action by remember(context, server.id) { mutableStateOf<ServerRoutineAction?>(null) }
    var actionIso by remember(context, server.id) { mutableStateOf<IsoOption?>(null) }
    var query by rememberSaveable(context, server.id) { mutableStateOf("") }
    val catalogueScroll = rememberSaveable(context, server.id, saver = ScrollState.Saver) { ScrollState(0) }
    val detailScroll = rememberSaveable(context, server.id, saver = ScrollState.Saver) { ScrollState(0) }
    val loader = rememberSectionLoader("${context}:server:${server.id}:iso", isEmpty = { value: ServerIsoCatalogue -> value.inventorySize == 0 }) {
        val architecture = server.hardware?.architecture ?:
            repository.getServerTypes().firstOrNull { it.name == server.serverType }?.architecture
        check(architecture != null)
        val all = repository.getIsos()
        ServerIsoCatalogue(all.filter { it.architecture == architecture }.sortedBy { it.name.lowercase() }, all.size)
    }
    val selected = loader.state.data?.options?.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_iso_title), style = MaterialTheme.typography.titleLarge)
        Text(server.attachedIso?.let { stringResource(R.string.server_iso_attached, isoReviewIdentity(it)) } ?: stringResource(R.string.server_iso_none))
        if (allowed) {
            if (server.attachedIso != null) OutlinedButton(onClick = { action = ServerRoutineAction.ISO_DETACH }) {
                Text(stringResource(R.string.server_iso_detach))
            } else OutlinedButton(onClick = { managing = true }) { Text(stringResource(R.string.server_iso_attach)) }
        }
    }
    val operation = action
    if (operation != null) {
        ServerRoutineFlow(server, repository, operation,
            stringResource(if (operation == ServerRoutineAction.ISO_ATTACH) R.string.server_iso_attach_confirm_title else R.string.server_iso_detach_confirm_title),
            stringResource(if (operation == ServerRoutineAction.ISO_ATTACH) R.string.server_iso_attach_warning else R.string.server_iso_detach_warning),
            allowed, onActionCompleted, { action = null; managing = false; detailOpen = false },
            ServerRoutineDraft(iso = actionIso),
            onReviewBack = { action = null; if (operation == ServerRoutineAction.ISO_ATTACH) { managing = true; detailOpen = true } },
            prepareImmediately = true)
    } else if (managing && detailOpen) {
        HCloudDialog(selected?.name ?: stringResource(R.string.server_iso_details_label),
            stringResource(R.string.server_iso_review_attach),
            { selected?.let { actionIso = it; action = ServerRoutineAction.ISO_ATTACH } },
            { detailOpen = false }, confirmEnabled = allowed && selected != null && loader.state.canOfferMutation && server.attachedIso == null,
            scrollState = detailScroll) {
            HCloudReadSection(loader) { inventory ->
                val iso = inventory.options.firstOrNull { it.id == selectedId }
                if (iso == null) HCloudSectionState(SectionPresentation(SectionKind.MISSING))
                else {
                    Text(isoReviewIdentity(iso), style = MaterialTheme.typography.titleMedium)
                    iso.description?.let { Text(it) }
                    iso.type?.let { Text(it) }
                    iso.architecture?.let { Text(stringResource(R.string.server_iso_architecture, it)) }
                    Text(stringResource(R.string.server_iso_attach_warning))
                }
            }
        }
    } else if (managing) {
        HCloudDialog(stringResource(R.string.server_iso_dialog_title, server.name),
            stringResource(R.string.server_iso_details_label), { if (selected != null) detailOpen = true },
            { managing = false }, confirmEnabled = selected != null, scrollState = catalogueScroll) {
            HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
            HCloudReadSection(loader) { isos ->
                val matching = isos.options.filter { query.isBlank() || it.name.contains(query, true) || it.description?.contains(query, true) == true }
                if (matching.isEmpty()) HCloudSectionState(SectionPresentation(if (isos.options.isEmpty()) SectionKind.NO_ELIGIBLE else SectionKind.NO_RESULTS))
                matching.forEach { iso ->
                    HCloudResourceCard(ResourceCardModel(iso.id.toString(), isoReviewIdentity(iso),
                        if (selectedId == iso.id) "✓" else "", listOfNotNull(iso.description, iso.type, iso.architecture).joinToString(" · ")),
                        onClick = { selectedId = iso.id; detailOpen = true })
                }
            }
        }
    }
}
