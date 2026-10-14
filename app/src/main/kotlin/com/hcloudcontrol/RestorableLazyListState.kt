package com.hcloudcontrol

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Keeps a list's viewport while a child detail screen replaces the list in the
 * composition.  The registry is also saveable, so a configuration recreation
 * does not turn a Back gesture into a jump to the beginning of an inventory.
 *
 * Keys are namespaced by the selected Cloud project.  This deliberately keeps
 * e.g. the Servers list in "Production" independent from the Servers list in
 * a test project, while a refresh can still reuse the closest stable item key.
 */
@Stable
internal class ScrollPositionRegistry internal constructor(
    restored: Map<String, ScrollPosition>,
) {
    private val positions = mutableStateMapOf<String, ScrollPosition>().apply { putAll(restored) }

    internal fun positionFor(key: String): ScrollPosition = positions[key] ?: ScrollPosition.Zero

    internal fun update(key: String, index: Int, offset: Int) {
        positions[key] = ScrollPosition(index, offset)
    }

    internal fun save(): ArrayList<String> = ArrayList<String>(positions.size * 3).also { values ->
        positions.toSortedMap().forEach { (key, value) ->
            values += key
            values += value.index.toString()
            values += value.offset.toString()
        }
    }

    internal companion object {
        val Saver: Saver<ScrollPositionRegistry, ArrayList<String>> = Saver(
            save = { it.save() },
            restore = { raw ->
                val restored = buildMap {
                    raw.chunked(3).forEach { entry ->
                        if (entry.size != 3) return@forEach
                        val index = entry[1].toIntOrNull()?.coerceAtLeast(0) ?: return@forEach
                        val offset = entry[2].toIntOrNull()?.coerceAtLeast(0) ?: return@forEach
                        put(entry[0], ScrollPosition(index, offset))
                    }
                }
                ScrollPositionRegistry(restored)
            },
        )
    }
}

internal data class ScrollPosition(val index: Int, val offset: Int) {
    companion object { val Zero = ScrollPosition(0, 0) }
}

internal val LocalScrollPositionRegistry = compositionLocalOf<ScrollPositionRegistry?> { null }
internal val LocalScrollProjectNamespace = compositionLocalOf { "global" }

internal fun scrollPositionKey(projectNamespace: String, screenKey: String): String =
    "$projectNamespace:$screenKey"

@Composable
internal fun rememberScrollPositionRegistry(): ScrollPositionRegistry = rememberSaveable(
    saver = ScrollPositionRegistry.Saver,
) { ScrollPositionRegistry(emptyMap()) }

/** Use this in every navigable inventory/catalog LazyColumn. */
@Composable
internal fun rememberRestorableLazyListState(screenKey: String): LazyListState {
    val registry = LocalScrollPositionRegistry.current
    val namespacedKey = scrollPositionKey(LocalScrollProjectNamespace.current, screenKey)
    val initial = registry?.positionFor(namespacedKey) ?: ScrollPosition.Zero
    val state = remember(namespacedKey) { LazyListState(initial.index, initial.offset) }

    // Record continuously rather than only on disposal: this covers process/configuration
    // recreation while a list is still visible, as well as detail navigation.
    LaunchedEffect(registry, namespacedKey, state) {
        snapshotFlow { ScrollPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .collect { position -> registry?.update(namespacedKey, position.index, position.offset) }
    }
    return state
}
