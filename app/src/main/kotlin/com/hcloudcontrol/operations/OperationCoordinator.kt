package com.hcloudcontrol.operations

/** One device-wide coordinator. Feature adapters supply authoritative reads, never retry writes. */
class OperationCoordinator(private val store: OperationStore, private val now: () -> Long = System::currentTimeMillis) {
    private val records = linkedMapOf<String, PersistedOperation>()

    init {
        store.load().forEach { record ->
            require(records.put(record.intent.intentId, record) == null) { "Duplicate operation identity" }
        }
        val restored = records.values.map { record ->
            if (record.stage == OperationStage.SENDING) record.copy(
                stage = OperationStage.OUTCOME_UNKNOWN,
                diagnostic = OperationDiagnostic(OperationDiagnostic.Code.PROCESS_LOST),
                updatedAtMillis = now(),
            ) else record
        }
        if (restored != records.values.toList()) {
            store.save(restored)
            records.clear()
            restored.forEach { records[it.intent.intentId] = it }
        }
    }

    @Synchronized fun all(): List<PersistedOperation> = records.values.toList()
    @Synchronized fun get(intentId: String): PersistedOperation? = records[intentId]

    /** This persist-before-send lock is acquired synchronously before invoking any provider write. */
    @Synchronized fun beginSubmission(
        review: OperationReview,
        activeContext: OperationContext,
        currentRevision: Long,
        changesAllowed: Boolean,
        maxReviewAgeMillis: Long = 60_000,
    ): PersistedOperation {
        require(maxReviewAgeMillis >= 0)
        check(changesAllowed) { "Changes unavailable" }
        check(activeContext == review.intent.resource.context) { "Context changed" }
        check(review.acknowledged && currentRevision == review.intent.revision) { "Review invalidated" }
        check(now() - review.reviewedAtMillis in 0..maxReviewAgeMillis) { "Review expired" }
        check(review.intent.intentId !in records) { "Intent already submitted; reconcile original operation" }
        check(records.values.none { it.isPending && conflicts(it, review.intent) }) { "Resource operation pending" }
        return commit(PersistedOperation(review.intent, OperationStage.SENDING, now()))
    }

    @Synchronized fun accepted(intentId: String, actionId: Long? = null, createdResourceId: String? = null,
        relatedActionIds: List<Long> = emptyList(), secretIssued: Boolean = false): PersistedOperation = transition(
        intentId, setOf(OperationStage.SENDING), OperationStage.ACCEPTED,
    ) { it.copy(providerActionId = actionId, createdResourceId = createdResourceId, relatedActionIds = relatedActionIds, secretIssued = secretIssued) }

    @Synchronized fun running(intentId: String): PersistedOperation = transition(
        intentId, setOf(OperationStage.ACCEPTED, OperationStage.RUNNING), OperationStage.RUNNING,
    )

    /** Immediate success must come from an authoritative endpoint adapter, not HTTP acceptance alone. */
    @Synchronized fun succeeded(intentId: String): PersistedOperation = transition(
        intentId, setOf(OperationStage.SENDING, OperationStage.ACCEPTED, OperationStage.RUNNING), OperationStage.SUCCEEDED,
    ) { check(it.relatedActionIds.isEmpty()) { "Multi-action success requires aggregate evidence" }; it }

    @Synchronized fun failed(intentId: String, diagnostic: OperationDiagnostic): PersistedOperation {
        require(diagnostic.code in setOf(OperationDiagnostic.Code.PROVIDER_DENIED, OperationDiagnostic.Code.PROVIDER_FAILED)) {
            "Only authoritative provider rejection or failure can mark a submitted operation failed"
        }
        return transition(intentId, setOf(OperationStage.SENDING, OperationStage.ACCEPTED, OperationStage.RUNNING), OperationStage.FAILED) {
            it.copy(diagnostic = diagnostic)
        }
    }

    @Synchronized fun outcomeUnknown(intentId: String): PersistedOperation = transition(
        intentId, setOf(OperationStage.SENDING, OperationStage.ACCEPTED, OperationStage.RUNNING, OperationStage.OUTCOME_UNKNOWN), OperationStage.OUTCOME_UNKNOWN,
    ) { it.copy(diagnostic = OperationDiagnostic(OperationDiagnostic.Code.TRANSPORT_LOST)) }

    @Synchronized fun refreshFailed(intentId: String): PersistedOperation {
        val record = requireNotNull(records[intentId])
        check(record.stage == OperationStage.SUCCEEDED)
        return commit(record.copy(refreshFailed = true, updatedAtMillis = now()))
    }

