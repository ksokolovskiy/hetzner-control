package com.hcloudcontrol.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import com.hcloudcontrol.R
import com.hcloudcontrol.ui.designsystem.*
import java.time.Instant

data class ReviewChange(val label: String, val oldValue: String?, val newValue: String)
data class RiskAcknowledgement(val id: String, val label: String)

internal fun canDismissOperationResult(record: PersistedOperation): Boolean = record.canClose

/** One result shell: no duplicate footer, no Cancel semantics after a submitted write. */
@Composable
fun OperationResultDialog(title: String, record: PersistedOperation, projectLabel: String,
    resourceLabel: String, onClose: () -> Unit, busy: Boolean = false,
    onCheckStatus: (() -> Unit)? = null, onRefreshResource: (() -> Unit)? = null,
    onOpenResource: (() -> Unit)? = null, session: OperationSession? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {}) {
    val close: () -> Unit = { if (canDismissOperationResult(record)) {
        onClose()
        session?.startNewAttemptAfterTerminalClose()
    } }
    HCloudDialog(title, "", close, close, showActions = false) {
        OperationResultPresentation(record, projectLabel, resourceLabel, close,
            onCheckStatus = onCheckStatus.takeUnless { busy },
            onRefreshResource = onRefreshResource.takeUnless { busy },
            onOpenResource = onOpenResource.takeUnless { busy })
        extraContent()
    }
}

/** Immutable display snapshot from a fresh adapter read. Never include credentials or raw payloads. */
data class OperationReviewContent(
    val intent: OperationIntent,
    val reviewedAtMillis: Long,
    val title: String,
    val projectLabel: String,
    val resourceLabel: String,
    val effects: List<ReviewChange>,
    val cost: CostPresentation? = null,
    val typedIdentity: String? = null,
    val acknowledgements: List<RiskAcknowledgement> = emptyList(),
    val blockedReason: String? = null,
    val typedConfirmation: String? = null,
) {
    init {
        require(reviewedAtMillis >= 0)
        require(typedIdentity == null || typedIdentity.isNotBlank())
        require(typedConfirmation == null || typedConfirmation.isNotBlank())
        require(acknowledgements.map { it.id }.distinct().size == acknowledgements.size)
    }
}

/** Presentation only; adapter rechecks freshness/context/gate before the coordinator locks submission. */
@Composable
fun OperationReviewDialog(model: OperationReviewContent, onConfirm: (OperationReview) -> Unit,
    onDismiss: () -> Unit, submitEnabled: Boolean = true) {
    var typed by remember(model) { mutableStateOf("") }
    var confirmation by remember(model) { mutableStateOf("") }
    var checked by remember(model) { mutableStateOf(emptySet<String>()) }
    // Synchronous local lock closes the double-tap window before consumer recomposition.
    var handedOff by remember(model) { mutableStateOf(false) }
    val complete = (model.typedIdentity == null || typed == model.typedIdentity) &&
        (model.typedConfirmation == null || confirmation == model.typedConfirmation) &&
        model.acknowledgements.all { it.id in checked } && model.blockedReason == null
    HCloudDialog(model.title, stringResource(R.string.ds_operation_confirm), {
        if (complete && submitEnabled && !handedOff) {
            handedOff = true
            onConfirm(OperationReview(model.intent, model.reviewedAtMillis, acknowledged = true))
        }
    }, onDismiss, confirmEnabled = complete && submitEnabled && !handedOff) {
        OperationIdentity(model.projectLabel, model.resourceLabel, model.intent)
        model.effects.forEach { effect ->
            Text(effect.label, style = MaterialTheme.typography.labelLarge)
            Text(if (effect.oldValue == null) effect.newValue else stringResource(R.string.ds_operation_change, effect.oldValue, effect.newValue))
        }
        model.cost?.let { HCloudCostPresentation(it) }
        model.blockedReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        model.typedIdentity?.let { expected ->
            Text(stringResource(R.string.ds_operation_type_identity, expected))
            HCloudFormField(typed, { typed = it }, stringResource(R.string.ds_operation_identity), enabled = !handedOff)
        }
        model.acknowledgements.forEach { risk ->
            Row(Modifier.fillMaxWidth().heightIn(min = HCloudDesignTokens.minimumTarget)
                .toggleable(value = risk.id in checked, enabled = !handedOff, role = Role.Checkbox, onValueChange = { selected ->
                    checked = if (selected) checked + risk.id else checked - risk.id
                }), horizontalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
                Checkbox(checked = risk.id in checked, onCheckedChange = null, enabled = !handedOff)
                Text(risk.label, Modifier.weight(1f).padding(vertical = HCloudDesignTokens.spaceMedium))
            }
        }
        model.typedConfirmation?.let { expected ->
            Text(stringResource(R.string.ds_operation_type_identity, expected))
            HCloudFormField(confirmation, { confirmation = it }, stringResource(R.string.ds_operation_identity), enabled = !handedOff)
        }
    }
}

