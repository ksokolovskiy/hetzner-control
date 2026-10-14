package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.DeleteProtectionPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.launch

/**
 * Shared deletion-protection control. It is not rendered as an actionable control until the
 * device-local high-impact permission is enabled, and re-checks that permission immediately
 * before the API call. The dialog remains open on failure so the user can retry or cancel.
 */
@Composable
internal fun ResourceProtectionControls(
    resourceName: String,
    deleteProtected: Boolean,
    repository: HCloudRepository,
    onChange: suspend (DeleteProtectionPatch) -> CloudAction,
    onCompleted: () -> Unit,
) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var reviewRequested by remember(resourceName, deleteProtected) { mutableStateOf(false) }
    val desiredProtected = !deleteProtected
    Text(
        stringResource(
            R.string.resource_protection_current,
            stringResource(if (deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled),
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (highImpactAllowed) {
        OutlinedButton(onClick = { reviewRequested = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (desiredProtected) R.string.resource_protection_enable else R.string.resource_protection_disable))
        }
    }
    if (reviewRequested) {
        ResourceProtectionReviewDialog(
            resourceName = resourceName,
            desiredProtected = desiredProtected,
            repository = repository,
            onChange = onChange,
            onDismiss = { reviewRequested = false },
            onCompleted = {
                reviewRequested = false
                onCompleted()
            },
        )
    }
}

@Composable
private fun ResourceProtectionReviewDialog(
    resourceName: String,
    desiredProtected: Boolean,
    repository: HCloudRepository,
    onChange: suspend (DeleteProtectionPatch) -> CloudAction,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    val allowed = LocalHighImpactOperations.current.isAllowed
    val expected = if (desiredProtected) "PROTECT" else "UNPROTECT"
    var typed by remember(resourceName, desiredProtected) { mutableStateOf("") }
    var submitting by remember(resourceName, desiredProtected) { mutableStateOf(false) }
    var failure by remember(resourceName, desiredProtected) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errorFallback = stringResource(R.string.resource_protection_failed)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.resource_protection_review_title, resourceName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (desiredProtected) R.string.resource_protection_review_enable else R.string.resource_protection_review_disable,
                        resourceName,
                    ),
                )
                Text(stringResource(R.string.resource_protection_type_confirmation, expected))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it; failure = null },
                    enabled = !submitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.resource_protection_confirmation_label)) },
                )
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!allowed || typed != expected) return@Button
                    submitting = true
                    failure = null
                    scope.launch {
                        try {
                            awaitCloudAction(repository, onChange(DeleteProtectionPatch(desiredProtected))) { }
                            onCompleted()
                        } catch (_: Throwable) {
                            failure = errorFallback
                            submitting = false
                        }
                    }
                },
                enabled = allowed && typed == expected && !submitting,
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.resource_protection_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}
