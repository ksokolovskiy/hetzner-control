package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.SectionLoader
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class WizardCatalogue(val types: List<ServerTypeOption>, val images: List<ImageOption>,
    val locations: List<LocationOption>, val keys: List<SshKeyOption>)
private suspend fun readWizardCatalogue(repository: HCloudRepository): WizardCatalogue = coroutineScope {
    val types = async { repository.getServerTypes() }; val images = async { repository.getSystemImages() }
    val locations = async { repository.getLocations() }; val keys = async { repository.getSshKeys() }
    WizardCatalogue(types.await(), images.await(), locations.await(), keys.await())
}
private fun draftSaver(context: OperationContext?) = Saver<ProvisioningDraft, ArrayList<String>>(
    save = { it.snapshotFor(context) }, restore = { ProvisioningDraft.restoreFor(context, it) })

internal suspend fun readWizardPrice(repository: HCloudRepository, type: ServerTypeOption, location: String): PricingState =
    try { repository.getServerTypePrice(type.id, location)?.let(PricingState::Available) ?: PricingState.Unavailable }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Throwable) { PricingState.Failed }

internal fun sameReviewedPrice(reviewed: PricingState, fresh: PricingState): Boolean =
    if (reviewed is PricingState.Available) fresh is PricingState.Available && reviewed.price == fresh.price
    else fresh !is PricingState.Available

@Composable
internal fun rememberWizardPrice(repository: HCloudRepository, type: ServerTypeOption?, location: String?): SectionLoader<PricingState> {
    val context = LocalOperationContext.current
    return rememberSectionLoader("$context:display-price:${type?.id}:$location") {
        if (type == null || location == null) PricingState.NotReady
        else repository.getServerTypePrice(type.id, location)?.let(PricingState::Available) ?: PricingState.Unavailable
    }
}

@Composable internal fun WizardPriceSection(loader: SectionLoader<PricingState>) {
    HCloudReadSection(loader) { PriceSummary(it) }
}

