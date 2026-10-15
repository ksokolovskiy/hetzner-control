package com.hcloudcontrol.operations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.HCloudApiException
import java.util.UUID

val LocalOperationContext = staticCompositionLocalOf<OperationContext?> { null }
val LocalOperationCoordinator = staticCompositionLocalOf<OperationCoordinator?> { null }

/** Presentation session only; the device-wide coordinator owns all mutation transitions. */
class OperationSession internal constructor(
    val context: OperationContext?,
    private val coordinator: OperationCoordinator?,
    val intent: OperationIntent?,
    private val reviewedAtMillis: Long,
    private val rotateAfterTerminalClose: (() -> Unit)? = null,
) {
    private var terminalClosed = false
    /** Only explicit terminal Close starts another deliberate attempt. Original journal is retained. */
    fun startNewAttemptAfterTerminalClose(): Boolean {
        if (terminalClosed || record?.stage !in setOf(OperationStage.SUCCEEDED, OperationStage.FAILED)) return false
        val rotate = rotateAfterTerminalClose ?: return false
        terminalClosed = true
        rotate()
        return true
    }
    val record: PersistedOperation? get() = intent?.let { coordinator?.get(it.intentId) }
    val configured: Boolean get() = coordinator != null && context != null && intent != null
    fun begin(changesAllowed: Boolean, currentContext: OperationContext?) {
        val identity = checkNotNull(intent) { "Operation context unavailable" }
        checkNotNull(coordinator).beginSubmission(OperationReview(identity, reviewedAtMillis, true),
            checkNotNull(currentContext), identity.revision, changesAllowed)
    }
    fun accepted(action: CloudAction) { checkNotNull(coordinator).accepted(checkNotNull(intent).intentId, action.id) }
    fun acceptedActions(actions: List<CloudAction>, createdResourceId: String?, secretIssued: Boolean) {
        val ids = actions.map { it.id }.distinct()
        checkNotNull(coordinator).accepted(checkNotNull(intent).intentId, ids.firstOrNull(), createdResourceId, ids.drop(1), secretIssued)
    }
    fun observeOriginalActions(actions: List<CloudAction>) {
        val original = checkNotNull(record)
        checkNotNull(coordinator).reconcile(original.intent.intentId, OperationCoordinator.Reconciliation.ActionStatuses(
            actions.associate { it.id to when (it.status.lowercase()) {
                "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
                "error" -> OperationCoordinator.Reconciliation.Status.FAILED
                else -> OperationCoordinator.Reconciliation.Status.RUNNING
            } },
        ))
    }
    fun beginReviewed(review: OperationReview, changesAllowed: Boolean, currentContext: OperationContext?) {
        check(review.intent == intent) { "Review identity changed" }
        checkNotNull(coordinator).beginSubmission(review, checkNotNull(currentContext), review.intent.revision, changesAllowed)
    }
    fun reconcileExistingEffect(confirmed: Boolean) {
        val identity = checkNotNull(intent)
        checkNotNull(coordinator).reconcile(identity.intentId,
            OperationCoordinator.Reconciliation.ExistingResourceEffect(identity.resource, confirmed))
    }
    fun reconcileCreatedResource(resourceId: String, confirmed: Boolean) {
        val identity = checkNotNull(intent)
        checkNotNull(coordinator).reconcile(identity.intentId, OperationCoordinator.Reconciliation.CreatedResourceEffect(
            identity.resource.copy(resourceId = resourceId), confirmed))
    }
    fun succeed() { checkNotNull(coordinator).succeeded(checkNotNull(intent).intentId) }
    fun refreshFailed() { checkNotNull(coordinator).refreshFailed(checkNotNull(intent).intentId) }
    fun refreshSucceeded() { checkNotNull(coordinator).refreshSucceeded(checkNotNull(intent).intentId) }
    fun unknownIfPending() {
        val current = record ?: return
        if (current.isPending) checkNotNull(coordinator).outcomeUnknown(current.intent.intentId)
    }
    /** Only an explicit rejection of the write itself proves failure; read errors never do. */
    fun rejectOrUnknown(throwable: Throwable) {
        val current = record ?: return
        val status = (throwable as? HCloudApiException)?.failure?.httpStatus
        if (current.stage == OperationStage.SENDING && status != null && status in 400..499 && status != 408) {
            checkNotNull(coordinator).failed(current.intent.intentId, OperationDiagnostic(
                if (status in setOf(401, 403)) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
        } else unknownIfPending()
    }
    fun observeOriginalAction(action: CloudAction) {
        val current = checkNotNull(record)
        checkNotNull(coordinator).reconcile(current.intent.intentId, OperationCoordinator.Reconciliation.ActionStatus(
            action.id, when (action.status.lowercase()) {
                "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
                "error" -> OperationCoordinator.Reconciliation.Status.FAILED
                else -> OperationCoordinator.Reconciliation.Status.RUNNING
            },
        ))
    }
}

@Composable
fun rememberOperationSession(resourceType: String, resourceId: String?, action: String, revision: Long): OperationSession {
    val context = LocalOperationContext.current
    val coordinator = LocalOperationCoordinator.current
    val identity = context?.let { ResourceIdentity(it, resourceType, resourceId) }
    var intentId by rememberSaveable(context?.connectionId, context?.endpoint, context?.environment, resourceType, resourceId, action, revision) {
        mutableStateOf(coordinator?.all()?.lastOrNull { it.isPending && it.intent.resource == identity && it.intent.action == action }?.intent?.intentId
            ?: UUID.randomUUID().toString())
    }
    // SavedState inputs are not evidence that a restored ID belongs to the current account.
    val scopedId = remember(context, coordinator, intentId, identity, action) {
        val saved = coordinator?.get(intentId)
        if (saved != null && (saved.intent.resource != identity || saved.intent.action != action)) {
            coordinator?.all()?.lastOrNull { it.isPending && it.intent.resource == identity && it.intent.action == action }?.intent?.intentId
                ?: UUID.randomUUID().toString()
        } else intentId
    }
    return remember(context, coordinator, scopedId, revision) {
        val existing = coordinator?.get(scopedId)
        OperationSession(context, coordinator, existing?.intent ?: identity?.let { OperationIntent(scopedId, it, action, revision) },
            System.currentTimeMillis(), rotateAfterTerminalClose = { intentId = UUID.randomUUID().toString() })
    }
}
