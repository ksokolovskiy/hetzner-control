package com.hcloudcontrol.connections

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

enum class ConnectionCheckPhase { CHECKING, SAVING }
enum class ConnectionFailureKind { INVALID_INPUT, OFFLINE, INVALID_TOKEN, PERMISSION_DENIED, SERVICE, SECURE_STORAGE }

/** Never retain Throwable/message, raw response, token or generated credential in UI state. */
data class ConnectionFailure(
    val kind: ConnectionFailureKind,
    val httpStatus: Int? = null,
    val code: String? = null,
    val requestId: String? = null,
)

sealed interface VerifiedConnectionResult {
    data class Connected(val connectionId: String) : VerifiedConnectionResult
    data class Failed(val failure: ConnectionFailure) : VerifiedConnectionResult
    data object AlreadyChecking : VerifiedConnectionResult
}

/**
 * One form owns one instance. Credentials exist only as transient call arguments and closures.
 * A read verifies only its tested scope; it does not assert write permission or token scope.
 * Cancellation before secure persistence never saves. The synchronous local save callback is
 * deliberately non-suspending so a cancellation cannot split a single secure-store transaction.
 */
class VerifiedCloudConnectionCoordinator(
    private val checkRead: suspend (token: String, config: HCloudConnectionConfig) -> Unit,
    private val secureSave: (displayName: String, token: String, config: HCloudConnectionConfig) -> String,
) {
    private val checking = Mutex()

    suspend fun connect(
        displayName: String,
        token: String,
        config: HCloudConnectionConfig,
        onPhase: (ConnectionCheckPhase) -> Unit = {},
    ): VerifiedConnectionResult {
        if (!checking.tryLock()) return VerifiedConnectionResult.AlreadyChecking
        try {
            if (displayName.isBlank() || token.isBlank()) {
                return VerifiedConnectionResult.Failed(ConnectionFailure(ConnectionFailureKind.INVALID_INPUT))
            }
            currentCoroutineContext().ensureActive()
            onPhase(ConnectionCheckPhase.CHECKING)
            try {
                checkRead(token.trim(), config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return VerifiedConnectionResult.Failed(connectionReadFailure(failure))
            }
            currentCoroutineContext().ensureActive()
            onPhase(ConnectionCheckPhase.SAVING)
            currentCoroutineContext().ensureActive()
            return try {
                VerifiedConnectionResult.Connected(secureSave(displayName.trim(), token.trim(), config))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                VerifiedConnectionResult.Failed(ConnectionFailure(ConnectionFailureKind.SECURE_STORAGE))
            }
        } finally {
            checking.unlock()
        }
    }
}

private fun connectionReadFailure(error: Exception): ConnectionFailure {
    val api = (error as? HCloudApiException)?.failure
    val kind = when {
        api?.httpStatus == 401 -> ConnectionFailureKind.INVALID_TOKEN
        api?.httpStatus == 403 -> ConnectionFailureKind.PERMISSION_DENIED
        error is IOException -> ConnectionFailureKind.OFFLINE
        else -> ConnectionFailureKind.SERVICE
    }
    return ConnectionFailure(
        kind,
        httpStatus = api?.httpStatus,
        code = api?.code?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) },
        requestId = api?.requestId?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,128}")) },
    )
}
