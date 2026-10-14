package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CreateServerSpec
import com.hcloudcontrol.core.domain.model.CreatedServer
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.model.ServerTypeLocationPrice
import com.hcloudcontrol.core.domain.model.SshKeyOption
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private data class ProvisioningChoices(
    val types: List<ServerTypeOption>,
    val images: List<ImageOption>,
    val locations: List<LocationOption>,
    val sshKeys: List<SshKeyOption>,
)

private sealed interface ChoicesState {
    data object Loading : ChoicesState
    data class Ready(val choices: ProvisioningChoices) : ChoicesState
    data class Failed(val message: String) : ChoicesState
}

private sealed interface TypeChoicesState {
    data object Loading : TypeChoicesState
    data class Ready(val types: List<ServerTypeOption>) : TypeChoicesState
    data class Failed(val message: String) : TypeChoicesState
}

private sealed interface PricingState {
    data object NotReady : PricingState
    data object Loading : PricingState
    data class Available(val price: ServerTypeLocationPrice) : PricingState
    data object Unavailable : PricingState
    data object Failed : PricingState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerProvisioningScreen(
    repository: HCloudRepository,
    isTestServer: Boolean = false,
    onCancel: () -> Unit,
    onCreated: () -> Unit,
) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var choicesState by remember { mutableStateOf<ChoicesState>(ChoicesState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<ServerTypeOption?>(null) }
    var selectedImage by remember { mutableStateOf<ImageOption?>(null) }
    var selectedLocation by remember { mutableStateOf<LocationOption?>(null) }
    var architectureFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var coresFilter by remember { mutableStateOf<Int?>(null) }
    var memoryFilter by remember { mutableStateOf<Double?>(null) }
    var diskFilter by remember { mutableStateOf<Int?>(null) }
    var pricingState by remember { mutableStateOf<PricingState>(PricingState.NotReady) }
    var ipv4 by remember { mutableStateOf(true) }
    var ipv6 by remember { mutableStateOf(true) }
    var selectedSshKeyIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmation by remember { mutableStateOf(false) }
    var createdServer by remember { mutableStateOf<CreatedServer?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val createSending = stringResource(R.string.provision_create_sending)
    val createProgress = stringResource(R.string.provision_create_progress, "%s")
    val createFailure = stringResource(R.string.provision_create_failed)
    val createRejected = stringResource(R.string.provision_create_rejected, 0, "%s", "%s")
    val createCode = stringResource(R.string.provision_create_error_code, "%s")
    val createRequestId = stringResource(R.string.provision_create_request_id, "%s")
    val createCheckFields = stringResource(R.string.provision_create_check_fields, "%s")
    val createCheckOptions = stringResource(R.string.provision_create_check_options)

    LaunchedEffect(reloadKey) {
        choicesState = try {
            coroutineScope {
                val types = async { repository.getServerTypes() }
                val images = async { repository.getSystemImages() }
                val locations = async { repository.getLocations() }
                val sshKeys = async { repository.getSshKeys() }
                ChoicesState.Ready(ProvisioningChoices(types.await(), images.await(), locations.await(), sshKeys.await()))
            }
        } catch (throwable: Throwable) {
            ChoicesState.Failed("")
        }
    }

    LaunchedEffect(selectedType?.id, selectedLocation?.name) {
        val type = selectedType
        val location = selectedLocation
        pricingState = if (type == null || location == null) {
            PricingState.NotReady
        } else {
            PricingState.Loading
            try {
                repository.getServerTypePrice(type.id, location.name)?.let(PricingState::Available)
                    ?: PricingState.Unavailable
            } catch (_: Throwable) {
                PricingState.Failed
            }
        }
    }

    LaunchedEffect(selectedType?.id, selectedLocation?.name, architectureFilter, categoryFilter, coresFilter, memoryFilter, diskFilter) {
        val type = selectedType
        if (type != null && !matchesActiveTypeFilters(type, selectedLocation, architectureFilter, categoryFilter, coresFilter, memoryFilter, diskFilter)) {
            selectedType = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.provision_title))
                    if (isTestServer) {
                        Text(
                            stringResource(R.string.test_server_badge),
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            navigationIcon = { TextButton(onClick = onCancel) { Text(stringResource(R.string.provision_cancel)) } },
        )
        when (val state = choicesState) {
            ChoicesState.Loading -> LoadingPanel(stringResource(R.string.provision_loading_choices))
            is ChoicesState.Failed -> ErrorPanel(state.message.ifBlank { stringResource(R.string.provision_choices_load_failed) }, onRetry = { reloadKey++ }, onCancel = onCancel)
            is ChoicesState.Ready -> {
                val allTypes = state.choices.types.filter { it.availableLocations.isNotEmpty() }.sortedWith(
                    compareBy<ServerTypeOption> { it.cores }
                        .thenBy { it.memoryGb }
                        .thenBy { it.diskGb }
                        .thenBy { it.name },
                )
                val typesForLocation = allTypes.filter { type ->
                    matchesActiveTypeFilters(type, selectedLocation, architectureFilter, categoryFilter, coresFilter, memoryFilter, diskFilter)
                }
                val locationsForType = state.choices.locations.sortedWith(
                    compareBy<LocationOption> { it.name }.thenBy { it.city.orEmpty() },
                ).filter { location ->
                    selectedType == null || location.name in selectedType!!.availableLocations
                }
                val imagesForType = selectedType?.architecture?.let { architecture -> state.choices.images.filter { it.architecture == null || it.architecture == architecture } } ?: state.choices.images
                val activeFilters = listOfNotNull(
                    architectureFilter?.let { stringResource(R.string.provision_filter_architecture_active, it) },
                    categoryFilter?.let { stringResource(R.string.provision_filter_category_active, it) },
                    coresFilter?.let { stringResource(R.string.provision_filter_cpu_active, it) },
                    memoryFilter?.let { stringResource(R.string.provision_filter_memory_active, it) },
                    diskFilter?.let { stringResource(R.string.provision_filter_disk_active, it) },
                )
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.provision_start_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.provision_name_label)) }, supportingText = { Text(stringResource(R.string.provision_name_example)) }, singleLine = true, isError = name.isBlank())
                    Text(stringResource(R.string.provision_type_filters), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    val any = stringResource(R.string.provision_any)
                    ChoiceField(stringResource(R.string.provision_filter_architecture), architectureFilter ?: any, listOf(any) + allTypes.mapNotNull { it.architecture }.distinct().sorted(), { it }) { architectureFilter = it.takeUnless { value -> value == any }; selectedType = selectedType?.takeIf { selected -> selected.architecture == architectureFilter || architectureFilter == null } }
                    ChoiceField(stringResource(R.string.provision_filter_category), categoryFilter ?: any, listOf(any) + allTypes.mapNotNull { it.category }.distinct().sorted(), { it }) { categoryFilter = it.takeUnless { value -> value == any }; selectedType = selectedType?.takeIf { selected -> selected.category == categoryFilter || categoryFilter == null } }
                    ChoiceField(stringResource(R.string.provision_filter_cores), coresFilter?.let { stringResource(R.string.provision_cpu_value, it) } ?: stringResource(R.string.provision_any_count), listOf(0) + allTypes.map { it.cores }.distinct().sorted(), { if (it == 0) stringResource(R.string.provision_any_count) else stringResource(R.string.provision_cpu_value, it) }) { coresFilter = it.takeIf { value -> value != 0 }; selectedType = selectedType?.takeIf { selected -> selected.cores == coresFilter || coresFilter == null } }
                    ChoiceField(stringResource(R.string.provision_filter_memory), memoryFilter?.let { stringResource(R.string.provision_memory_value, it) } ?: stringResource(R.string.provision_any_size), listOf(-1.0) + allTypes.map { it.memoryGb }.distinct().sorted(), { if (it < 0) stringResource(R.string.provision_any_size) else stringResource(R.string.provision_memory_value, it) }) { memoryFilter = it.takeIf { value -> value >= 0 }; selectedType = selectedType?.takeIf { selected -> selected.memoryGb == memoryFilter || memoryFilter == null } }
                    ChoiceField(stringResource(R.string.provision_filter_disk), diskFilter?.let { stringResource(R.string.provision_disk_value, it) } ?: stringResource(R.string.provision_any_size), listOf(0) + allTypes.map { it.diskGb }.distinct().sorted(), { if (it == 0) stringResource(R.string.provision_any_size) else stringResource(R.string.provision_disk_value, it) }) { diskFilter = it.takeIf { value -> value != 0 }; selectedType = selectedType?.takeIf { selected -> selected.diskGb == diskFilter || diskFilter == null } }
                    TextButton(onClick = { architectureFilter = null; categoryFilter = null; coresFilter = null; memoryFilter = null; diskFilter = null }, enabled = activeFilters.isNotEmpty()) { Text(stringResource(R.string.provision_clear_filters)) }
                    val filtersSummary = if (activeFilters.isEmpty()) stringResource(R.string.provision_active_filters_none, typesForLocation.size) else stringResource(R.string.provision_active_filters, activeFilters.joinToString(), typesForLocation.size)
                    Text(
                        filtersSummary,
                        modifier = Modifier
                            .testTag("server_type_active_filters")
                            .semantics { contentDescription = filtersSummary },
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AvailableServerTypes(types = typesForLocation, selectedType = selectedType, onSelect = { type ->
                        selectedType = type
                        if (selectedLocation != null && selectedLocation!!.name !in type.availableLocations) selectedLocation = null
                        if (selectedImage?.architecture != null && type.architecture != null && selectedImage!!.architecture != type.architecture) selectedImage = null
                    })
                    Text(stringResource(R.string.provision_selected_type, selectedType?.let { typeDescription(it) } ?: stringResource(R.string.provision_not_selected)), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceField(stringResource(R.string.provision_location_label), selectedLocation?.let { "${it.name} · ${it.city ?: it.description}" } ?: stringResource(R.string.provision_location_choose), locationsForType, { "${it.name} · ${it.city ?: it.description}" }, { selectedLocation = it; if (selectedType != null && it.name !in selectedType!!.availableLocations) selectedType = null })
                    ChoiceField(stringResource(R.string.provision_type_label), selectedType?.let { typeDescription(it) } ?: stringResource(R.string.provision_type_choose), typesForLocation, { type -> typeDescription(type) }, { selectedType = it; if (selectedLocation != null && selectedLocation!!.name !in it.availableLocations) selectedLocation = null; if (selectedImage?.architecture != null && it.architecture != null && selectedImage!!.architecture != it.architecture) selectedImage = null })
                    if (typesForLocation.isEmpty()) InlineError(stringResource(R.string.provision_no_types_for_filters))
                    PriceSummary(pricingState)
                    ChoiceField(stringResource(R.string.provision_image_label), selectedImage?.let { "${it.name} · ${it.description}" } ?: stringResource(R.string.provision_image_choose), imagesForType, { "${it.name} · ${it.description}" }, { selectedImage = it })
                    SshKeySelector(
                        keys = state.choices.sshKeys,
                        selectedIds = selectedSshKeyIds,
                        onSelectionChange = { selectedSshKeyIds = it },
                    )
                    Text(stringResource(R.string.provision_network), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    NetworkSwitch(stringResource(R.string.provision_ipv4), ipv4) { ipv4 = it }
                    NetworkSwitch(stringResource(R.string.provision_ipv6), ipv6) { ipv6 = it }
                    if (!ipv4 && !ipv6) {
                        ConnectivityWarning(stringResource(R.string.provision_no_public_network))
                    }
                    BillableWarning(stringResource(R.string.provision_billable_create))
                    error?.let { InlineError(it) }
                    if (selectedLocation == null) Text(stringResource(R.string.provision_location_first), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    if (highImpactAllowed) {
                        Button(onClick = {
                            error = null
                            confirmation = true
                        }, enabled = name.isNotBlank() && selectedType != null && selectedImage != null && selectedLocation != null && !submitting, modifier = Modifier.fillMaxWidth()) { if (submitting) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp)); else Text(stringResource(R.string.provision_review_create)) }
                    }
                }
            }
        }
    }
    if (highImpactAllowed && confirmation && selectedType != null && selectedImage != null && selectedLocation != null) {
        AlertDialog(
            onDismissRequest = { if (!submitting) confirmation = false },
            title = { Text(stringResource(R.string.provision_create_confirm_title, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.provision_create_confirm_message, typeDescription(selectedType!!), selectedImage!!.name.orEmpty(), selectedLocation!!.name.orEmpty()))
                    PriceSummary(pricingState)
                    error?.let {
                        InlineError(it)
                        Text(
                            stringResource(R.string.provision_form_preserved),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (submitting) OperationProgress(operationMessage)
                }
            },
            confirmButton = { Button(onClick = {
                error = null
                submitting = true
                operationMessage = createSending
                scope.launch {
                    try {
                        val result = repository.createServer(CreateServerSpec(name, selectedType!!.name, selectedImage!!.name, selectedLocation!!.name, ipv4, ipv6, sshKeyIds = selectedSshKeyIds.toList()))
                        awaitHetznerActions(repository, result.actions) { status ->
                            operationMessage = createProgress.format(actionStatusText(status))
                        }
                        confirmation = false
                        if (result.rootPassword == null) onCreated() else createdServer = result
                    } catch (throwable: Throwable) {
                        error = localizedCreateServerFailureMessage(throwable, createFailure, createRejected, createCode, createRequestId, createCheckFields, createCheckOptions)
                    } finally { submitting = false }
                }
            }, enabled = !submitting) { Text(if (submitting) stringResource(R.string.provision_creating) else stringResource(R.string.provision_create_paid)) } },
            dismissButton = { OutlinedButton(onClick = { confirmation = false }, enabled = !submitting) { Text(stringResource(R.string.provision_cancel)) } },
        )
    }
    createdServer?.let { result ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.provision_root_password_title)) },
            text = { Text(stringResource(R.string.provision_root_password_message, result.server.name, result.rootPassword.orEmpty())) },
            confirmButton = { Button(onClick = { createdServer = null; onCreated() }) { Text(stringResource(R.string.provision_password_saved)) } },
        )
    }
}

