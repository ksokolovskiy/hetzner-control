package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository
import kotlinx.coroutines.CancellationException

/** Documented snapshot description accepts ASCII letters/digits and the listed punctuation only. */
internal fun validBoxSnapshotDescription(description: String?): Boolean = description == null ||
    (description.length <= 1000 && description.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_,:<>+#!()[]{} " })

/** One typed snapshot mutation path. No retry, secret data, inventory-based proof or local poller. */
internal class BoxSnapshotAdapter(private val repository: StorageBoxRepository,
    private val coordinator: OperationCoordinator, private val currentContext: () -> OperationContext?,
    private val allowed: () -> Boolean) {
    suspend fun submit(review: OperationReview, expected: StorageBox, spec: CreateStorageBoxSnapshotSpec,
        onSending: suspend () -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.resourceType == "storage_box" && intent.resource.resourceId == expected.id.toString() && intent.action == "create_snapshot")
        require(validBoxSnapshotDescription(spec.description))
        require(spec.labels?.all { validResourceLabel(it.key, it.value) } != false)
        check(currentContext() == intent.resource.context && allowed())
        val fresh = repository.getStorageBox(expected.id)
        check(fresh.id == expected.id && fresh.name == expected.name && fresh.status == expected.status)
        coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, allowed())
        try {
            onSending()
            check(currentContext() == intent.resource.context && allowed())
            val result = repository.createStorageBoxSnapshot(expected.id, spec)
            coordinator.accepted(intent.intentId, result.action.id, result.snapshot.id.toString())
            observe(intent.intentId, result.action)
        } catch (cancelled: CancellationException) {
            unknownIfPending(intent.intentId)
            throw cancelled
        } catch (failure: Throwable) {
            val status = (failure as? HCloudApiException)?.failure?.httpStatus
            if (coordinator.get(intent.intentId)?.stage == OperationStage.SENDING && status != null && status in 400..499 && status != 408)
                coordinator.failed(intent.intentId, OperationDiagnostic(
                    if (status in setOf(401, 403)) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            else unknownIfPending(intent.intentId)
        }
        return checkNotNull(coordinator.get(intent.intentId))
    }

    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        check(currentContext() == record.intent.resource.context)
        require(record.intent.resource.resourceType == "storage_box" && record.intent.action == "create_snapshot")
        val id = record.providerActionId ?: return coordinator.reconcile(record.intent.intentId, OperationCoordinator.Reconciliation.Ambiguous)
        return try { observe(record.intent.intentId, repository.getStorageBoxAction(id)) }
        catch (cancelled: CancellationException) { unknownIfPending(record.intent.intentId); throw cancelled }
        catch (failure: Throwable) { unknownIfPending(record.intent.intentId); throw failure }
    }

    private fun unknownIfPending(id: String) { if (coordinator.get(id)?.isPending == true) coordinator.outcomeUnknown(id) }
    private fun observe(id: String, action: CloudAction): PersistedOperation = coordinator.reconcile(id,
        OperationCoordinator.Reconciliation.ActionStatus(action.id, when (action.status.lowercase()) {
            "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
            "error" -> OperationCoordinator.Reconciliation.Status.FAILED
            else -> OperationCoordinator.Reconciliation.Status.RUNNING
        }))
}
