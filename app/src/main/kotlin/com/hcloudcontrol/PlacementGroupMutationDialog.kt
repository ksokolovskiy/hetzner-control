package com.hcloudcontrol

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class PlacementMutationMode { CREATE, EDIT, DELETE, ADD, REMOVE }

/** One form/review/result consumer, parameterized by typed resource intent. */
@Composable
internal fun PlacementGroupMutationDialog(repository: HCloudRepository, group: PlacementGroupInventory?,
    mode: PlacementMutationMode, serverId: Long? = null, onDismiss: () -> Unit, onCompleted: () -> Unit,
    draftState: MutableState<PlacementFormDraft>? = null) {
    val draftScope = "placement-form:${LocalOperationContext.current}:${group?.id}:$mode"
    val localDraft = rememberPlacementFormDraft(draftScope, group)
    val draft = draftState ?: localDraft
    val name = draft.value.name
    val labelsText = draft.value.labelsText
    val labels = parseResourceLabels(labelsText)
    val change = if (name.isBlank() || labels == null) null else when (mode) {
        PlacementMutationMode.CREATE -> PlacementChange.Create(CreatePlacementGroupSpec(name.trim(), labels = labels))
        PlacementMutationMode.EDIT -> PlacementChange.Edit(checkNotNull(group), PlacementGroupPatch(name.trim(), labels))
        PlacementMutationMode.DELETE -> PlacementChange.Delete(checkNotNull(group))
        PlacementMutationMode.ADD, PlacementMutationMode.REMOVE -> PlacementChange.Member(checkNotNull(group), checkNotNull(serverId), mode == PlacementMutationMode.ADD)
    }
    val action = when (mode) {
        PlacementMutationMode.CREATE -> "create"
        PlacementMutationMode.EDIT -> "edit"
        PlacementMutationMode.DELETE -> "delete"
        PlacementMutationMode.ADD -> "add_server_$serverId"
        PlacementMutationMode.REMOVE -> "remove_server_$serverId"
    }
    val session = rememberOperationSession("placement_group", group?.id?.toString(), action, (change?.hashCode() ?: 0).toLong() and 0xffffffffL)
    val adapter = remember(repository) { PlacementGroupMutationAdapter(repository) }
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val scope = rememberCoroutineScope()
    var busy by remember(session) { mutableStateOf(false) }
    var failed by remember(session) { mutableStateOf(false) }
    var review by remember(session) { mutableStateOf<OperationReviewContent?>(null) }
    var progress by remember(session) { mutableStateOf(0) }
    val record = remember(session, busy, progress) { session.record }
    val exitGuard = com.hcloudcontrol.ui.navigation.rememberDirtyFormExitGuard(
        isDirty = mode in setOf(PlacementMutationMode.CREATE, PlacementMutationMode.EDIT) && draft.value != PlacementFormDraft.initial(group),
        onRetain = {}, onDiscard = { draft.value = PlacementFormDraft.initial(group) }, onLeave = onDismiss,
        submitted = record != null, contextKey = draftScope)
    DirtyFormExitPrompt(exitGuard)
    val title = stringResource(when (mode) {
        PlacementMutationMode.CREATE -> R.string.placement_groups_create_title
        PlacementMutationMode.EDIT -> R.string.placement_groups_edit
        PlacementMutationMode.DELETE -> R.string.placement_groups_delete
        else -> R.string.placement_groups_manage_members
    })
    val warning = stringResource(when (mode) {
        PlacementMutationMode.CREATE -> R.string.placement_groups_create_ack
        PlacementMutationMode.EDIT -> R.string.placement_groups_edit_ack
        PlacementMutationMode.DELETE -> R.string.placement_groups_delete_ack
        PlacementMutationMode.ADD -> R.string.placement_groups_add_warning
        PlacementMutationMode.REMOVE -> R.string.placement_groups_remove_warning
    })
    val labelsLabel = stringResource(R.string.placement_groups_labels)
    val nameLabel = stringResource(R.string.placement_groups_name)
    val membersLabel = stringResource(R.string.placement_groups_members_title)
    val typeLabel = stringResource(R.string.placement_groups_type_label)
    if (record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId,
            group?.let { "${it.name} · #${it.id}" } ?: record.createdResourceId.orEmpty(),
            onClose = { if (record.stage == OperationStage.SUCCEEDED) onCompleted() else onDismiss() }, busy = busy, session = session,
            onCheckStatus = {
                if (!busy && currentContext == session.context) { busy = true; scope.launch {
                    try {
                        val id = record.providerActionId
                        if (id != null) {
                            val original = repository.getAction(id); session.observeOriginalAction(original)
                            if (original.status !in setOf("success", "error")) awaitCloudAction(original,
                                { actionId -> repository.getAction(actionId).also { session.observeOriginalAction(it); progress++ } }, {})
                        } else when (val evidence = withContext(Dispatchers.IO) { OriginalCloudResourceStatusReader(repository).read(record) }) {
                            is OperationCoordinator.Reconciliation.CreatedResourceEffect -> session.reconcileCreatedResource(checkNotNull(evidence.resource.resourceId), evidence.effectConfirmed)
                            is OperationCoordinator.Reconciliation.ExistingResourceEffect -> session.reconcileExistingEffect(evidence.effectConfirmed)
                            else -> session.unknownIfPending()
                        }
                    } catch (_: Throwable) { failed = true; session.unknownIfPending() }
                    busy = false; progress++
                } }
            }) { if (failed) Text(stringResource(R.string.placement_groups_change_error), color = MaterialTheme.colorScheme.error) }
        return
    }
    review?.let { frozen ->
        OperationReviewDialog(frozen, { proof ->
            val intent = change ?: return@OperationReviewDialog
            if (!busy) { busy = true; scope.launch {
                try {
                    withContext(Dispatchers.IO) { adapter.validate(intent) }
                    session.beginReviewed(proof, allowed, currentContext)
                    val response = withContext(Dispatchers.IO) {
                        check(allowed && currentContext == session.context)
                        adapter.execute(intent)
                    }
                    if (response.action != null || response.createdId != null) {
                        session.acceptedActions(listOfNotNull(response.action), response.createdId?.toString(), false)
                        response.action?.let { original ->
                            session.observeOriginalAction(original)
                            awaitCloudAction(original, { id -> repository.getAction(id).also { session.observeOriginalAction(it); progress++ } }, {})
                        }
                    }
                    if (session.record?.stage != OperationStage.SUCCEEDED) session.succeed()
                } catch (error: Throwable) { session.rejectOrUnknown(error); failed = true; review = null }
                busy = false; progress++
            } }
        }, { review = null }, submitEnabled = allowed && !busy)
        return
    }
    HCloudDialog(title, stringResource(R.string.placement_groups_review_change), {
        val frozenChange = change
        if (frozenChange != null && !busy && allowed) { busy = true; failed = false; scope.launch {
            try {
                withContext(Dispatchers.IO) { adapter.validate(frozenChange) }
                val identity = checkNotNull(session.intent)
                review = OperationReviewContent(identity, System.currentTimeMillis(), title, identity.resource.context.connectionId,
                    group?.let { "${it.name} · #${it.id}" } ?: name.trim(),
                    listOf(ReviewChange(typeLabel, null, "spread"), ReviewChange(labelsLabel,
                        group?.labels?.entries?.sortedBy { it.key }?.joinToString { "${it.key}=${it.value}" },
                        checkNotNull(labels).entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }),
                        ReviewChange("ID", null, listOfNotNull(group?.id, serverId).joinToString(" · ")),
                        ReviewChange(nameLabel, group?.name, name.trim()),
                        ReviewChange(membersLabel, null, group?.serverIds?.sorted()?.joinToString().orEmpty())),
                    typedIdentity = if (mode == PlacementMutationMode.DELETE) group?.name else null,
                    acknowledgements = listOf(RiskAcknowledgement("placement", warning)))
            } catch (_: Throwable) { failed = true }
            busy = false
        } }
    }, { if (!busy) exitGuard.requestLeave() }, confirmEnabled = change != null && allowed && !busy && session.configured) {
        if (mode == PlacementMutationMode.CREATE || mode == PlacementMutationMode.EDIT) {
            HCloudFormField(name, { draft.value = draft.value.copy(name = it) }, stringResource(R.string.placement_groups_name),
                error = stringResource(R.string.placement_groups_name_required).takeIf { name.isBlank() }, enabled = !busy)
            HCloudFormField(labelsText, { draft.value = draft.value.copy(labelsText = it) }, stringResource(R.string.placement_groups_labels),
                error = if (labels == null) stringResource(R.string.placement_groups_labels_invalid) else null, enabled = !busy)
            Text(stringResource(R.string.placement_groups_create_type))
        } else Text(warning)
        if (busy) CircularProgressIndicator()
        if (failed) Text(stringResource(R.string.placement_groups_change_error), color = MaterialTheme.colorScheme.error)
    }
}
