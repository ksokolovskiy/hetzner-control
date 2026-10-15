package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.R

data class ChoiceItem(val id: String, val label: String, val enabled: Boolean = true)
data class MenuAction(val id: String, val label: String, val enabled: Boolean = true)

/** The caller owns validation/focus order; secret values must never enter saved drafts. */
@Composable
fun HCloudFormField(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, help: String? = null, error: String? = null,
    enabled: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false, trailingIcon: (@Composable () -> Unit)? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), enabled = enabled, isError = error != null,
        keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
        singleLine = singleLine, trailingIcon = trailingIcon, keyboardActions = keyboardActions,
        supportingText = if (error != null || help != null) ({ Text(error ?: help.orEmpty()) }) else null)
}

@Composable
fun HCloudTabs(items: List<ChoiceItem>, selectedId: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val selected = items.indexOfFirst { it.id == selectedId }
    require(selected >= 0) { "Tabs require a selected item from their choices" }
    ScrollableTabRow(selectedTabIndex = selected, modifier = modifier, edgePadding = 0.dp) {
        items.forEach { item ->
            Tab(selected = item.id == selectedId, enabled = item.enabled, onClick = { onSelected(item.id) },
                text = { Text(item.label) }, modifier = Modifier.heightIn(min = HCloudDesignTokens.minimumTarget))
        }
    }
}

@Composable
fun HCloudSegmentedControl(items: List<ChoiceItem>, selectedId: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Surface(onClick = { onSelected(item.id) }, enabled = item.enabled,
                shape = HCloudDesignTokens.compactShape,
                color = if (item.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = HCloudDesignTokens.minimumTarget).semantics {
                    role = Role.RadioButton
                    selected = item.id == selectedId
                }) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { Text(item.label) }
            }
        }
    }
}

@Composable
fun HCloudFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, enabled = enabled,
        modifier = modifier.heightIn(min = HCloudDesignTokens.minimumTarget))
}

@Composable
fun HCloudMenu(expanded: Boolean, actions: List<MenuAction>, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            DropdownMenuItem(text = { Text(action.label) }, enabled = action.enabled,
                modifier = Modifier.heightIn(min = HCloudDesignTokens.minimumTarget),
                onClick = { onDismiss(); onAction(action.id) })
        }
    }
}

@Composable
fun HCloudDialog(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, confirmEnabled: Boolean = true, dismissLabel: String? = null,
    securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    showActions: Boolean = true,
    scrollState: ScrollState? = null,
    showDismissAction: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    val resolvedScroll = scrollState ?: rememberScrollState()
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier, title = { Text(title) },
        properties = DialogProperties(securePolicy = securePolicy),
        text = { Column(Modifier.verticalScroll(resolvedScroll), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { if (showActions) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) } },
        dismissButton = { if (showActions && showDismissAction) TextButton(onClick = onDismiss) { Text(dismissLabel ?: stringResource(R.string.ds_cancel)) } })
}

@Composable
fun HCloudFormShell(title: String, submitLabel: String, onSubmit: () -> Unit, modifier: Modifier = Modifier,
    submitEnabled: Boolean = true, busy: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(HCloudDesignTokens.contentInset),
            verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.contentInset)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
        Button(onClick = onSubmit, enabled = submitEnabled && !busy,
            modifier = Modifier.fillMaxWidth().padding(HCloudDesignTokens.contentInset).heightIn(min = HCloudDesignTokens.minimumTarget)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(submitLabel)
        }
    }
}

/** Identity is immutable context+type+ID, never the mutable display name. No secrets belong here. */
data class ResourceCardModel(val identity: String, val title: String, val status: String,
    val summary: String, val updatedAt: String? = null, val stale: Boolean = false)

@Composable
fun HCloudResourceCard(model: ResourceCardModel, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier,
    menu: (@Composable () -> Unit)? = null) {
    val interaction = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Card(modifier = modifier.fillMaxWidth().heightIn(min = HCloudDesignTokens.minimumRow).then(interaction),
        shape = HCloudDesignTokens.cardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(HCloudDesignTokens.contentInset), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(model.title, style = MaterialTheme.typography.titleMedium)
                if (model.status.isNotBlank()) Text(model.status, style = MaterialTheme.typography.labelLarge)
                if (model.summary.isNotBlank()) Text(model.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.stale) Text(stringResource(R.string.ds_stale), color = MaterialTheme.colorScheme.error)
                model.updatedAt?.let { Text(stringResource(R.string.ds_updated, it), style = MaterialTheme.typography.bodySmall) }
            }
            menu?.invoke()
        }
    }
}

enum class SectionKind { LOADING, CONTENT, EMPTY, NO_RESULTS, NO_ELIGIBLE, NO_CONNECTION, DENIED, AUTHENTICATION, OFFLINE, ERROR, MISSING }
data class SectionPresentation(val kind: SectionKind, val detail: String? = null, val stale: Boolean = false,
    val updatedAt: String? = null, val refreshing: Boolean = false, val message: String? = null)

/** Read recovery belongs to unsuccessful or stale sections, never a healthy content block. */
fun shouldOfferSectionRetry(state: SectionPresentation): Boolean =
    !state.refreshing && state.kind != SectionKind.LOADING &&
        (state.stale || state.kind != SectionKind.CONTENT)

/** Independent subsection presentation: callers keep successfully loaded sibling sections mounted. */
@Composable
fun HCloudSectionState(state: SectionPresentation, modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(HCloudDesignTokens.contentInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.stale) Text(stringResource(R.string.ds_stale), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.updatedAt?.let { Text(stringResource(R.string.ds_updated, it), style = MaterialTheme.typography.bodySmall) }
        if (state.kind == SectionKind.CONTENT) content()
        else {
            if (state.kind == SectionKind.LOADING) CircularProgressIndicator()
            Text(state.message ?: stringResource(when (state.kind) {
                SectionKind.LOADING -> R.string.ds_loading
                SectionKind.EMPTY -> R.string.ds_empty
                SectionKind.NO_RESULTS -> R.string.ds_no_results
                SectionKind.NO_ELIGIBLE -> R.string.ds_no_eligible
                SectionKind.NO_CONNECTION -> R.string.ds_no_connection
                SectionKind.DENIED -> R.string.ds_denied
                SectionKind.AUTHENTICATION -> R.string.ds_authentication
                SectionKind.OFFLINE -> R.string.ds_offline
                SectionKind.MISSING -> R.string.ds_missing
                else -> R.string.ds_read_error
            }), style = MaterialTheme.typography.bodyLarge)
        }
        state.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (onRetry != null && shouldOfferSectionRetry(state)) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.ds_retry)) }
        }
    }
}
