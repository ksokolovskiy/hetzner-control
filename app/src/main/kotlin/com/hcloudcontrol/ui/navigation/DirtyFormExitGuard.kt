package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.*

/** Local navigation only. This guard never cancels, retries or submits a provider operation. */
@Stable
class DirtyFormExitGuard internal constructor(
    private val dirty: () -> Boolean,
    private val submitted: () -> Boolean,
    private val retain: () -> Unit,
    private val discard: () -> Unit,
    private val leave: () -> Unit,
) {
    var awaitingChoice by mutableStateOf(false)
        private set

    fun requestLeave() {
        if (submitted() || !dirty()) leave() else awaitingChoice = true
    }

    fun retainAndLeave() {
        if (!awaitingChoice) return
        awaitingChoice = false
        retain()
        leave()
    }

    fun discardAndLeave() {
        if (!awaitingChoice) return
        awaitingChoice = false
        discard()
        leave()
    }

    fun stay() { awaitingChoice = false }
}

/** The caller owns a nonsecret draft; an already-submitted operation closes without discarding it. */
@Composable
fun rememberDirtyFormExitGuard(
    isDirty: Boolean,
    onRetain: () -> Unit,
    onDiscard: () -> Unit,
    onLeave: () -> Unit,
    submitted: Boolean = false,
    contextKey: Any? = Unit,
): DirtyFormExitGuard {
    val currentDirty by rememberUpdatedState(isDirty)
    val currentSubmitted by rememberUpdatedState(submitted)
    val currentRetain by rememberUpdatedState(onRetain)
    val currentDiscard by rememberUpdatedState(onDiscard)
    val currentLeave by rememberUpdatedState(onLeave)
    return remember(contextKey) { DirtyFormExitGuard({ currentDirty }, { currentSubmitted },
        { currentRetain() }, { currentDiscard() }, { currentLeave() }) }
}
