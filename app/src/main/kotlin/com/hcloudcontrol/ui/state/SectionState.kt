package com.hcloudcontrol.ui.state

/** Safe, vetted read error metadata; never carry raw response bodies or request headers. */
enum class SectionReadFailureKind { OFFLINE, AUTHENTICATION, DENIED, MISSING, SERVICE }
data class SectionReadError(val code: String, val permissionDenied: Boolean = false) {
    val kind: SectionReadFailureKind get() = when {
        permissionDenied || code == "denied" -> SectionReadFailureKind.DENIED
        code == "offline" -> SectionReadFailureKind.OFFLINE
        code == "authentication" -> SectionReadFailureKind.AUTHENTICATION
        code == "missing" -> SectionReadFailureKind.MISSING
        else -> SectionReadFailureKind.SERVICE
    }
}

enum class EmptyReason { INVENTORY, FILTERED_NO_RESULTS, NO_ELIGIBLE_CANDIDATES, NO_CONNECTION }
enum class SectionPhase { INITIAL, LOADING, READY, EMPTY, ERROR, STALE }

/**
 * Independent section state: a failed history load cannot erase successful resource metadata.
 * A successful empty collection is data; a null value means no successful response yet.
 * Instances must be keyed by context plus destination (and section for detail subsections).
 */
data class SectionState<T>(
    val data: T? = null,
    val updatedAtMillis: Long? = null,
    val phase: SectionPhase = SectionPhase.INITIAL,
    val refreshing: Boolean = false,
    val error: SectionReadError? = null,
    val emptyReason: EmptyReason? = null,
) {
    val hasSuccessfulResponse: Boolean get() = updatedAtMillis != null
    val isStale: Boolean get() = phase == SectionPhase.STALE
    /** Fresh provider preconditions are still required at execution time. */
    val canOfferMutation: Boolean get() = phase in setOf(SectionPhase.READY, SectionPhase.EMPTY) &&
        !refreshing && error == null && emptyReason != EmptyReason.NO_CONNECTION

    fun startRead(): SectionState<T> = copy(
        phase = if (hasSuccessfulResponse) phase else SectionPhase.LOADING,
        refreshing = hasSuccessfulResponse,
        error = null,
    )

    fun succeed(value: T, atMillis: Long, empty: EmptyReason? = null): SectionState<T> {
        require(atMillis >= 0)
        return SectionState(
            data = value,
            updatedAtMillis = atMillis,
            phase = if (empty == null) SectionPhase.READY else SectionPhase.EMPTY,
            emptyReason = empty,
        )
    }

    fun fail(failure: SectionReadError): SectionState<T> = copy(
        phase = if (hasSuccessfulResponse) SectionPhase.STALE else SectionPhase.ERROR,
        refreshing = false,
        error = failure,
    )
}
