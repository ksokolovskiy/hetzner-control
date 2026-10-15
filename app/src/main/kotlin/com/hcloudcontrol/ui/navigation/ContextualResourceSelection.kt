package com.hcloudcontrol.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** The scope includes immutable owner, endpoint/environment and resource type, never a name. */
fun resourceSelectionSnapshot(scope: String, resourceId: Long?): ArrayList<String> {
    require(scope.isNotBlank())
    require(resourceId == null || resourceId > 0)
    return arrayListOf("resource-id-v1", scope, resourceId?.toString().orEmpty())
}

/** Restore checks the saved scope itself; rememberSaveable inputs alone cannot do that. */
fun restoreResourceSelection(scope: String, snapshot: List<String>): Long? {
    if (snapshot.size != 3 || snapshot[0] != "resource-id-v1" || snapshot[1] != scope) return null
    return snapshot[2].toLongOrNull()?.takeIf { it > 0 }
}

@Composable
fun rememberContextualResourceId(scope: String): MutableState<Long?> {
    val saver = remember(scope) {
        Saver<MutableState<Long?>, ArrayList<String>>(
            save = { resourceSelectionSnapshot(scope, it.value) },
            restore = { mutableStateOf(restoreResourceSelection(scope, it)) },
        )
    }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(null) }
}

/** Safe route visibility also validates context across process restoration. */
@Composable
fun rememberContextualFlag(scope: String): MutableState<Boolean> {
    val saver = remember(scope) {
        Saver<MutableState<Boolean>, ArrayList<String>>(
            save = { arrayListOf("context-flag-v1", scope, it.value.toString()) },
            restore = { mutableStateOf(restoreContextualFlag(scope, it)) },
        )
    }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(false) }
}

fun restoreContextualFlag(scope: String, snapshot: List<String>): Boolean =
    snapshot.size == 3 && snapshot[0] == "context-flag-v1" && snapshot[1] == scope && snapshot[2] == "true"
