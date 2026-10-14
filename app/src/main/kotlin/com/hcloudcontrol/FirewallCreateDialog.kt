package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CreateFirewallSpec
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deliberately creates an unattached Firewall only. Rule authoring is deferred until a complete
 * rule editor can make the full-ruleset replacement explicit; an empty inbound ruleset is shown
 * as the documented DROP-by-default policy instead of being silently assumed.
 */
@Composable
internal fun FirewallCreateDialog(repository: HCloudRepository, onDismiss: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var reviewed by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failure = stringResource(R.string.firewall_create_failed)
    val operationsAllowed = LocalHighImpactOperations.current.isAllowed
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.firewall_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.firewall_create_intro))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.firewall_create_name)) },
                    enabled = !submitting,
                    singleLine = true,
                )
                Text(stringResource(R.string.firewall_create_no_rules_warning), color = MaterialTheme.colorScheme.error)
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reviewed, onCheckedChange = { reviewed = it }, enabled = !submitting)
                    Text(stringResource(R.string.firewall_create_no_rules_ack), modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!operationsAllowed) return@Button
                    submitting = true; error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.createFirewall(CreateFirewallSpec(name = name.trim())) }
                            onCreated()
                        } catch (throwable: Throwable) {
                            error = safeApiFailureMessage(throwable, failure)
                            submitting = false
                        }
                    }
                },
                enabled = operationsAllowed && name.isNotBlank() && reviewed && !submitting,
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.firewall_create_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}