@Composable
private fun OperationIdentity(projectLabel: String, resourceLabel: String, intent: OperationIntent) {
    Text(projectLabel, style = MaterialTheme.typography.titleMedium)
    Text(intent.resource.context.endpoint, style = MaterialTheme.typography.bodySmall)
    Text(resourceLabel, style = MaterialTheme.typography.bodyLarge)
    Text(listOfNotNull(intent.resource.resourceType, intent.resource.resourceId, intent.action).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall)
}

/** Result actions are reads/navigation only. No mutation retry callback exists. */
@Composable
fun OperationResultPresentation(record: PersistedOperation, projectLabel: String, resourceLabel: String,
    onClose: () -> Unit, modifier: Modifier = Modifier, onCheckStatus: (() -> Unit)? = null,
    onRefreshResource: (() -> Unit)? = null, onOpenResource: (() -> Unit)? = null, closeEnabled: Boolean = true,
    session: OperationSession? = null) {
    Column(modifier.fillMaxWidth().padding(HCloudDesignTokens.contentInset),
        verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceLarge)) {
        val stageLabel = stringResource(when (record.stage) {
            OperationStage.VALIDATING -> R.string.ds_operation_validating
            OperationStage.REVIEW -> R.string.ds_operation_review
            OperationStage.SENDING -> R.string.ds_operation_sending
            OperationStage.ACCEPTED -> R.string.ds_operation_accepted
            OperationStage.RUNNING -> R.string.ds_operation_running
            OperationStage.SUCCEEDED -> R.string.ds_operation_succeeded
            OperationStage.FAILED -> R.string.ds_operation_failed
            OperationStage.OUTCOME_UNKNOWN -> R.string.ds_operation_unknown
        })
        Text(stageLabel, style = MaterialTheme.typography.titleMedium,
            color = when (record.stage) {
                OperationStage.SUCCEEDED -> hCloudSemanticColors.confirmed
                OperationStage.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        OperationIdentity(projectLabel, resourceLabel, record.intent)
        Text(Instant.ofEpochMilli(record.updatedAtMillis).toString(), style = MaterialTheme.typography.bodySmall)
        record.providerActionId?.let { Text(stringResource(R.string.ds_operation_action_id, it)) }
        record.createdResourceId?.let { Text(stringResource(R.string.ds_operation_resource_id, it)) }
        record.diagnostic?.let { diagnostic ->
            Text(stringResource(when (diagnostic.code) {
                OperationDiagnostic.Code.LOCAL_GATE -> R.string.ds_operation_local_gate
                OperationDiagnostic.Code.CONTEXT_CHANGED -> R.string.ds_operation_context_changed
                OperationDiagnostic.Code.STALE_REVIEW -> R.string.ds_operation_stale_review
                OperationDiagnostic.Code.PROVIDER_DENIED -> R.string.ds_operation_denied
                OperationDiagnostic.Code.PROVIDER_FAILED -> R.string.ds_operation_failed
                OperationDiagnostic.Code.TRANSPORT_LOST, OperationDiagnostic.Code.PROCESS_LOST -> R.string.ds_operation_unknown_explanation
                OperationDiagnostic.Code.READ_FAILED -> R.string.ds_read_error
            }))
            diagnostic.httpStatus?.let { Text(stringResource(R.string.ds_operation_http, it)) }
        }
        if (record.stage == OperationStage.OUTCOME_UNKNOWN) Text(stringResource(R.string.ds_operation_no_replay))
        if (record.stage == OperationStage.SUCCEEDED && record.refreshFailed) {
            Text(stringResource(R.string.ds_operation_success_read_failed))
            onRefreshResource?.let { action -> OutlinedButton(onClick = action) { Text(stringResource(R.string.ds_retry)) } }
        }
        if (record.isPending && record.stage != OperationStage.SENDING) {
            onCheckStatus?.let { action -> Button(onClick = action) { Text(stringResource(R.string.ds_operation_check_status)) } }
        }
        if (record.stage == OperationStage.SUCCEEDED) {
            onOpenResource?.let { action -> Button(onClick = action) { Text(stringResource(R.string.ds_operation_open_resource)) } }
        }
        OutlinedButton(onClick = { if (record.canClose && closeEnabled) { onClose(); session?.startNewAttemptAfterTerminalClose() } },
            enabled = record.canClose && closeEnabled) { Text(stringResource(R.string.ds_close)) }
    }
}
