package com.hcloudcontrol.ui.state

import androidx.compose.runtime.*
import kotlinx.coroutines.*

/** A single independent read owner. Refresh never discards a successful response. */
@Stable
class SectionLoader<T> internal constructor(
    private val scope: CoroutineScope,
    private val read: suspend () -> T,
    private val isEmpty: (T) -> Boolean,
) {
    var state by mutableStateOf(SectionState<T>())
        private set
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        state = state.startRead()
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { read() }
                ensureActive()
                state = state.succeed(result, System.currentTimeMillis(),
                    if (isEmpty(result)) EmptyReason.INVENTORY else null)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (failure: Exception) { state = state.fail(sectionReadFailure(failure)) }
        }
    }

    internal fun cancel() { job?.cancel() }
}

@Composable
fun <T> rememberSectionLoader(
    contextKey: String,
    isEmpty: (T) -> Boolean = { false },
    read: suspend () -> T,
): SectionLoader<T> {
    val scope = rememberCoroutineScope()
    val currentRead by rememberUpdatedState(read)
    val currentIsEmpty by rememberUpdatedState(isEmpty)
    val loader = remember(contextKey) { SectionLoader(scope, { currentRead() }, { currentIsEmpty(it) }) }
    LaunchedEffect(loader) { loader.refresh() }
    DisposableEffect(loader) { onDispose { loader.cancel() } }
    return loader
}
