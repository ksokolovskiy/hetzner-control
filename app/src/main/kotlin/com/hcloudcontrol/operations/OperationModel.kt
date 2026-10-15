package com.hcloudcontrol.operations

import java.net.URI
import com.hcloudcontrol.core.domain.model.OneTimeRootPassword

/** Durable identity contains identifiers only: never credentials, drafts or response bodies. */
data class OperationContext(val connectionId: String, val endpoint: String, val environment: Environment) {
    enum class Environment { CLOUD, TEST, S3 }
    init {
        require(connectionId.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
        val uri = URI(endpoint)
        require(uri.scheme in setOf("https", "http") && uri.host != null)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    }
}

data class ResourceIdentity(val context: OperationContext, val resourceType: String, val resourceId: String?) {
    init {
        require(resourceType.matches(Regex("[a-z][a-z0-9_]{0,63}")))
        require(resourceId == null || resourceId.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
    }
}

data class OperationIntent(val intentId: String, val resource: ResourceIdentity, val action: String, val revision: Long) {
    init {
        require(intentId.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
        require(action.matches(Regex("[a-z][a-z0-9_]{0,63}")))
        require(revision >= 0)
    }
}

enum class OperationStage { VALIDATING, REVIEW, SENDING, ACCEPTED, RUNNING, SUCCEEDED, FAILED, OUTCOME_UNKNOWN }

/** Deliberately excludes exception.message and provider response text. */
data class OperationDiagnostic(val code: Code, val httpStatus: Int? = null) {
    enum class Code { LOCAL_GATE, CONTEXT_CHANGED, STALE_REVIEW, PROVIDER_DENIED, PROVIDER_FAILED, TRANSPORT_LOST, PROCESS_LOST, READ_FAILED }
    init { require(httpStatus == null || httpStatus in 100..599) }
}

data class PersistedOperation(
    val intent: OperationIntent,
    val stage: OperationStage,
    val updatedAtMillis: Long,
    val providerActionId: Long? = null,
    val createdResourceId: String? = null,
    val diagnostic: OperationDiagnostic? = null,
    val refreshFailed: Boolean = false,
    val relatedActionIds: List<Long> = emptyList(),
    val secretIssued: Boolean = false,
) {
    init {
        require(updatedAtMillis >= 0)
        require(providerActionId == null || providerActionId > 0)
        require(relatedActionIds.size <= 100 && relatedActionIds.all { it > 0 } && relatedActionIds.distinct().size == relatedActionIds.size)
        require(providerActionId !in relatedActionIds)
        require(createdResourceId == null || createdResourceId.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
    }
    val isPending: Boolean get() = stage in setOf(OperationStage.SENDING, OperationStage.ACCEPTED, OperationStage.RUNNING, OperationStage.OUTCOME_UNKNOWN)
    val canClose: Boolean get() = stage != OperationStage.SENDING
    val originalActionIds: List<Long> get() = listOfNotNull(providerActionId) + relatedActionIds
}

/** Acknowledgements belong to the reviewed revision and are never persisted. */
data class OperationReview(val intent: OperationIntent, val reviewedAtMillis: Long, val acknowledged: Boolean)

interface OperationStore {
    fun load(): List<PersistedOperation>
    /** Must atomically replace all records, and throw on failure before submission starts. */
    fun save(records: List<PersistedOperation>)
}

/** One-time values stay in memory, are redacted in diagnostics, and are explicitly destroyed. */
class SecretResult(secret: CharArray) : AutoCloseable {
    private var value: CharArray? = secret.copyOf()
    var revealed: Boolean = false
        private set
    var acknowledged: Boolean = false
        private set
    val available: Boolean get() = value != null
    val canFinish: Boolean get() = acknowledged || !available
    fun reveal() { if (available) revealed = true }
    fun hide() { revealed = false }
    fun acknowledge() { acknowledged = true }
    /** Caller owns and must clear the returned copy after rendering/copying. */
    fun copyForExplicitUserAction(): CharArray = checkNotNull(value).copyOf()
    override fun close() { value?.fill('\u0000'); value = null; revealed = false }
    override fun toString(): String = "SecretResult([REDACTED])"
}

/** A missing/previously consumed value is a lost-secret result, never a reason to replay a write. */
fun consumeSecretResult(password: OneTimeRootPassword): SecretResult? {
    val chars = password.revealOnce()?.toCharArray() ?: return null
    return try { SecretResult(chars) } finally { chars.fill('\u0000') }
}