/** One ID-only draft and one create mutation path for every catalogue/list/prefill entry. */
@Composable
internal fun UnifiedServerProvisioningScreen(repository: HCloudRepository, isTestServer: Boolean,
    initialType: ServerTypeOption?, initialLocation: LocationOption?, onCancel: () -> Unit, onCreated: () -> Unit,
    onCreatedResource: ((Long) -> Unit)?, onManageSshKeys: (() -> Unit)?) {
    val context = LocalOperationContext.current
    val currentContext by rememberUpdatedState(context)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val scopedSaver = remember(context) { draftSaver(context) }
    val savedPages = rememberSaveableStateHolder()
    val pageScope = "${context?.connectionId}:${context?.endpoint}"
    var draft by rememberSaveable(context?.connectionId, context?.endpoint, stateSaver = scopedSaver) {
        mutableStateOf(ProvisioningDraft(typeId = initialType?.id, locationId = initialLocation?.id))
    }
    val session = rememberOperationSession("server", null, "create_server", draft.copy(step = ProvisioningStep.RESOURCES).hashCode().toLong() and 0xffffffffL)
    val typesRead = rememberSectionLoader("$context:wizard:types", isEmpty = { it: List<ServerTypeOption> -> it.isEmpty() }) { repository.getServerTypes() }
    val imagesRead = rememberSectionLoader("$context:wizard:images", isEmpty = { it: List<ImageOption> -> it.isEmpty() }) { repository.getSystemImages() }
    val locationsRead = rememberSectionLoader("$context:wizard:locations", isEmpty = { it: List<LocationOption> -> it.isEmpty() }) { repository.getLocations() }
    val keysRead = rememberSectionLoader("$context:wizard:ssh", isEmpty = { it: List<SshKeyOption> -> it.isEmpty() }) { repository.getSshKeys() }
    val catalogue = WizardCatalogue(typesRead.state.data.orEmpty(), imagesRead.state.data.orEmpty(), locationsRead.state.data.orEmpty(), keysRead.state.data.orEmpty())
    fun refreshCatalogue() { typesRead.refresh(); imagesRead.refresh(); locationsRead.refresh(); keysRead.refresh() }
    var typePicker by remember(context) { mutableStateOf(false) }
    var picker by remember(context) { mutableStateOf<String?>(null) }
    var resets by remember(context) { mutableStateOf(emptySet<DraftReset>()) }
    var busy by remember(context) { mutableStateOf(false) }
    var failure by remember(context) { mutableStateOf(false) }
    var resultRevision by remember(context) { mutableStateOf(0) }
    var secret by remember(context) { mutableStateOf<SecretResult?>(null) }
    var secretOpen by remember(context) { mutableStateOf(false) }
    var reviewAt by remember(context) { mutableStateOf(System.currentTimeMillis()) }
    var reviewedPrice by remember(context) { mutableStateOf<PricingState?>(null) }
    var appliedPrefillType by rememberSaveable(context?.connectionId, context?.endpoint) { mutableStateOf<Long?>(null) }
    var appliedPrefillLocation by rememberSaveable(context?.connectionId, context?.endpoint) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val record = remember(session, resultRevision, busy) { session.record }
    val type = catalogue?.types?.firstOrNull { it.id == draft.typeId }
    val image = catalogue?.images?.firstOrNull { it.id == draft.imageId }
    val location = catalogue?.locations?.firstOrNull { it.id == draft.locationId }
    val price = rememberWizardPrice(repository, type, location?.name)
    val exit = rememberDirtyFormExitGuard(isDirty = draft != ProvisioningDraft(), onRetain = {},
        onDiscard = {
            draft = ProvisioningDraft()
            appliedPrefillType = null; appliedPrefillLocation = null
            ProvisioningStep.entries.forEach { savedPages.removeState("$pageScope:step:$it") }
            savedPages.removeState("$pageScope:type-picker")
        }, onLeave = onCancel, submitted = record != null, contextKey = context)
    DirtyFormExitPrompt(exit)

    fun openReview() {
        val selectedType = type ?: return
        val selectedLocation = location ?: return
        if (busy || !typesRead.state.canOfferMutation || !imagesRead.state.canOfferMutation || !locationsRead.state.canOfferMutation || !keysRead.state.canOfferMutation) return
        val originalDraft = draft
        busy = true
        scope.launch {
            try {
                val freshPrice = readWizardPrice(repository, selectedType, selectedLocation.name)
                check(draft == originalDraft)
                reviewedPrice = freshPrice
                reviewAt = System.currentTimeMillis()
                draft = draft.copy(step = ProvisioningStep.REVIEW)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { failure = true }
            finally { busy = false }
        }
    }

    fun back() {
        when {
            typePicker -> typePicker = false
            picker != null -> picker = null
            record != null -> { if (record.canClose) { session.startNewAttemptAfterTerminalClose(); onCancel() } }
            draft.step == ProvisioningStep.REVIEW -> draft = draft.copy(step = ProvisioningStep.ACCESS)
            draft.step == ProvisioningStep.ACCESS -> draft = draft.copy(step = ProvisioningStep.RESOURCES)
            else -> exit.requestLeave()
        }
    }
    val navContext = LocalNavigationContext.current
    val screenTitle = stringResource(R.string.provision_title)
    if (navContext != null) RegisterChildNavigation(CanonicalDestination(RootDestination.CREATE, DestinationKind.FORM,
        navContext, ResourceType.SERVER), screenTitle, ::back)
    if (LocalNavigationChrome.current == null) BackHandler { back() }
    DisposableEffect(context) { onDispose { secret?.close() } }
    LaunchedEffect(context, catalogue, initialType?.id, initialLocation?.id,
        typesRead.state.phase, imagesRead.state.phase, locationsRead.state.phase, keysRead.state.phase) {
        val fresh = catalogue
        if (!typesRead.state.canOfferMutation || !imagesRead.state.canOfferMutation || !locationsRead.state.canOfferMutation) return@LaunchedEffect
        if (session.record != null) return@LaunchedEffect
        var updated = draft
        var notices = emptySet<DraftReset>()
        if (initialType == null) appliedPrefillType = null
        else if (appliedPrefillType != initialType.id) {
            val selected = fresh.types.firstOrNull { it.id == initialType.id }
            if (selected != null) {
                val change = updated.selectType(selected, fresh.images.firstOrNull { it.id == updated.imageId },
                    fresh.locations.firstOrNull { it.id == updated.locationId })
                updated = change.draft.copy(step = ProvisioningStep.RESOURCES); notices = notices + change.resets
                appliedPrefillType = initialType.id
            }
        }
        if (initialLocation == null) appliedPrefillLocation = null
        else if (appliedPrefillLocation != initialLocation.id) {
            val selected = fresh.locations.firstOrNull { it.id == initialLocation.id }
            if (selected != null) {
                val change = updated.selectLocation(selected, fresh.types.firstOrNull { it.id == updated.typeId })
                updated = change.draft.copy(step = ProvisioningStep.RESOURCES); notices = notices + change.resets
                appliedPrefillLocation = initialLocation.id
            }
        }
        val checked = reconcileCatalogueDraft(updated, fresh.types, fresh.images, fresh.locations,
            fresh.keys.takeIf { keysRead.state.canOfferMutation })
        updated = checked.draft
        notices = notices + checked.resets
        draft = updated
        if (notices.isNotEmpty()) resets = notices
    }
    fun finishCreated() {
        val id = session.record?.createdResourceId?.toLongOrNull()
        if (id != null && onCreatedResource != null) onCreatedResource(id) else onCreated()
    }
    fun checkStatus() {
        if (busy || currentContext != session.context) return
        busy = true
        scope.launch {
            try {
                if (session.record?.originalActionIds.isNullOrEmpty()) {
                    val createdId = session.record?.createdResourceId?.toLongOrNull()
                    if (createdId == null) {
                        repository.getServers()
                        // A matching mutable name cannot prove that this request created the resource.
                        session.unknownIfPending()
                    } else {
                        val created = repository.getServer(createdId)
                        session.reconcileCreatedResource(created.id.toString(), created.status in setOf(ServerStatus.RUNNING, ServerStatus.OFF))
                    }
                } else awaitOriginalOperationActions(session, repository)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { session.unknownIfPending() }
            finally { busy = false; resultRevision++ }
        }
    }
    if (secretOpen) {
        SecretResultDialog(secret) { secret = null; secretOpen = false; finishCreated() }
        return
    }
    if (record != null) {
        OperationResultPresentation(record, record.intent.resource.context.connectionId,
            record.createdResourceId?.let { "#$it" } ?: draft.name, onCancel, session = session,
            onCheckStatus = if (!busy) ::checkStatus else null,
            onOpenResource = if (record.stage == OperationStage.SUCCEEDED) ({
                if (record.secretIssued) secretOpen = true else finishCreated()
            }) else null)
        return
    }
    val data = catalogue
    if (typePicker) {
        savedPages.SaveableStateProvider("$pageScope:type-picker") {
            WizardTypePicker(data.types.filter { it.availableLocations.isNotEmpty() }, draft.typeId, typesRead, onDismiss = { typePicker = false }) { selected ->
                val change = draft.selectType(selected, image, location)
                draft = change.draft; resets = change.resets; typePicker = false
            }
        }
        return
    }
    val resourcesValid = typesRead.state.canOfferMutation && imagesRead.state.canOfferMutation && locationsRead.state.canOfferMutation &&
        draft.name.isNotBlank() && type != null && image != null && location != null &&
        location.name in type.availableLocations && (type.architecture == null || image.architecture == null || type.architecture == image.architecture)
    val stepLabels = listOf(stringResource(R.string.wizard_resources), stringResource(R.string.wizard_access), stringResource(R.string.wizard_review))
    if (draft.step == ProvisioningStep.REVIEW && resourcesValid) {
        val identity = session.intent
        if (identity != null) {
            val frozen = CreateServerSpec(draft.name.trim(), type!!.name, image!!.name, location!!.name,
                draft.ipv4, draft.ipv6, draft.sshKeyIds.sorted())
            val frozenPrice = reviewedPrice ?: PricingState.Unavailable
            val baseCost = pricePresentation(frozenPrice)
            val serverRate = stringResource(R.string.wizard_server_rate)
            val ipv4Rate = stringResource(R.string.provision_ipv4)
            val cost = if (baseCost is CostPresentation.Available) baseCost.copy(snapshot = baseCost.snapshot.copy(
                locationLabel = location.displayName(), components = listOf(CostComponent(serverRate, CostComponentCoverage.INCLUDED)) +
                    if (draft.ipv4) listOf(CostComponent(ipv4Rate, CostComponentCoverage.UNKNOWN)) else emptyList())) else baseCost
            val unknownCost = cost !is CostPresentation.Available || cost.snapshot.incomplete
            val paid = stringResource(R.string.provision_billable_create)
            val unknown = stringResource(R.string.wizard_unknown_cost_ack)
            val title = stringResource(R.string.provision_create_confirm_title, frozen.name)
            val keyNames = data.keys.filter { it.id in draft.sshKeyIds }.joinToString { it.name }
            val effects = listOf(
                ReviewChange(stringResource(R.string.provision_name_label), null, frozen.name),
                ReviewChange(stringResource(R.string.provision_type_label), null, "${type.name} · ${type.cores} vCPU · ${type.memoryGb} GB · ${type.diskGb} GB"),
                ReviewChange(stringResource(R.string.provision_image_label), null, image.description),
                ReviewChange(stringResource(R.string.provision_location_label), null, location.displayName()),
                ReviewChange(stringResource(R.string.ssh_keys_title), null, keyNames.ifBlank { "—" }),
                ReviewChange(stringResource(R.string.provision_network), null, listOfNotNull(if (draft.ipv4) "IPv4" else null, if (draft.ipv6) "IPv6" else null).joinToString(" + ").ifBlank { "—" }))
            val model = remember(identity, frozen, cost, title, paid, unknown, effects, reviewAt) {
                OperationReviewContent(identity, reviewAt, title, identity.resource.context.connectionId, frozen.name,
                    effects, cost, acknowledgements = listOf(RiskAcknowledgement("paid", paid)) +
                        if (unknownCost) listOf(RiskAcknowledgement("unknown-cost", unknown)) else emptyList())
            }
            OperationReviewDialog(model, onConfirm = { proof ->
                if (!busy) {
                    busy = true; failure = false
                    scope.launch {
                        try {
                            val fresh = readWizardCatalogue(repository)
                            val freshType = fresh.types.firstOrNull { it.id == type.id }
                            check(freshType == type && location.name in type.availableLocations)
                            check(fresh.images.any { it == image && (it.architecture == null || type.architecture == null || it.architecture == type.architecture) })
                            check(fresh.locations.any { it.id == location.id && it.name == location.name })
                            check(fresh.keys.map { it.id }.containsAll(frozen.sshKeyIds))
                            check(sameReviewedPrice(frozenPrice, readWizardPrice(repository, type, location.name)))
                            session.beginReviewed(proof, allowed, currentContext)
                            resultRevision++
                            val result = repository.createServer(frozen)
                            session.acceptedActions(result.actions, result.server.id.toString(), result.rootPassword != null)
                            result.rootPassword?.toCharArray()?.let { chars ->
                                try { secret = SecretResult(chars) } finally { chars.fill('\u0000') }
                            }
                            resultRevision++
                            if (result.actions.isEmpty()) {
                                val created = repository.getServer(result.server.id)
                                session.reconcileCreatedResource(created.id.toString(), created.status in setOf(ServerStatus.RUNNING, ServerStatus.OFF))
                            } else awaitOriginalOperationActions(session, repository, result.actions) { resultRevision++ }
                            if (session.record?.stage == OperationStage.SUCCEEDED && session.record?.secretIssued == true) secretOpen = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                          catch (throwable: Throwable) {
                              session.rejectOrUnknown(throwable); failure = true
                              if (session.record == null) { draft = draft.copy(step = ProvisioningStep.ACCESS); refreshCatalogue() }
                          }
                        finally { busy = false; resultRevision++ }
                    }
                }
            }, onDismiss = { draft = draft.copy(step = ProvisioningStep.ACCESS) }, submitEnabled = allowed && !busy)
        }
    }
    savedPages.SaveableStateProvider("$pageScope:step:${draft.step}") {
    HCloudFormShell(stepLabels[draft.step.ordinal],
        if (draft.step == ProvisioningStep.RESOURCES) stringResource(R.string.wizard_next_access) else stringResource(R.string.provision_review_create),
        onSubmit = {
            if (draft.step == ProvisioningStep.RESOURCES) draft = draft.copy(step = ProvisioningStep.ACCESS)
            else openReview()
        }, submitEnabled = resourcesValid && (draft.step == ProvisioningStep.RESOURCES || keysRead.state.canOfferMutation) && allowed && session.configured && draft.step != ProvisioningStep.REVIEW, busy = busy) {
        if (LocalNavigationChrome.current == null) TextButton(onClick = ::back) { Text(stringResource(R.string.common_back)) }
        if (isTestServer) Text(stringResource(R.string.test_server_badge))
        HCloudSegmentedControl(ProvisioningStep.entries.mapIndexed { index, step -> ChoiceItem(step.name, stepLabels[index],
            step == ProvisioningStep.RESOURCES || (resourcesValid && (step != ProvisioningStep.REVIEW || keysRead.state.canOfferMutation))) }, draft.step.name, { selected ->
            val next = ProvisioningStep.valueOf(selected)
            if (next == ProvisioningStep.REVIEW) openReview() else draft = draft.copy(step = next)
        })
        if (failure) Text(stringResource(R.string.provision_form_preserved), color = MaterialTheme.colorScheme.error)
        if (resets.isNotEmpty()) Text(stringResource(if (DraftReset.UNAVAILABLE_SELECTION in resets)
            R.string.wizard_unavailable_selection else R.string.wizard_compatibility_reset), color = MaterialTheme.colorScheme.error)
        if (draft.step == ProvisioningStep.RESOURCES) {
            HCloudFormField(draft.name, { draft = draft.copy(name = it) }, stringResource(R.string.provision_name_label))
            HCloudReadSection(typesRead) {}
            WizardChoiceButton(stringResource(R.string.provision_type_label), type?.let { "${it.name} · ${it.cores} vCPU · ${it.memoryGb} GB · ${it.diskGb} GB" }) { typePicker = true }
            HCloudReadSection(imagesRead) {}
            WizardChoiceButton(stringResource(R.string.provision_image_label), image?.description) { picker = "image" }
            HCloudReadSection(locationsRead) {}
            WizardChoiceButton(stringResource(R.string.provision_location_label), location?.displayName()) { picker = "location" }
            WizardPriceSection(price)
            Text(stringResource(R.string.wizard_safe_draft))
        } else {
            HCloudReadSection(keysRead) {}
            SshKeySelector(data.keys, draft.sshKeyIds) { draft = draft.copy(sshKeyIds = it) }
            onManageSshKeys?.let { manage -> OutlinedButton(onClick = manage) { Text(stringResource(R.string.ssh_key_create_title)) } }
            Row { Checkbox(draft.ipv4, { draft = draft.copy(ipv4 = it) }); Text(stringResource(R.string.provision_ipv4)) }
            Row { Checkbox(draft.ipv6, { draft = draft.copy(ipv6 = it) }); Text(stringResource(R.string.provision_ipv6)) }
            if (!draft.ipv4 && !draft.ipv6) Text(stringResource(R.string.provision_no_public_network))
        }
    }
    }
    if (picker == "image") WizardChoiceDialog(stringResource(R.string.provision_image_label),
        data.images.filter { type?.architecture == null || it.architecture == null || it.architecture == type.architecture }
            .map { ChoiceItem(it.id.toString(), "${it.name} · ${it.description}", imagesRead.state.canOfferMutation) }, draft.imageId?.toString(), { picker = null }, readStatus = { HCloudReadSection(imagesRead) {} }, showEmpty = imagesRead.state.hasSuccessfulResponse && data.images.isNotEmpty(), selectionScope = "$context:wizard:image") {
        draft = draft.copy(imageId = it.toLong()); picker = null
    }
    if (picker == "location") WizardChoiceDialog(stringResource(R.string.provision_location_label),
        data.locations.map { ChoiceItem(it.id.toString(), it.displayName(), locationsRead.state.canOfferMutation) }, draft.locationId?.toString(), { picker = null }, readStatus = { HCloudReadSection(locationsRead) {} }, showEmpty = locationsRead.state.hasSuccessfulResponse && data.locations.isNotEmpty(), selectionScope = "$context:wizard:location") { selected ->
        val change = draft.selectLocation(data.locations.first { it.id.toString() == selected }, type)
        draft = change.draft; resets = change.resets; picker = null
    }
}

@Composable private fun WizardChoiceButton(label: String, value: String?, onClick: () -> Unit) {
    HCloudResourceCard(ResourceCardModel(label, label, value ?: stringResource(R.string.provision_not_selected), ""), onClick)
}

@Composable internal fun WizardChoiceDialog(title: String, choices: List<ChoiceItem>, selected: String?, onDismiss: () -> Unit, readStatus: @Composable () -> Unit = {}, showEmpty: Boolean = true, selectionScope: String = title, onPick: (String) -> Unit) {
    // Only an opaque resource ID is retained; a refresh must not reset a pending choice.
    var candidate by rememberSaveable(selectionScope, selected) { mutableStateOf(selected) }
    HCloudDialog(title, stringResource(R.string.wizard_select), { candidate?.let(onPick) }, onDismiss,
        confirmEnabled = choices.any { it.id == candidate && it.enabled }) {
        readStatus()
        if (showEmpty && choices.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_ELIGIBLE))
        choices.forEach { item -> HCloudResourceCard(ResourceCardModel(item.id, item.label,
            if (item.id == candidate) "✓" else "", ""), { if (item.enabled) candidate = item.id }) }
    }
}

@Composable private fun WizardTypePicker(types: List<ServerTypeOption>, selectedId: Long?, loader: SectionLoader<List<ServerTypeOption>>, onDismiss: () -> Unit, onPick: (ServerTypeOption) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var architecture by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var cores by rememberSaveable { mutableStateOf<Int?>(null) }
    var memory by rememberSaveable { mutableStateOf<Double?>(null) }
    var disk by rememberSaveable { mutableStateOf<Int?>(null) }
    var candidate by rememberSaveable { mutableStateOf(selectedId) }
    val filtered = ServerTypeFilters(filter, architecture, category, cores, memory, disk).apply(types)
    HCloudFormShell(stringResource(R.string.provision_type_choose), stringResource(R.string.wizard_select),
        { types.firstOrNull { it.id == candidate }?.let(onPick) }, submitEnabled = loader.state.canOfferMutation && types.any { it.id == candidate }) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_back)) }
        HCloudReadSection(loader) {}
        HCloudFormField(filter, { filter = it }, stringResource(R.string.wizard_search_types))
        val any = stringResource(R.string.provision_any)
        WizardFilter(stringResource(R.string.provision_filter_architecture), listOf(any) + types.mapNotNull { it.architecture }.distinct().sorted(), architecture ?: any) { architecture = it.takeUnless { it == any } }
        WizardFilter(stringResource(R.string.provision_filter_category), listOf(any) + types.mapNotNull { it.category }.distinct().sorted(), category ?: any) { category = it.takeUnless { it == any } }
        WizardFilter(stringResource(R.string.provision_filter_cores), listOf(any) + types.map { it.cores }.distinct().sorted().map { it.toString() }, cores?.toString() ?: any) { cores = it.toIntOrNull() }
        WizardFilter(stringResource(R.string.provision_filter_memory), listOf(any) + types.map { it.memoryGb }.distinct().sorted().map { it.toString() }, memory?.toString() ?: any) { memory = it.toDoubleOrNull() }
        WizardFilter(stringResource(R.string.provision_filter_disk), listOf(any) + types.map { it.diskGb }.distinct().sorted().map { it.toString() }, disk?.toString() ?: any) { disk = it.toIntOrNull() }
        Text(stringResource(R.string.provision_available_types_count, filtered.size))
        TextButton(onClick = { filter = ""; architecture = null; category = null; cores = null; memory = null; disk = null }) { Text(stringResource(R.string.provision_clear_filters)) }
        filtered.forEach { type -> HCloudResourceCard(ResourceCardModel(type.id.toString(), "${type.name} · ${type.cores} vCPU",
            if (candidate == type.id) "✓" else type.architecture.orEmpty(), "${type.memoryGb} GB RAM · ${type.diskGb} GB"), { candidate = type.id }) }
        cataloguePickerEmptyKind(loader.state.hasSuccessfulResponse, loader.state.data.orEmpty().size, types.size, filtered.size)?.let {
            HCloudSectionState(SectionPresentation(it))
        }
    }
}

@Composable private fun WizardFilter(label: String, choices: List<String>, selected: String, onPick: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    HCloudSegmentedControl(choices.map { ChoiceItem(it, it) }, selected, onPick)
}
