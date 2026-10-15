package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*

internal class SshKeyOperationAdapter(private val coordinator: OperationCoordinator, private val repository: HCloudRepository,
    private val context: OperationContext, private val currentContext: () -> OperationContext?, private val allowed: () -> Boolean) {
    suspend fun submit(review: OperationReview, create: CreateSshKeySpec? = null, expected: SshKeyOption? = null,
        onSending: suspend (PersistedOperation) -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.context == context && intent.resource.resourceType == "ssh_key")
        require((create != null) != (expected != null))
        check(currentContext() == context && allowed())
        val keys = repository.getSshKeys()
        if (create != null) {
            require(intent.action == "create_ssh_key" && intent.resource.resourceId == null)
            val validated = validateSshPublicKey(create.publicKey) as? SshPublicKeyValidation.Valid
                ?: error("Invalid public key")
            check(keys.none { it.fingerprint == validated.fingerprint || it.fingerprint.equals(validated.md5Fingerprint, true) }) {
                "SSH public key already exists"
            }
        } else {
            require(intent.action == "delete_ssh_key" && intent.resource.resourceId == expected?.id.toString())
            check(keys.singleOrNull { it.id == expected?.id } == expected) { "SSH key changed" }
        }
        val sending = coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, allowed())
        try {
            onSending(sending)
            check(currentContext() == context && allowed())
            if (create != null) {
                val created = repository.createSshKey(create)
                coordinator.accepted(intent.intentId, createdResourceId = created.id.toString())
            } else repository.deleteSshKey(checkNotNull(expected).id)
            coordinator.succeeded(intent.intentId)
        } catch (error: HCloudApiException) {
            val status = error.failure.httpStatus
            if (status in 400..499 && status != 408) coordinator.failed(intent.intentId,
                OperationDiagnostic(if (status == 401 || status == 403) OperationDiagnostic.Code.PROVIDER_DENIED
                    else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            else coordinator.outcomeUnknown(intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(intent.intentId) }
        return checkNotNull(coordinator.get(intent.intentId))
    }

    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        check(currentContext() == context && record.intent.resource.context == context)
        val keys = repository.getSshKeys()
        val targetId = record.intent.resource.resourceId?.toLongOrNull()
        val evidence = if (record.intent.action == "delete_ssh_key" && targetId != null && keys.none { it.id == targetId }) {
            OperationCoordinator.Reconciliation.ExistingResourceEffect(record.intent.resource, true)
        } else OperationCoordinator.Reconciliation.Ambiguous
        return coordinator.reconcile(record.intent.intentId, evidence)
    }
}
