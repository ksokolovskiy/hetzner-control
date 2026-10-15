package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.isValidObjectStorageBucketName
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.network.ObjectStorageBucketNotEmptyException
import com.hcloudcontrol.core.network.ObjectStorageRequestException
import com.hcloudcontrol.operations.*

internal enum class S3BucketAction(val wireName: String) { CREATE("create_bucket"), DELETE("delete_bucket") }
internal class S3PreconditionChanged : IllegalStateException()

/** Thin S3 adapter: durable transitions and duplicate locks belong to the shared coordinator. */
internal class S3BucketOperationAdapter(private val coordinator: OperationCoordinator,
    private val repository: ObjectStorageRepository, private val context: OperationContext,
    private val currentContext: () -> OperationContext?, private val changesAllowed: () -> Boolean) {
    suspend fun submit(review: OperationReview, onSending: suspend (PersistedOperation) -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.context == context && intent.resource.resourceType == "s3_bucket")
        val name = requireNotNull(intent.resource.resourceId)
        require(isValidObjectStorageBucketName(name))
        val action = S3BucketAction.entries.single { it.wireName == intent.action }
        check(currentContext() == context && changesAllowed())
        val exists = repository.listBuckets().any { it.name == name }
        if ((action == S3BucketAction.CREATE && exists) || (action == S3BucketAction.DELETE && !exists)) throw S3PreconditionChanged()
        val sending = coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, changesAllowed())
        try {
            onSending(sending)
            check(currentContext() == context && changesAllowed())
            when (action) { S3BucketAction.CREATE -> repository.createBucket(name); S3BucketAction.DELETE -> repository.deleteBucket(name) }
            coordinator.succeeded(intent.intentId)
        } catch (_: ObjectStorageBucketNotEmptyException) {
            coordinator.failed(intent.intentId, OperationDiagnostic(OperationDiagnostic.Code.PROVIDER_FAILED))
        } catch (failure: ObjectStorageRequestException) {
            val status = failure.httpStatus
            if (status != null && status in 400..499 && status != 408) {
                coordinator.failed(intent.intentId, OperationDiagnostic(
                    if (status == 401 || status == 403) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED,
                    status))
            } else coordinator.outcomeUnknown(intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(intent.intentId) }
        return checkNotNull(coordinator.get(intent.intentId))
    }
    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        require(record.intent.resource.context == context)
        check(currentContext() == context)
        val exists = repository.listBuckets().any { it.name == record.intent.resource.resourceId }
        return coordinator.reconcile(record.intent.intentId,
            if (record.intent.action == S3BucketAction.DELETE.wireName && !exists)
                OperationCoordinator.Reconciliation.ExistingResourceEffect(record.intent.resource, true)
            else OperationCoordinator.Reconciliation.Ambiguous)
    }
}
