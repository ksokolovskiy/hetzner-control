package com.hcloudcontrol

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** Public, nonsecret form fields only. Checkboxes, typed confirmations and operations are excluded. */
internal data class FirewallFormDraft(val name: String = "", val port: String = "", val sources: String = "", val description: String = "") {
    val isDirty: Boolean get() = name.isNotEmpty() || port.isNotEmpty() || sources.isNotEmpty() || description.isNotEmpty()
    fun snapshot(scope: String): ArrayList<String> = arrayListOf("firewall-draft-v1", scope, name, port, sources, description)
    companion object {
        fun restore(scope: String, values: List<String>): FirewallFormDraft =
            if (values.size == 6 && values[0] == "firewall-draft-v1" && values[1] == scope)
                FirewallFormDraft(values[2], values[3], values[4], values[5]) else FirewallFormDraft()
    }
}

/** Owned by the inventory/detail, not the removable dialog, so Retain actually retains the draft. */
@Composable
internal fun rememberFirewallFormDraft(scope: String): MutableState<FirewallFormDraft> {
    val saver = remember(scope) { Saver<MutableState<FirewallFormDraft>, ArrayList<String>>(
        save = { it.value.snapshot(scope) }, restore = { mutableStateOf(FirewallFormDraft.restore(scope, it)) }) }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(FirewallFormDraft()) }
}
