package com.hcloudcontrol

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.operations.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One shared review, durable journal and result for every protection-capable resource. */
@Composable
internal fun ResourceProtectionControls(resourceName: String, currentProtected: Boolean,
    adapter: ProtectionAdapter, onCompleted: () -> Unit, enabled: Boolean = true, currentKnown: Boolean = true) {
    var attemptProtected by remember(adapter.resource, adapter.id, adapter.field, LocalOperationContext.current) { mutableStateOf(currentProtected) }
    var attemptName by remember(adapter.resource, adapter.id, adapter.field, LocalOperationContext.current) { mutableStateOf(resourceName) }
    val exactAction = adapter.action + if (attemptProtected) "_disable" else "_enable"
    val session = rememberOperationSession(adapter.resource.identity, adapter.id.toString(), exactAction,
        listOf(attemptName, attemptProtected, adapter.field).hashCode().toLong() and 0xffffffffL)
    val activeContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed && enabled && currentKnown)
    var snapshot by remember(session) { mutableStateOf<ProtectionSnapshot?>(null) }
    var reviewTime by remember(session) { mutableStateOf(0L) }
    var busy by remember(session) { mutableStateOf(false) }
    var failure by remember(session) { mutableStateOf(false) }
    var resultVisible by remember(session) { mutableStateOf(session.record != null) }
    var progress by remember(session) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val record = remember(session, progress, resultVisible, busy) { session.record }
    LaunchedEffect(currentProtected, resourceName, resultVisible, snapshot) {
        if (!resultVisible && snapshot == null && !busy) { attemptProtected = currentProtected; attemptName = resourceName }
    }

    fun refreshAfterSuccess() {
        scope.launch {
            refreshOperationResource(session) {
                check(activeContext == session.context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { adapter.read() }
                check(activeContext == session.context)
                onCompleted()
            }
            progress++
        }
    }
    fun checkOriginalStatus() {
        if (busy || activeContext != session.context) return
        val original = session.record ?: return
        busy = true
        scope.launch {
            try {
                val id = original.providerActionId
                if (id == null) {
                    val current = adapter.read()
                    session.reconcileExistingEffect(current.id == adapter.id && current.protected == original.intent.action.endsWith("_enable"))
                } else {
                    val initial = adapter.repository.getAction(id)
                    session.observeOriginalAction(initial)
                    if (initial.status.lowercase() !in setOf("success", "error")) awaitCloudAction(initial,
                        { actionId -> adapter.repository.getAction(actionId).also { session.observeOriginalAction(it); progress++ } }, {})
                }
                if (session.record?.stage == OperationStage.SUCCEEDED) refreshAfterSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Throwable) { session.unknownIfPending(); failure = true }
            finally { busy = false; progress++ }
        }
    }
    Text(if (!currentKnown) stringResource(R.string.server_protection_unavailable) else stringResource(R.string.resource_protection_current,
        stringResource(if (currentProtected) R.string.common_protected_enabled else R.string.common_protected_disabled)),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!currentKnown && session.record != null) OutlinedButton(onClick = { resultVisible = true }) { Text(stringResource(R.string.operation_check_status)) }
    if (allowed) OutlinedButton(onClick = {
        if (busy) return@OutlinedButton
        if (session.record?.isPending == true) { resultVisible = true; return@OutlinedButton }
        busy = true; failure = false
        scope.launch {
            try {
                val fresh = adapter.read()
                fresh.requireUnchanged(ProtectionSnapshot(adapter.id, attemptName, attemptProtected))
                snapshot = fresh
                reviewTime = System.currentTimeMillis()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { failure = true }
            finally { busy = false }
        }
    }, enabled = !busy && session.configured && (resultVisible || (attemptProtected == currentProtected && attemptName == resourceName)), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (currentProtected) R.string.resource_protection_disable else R.string.resource_protection_enable))
    } else Text(stringResource(R.string.resource_protection_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (failure) Text(stringResource(R.string.resource_protection_failed), color = MaterialTheme.colorScheme.error)
    snapshot?.takeIf { !resultVisible }?.let { reviewed ->
        val desired = !reviewed.protected
        val fieldLabel = stringResource(if (adapter.field == ProtectionField.DELETE) R.string.server_protection_delete else R.string.server_protection_rebuild)
        val before = stringResource(if (reviewed.protected) R.string.common_protected_enabled else R.string.common_protected_disabled)
        val after = stringResource(if (desired) R.string.common_protected_enabled else R.string.common_protected_disabled)
        val title = stringResource(R.string.resource_protection_review_title, reviewed.name)
        val note = stringResource(R.string.server_protection_confirm_note)
        val identity = session.intent
        if (identity != null) {
            val model = remember(identity, reviewed, reviewTime, title, fieldLabel, before, after, note) {
                OperationReviewContent(identity, reviewTime, title, identity.resource.context.connectionId,
                    "${reviewed.name} · #${reviewed.id}", listOf(ReviewChange(fieldLabel, before, after)),
                    typedIdentity = reviewed.name.ifBlank { "#${reviewed.id}" },
                    acknowledgements = listOf(RiskAcknowledgement("protection", note)))
            }
            OperationReviewDialog(model, onConfirm = { proof ->
                if (!busy) {
                    busy = true; failure = false
                    scope.launch {
                        try {
                            adapter.read().requireUnchanged(reviewed)
                            session.beginReviewed(proof, allowed, activeContext)
                            resultVisible = true
                            val action = adapter.applyDesired(desired)
                            session.accepted(action)
                            session.observeOriginalAction(action)
                            progress++
                            awaitCloudAction(action,
                                { id -> adapter.repository.getAction(id).also { session.observeOriginalAction(it); progress++ } }, {})
                            if (session.record?.stage != OperationStage.SUCCEEDED) session.succeed()
                            refreshAfterSuccess()
                        } catch (cancelled: CancellationException) { throw cancelled }
                          catch (_: Throwable) { session.unknownIfPending(); failure = true; snapshot = null }
                        finally { busy = false; progress++ }
                    }
                }
            }, onDismiss = { snapshot = null }, submitEnabled = allowed && !busy)
        }
    }
    if (resultVisible && record != null) OperationResultDialog(stringResource(R.string.resource_protection_review_title, resourceName), record,
        record.intent.resource.context.connectionId, "$attemptName · #${adapter.id}",
        onClose = { resultVisible = false; snapshot = null },
        busy = busy, session = session,
        onCheckStatus = if (!busy) ::checkOriginalStatus else null,
        onRefreshResource = ::refreshAfterSuccess)
}
