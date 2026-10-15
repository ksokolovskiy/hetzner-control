package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository

internal enum class VolumeMutation { ATTACH, DETACH, RESIZE, DELETE }
internal data class VolumeMutationDraft(val kind: VolumeMutation, val targetServerId: Long? = null, val newSizeGb: Int? = null)

/** API preconditions follow the official cloud.spec.json volume and volume-action endpoints. */
internal class VolumeOperationAdapter(private val repository: HCloudRepository,
    private val coordinator: OperationCoordinator, private val currentContext: () -> OperationContext?,
    private val allowed: () -> Boolean) {
    suspend fun create(review: OperationReview, spec: CreateVolumeSpec, onSending: suspend () -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.resourceType == "volume" && intent.resource.resourceId == null && intent.action == "create")
        check(currentContext() == intent.resource.context && allowed())
        check(repository.getLocations().any { it.name == spec.location })
        coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, allowed())
        try {
            onSending()
            check(currentContext() == intent.resource.context && allowed())
            val result = repository.createVolume(spec)
            val actions = (listOf(result.action) + result.nextActions).distinctBy { it.id }
            coordinator.accepted(intent.intentId, result.action.id, result.volume.id.toString(), actions.drop(1).map { it.id })
            observeActions(intent.intentId, actions)
        } catch (error: HCloudApiException) {
            val status = error.failure.httpStatus
            if (status in 400..499 && status != 408) coordinator.failed(intent.intentId,
                OperationDiagnostic(if (status in setOf(401, 403)) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            else coordinator.outcomeUnknown(intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(intent.intentId) }
        return checkNotNull(coordinator.get(intent.intentId))
    }

    suspend fun submit(review: OperationReview, expected: VolumeInventory, draft: VolumeMutationDraft,
        onSending: suspend () -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.resourceType == "volume" && intent.resource.resourceId == expected.id.toString())
        require(intent.action == draft.kind.name.lowercase())
        check(currentContext() == intent.resource.context && allowed())
        val fresh = repository.getVolume(expected.id)
        check(fresh.id == expected.id && fresh.name == expected.name && fresh.location == expected.location &&
            fresh.sizeGb == expected.sizeGb && fresh.serverId == expected.serverId && fresh.deleteProtected == expected.deleteProtected)
        when (draft.kind) {
            VolumeMutation.ATTACH -> {
                check(fresh.serverId == null)
                val server = repository.getServer(requireNotNull(draft.targetServerId))
                check(server.id == draft.targetServerId && server.location == fresh.location)
            }
            VolumeMutation.DETACH -> check(fresh.serverId != null)
            VolumeMutation.RESIZE -> check(requireNotNull(draft.newSizeGb) > fresh.sizeGb)
            VolumeMutation.DELETE -> check(fresh.serverId == null && fresh.protectionKnown && !fresh.deleteProtected)
        }
        coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, allowed())
        try {
            onSending()
            check(currentContext() == intent.resource.context && allowed())
            val action = when (draft.kind) {
                VolumeMutation.ATTACH -> repository.attachVolume(fresh.id, requireNotNull(draft.targetServerId))
                VolumeMutation.DETACH -> repository.detachVolume(fresh.id)
                VolumeMutation.RESIZE -> repository.resizeVolume(fresh.id, fresh.sizeGb, requireNotNull(draft.newSizeGb))
                VolumeMutation.DELETE -> repository.deleteVolume(fresh.id)
            }
            if (action != null) {
                coordinator.accepted(intent.intentId, action.id)
                observe(intent.intentId, action)
            } else {
                check(draft.kind == VolumeMutation.DELETE)
                coordinator.succeeded(intent.intentId)
            }
        } catch (error: HCloudApiException) {
            val status = error.failure.httpStatus
            if (status in 400..499 && status != 408) coordinator.failed(intent.intentId,
                OperationDiagnostic(if (status in setOf(401, 403)) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            else coordinator.outcomeUnknown(intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(intent.intentId) }
        return checkNotNull(coordinator.get(intent.intentId))
    }

    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        check(currentContext() == record.intent.resource.context)
        if (record.originalActionIds.isNotEmpty()) return observeActions(record.intent.intentId,
            record.originalActionIds.map { repository.getAction(it) })
        // Without original action evidence, a generic volume read does not prove resize/attach effects.
        if (record.intent.action == "delete") {
            try { repository.getVolume(requireNotNull(record.intent.resource.resourceId).toLong()) }
            catch (error: HCloudApiException) {
                if (error.failure.httpStatus == 404 && error.failure.code == "not_found") return coordinator.reconcile(
                    record.intent.intentId, OperationCoordinator.Reconciliation.ExistingResourceEffect(record.intent.resource, true))
                throw error
            }
        }
        return coordinator.reconcile(record.intent.intentId, OperationCoordinator.Reconciliation.Ambiguous)
    }

    private fun observe(id: String, action: CloudAction): PersistedOperation = coordinator.reconcile(id,
        OperationCoordinator.Reconciliation.ActionStatus(action.id, when (action.status.lowercase()) {
            "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
            "error" -> OperationCoordinator.Reconciliation.Status.FAILED
            else -> OperationCoordinator.Reconciliation.Status.RUNNING
        }))

    private fun observeActions(id: String, actions: List<CloudAction>): PersistedOperation = coordinator.reconcile(id,
        OperationCoordinator.Reconciliation.ActionStatuses(actions.associate { it.id to when (it.status.lowercase()) {
            "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
            "error" -> OperationCoordinator.Reconciliation.Status.FAILED
            else -> OperationCoordinator.Reconciliation.Status.RUNNING
        } }))
}
