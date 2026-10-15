package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.rememberDirtyFormExitGuard
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** One shared review/result lifecycle for routine server writes. No mutation retry is exposed. */
@Composable
internal fun ServerRoutineFlow(server: Server, repository: HCloudRepository,
    action: ServerRoutineAction, title: String, warning: String, canExecute: Boolean,
    onCompleted: () -> Unit, onDismiss: () -> Unit,
    initialDraft: ServerRoutineDraft = ServerRoutineDraft(),
    onReviewBack: (() -> Unit)? = null, prepareImmediately: Boolean = false) {
    val context by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed && canExecute)
    val adapter = remember(repository) { ServerRoutineAdapter(repository, { context }, { allowed }) }
    var description by rememberSaveable(LocalOperationContext.current, server.id, action) { mutableStateOf(initialDraft.snapshotDescription) }
    val draft = initialDraft.copy(snapshotDescription = description)
    val originalServer = remember(LocalOperationContext.current, server.id, action) { server }
    val session = rememberOperationSession("server", originalServer.id.toString(), action.identity(draft), originalServer.hashCode().toLong() and 0x7fffffffL)
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var fresh by remember { mutableStateOf<Server?>(null) }
    var review by remember { mutableStateOf<OperationReviewContent?>(null) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val record = remember(session, tick, busy) { session.record }
    val exit = rememberDirtyFormExitGuard(isDirty = action == ServerRoutineAction.SNAPSHOT && description != initialDraft.snapshotDescription,
        onRetain = {}, onDiscard = { description = initialDraft.snapshotDescription }, onLeave = onDismiss,
        submitted = record != null, contextKey = session.context)
    DirtyFormExitPrompt(exit)
    val failureText = stringResource(R.string.common_action_failed)
    val stringResourceValueLabels = stringResource(R.string.server_metadata_labels_input)
    val stringResourceValuePlacement = stringResource(R.string.server_metadata_placement_keep)
    val isoLabel = stringResource(R.string.server_iso_title)
    val noneLabel = stringResource(R.string.common_none)
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    suspend fun prepareReview() {
        val value = repository.getServer(originalServer.id)
        check(value.name == originalServer.name && value.id == originalServer.id && serverRoutineEligible(value, action))
        fresh = value
        review = OperationReviewContent(requireNotNull(session.intent), System.currentTimeMillis(), title,
            requireNotNull(context).connectionId, value.name,
            if (action in setOf(ServerRoutineAction.ISO_ATTACH, ServerRoutineAction.ISO_DETACH)) listOf(
            ReviewChange(isoLabel, value.attachedIso?.let(::isoReviewIdentity) ?: noneLabel,
                if (action == ServerRoutineAction.ISO_ATTACH) isoReviewIdentity(requireNotNull(draft.iso)) else noneLabel),
            ReviewChange(title, null, warning),
        ) else if (action == ServerRoutineAction.METADATA) listOfNotNull(
                draft.metadata?.name?.let { ReviewChange(title, value.name, it) },
                draft.metadata?.labels?.let { ReviewChange(stringResourceValueLabels, value.labels.entries.joinToString { "${it.key}=${it.value}" }, it.entries.joinToString { "${it.key}=${it.value}" }) },
                draft.metadata?.placementGroupId?.let { ReviewChange(stringResourceValuePlacement, value.placementGroupId?.toString(), it.toString()) },
            ) else listOf(ReviewChange(title, androidContext.getString(serverStatusLabelResource(value.status)), warning)),
            cost = if (action in setOf(ServerRoutineAction.SNAPSHOT, ServerRoutineAction.BACKUPS_ENABLE)) CostPresentation.Unavailable else null,
            typedIdentity = if (action in setOf(ServerRoutineAction.HARD_OFF, ServerRoutineAction.HARD_RESET, ServerRoutineAction.BACKUPS_DISABLE)) value.name else null,
            acknowledgements = listOf(RiskAcknowledgement("effect", warning)))
    }
    fun requestReview() {
        if (busy) return
        busy = true; error = false
        scope.launch {
            try { prepareReview() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { error = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(session, prepareImmediately) {
        if (prepareImmediately && session.record == null) requestReview()
    }
    suspend fun completeRead() {
        refreshOperationResource(session) {
            check(context == session.context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.getServer(originalServer.id) }
            check(context == session.context)
            onCompleted()
        }
    }
    fun checkStatus() {
        if (busy) return
        busy = true
        scope.launch {
            try { adapter.reconcile(session, action, draft); completeRead() }
            catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
            catch (_: Throwable) { session.unknownIfPending() }
            finally { busy = false; tick++ }
        }
    }
    if (record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId, originalServer.name,
            onClose = onDismiss, busy = busy, session = session,
            onCheckStatus = if (record.isPending) ::checkStatus else null,
            onRefreshResource = if (record.stage == OperationStage.SUCCEEDED) ({ scope.launch { completeRead(); tick++ } }) else null)
    } else if (review != null && fresh != null) {
        OperationReviewDialog(requireNotNull(review), onConfirm = { proof ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        adapter.submit(session, proof, requireNotNull(fresh), action, draft) { tick++ }
                        if (session.record?.isPending == true && session.record?.originalActionIds?.isNotEmpty() == true)
                            awaitOriginalOperationActions(session, repository)
                        completeRead()
                        if (session.record == null) { review = null; error = true }
                    } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
                    catch (_: Throwable) { session.unknownIfPending(); if (session.record == null) { review = null; error = true } }
                    finally { busy = false; tick++ }
                }
            }
        }, onDismiss = { if (!busy) { if (onReviewBack != null) onReviewBack() else { review = null; fresh = null } } }, submitEnabled = allowed && !busy)
    } else {
        HCloudDialog(title, stringResource(R.string.ds_operation_confirm), onConfirm = ::requestReview,
            onDismiss = { if (!busy) { if (onReviewBack != null) onReviewBack() else exit.requestLeave() } }, confirmEnabled = allowed && session.configured && !busy &&
            (action != ServerRoutineAction.SNAPSHOT || description.isNotBlank())) {
            Text(warning)
            if (action == ServerRoutineAction.SNAPSHOT) HCloudFormField(description, { description = it }, stringResource(R.string.snapshot_description_label), enabled = !busy)
            if (busy) CircularProgressIndicator()
            if (error) Text(failureText, color = MaterialTheme.colorScheme.error)
        }
    }
}