    /** A successful read-only retry clears only the refresh warning, never the write outcome. */
    @Synchronized fun refreshSucceeded(intentId: String): PersistedOperation {
        val record = requireNotNull(records[intentId])
        check(record.stage == OperationStage.SUCCEEDED)
        return commit(record.copy(refreshFailed = false, updatedAtMillis = now()))
    }

    sealed interface Reconciliation {
        data object Ambiguous : Reconciliation
        /** Adapter must match this exact provider action to the original pinned operation. */
        data class ActionStatus(val actionId: Long, val status: Status) : Reconciliation
        data class ActionStatuses(val statuses: Map<Long, Status>) : Reconciliation
        /** Resource reads prove an effect for the pinned existing ID, never a matching mutable name. */
        data class ExistingResourceEffect(val resource: ResourceIdentity, val effectConfirmed: Boolean) : Reconciliation
        /** Creation response returned this exact immutable ID before the later read. */
        data class CreatedResourceEffect(val resource: ResourceIdentity, val effectConfirmed: Boolean) : Reconciliation
        enum class Status { RUNNING, SUCCEEDED, FAILED }
    }

    @Synchronized fun reconcile(intentId: String, evidence: Reconciliation): PersistedOperation {
        val record = requireNotNull(records[intentId])
        check(record.isPending)
        val stage = when (evidence) {
            Reconciliation.Ambiguous -> OperationStage.OUTCOME_UNKNOWN
            is Reconciliation.ActionStatus -> {
                check(record.relatedActionIds.isEmpty()) { "A multi-action operation requires aggregate evidence" }
                check(record.providerActionId != null && record.providerActionId == evidence.actionId)
                when (evidence.status) {
                    Reconciliation.Status.RUNNING -> OperationStage.RUNNING
                    Reconciliation.Status.SUCCEEDED -> OperationStage.SUCCEEDED
                    Reconciliation.Status.FAILED -> OperationStage.FAILED
                }
            }
            is Reconciliation.ActionStatuses -> {
                val expected = record.originalActionIds.toSet()
                check(expected.isNotEmpty() && evidence.statuses.keys.all { it in expected })
                when {
                    evidence.statuses.keys != expected -> OperationStage.OUTCOME_UNKNOWN
                    evidence.statuses.values.any { it == Reconciliation.Status.FAILED } -> OperationStage.FAILED
                    evidence.statuses.values.all { it == Reconciliation.Status.SUCCEEDED } -> OperationStage.SUCCEEDED
                    else -> OperationStage.RUNNING
                }
            }
            is Reconciliation.ExistingResourceEffect -> {
                check(record.intent.resource.resourceId != null && evidence.resource == record.intent.resource)
                if (evidence.effectConfirmed) OperationStage.SUCCEEDED else OperationStage.OUTCOME_UNKNOWN
            }
            is Reconciliation.CreatedResourceEffect -> {
                check(record.intent.resource.resourceId == null && record.createdResourceId != null)
                check(evidence.resource == record.intent.resource.copy(resourceId = record.createdResourceId))
                if (evidence.effectConfirmed) OperationStage.SUCCEEDED else OperationStage.OUTCOME_UNKNOWN
            }
        }
        return commit(record.copy(stage = stage, updatedAtMillis = now(), diagnostic =
            if (stage == OperationStage.FAILED) OperationDiagnostic(OperationDiagnostic.Code.PROVIDER_FAILED) else null))
    }

    private fun conflicts(record: PersistedOperation, right: OperationIntent): Boolean {
        val left = record.intent
        val targetId = left.resource.resourceId ?: record.createdResourceId
        return if (left.resource.resourceId == null && right.resource.resourceId == null) {
            left.resource.context == right.resource.context &&
                left.resource.resourceType == right.resource.resourceType && left.action == right.action
        } else targetId != null && left.resource.copy(resourceId = targetId) == right.resource
    }

    private fun transition(id: String, allowed: Set<OperationStage>, stage: OperationStage, update: (PersistedOperation) -> PersistedOperation = { it }): PersistedOperation {
        val current = requireNotNull(records[id])
        check(current.stage in allowed) { "Invalid operation transition" }
        return commit(update(current).copy(stage = stage, updatedAtMillis = now()))
    }

    private fun commit(record: PersistedOperation): PersistedOperation {
        val updated = LinkedHashMap(records).apply { put(record.intent.intentId, record) }
        store.save(updated.values.toList())
        records.clear()
        records.putAll(updated)
        return record
    }
}
