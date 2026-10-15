package com.hcloudcontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.model.ServerTypeLocationPrice
import com.hcloudcontrol.core.domain.model.SshKeyOption
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.toCostPresentation
import java.text.NumberFormat
import java.util.Locale

internal sealed interface PricingState {
    data object NotReady : PricingState
    data object Loading : PricingState
    data class Available(val price: ServerTypeLocationPrice) : PricingState {
        val retrievedAtMillis: Long = System.currentTimeMillis()
    }
    data object Unavailable : PricingState
    data object Failed : PricingState
}

@Composable
internal fun ServerProvisioningScreen(
    repository: HCloudRepository,
    isTestServer: Boolean = false,
    initialServerType: ServerTypeOption? = null,
    initialLocation: LocationOption? = null,
    onCancel: () -> Unit,
    onCreated: () -> Unit,
    onCreatedResource: ((Long) -> Unit)? = null,
    onManageSshKeys: (() -> Unit)? = null,
) = UnifiedServerProvisioningScreen(repository, isTestServer, initialServerType, initialLocation,
    onCancel, onCreated, onCreatedResource, onManageSshKeys)

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

/** Only names represented by the current create-server request may be shown to the user. */
internal fun safeCreateServerFieldNames(fieldNames: List<String>): List<String> =
    fieldNames.filter {
        it in setOf("name", "server_type", "image", "location", "public_net", "ssh_keys")
    }.distinct()

@Composable
internal fun PriceSummary(state: PricingState) {
    com.hcloudcontrol.ui.designsystem.HCloudCostPresentation(pricePresentation(state))
}

internal fun pricePresentation(state: PricingState): com.hcloudcontrol.ui.designsystem.CostPresentation = when (state) {
        PricingState.NotReady, PricingState.Unavailable -> com.hcloudcontrol.ui.designsystem.CostPresentation.Unavailable
        PricingState.Loading -> com.hcloudcontrol.ui.designsystem.CostPresentation.Loading
        PricingState.Failed -> com.hcloudcontrol.ui.designsystem.CostPresentation.Error()
        is PricingState.Available -> state.price.toCostPresentation(
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(state.retrievedAtMillis)),
        )
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

@Composable
internal fun SshKeySelector(keys: List<SshKeyOption>, selectedIds: Set<Long>, onSelectionChange: (Set<Long>) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
internal fun ChangeServerTypeScreen(server: Server, repository: HCloudRepository, onCancel: () -> Unit, onChanged: () -> Unit) =
    UnifiedResizeServerScreen(server, repository, onCancel, onChanged)
