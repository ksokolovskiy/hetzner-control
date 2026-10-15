package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Delete is never retried after sending. Only the original action or exact resource absence resolves it. */
internal class ServerDeletionAdapter(
    private val repository: HCloudRepository,
    private val coordinator: OperationCoordinator,
    private val currentContext: () -> OperationContext?,
    private val changesAllowed: () -> Boolean,
) {
    suspend fun submit(review: OperationReview, expectedName: String, onSending: suspend () -> Unit = {}): PersistedOperation {
        val identity = review.intent.resource
        require(identity.resourceType == "server" && review.intent.action == "delete")
        val id = requireNotNull(identity.resourceId).toLong()
        check(currentContext() == identity.context && changesAllowed())
        val fresh = repository.getServer(id)
        check(fresh.id == id && fresh.name == expectedName && fresh.protection?.delete == false)
        coordinator.beginSubmission(review, checkNotNull(currentContext()), review.intent.revision, changesAllowed())
        try {
            onSending()
            check(currentContext() == identity.context && changesAllowed())
            val action = repository.deleteServerWithAction(id)
            coordinator.accepted(review.intent.intentId, action?.id)
            if (action != null) reconcileAction(review.intent.intentId, action.id, action.status)
        } catch (failure: HCloudApiException) {
            val status = failure.failure.httpStatus
            if (status in 400..499 && status !in setOf(404, 408)) {
                coordinator.failed(review.intent.intentId, OperationDiagnostic(
                    if (status == 401 || status == 403) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            } else coordinator.outcomeUnknown(review.intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(review.intent.intentId) }
        return checkNotNull(coordinator.get(review.intent.intentId))
    }

    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        check(currentContext() == record.intent.resource.context)
        require(record.intent.action == "delete" && record.intent.resource.resourceType == "server")
        val actionId = record.providerActionId
        if (actionId != null) {
            val action = repository.getAction(actionId)
            return reconcileAction(record.intent.intentId, action.id, action.status)
        }
        try {
            repository.getServer(requireNotNull(record.intent.resource.resourceId).toLong())
        } catch (failure: HCloudApiException) {
            if (failure.failure.httpStatus == 404 && failure.failure.code == "not_found") {
                return coordinator.reconcile(record.intent.intentId,
                    OperationCoordinator.Reconciliation.ExistingResourceEffect(record.intent.resource, true))
            }
            throw failure
        }
        return coordinator.reconcile(record.intent.intentId, OperationCoordinator.Reconciliation.Ambiguous)
    }

    private fun reconcileAction(intentId: String, id: Long, status: String): PersistedOperation =
        coordinator.reconcile(intentId, OperationCoordinator.Reconciliation.ActionStatus(id, when (status.lowercase()) {
            "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
            "error" -> OperationCoordinator.Reconciliation.Status.FAILED
            else -> OperationCoordinator.Reconciliation.Status.RUNNING
        }))
}
