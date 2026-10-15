package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.FirewallInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

/** Canonical inventory; all mutations and detail entrances share one feature owner. */
@Composable
internal fun FirewallInventoryScreen(repository: HCloudRepository, projectId: String, onBack: () -> Unit) {
    val context = "firewalls:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    val inventory = rememberSectionLoader(context, isEmpty = { it: List<FirewallInventory> -> it.isEmpty() }) { repository.getFirewalls() }
    var query by rememberSaveable(context) { mutableStateOf("") }
    var selectedId by rememberContextualResourceId(context)
    var creating by remember(context) { mutableStateOf(false) }
    val draft = rememberFirewallFormDraft(context)
    selectedId?.let { id -> FirewallDetailById(id, repository) { selectedId = null; inventory.refresh() }; return }
    BackHandler(enabled = LocalNavigationChrome.current == null) { onBack() }
    HCloudPullToRefresh(inventory.state.refreshing, inventory::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(context, contentReady = inventory.state.data != null), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(inventory) {}
                if (LocalHighImpactOperations.current.isAllowed) OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.firewall_create_action))
                }
            }
            val all = inventory.state.data.orEmpty()
            val visible = all.filter { firewall -> query.isBlank() || firewall.name.contains(query.trim(), true) ||
                firewall.labels.any { (key, value) -> "$key=$value".contains(query.trim(), true) } }
            if (all.isNotEmpty() && visible.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(visible.sortedWith(compareBy<FirewallInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { firewall ->
                HCloudResourceCard(ResourceCardModel("$context:${firewall.id}", firewall.name,
                    stringResource(R.string.network_firewall_rules, firewall.rules.size, firewall.appliedTo.size),
                    firewall.labels.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }), { selectedId = firewall.id })
            }
        }
    }
    if (creating) FirewallMutationDialog(repository, null, FirewallMutationMode.CREATE, { creating = false },
        { id -> creating = false; draft.value = FirewallFormDraft(); selectedId = id; inventory.refresh() }, draft)
}
