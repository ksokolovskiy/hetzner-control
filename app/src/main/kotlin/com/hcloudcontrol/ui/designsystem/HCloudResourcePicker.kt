package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.hcloudcontrol.R

data class ResourcePickerItem(val id: String, val label: String, val summary: String? = null, val enabled: Boolean = true)
data class ResourcePickerState(val contextKey: String, val items: List<ResourcePickerItem>,
    val loading: Boolean = false, val safeError: String? = null)

/** IDs are immutable resource IDs, never names; selection returns into the existing draft. */
@Composable
fun HCloudResourcePicker(title: String, state: ResourcePickerState, selectedId: String?,
    onSelect: (String) -> Unit, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null,
    searchAutoFocus: Boolean = false) {
    var query by rememberSaveable(state.contextKey) { mutableStateOf("") }
    val focus = remember(state.contextKey) { FocusRequester() }
    LaunchedEffect(state.contextKey, searchAutoFocus) { if (searchAutoFocus) focus.requestFocus() }
    val choices = state.items.filter { item ->
        query.isBlank() || item.label.contains(query.trim(), true) || item.summary.orEmpty().contains(query.trim(), true)
    }
    HCloudDialog(title, stringResource(R.string.ds_close), onDismiss, onDismiss, showDismissAction = false) {
        HCloudFormField(query, { query = it }, stringResource(R.string.ds_search),
            Modifier.focusRequester(focus), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false))
        val kind = when {
            state.loading -> SectionKind.LOADING
            state.safeError != null -> SectionKind.ERROR
            state.items.none { it.enabled } -> SectionKind.NO_ELIGIBLE
            choices.isEmpty() -> SectionKind.NO_RESULTS
            else -> SectionKind.CONTENT
        }
        HCloudSectionState(SectionPresentation(kind, state.safeError), modifier = Modifier.selectableGroup(),
            onRetry = if (state.safeError != null) onRetry else null) {
            choices.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = HCloudDesignTokens.minimumTarget)
                    .selectable(selected = selectedId == item.id, enabled = item.enabled, role = Role.RadioButton,
                        onClick = { onSelect(item.id); onDismiss() }),
                    horizontalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
                    RadioButton(selected = selectedId == item.id, onClick = null, enabled = item.enabled)
                    Column(Modifier.weight(1f).padding(vertical = HCloudDesignTokens.spaceMedium)) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                        item.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
