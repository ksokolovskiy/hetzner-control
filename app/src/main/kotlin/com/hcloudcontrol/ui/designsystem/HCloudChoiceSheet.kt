package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.R

/** Shared, accessible single-choice presentation for settings and feature pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HCloudChoiceSheet(title: String, items: List<ChoiceItem>, selectedId: String,
    onSelected: (String) -> Unit, onDismiss: () -> Unit, searchable: Boolean = false) {
    var query by rememberSaveable(title) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(HCloudDesignTokens.contentInset), verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (searchable) HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
            val matching = if (searchable) items.filter { it.label.contains(query.trim(), ignoreCase = true) } else items
            if (matching.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
            matching.forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = HCloudDesignTokens.minimumTarget)
                    .selectable(selected = item.id == selectedId, enabled = item.enabled,
                        role = Role.RadioButton, onClick = { onSelected(item.id); onDismiss() }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = item.id == selectedId, onClick = null, enabled = item.enabled)
                    Text(item.label, Modifier.weight(1f).padding(start = HCloudDesignTokens.spaceMedium), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
