package com.hcloudcontrol

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory

/** Public editable metadata only; no checked acknowledgement or typed delete value survives. */
internal data class PlacementFormDraft(val name: String = "", val labelsText: String = "") {
    fun snapshot(scope: String): ArrayList<String> = arrayListOf("placement-draft-v1", scope, name, labelsText)
    companion object {
        fun initial(group: PlacementGroupInventory?) = PlacementFormDraft(group?.name.orEmpty(),
            group?.labels?.toSortedMap()?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty())
        fun restore(scope: String, values: List<String>, fallback: PlacementFormDraft): PlacementFormDraft =
            if (values.size == 4 && values[0] == "placement-draft-v1" && values[1] == scope)
                PlacementFormDraft(values[2], values[3]) else fallback
    }
}

@Composable
internal fun rememberPlacementFormDraft(scope: String, group: PlacementGroupInventory? = null): MutableState<PlacementFormDraft> {
    val initial = PlacementFormDraft.initial(group)
    val saver = remember(scope) { Saver<MutableState<PlacementFormDraft>, ArrayList<String>>(
        save = { it.value.snapshot(scope) }, restore = { mutableStateOf(PlacementFormDraft.restore(scope, it, initial)) }) }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(initial) }
}