@Composable
private fun NetworkSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = onChange)
}

@Composable
private fun LoadingPanel(message: String) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(message)
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit, onCancel: () -> Unit) = Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(stringResource(R.string.provision_load_failed), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    TextButton(onClick = onCancel) { Text(stringResource(R.string.provision_cancel)) }
}

@Composable
private fun BillableWarning(message: String) = Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) {
    Text(stringResource(R.string.provision_important, message), Modifier.padding(16.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer)
}

@Composable
private fun ConnectivityWarning(message: String) = Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer)) {
    Text(stringResource(R.string.provision_no_internet, message), Modifier.padding(16.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
}

@Composable
private fun OperationProgress(message: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(12.dp))
    Text(message, modifier = Modifier.weight(1f), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InlineError(message: String) = Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)

/**
 * Keeps server-side diagnostics useful without exposing an API response, request data, or
 * generated credentials. In particular, do not use [Throwable.message] here.
 */
internal fun createServerFailureMessage(throwable: Throwable): String {
    val failure = (throwable as? HCloudApiException)?.failure
        ?: return "Не удалось создать сервер. Проверьте подключение и повторите попытку."
    return buildString {
        append("Hetzner Cloud отклонил создание сервера (HTTP ${failure.httpStatus}")
        failure.code?.takeIf { it.isNotBlank() }?.let { append(", код: $it") }
        failure.requestId?.takeIf { it.isNotBlank() }?.let { append(", request ID: $it") }
        append(").")
        val fields = safeCreateServerFieldNames(failure.fieldNames)
        if (fields.isEmpty()) {
            append(" Проверьте выбранные параметры и повторите попытку.")
        } else {
            append(" Проверьте поля: ${fields.joinToString()}.")
        }
    }
}

private fun localizedCreateServerFailureMessage(
    throwable: Throwable,
    genericFailure: String,
    rejectedTemplate: String,
    codeTemplate: String,
    requestIdTemplate: String,
    checkFieldsTemplate: String,
    checkOptions: String,
): String {
    val failure = (throwable as? HCloudApiException)?.failure ?: return genericFailure
    val code = failure.code?.takeIf { it.isNotBlank() }?.let { codeTemplate.format(it) }.orEmpty()
    val requestId = failure.requestId?.takeIf { it.isNotBlank() }?.let { requestIdTemplate.format(it) }.orEmpty()
    val base = rejectedTemplate.format(failure.httpStatus, code, requestId)
    val fields = safeCreateServerFieldNames(failure.fieldNames)
    return if (fields.isEmpty()) "$base $checkOptions" else "$base ${checkFieldsTemplate.format(fields.joinToString())}"
}

/** Only names represented by the current create-server request may be shown to the user. */
internal fun safeCreateServerFieldNames(fieldNames: List<String>): List<String> =
    fieldNames.filter {
        it in setOf("name", "server_type", "image", "location", "public_net", "ssh_keys")
    }.distinct()

/** Uses only fields returned by the Server Types endpoint; no capacity is inferred. */
private fun resizeTypeDescription(context: android.content.Context, type: ServerTypeOption): String =
    context.getString(R.string.resize_type_summary, type.name, type.cores, type.memoryGb, type.diskGb)

@Composable
private fun typeDescription(type: ServerTypeOption): String = stringResource(
    R.string.provision_type_description,
    type.name,
    type.cores,
    type.memoryGb,
    type.diskGb,
)

private fun matchesActiveTypeFilters(
    type: ServerTypeOption,
    location: LocationOption?,
    architecture: String?,
    category: String?,
    cores: Int?,
    memoryGb: Double?,
    diskGb: Int?,
): Boolean =
    (architecture == null || type.architecture == architecture) &&
        (category == null || type.category == category) &&
        (cores == null || type.cores == cores) &&
        (memoryGb == null || type.memoryGb == memoryGb) &&
        (diskGb == null || type.diskGb == diskGb) &&
        (location == null || location.name in type.availableLocations)

@Composable
private fun PriceSummary(state: PricingState) = when (state) {
    PricingState.NotReady -> Text(stringResource(R.string.pricing_not_ready), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    PricingState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.pricing_loading), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
    is PricingState.Available -> Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pricing_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pricing_currency, state.price.currency))
            Text(stringResource(R.string.pricing_hourly, formatApiMoney(state.price.hourly.gross, Locale.getDefault()), state.price.currency))
            Text(stringResource(R.string.pricing_monthly, formatApiMoney(state.price.monthly.gross, Locale.getDefault()), state.price.currency))
            state.price.vatRate.takeUnless { vatRateIsZero(it) }?.let { vatRate ->
                Text(stringResource(R.string.pricing_vat, formatVatPercent(vatRate)), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(stringResource(R.string.pricing_api_note), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    PricingState.Unavailable -> Text(stringResource(R.string.pricing_unavailable), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    PricingState.Failed -> Text(stringResource(R.string.pricing_failed), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
}

/** Formats an API decimal without deriving or changing its monetary value. */
internal fun formatApiMoney(raw: String, locale: Locale = Locale("ru", "RU")): String {
    val decimal = raw.toBigDecimalOrNull() ?: return raw
    val fractionDigits = maxOf(2, decimal.stripTrailingZeros().scale().coerceAtLeast(0))
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(decimal)
}

private fun vatRateIsZero(raw: String): Boolean = raw.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0

private fun formatVatPercent(raw: String): String {
    val percent = raw.toBigDecimalOrNull()?.movePointRight(2) ?: return raw
    val fractionDigits = percent.stripTrailingZeros().scale().coerceAtLeast(0)
    return NumberFormat.getNumberInstance(Locale("ru", "RU")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }.format(percent) + "%"
}

@Composable
private fun AvailableServerTypes(types: List<ServerTypeOption>, selectedType: ServerTypeOption?, onSelect: (ServerTypeOption) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val resultCount = stringResource(R.string.provision_available_types_count, types.size)
    Text(
        resultCount,
        modifier = Modifier
            .testTag("server_type_filtered_result_count")
            .semantics { contentDescription = resultCount },
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
    )
    if (types.isEmpty()) {
        Text(stringResource(R.string.provision_no_matching_types), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        types.forEach { type ->
            val typeLabel = typeDescription(type)
            val isSelected = type.id == selectedType?.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        selected = isSelected
                    }
                    .clickable { onSelect(type) },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(typeLabel, Modifier.padding(14.dp), color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SshKeySelector(keys: List<SshKeyOption>, selectedIds: Set<Long>, onSelectionChange: (Set<Long>) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.ssh_keys_title), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.ssh_keys_description), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    if (keys.isEmpty()) {
        Text(stringResource(R.string.ssh_keys_empty), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        keys.forEach { key ->
            val selected = key.id in selectedIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectionChange(if (selected) selectedIds - key.id else selectedIds + key.id)
                    }
                    .semantics { contentDescription = key.name },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = null)
                Column(Modifier.weight(1f)) {
                    Text(key.name)
                    Text(stringResource(R.string.ssh_keys_fingerprint, key.fingerprint), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (selectedIds.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer)) {
            Text(stringResource(R.string.ssh_keys_no_selection_warning), Modifier.padding(16.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
        }
    } else {
        Text(stringResource(R.string.ssh_keys_selected, selectedIds.size), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChoiceField(label: String, value: String, choices: List<T>, render: @Composable (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value", modifier = Modifier.weight(1f)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(render(choice)) }, onClick = { expanded = false; onPick(choice) }) }
        }
    }
}

@Composable
internal fun ChangeServerTypeScreen(server: Server, repository: HCloudRepository, onCancel: () -> Unit, onChanged: () -> Unit) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val context = LocalContext.current
    var typesState by remember { mutableStateOf<TypeChoicesState>(TypeChoicesState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<ServerTypeOption?>(null) }
    var upgradeDisk by remember { mutableStateOf(false) }
    var diskUpgradeAcknowledged by remember { mutableStateOf(false) }
    var pricingState by remember { mutableStateOf<PricingState>(PricingState.NotReady) }
    var confirmation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        typesState = TypeChoicesState.Loading
        error = null
        typesState = try {
            TypeChoicesState.Ready(repository.getServerTypes().filter { it.name != server.serverType && server.location in it.availableLocations })
        } catch (_: Throwable) {
            TypeChoicesState.Failed("Не удалось загрузить доступные типы. Проверьте подключение и попробуйте снова.")
        }
    }
    LaunchedEffect(selected?.id, server.location) {
        val target = selected
        pricingState = if (target == null) {
            PricingState.NotReady
        } else {
            PricingState.Loading
            try {
                repository.getServerTypePrice(target.id, server.location)?.let(PricingState::Available)
                    ?: PricingState.Unavailable
            } catch (_: Throwable) {
                PricingState.Failed
            }
        }
    }
    when (val state = typesState) {
        TypeChoicesState.Loading -> LoadingPanel(stringResource(R.string.resize_types_loading))
        is TypeChoicesState.Failed -> ErrorPanel(stringResource(R.string.resize_types_load_failed), onRetry = { reloadKey++ }, onCancel = onCancel)
        is TypeChoicesState.Ready -> Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.resize_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.resize_server, server.name))
            Text(stringResource(R.string.resize_current_type, server.serverType), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.resize_location, server.location), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            if (server.status != com.hcloudcontrol.core.domain.model.ServerStatus.OFF) {
                BillableWarning(stringResource(R.string.resize_server_must_be_off))
            } else {
                Text(stringResource(R.string.resize_server_off), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            }
            ChoiceField(
                stringResource(R.string.resize_target_type),
                selected?.let { resizeTypeDescription(context, it) } ?: stringResource(R.string.resize_choose_type),
                state.types.sortedWith(compareBy<ServerTypeOption> { it.cores }.thenBy { it.memoryGb }.thenBy { it.diskGb }.thenBy { it.name }),
                { resizeTypeDescription(context, it) },
            ) { selected = it }
            selected?.let {
                Text(stringResource(R.string.resize_price_location, it.name, server.location), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                PriceSummary(pricingState)
            }
            NetworkSwitch(stringResource(R.string.resize_upgrade_disk_label), upgradeDisk) {
                upgradeDisk = it
                if (!it) diskUpgradeAcknowledged = false
            }
            if (upgradeDisk) {
                BillableWarning(stringResource(R.string.resize_disk_upgrade))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { diskUpgradeAcknowledged = !diskUpgradeAcknowledged },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = diskUpgradeAcknowledged, onCheckedChange = null)
                    Text(stringResource(R.string.resize_disk_upgrade_acknowledgement))
                }
            }
            else Text(stringResource(R.string.resize_keep_disk), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { InlineError(it) }
            if (highImpactAllowed) Button(onClick = { error = null; confirmation = true }, enabled = selected != null && server.status == com.hcloudcontrol.core.domain.model.ServerStatus.OFF && (!upgradeDisk || diskUpgradeAcknowledged) && !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resize_review)) }
            OutlinedButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.provision_cancel)) }
        }
    }
    if (highImpactAllowed && confirmation && selected != null) AlertDialog(
        onDismissRequest = { if (!submitting) confirmation = false },
        title = { Text(stringResource(R.string.resize_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.resize_confirm_message, server.name, server.serverType, selected!!.name, if (upgradeDisk) stringResource(R.string.resize_disk_upgrade) else stringResource(R.string.resize_disk_keep)))
                if (submitting) OperationProgress(operationMessage)
                error?.let { InlineError(it) }
            }
        },
        confirmButton = { Button(onClick = { val target = selected ?: return@Button; submitting = true; error = null; operationMessage = context.getString(R.string.resize_sending); scope.launch { try { val action = repository.changeServerType(server.id, target.name, upgradeDisk = upgradeDisk); awaitHetznerActions(repository, listOf(action)) { status -> operationMessage = context.getString(R.string.resize_progress, actionStatusText(status)) }; onChanged() } catch (_: Throwable) { error = context.getString(R.string.resize_failed) } finally { submitting = false } } }, enabled = !submitting) { Text(if (submitting) stringResource(R.string.resize_changing) else stringResource(R.string.resize_confirm)) } },
        dismissButton = { OutlinedButton(onClick = { confirmation = false }, enabled = !submitting) { Text(stringResource(R.string.provision_cancel)) } },
    )
}

/** Hetzner returns long-running actions for provisioning and resizing. */
private suspend fun awaitHetznerActions(repository: HCloudRepository, actions: List<com.hcloudcontrol.core.domain.model.CloudAction>, onProgress: (String) -> Unit) {
    actions.forEach { initial ->
        var action = initial
        repeat(600) {
            onProgress(action.status)
            when (action.status.lowercase()) {
                "success" -> return@forEach
                "error" -> throw IllegalStateException("Hetzner action failed")
            }
            delay(1_000)
            action = repository.getAction(action.id)
        }
        throw IllegalStateException("Hetzner action did not finish in time")
    }
}

private fun actionStatusText(status: String): String = when (status.lowercase()) {
    "running" -> "выполняется"
    "pending" -> "ожидает выполнения"
    "success" -> "завершена"
    "error" -> "завершилась с ошибкой"
    else -> "обрабатывается"
}
