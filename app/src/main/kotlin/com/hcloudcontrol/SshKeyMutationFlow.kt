package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** One shared consumer flow for SSH create/delete; no key text enters saved state or journal. */
@Composable
internal fun SshKeyMutationFlow(repository: HCloudRepository, existing: SshKeyOption?,
    restoredRecord: PersistedOperation? = null, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalOperationContext.current ?: return
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val gate by rememberUpdatedState(LocalHighImpactOperations.current)
    val coordinator = LocalOperationCoordinator.current ?: return
    val scope = rememberCoroutineScope()
    val action = restoredRecord?.intent?.action ?: if (existing == null) "create_ssh_key" else "delete_ssh_key"
    val resource = ResourceIdentity(context, "ssh_key", existing?.id?.toString())
    val savedIntentId = rememberSaveable(context, action, existing?.id, restoredRecord?.intent?.intentId) {
        restoredRecord?.intent?.intentId ?: coordinator.all().lastOrNull {
            it.isPending && it.intent.resource == resource && it.intent.action == action
        }?.intent?.intentId ?: UUID.randomUUID().toString()
    }
    val intentId = remember(context, savedIntentId) {
        val restored = coordinator.get(savedIntentId)
        if (restored != null && (restored.intent.resource.context != context || restored.intent.resource.resourceType != "ssh_key"))
            UUID.randomUUID().toString() else savedIntentId
    }
    val intent = remember(context, intentId) { coordinator.get(intentId)?.intent ?: OperationIntent(intentId, resource, action, 0) }
    var name by remember(intentId) { mutableStateOf("") }
    var publicKey by remember(intentId) { mutableStateOf("") }
    var busy by remember(intentId) { mutableStateOf(false) }
    var error by remember(intentId) { mutableStateOf<String?>(null) }
    var review by remember(intentId) { mutableStateOf<OperationReviewContent?>(null) }
    var record by remember(intentId) { mutableStateOf(coordinator.get(intentId)) }
    val validation = remember(publicKey) { validateSshPublicKey(publicKey) }
    val fallback = stringResource(if (action == "create_ssh_key") R.string.ssh_key_create_failed else R.string.ssh_key_delete_failed)
    val title = stringResource(if (action == "create_ssh_key") R.string.ssh_key_create_title else R.string.ssh_key_delete_confirm)
    val fingerprintLabel = stringResource(R.string.ssh_redesign_fingerprint)
    val acknowledgment = stringResource(R.string.ssh_redesign_acknowledgment)
    val displayName = existing?.name ?: name
    val adapter = remember(context, coordinator, repository) { SshKeyOperationAdapter(coordinator, repository, context, { currentContext }, { gate.isAllowed }) }

    record?.let { current ->
        OperationResultDialog(title, current, current.intent.resource.context.connectionId,
            current.intent.resource.resourceId ?: current.createdResourceId ?: stringResource(R.string.ssh_redesign_new_key),
            onClose = onDismiss, busy = busy,
            onRefreshResource = {
                if (!busy && currentContext == current.intent.resource.context) {
                    busy = true
                    scope.launch {
                        try {
                            val refreshSession = OperationSession(current.intent.resource.context, coordinator, current.intent, 0)
                            val refreshed = refreshOperationResource(refreshSession) {
                                check(currentContext == current.intent.resource.context)
                                withContext(Dispatchers.IO) { repository.getSshKeys() }
                                check(currentContext == current.intent.resource.context)
                            }
                            record = coordinator.get(current.intent.intentId)
                            if (refreshed) { error = null; onChanged() } else error = fallback
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                          catch (_: Exception) { error = fallback }
                          finally { busy = false }
                    }
                }
            }, onCheckStatus = {
                if (!busy) { busy = true; scope.launch {
                    try { record = withContext(Dispatchers.IO) { adapter.reconcile(current) } }
                    catch (_: Throwable) { error = fallback }
                    busy = false
                } }
            }) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        return
    }
    review?.let { snapshot ->
        OperationReviewDialog(snapshot, { proof ->
            review = null; busy = true
            val spec = if (existing == null) CreateSshKeySpec(name.trim(), publicKey.trim()) else null
            scope.launch {
                try {
                    record = withContext(Dispatchers.IO) { adapter.submit(proof, spec, existing) { sending ->
                        withContext(Dispatchers.Main) { record = sending }
                    } }
                    if (record?.stage == OperationStage.SUCCEEDED) {
                        publicKey = ""
                        try { withContext(Dispatchers.IO) { repository.getSshKeys() }; onChanged() }
                        catch (_: Throwable) { coordinator.refreshFailed(intentId); record = coordinator.get(intentId) }
                    }
                } catch (_: Throwable) { error = fallback }
                busy = false
            }
        }, { review = null }, submitEnabled = gate.isAllowed)
        return
    }
    val valid = if (existing == null) name.isNotBlank() && validation is SshPublicKeyValidation.Valid else true
    HCloudDialog(title, stringResource(R.string.ssh_key_review), {
        if (!busy && valid && gate.isAllowed) {
            busy = true; error = null
            scope.launch {
                try {
                    val keys = withContext(Dispatchers.IO) { repository.getSshKeys() }
                    check(currentContext == context)
                    if (existing != null) check(keys.singleOrNull { it.id == existing.id } == existing)
                    review = OperationReviewContent(intent, System.currentTimeMillis(), title,
                        context.connectionId, displayName,
                        listOf(ReviewChange(fingerprintLabel, null,
                            existing?.fingerprint ?: (validation as SshPublicKeyValidation.Valid).fingerprint)),
                        typedIdentity = existing?.name,
                        acknowledgements = listOf(RiskAcknowledgement("effect", acknowledgment)))
                } catch (_: Throwable) { error = fallback }
                busy = false
            }
        }
    }, onDismiss, confirmEnabled = valid && !busy && gate.isAllowed) {
        if (existing == null) {
            HCloudFormField(name, { name = it }, stringResource(R.string.ssh_key_name), enabled = !busy)
            HCloudFormField(publicKey, { publicKey = it }, stringResource(R.string.ssh_key_public_key), enabled = !busy,
                error = if (publicKey.isBlank() || validation is SshPublicKeyValidation.Valid) null else stringResource(
                    if (validation == SshPublicKeyValidation.PrivateMaterial) R.string.ds_ssh_private_rejected else R.string.ds_ssh_invalid),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii))
            Text(stringResource(R.string.ssh_key_no_private_key))
        } else {
            Text(existing.name)
            Text(existing.fingerprint)
            Text(stringResource(R.string.ssh_key_delete_warning, existing.name))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
