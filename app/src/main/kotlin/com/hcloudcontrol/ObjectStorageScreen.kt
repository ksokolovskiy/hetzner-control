package com.hcloudcontrol

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.network.ObjectStorageRepositoryFactory
import com.hcloudcontrol.core.security.ObjectStorageConnection
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.SectionState
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class BucketReview(val model: OperationReviewContent, val repository: ObjectStorageRepository)

private fun s3Context(store: ObjectStorageCredentialStore): OperationContext? {
    val id = store.activeConnectionId() ?: return null
    val connection = store.connections().firstOrNull { it.id == id } ?: return null
    return OperationContext(id, "https://${connection.location.host}", OperationContext.Environment.S3)
}

@Composable
internal fun ObjectStorageScreen(store: ObjectStorageCredentialStore, repository: ObjectStorageRepository,
    onBackToMore: () -> Unit = {}) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = LocalOperationCoordinator.current
    val gate = LocalHighImpactOperations.current
    val currentGate by rememberUpdatedState(gate)
    var connections by remember { mutableStateOf(store.connections()) }
    var activeId by remember { mutableStateOf(store.activeConnectionId()) }
    val active = connections.firstOrNull { it.id == activeId }
    val operationContext = active?.let { OperationContext(it.id, "https://${it.location.host}", OperationContext.Environment.S3) }
    val unavailable = unavailableS3Operations(coordinator?.all().orEmpty(), connections.map {
        OperationContext(it.id, "https://${it.location.host}", OperationContext.Environment.S3)
    }.toSet())
    val pendingRecords = pendingS3Operations(coordinator?.all().orEmpty())
    val inventory = if (operationContext != null) rememberSectionLoader(
        "s3:${operationContext.connectionId}:${operationContext.endpoint}:${operationContext.environment}:buckets",
        isEmpty = { it: List<ObjectStorageBucket> -> it.isEmpty() },
    ) { readS3Inventory(store, operationContext) } else null
    val inventoryState = inventory?.state ?: SectionState<List<ObjectStorageBucket>>()
    val publicDraft = rememberS3PublicDraft(operationContext)
    val query = publicDraft.value.query
    var showConnect by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var showCreate by remember(activeId) { mutableStateOf(false) }
    val draftName = publicDraft.value.bucketName
    var removing by remember { mutableStateOf<ObjectStorageConnection?>(null) }
    var review by remember(activeId) { mutableStateOf<BucketReview?>(null) }
    var resultId by rememberSaveable(activeId) { mutableStateOf<String?>(null) }
    var result by remember(activeId) { mutableStateOf<PersistedOperation?>(null) }
    var localError by remember(activeId) { mutableStateOf<String?>(null) }
    var checking by remember(activeId) { mutableStateOf(false) }
    var successAwaitingRefresh by remember(operationContext) { mutableStateOf<String?>(null) }
    val failure = stringResource(R.string.object_storage_request_failed)
    val preconditionError = stringResource(R.string.s3_precondition_changed)
    if (!showConnect && !showCreate && !manage && review == null && result == null && operationContext != null) {
        RegisterChildNavigation(CanonicalDestination(RootDestination.MORE, DestinationKind.LIST,
            ResourceContext(operationContext.connectionId, operationContext.endpoint, ContextKind.S3), ResourceType.BUCKET),
            stringResource(R.string.object_storage_title), onBackToMore)
    }
    if (showConnect || showCreate || manage || review != null || result != null) {
        val routeContext = (result?.intent?.resource?.context ?: operationContext)?.let { ResourceContext(it.connectionId, it.endpoint, ContextKind.S3) }
        RegisterChildNavigation(if (routeContext == null) CanonicalDestination(RootDestination.MORE, DestinationKind.LIST,
            resourceType = ResourceType.S3_CONNECTIONS) else CanonicalDestination(RootDestination.MORE,
            if (showConnect || showCreate || review != null) DestinationKind.FORM else DestinationKind.LIST, routeContext, ResourceType.S3_CONNECTIONS),
            stringResource(when { showConnect -> R.string.object_storage_connect_title; showCreate -> R.string.object_storage_create_title; else -> R.string.s3_profiles_title })) {
            when { review != null -> review = null
                result != null -> if (result?.canClose == true) { result = null; resultId = null }
                showCreate -> showCreate = false; showConnect -> showConnect = false; else -> manage = false }
        }
    }

    fun refresh() { inventory?.refresh() }
    LaunchedEffect(operationContext) {
        result = resultId?.let { coordinator?.get(it) }?.takeIf {
            it.intent.resource.context == operationContext && it.intent.resource.resourceType == "s3_bucket"
        } ?: coordinator?.all()?.lastOrNull {
            it.isPending && it.intent.resource.context == operationContext && it.intent.resource.resourceType == "s3_bucket"
        }
        resultId = result?.intent?.intentId
    }
    LaunchedEffect(successAwaitingRefresh, inventoryState.error) {
        val id = successAwaitingRefresh
        val original = id?.let { coordinator?.get(it) }
        if (original?.stage == OperationStage.SUCCEEDED && original.intent.resource.context == operationContext &&
            inventoryState.error != null && !original.refreshFailed) {
            try {
                coordinator?.refreshFailed(original.intent.intentId)
                if (resultId == original.intent.intentId) result = coordinator?.get(original.intent.intentId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) { localError = failure }
        }
    }
    fun adapter(pinned: ObjectStorageRepository, context: OperationContext) = S3BucketOperationAdapter(
        checkNotNull(coordinator), pinned, context, { s3Context(store) }, { currentGate.isAllowed })

    fun prepare(name: String, action: S3BucketAction) {
        val pinnedContext = operationContext ?: return
        val credentials = store.activeCredentials() ?: return
        if (!gate.isAllowed || coordinator == null || !isValidObjectStorageBucketName(name) || checking) return
        checking = true; localError = null
        val pinned = ObjectStorageRepositoryFactory.create { credentials }
        scope.launch {
            try {
                val buckets = withContext(Dispatchers.IO) { pinned.listBuckets() }
                check(s3Context(store) == pinnedContext)
                val exists = buckets.any { it.name == name }
                if ((action == S3BucketAction.CREATE && exists) || (action == S3BucketAction.DELETE && !exists)) throw S3PreconditionChanged()
                val intent = OperationIntent(UUID.randomUUID().toString(), ResourceIdentity(pinnedContext, "s3_bucket", name), action.wireName, 0)
                review = BucketReview(OperationReviewContent(intent, System.currentTimeMillis(),
                    androidContext.getString(if (action == S3BucketAction.CREATE) R.string.object_storage_create_title else R.string.object_storage_delete_action),
                    active?.displayName.orEmpty(), name,
                    listOf(ReviewChange(androidContext.getString(R.string.object_storage_location), null, pinnedContext.endpoint),
                        ReviewChange(androidContext.getString(R.string.s3_effect), null,
                            androidContext.getString(if (action == S3BucketAction.CREATE) R.string.object_storage_create_warning else R.string.object_storage_delete_warning))),
                    cost = if (action == S3BucketAction.CREATE) CostPresentation.Unavailable else null,
                    typedIdentity = name,
                    acknowledgements = listOf(RiskAcknowledgement("effect", androidContext.getString(
                        if (action == S3BucketAction.CREATE) R.string.object_storage_create_cost_warning else R.string.object_storage_delete_protection_warning)))), pinned)
            } catch (_: Throwable) { localError = preconditionError }
            checking = false
        }
    }

    BackHandler(enabled = LocalNavigationChrome.current == null && (showConnect || manage || showCreate || review != null || result != null)) {
        when {
            review != null -> review = null
            result != null -> if (result?.canClose == true) { result = null; resultId = null }
            showCreate -> showCreate = false
            showConnect -> showConnect = false
            manage -> manage = false
        }
    }
    if (showConnect) {
        ObjectStorageConnectionScreen(store, { showConnect = false }, {
            connections = store.connections(); activeId = store.activeConnectionId(); showConnect = false
        })
        return
    }
    if (manage) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.s3_profiles_title), style = MaterialTheme.typography.titleLarge) }
            pendingS3JournalRows(pendingRecords, unavailable.map { it.intent.intentId }.toSet()) { record -> result = record; resultId = record.intent.intentId }
            items(connections, key = { it.id }) { connection ->
                HCloudResourceCard(ResourceCardModel(connection.id, connection.displayName, connection.location.host,
                    stringResource(if (connection.id == activeId) R.string.s3_selected else R.string.s3_select)), {
                    when (val changed = changeS3Profile(store, S3LocalProfileChange.Select(connection.id))) {
                        is S3ProfileChangeResult.Applied -> {
                            connections = changed.snapshot.connections; activeId = changed.snapshot.activeId
                            localError = null; manage = false
                        }
                        S3ProfileChangeResult.Failed -> localError = androidContext.getString(R.string.s3_profile_change_failed)
                    }
                }, menu = { TextButton(onClick = { removing = connection }) { Text(stringResource(R.string.object_storage_remove_connection)) } })
            }
            item { localError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { OutlinedButton(onClick = { showConnect = true }) { Text(stringResource(R.string.object_storage_add_connection)) } }
            item { OutlinedButton(onClick = { manage = false }) { Text(stringResource(R.string.common_back)) } }
        }
    } else if (showCreate) {
        HCloudFormShell(stringResource(R.string.object_storage_create_title), stringResource(R.string.s3_review),
            { prepare(draftName, S3BucketAction.CREATE) }, submitEnabled = isValidObjectStorageBucketName(draftName) && gate.isAllowed,
            busy = checking) {
            Text(active?.displayName.orEmpty())
            Text(operationContext?.endpoint.orEmpty())
            HCloudFormField(draftName, { publicDraft.value = publicDraft.value.copy(bucketName = it); localError = null }, stringResource(R.string.object_storage_bucket_name),
                error = if (draftName.isNotEmpty() && !isValidObjectStorageBucketName(draftName)) stringResource(R.string.s3_invalid_name) else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii))
            Text(stringResource(R.string.object_storage_create_cost_warning))
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = { showCreate = false }) { Text(stringResource(R.string.common_back)) }
        }
    } else {
        HCloudPullToRefresh(inventoryState.refreshing, ::refresh, Modifier.fillMaxSize()) {
            LazyColumn(state = rememberRestorableLazyListState("s3:${activeId.orEmpty()}:buckets", contentReady = inventoryState.data != null), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.object_storage_title), style = MaterialTheme.typography.titleLarge)
                    if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.object_storage_subtitle))
                    active?.let { HCloudProjectContext(ProjectContext(it.displayName, it.location.host)) }
                    OutlinedButton(onClick = { if (connections.isEmpty()) showConnect = true else manage = true }) {
                        Text(stringResource(if (connections.isEmpty()) R.string.object_storage_add_connection else R.string.s3_profiles_title))
                    }
                }
                pendingS3JournalRows(pendingRecords, unavailable.map { it.intent.intentId }.toSet()) { record -> result = record; resultId = record.intent.intentId }
                if (active == null) item { HCloudSectionState(SectionPresentation(SectionKind.NO_CONNECTION)) }
                else {
                    item { HCloudFormField(query, { publicDraft.value = publicDraft.value.copy(query = it) }, stringResource(R.string.ds_search)) }
                    val buckets = inventoryState.data.orEmpty()
                    val filtered = buckets.filter { query.isBlank() || it.name.contains(query.trim(), true) }
                    item {
                        HCloudReadSection(checkNotNull(inventory)) {}
                        if (buckets.isNotEmpty() && filtered.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
                    }
                    items(filtered, key = { "${active.id}:${it.name}" }) { bucket ->
                        var menu by remember(bucket.name) { mutableStateOf(false) }
                        HCloudResourceCard(ResourceCardModel("${active.id}:${bucket.name}", bucket.name,
                            bucket.createdAt ?: stringResource(R.string.object_storage_created_unknown), active.location.host), menu = {
                            if (gate.isAllowed && inventoryState.canOfferMutation) Box {
                                TextButton(onClick = { menu = true }) { Text(stringResource(R.string.ds_actions)) }
                                HCloudMenu(menu, listOf(MenuAction("delete", stringResource(R.string.object_storage_delete_action))),
                                    { menu = false }, { prepare(bucket.name, S3BucketAction.DELETE) })
                            }
                        })
                    }
                    if (gate.isAllowed && inventoryState.canOfferMutation && coordinator != null) item {
                        Button(onClick = { showCreate = true }, enabled = result?.isPending != true) { Text(stringResource(R.string.object_storage_create_action)) }
                    }
                    item { localError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }
    removing?.let { connection ->
        HCloudDialog(stringResource(R.string.object_storage_remove_connection), stringResource(R.string.object_storage_remove_connection), {
            when (val changed = changeS3Profile(store, S3LocalProfileChange.Remove(connection.id))) {
                is S3ProfileChangeResult.Applied -> {
                    connections = changed.snapshot.connections; activeId = changed.snapshot.activeId
                    removing = null; localError = null
                }
                S3ProfileChangeResult.Failed -> localError = androidContext.getString(R.string.s3_profile_change_failed)
            }
        }, { removing = null }) {
            Text(stringResource(R.string.s3_remove_local_only, connection.displayName))
            if (coordinator?.all()?.any { it.isPending && it.intent.resource.context.connectionId == connection.id } == true) {
                Text(stringResource(R.string.s3_pending_reauthentication))
            }
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    review?.let { frozen ->
        OperationReviewDialog(frozen.model, { proof ->
            review = null; showCreate = false
            checking = true
            scope.launch {
                try {
                    val returned = withContext(Dispatchers.IO) {
                        adapter(frozen.repository, proof.intent.resource.context).submit(proof) { sending ->
                            withContext(Dispatchers.Main) { result = sending; resultId = sending.intent.intentId }
                        }
                    }
                    result = returned; resultId = returned.intent.intentId
                    if (returned.stage == OperationStage.FAILED && returned.diagnostic?.httpStatus == null &&
                        returned.diagnostic?.code == OperationDiagnostic.Code.PROVIDER_FAILED) {
                        localError = androidContext.getString(R.string.object_storage_bucket_not_empty)
                    }
                    if (returned.stage == OperationStage.SUCCEEDED) {
                        if (s3Context(store) == proof.intent.resource.context) {
                            successAwaitingRefresh = returned.intent.intentId
                            refresh()
                        }
                    }
                } catch (_: Throwable) { localError = preconditionError }
                checking = false
            }
        }, { review = null }, submitEnabled = gate.isAllowed)
    }
    result?.let { record ->
        val originalAvailable = connections.any { it.id == record.intent.resource.context.connectionId &&
            "https://${it.location.host}" == record.intent.resource.context.endpoint }
        OperationResultDialog(stringResource(R.string.s3_operation_result), record,
            record.intent.resource.context.connectionId, record.intent.resource.resourceId.orEmpty(),
            onClose = { result = null; resultId = null }, busy = checking,
            onRefreshResource = if (record.intent.resource.context == operationContext) ({
                if (!checking && s3Context(store) == record.intent.resource.context) {
                    checking = true
                    scope.launch {
                        try {
                            val originalContext = record.intent.resource.context
                            val refreshSession = OperationSession(originalContext, coordinator, record.intent, 0)
                            val refreshed = refreshOperationResource(refreshSession) {
                                check(s3Context(store) == originalContext)
                                withContext(Dispatchers.IO) { readS3Inventory(store, originalContext) }
                                check(s3Context(store) == originalContext)
                            }
                            if (shouldPublishS3Result(resultId, record.intent.intentId, s3Context(store), originalContext)) {
                                result = coordinator?.get(record.intent.intentId)
                                localError = if (refreshed) null else failure
                                if (refreshed) { successAwaitingRefresh = null; refresh() }
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                          catch (_: Exception) {
                              if (shouldPublishS3Result(resultId, record.intent.intentId, s3Context(store), record.intent.resource.context)) localError = failure
                          }
                          finally { checking = false }
                    }
                }
            }) else null, onCheckStatus = if (!originalAvailable) null else ({
                val credentials = store.activeCredentials()
                if (credentials == null || s3Context(store) != record.intent.resource.context) localError = androidContext.getString(R.string.s3_pending_reauthentication)
                else if (!checking) {
                    checking = true
                    val pinned = ObjectStorageRepositoryFactory.create { credentials }
                    scope.launch {
                        try {
                            val reconciled = withContext(Dispatchers.IO) { adapter(pinned, record.intent.resource.context).reconcile(record) }
                            if (shouldPublishS3Result(resultId, record.intent.intentId, s3Context(store), record.intent.resource.context)) result = reconciled
                        }
                        catch (_: Throwable) { localError = failure }
                        checking = false
                    }
                }
            })) {
            if (!originalAvailable) Text(stringResource(R.string.s3_original_connection_unavailable), color = MaterialTheme.colorScheme.error)
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Local, nonsecret journal presentation; opening it never selects or recreates a profile. */
private fun LazyListScope.pendingS3JournalRows(records: List<PersistedOperation>, unavailableIds: Set<String>, onOpen: (PersistedOperation) -> Unit) {
    items(records, key = { "pending:${it.intent.intentId}" }) { record ->
        HCloudResourceCard(ResourceCardModel("pending:${record.intent.intentId}",
            record.intent.resource.resourceId.orEmpty(), stringResource(if (record.intent.intentId in unavailableIds)
                R.string.s3_original_connection_unavailable_title else R.string.s3_pending_operation),
            "${record.intent.resource.context.connectionId}\n${record.intent.resource.context.endpoint}"), { onOpen(record) })
    }
}

@Composable
private fun ObjectStorageConnectionScreen(store: ObjectStorageCredentialStore, onCancel: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var access by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<ObjectStorageLocation?>(null) }
    var locationPicker by remember { mutableStateOf(false) }
    var revealAccess by remember { mutableStateOf(false) }
    var revealSecret by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activity = androidx.activity.compose.LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { revealAccess = false; revealSecret = false }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val failure = stringResource(R.string.s3_connection_check_failed)
    val valid = name.isNotBlank() && access.isNotBlank() && secret.isNotBlank() && location != null
    HCloudFormShell(stringResource(R.string.object_storage_connect_title), stringResource(R.string.object_storage_connect_action), {
        if (valid && !checking) {
            checking = true; error = null
            val credentials = ObjectStorageCredentials(access.trim(), secret.trim(), checkNotNull(location))
            scope.launch {
                try {
                    val transient = ObjectStorageRepositoryFactory.create { credentials }
                    withContext(Dispatchers.IO) { transient.listBuckets() }
                    store.saveConnection(name, credentials.location, credentials.accessKey, credentials.secretKey)
                    access = ""; secret = ""; onSaved()
                } catch (_: Throwable) { error = failure; checking = false }
            }
        }
    }, submitEnabled = valid, busy = checking) {
        Text(stringResource(R.string.object_storage_setup_explanation))
        HCloudFormField(name, { name = it }, stringResource(R.string.object_storage_profile_name), enabled = !checking)
        OutlinedButton(onClick = { locationPicker = true }, enabled = !checking) {
            Text(location?.let { "${it.id} · ${it.host}" } ?: stringResource(R.string.object_storage_location))
        }
        HCloudFormField(access, { access = it }, stringResource(R.string.object_storage_access_key), enabled = !checking,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            visualTransformation = if (revealAccess) VisualTransformation.None else PasswordVisualTransformation())
        TextButton(onClick = { revealAccess = !revealAccess }) { Text(stringResource(if (revealAccess) R.string.s3_hide_access else R.string.s3_show_access)) }
        HCloudFormField(secret, { secret = it }, stringResource(R.string.object_storage_secret_key), enabled = !checking,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation())
        TextButton(onClick = { revealSecret = !revealSecret }) { Text(stringResource(if (revealSecret) R.string.s3_hide_secret else R.string.s3_show_secret)) }
        if (checking) Text(stringResource(R.string.s3_checking_connection))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onCancel, enabled = !checking) { Text(stringResource(R.string.common_cancel)) }
    }
    if (locationPicker) HCloudResourcePicker(stringResource(R.string.object_storage_location),
        ResourcePickerState("s3-locations", ObjectStorageLocation.entries.map { ResourcePickerItem(it.id, it.id, it.host) }),
        location?.id, { selected -> location = ObjectStorageLocation.entries.single { it.id == selected } }, { locationPicker = false })
}
