package com.hcloudcontrol.ui.designsystem

import com.hcloudcontrol.ui.state.*
import com.hcloudcontrol.R

fun SectionReadError.messageResource(): Int = when (kind) {
    SectionReadFailureKind.MISSING -> R.string.ds_missing
    SectionReadFailureKind.DENIED -> R.string.ds_denied
    SectionReadFailureKind.AUTHENTICATION -> R.string.ds_authentication
    SectionReadFailureKind.OFFLINE -> R.string.ds_offline
    SectionReadFailureKind.SERVICE -> R.string.ds_read_error
}

/** Stale metadata remains visible, but its read failure is presented alongside it. */
fun SectionState<*>.presentationKind(): SectionKind = when {
    hasSuccessfulResponse && emptyReason != null -> SectionKind.EMPTY
    hasSuccessfulResponse -> SectionKind.CONTENT
    phase != SectionPhase.ERROR -> SectionKind.LOADING
    error?.kind == SectionReadFailureKind.MISSING -> SectionKind.MISSING
    error?.kind == SectionReadFailureKind.DENIED -> SectionKind.DENIED
    error?.kind == SectionReadFailureKind.AUTHENTICATION -> SectionKind.AUTHENTICATION
    error?.kind == SectionReadFailureKind.OFFLINE -> SectionKind.OFFLINE
    else -> SectionKind.ERROR
}
