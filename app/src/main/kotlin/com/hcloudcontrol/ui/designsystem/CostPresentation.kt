package com.hcloudcontrol.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.R
import com.hcloudcontrol.formatApiMoney
import com.hcloudcontrol.core.domain.model.ServerTypeLocationPrice
import com.hcloudcontrol.core.domain.model.BillingLocationRate
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

enum class CostComponentCoverage { INCLUDED, EXCLUDED, UNKNOWN }
data class CostComponent(val label: String, val coverage: CostComponentCoverage)
data class CostSnapshot(val typeLabel: String, val locationLabel: String, val currency: String,
    val hourly: String?, val monthly: String?, val vatRate: String?, val retrievedAt: String,
    val components: List<CostComponent> = emptyList(), val gross: Boolean = true) {
    val incomplete: Boolean get() = hourly.validMoney() == null || monthly.validMoney() == null ||
        currency.isBlank() || vatRate?.toBigDecimalOrNull() == null || components.any { it.coverage == CostComponentCoverage.UNKNOWN }
}
sealed interface CostPresentation {
    data object Loading : CostPresentation
    data object Unavailable : CostPresentation
    data class Error(val safeMessage: String? = null) : CostPresentation
    data class Available(val snapshot: CostSnapshot) : CostPresentation
}

/** Retrieval time is supplied by the successful read owner; adapters never invent freshness. */
fun ServerTypeLocationPrice.toCostPresentation(retrievedAt: String, locationLabel: String = location,
    components: List<CostComponent> = emptyList()): CostPresentation = CostPresentation.Available(
    CostSnapshot(serverTypeName, locationLabel, currency, hourly.gross, monthly.gross, vatRate, retrievedAt, components),
)

fun BillingLocationRate.toCostPresentation(typeLabel: String, currency: String, vatRate: String?,
    retrievedAt: String, locationLabel: String = location,
    components: List<CostComponent> = emptyList()): CostPresentation = CostPresentation.Available(
    CostSnapshot(typeLabel, locationLabel, currency, hourly.gross, monthly.gross, vatRate, retrievedAt, components),
)

private fun String?.validMoney(): BigDecimal? = this?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

/** Hetzner pricing vat_rate is already a percentage, not a fractional multiplier. */
fun formatCostVat(raw: String?, locale: Locale = Locale.getDefault()): String? = raw.validMoney()?.let {
    java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; isGroupingUsed = false }.format(it)
}

/** Bounds display precision only. Tiny known amounts remain visibly positive; missing stays unknown. */
fun formatCostAmount(raw: String?, locale: Locale = Locale.getDefault(), digits: Int = 6): String? {
    val value = raw.validMoney() ?: return null
    val scale = digits.coerceIn(2, 6)
    val rounded = value.setScale(scale, RoundingMode.HALF_UP)
    if (value.signum() > 0 && rounded.signum() == 0) {
        return "<" + formatApiMoney(BigDecimal.ONE.movePointLeft(scale).toPlainString(), locale)
    }
    return formatApiMoney(rounded.stripTrailingZeros().toPlainString(), locale)
}

@Composable
fun HCloudCostPresentation(state: CostPresentation, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    when (state) {
        CostPresentation.Loading -> HCloudSectionState(SectionPresentation(SectionKind.LOADING), modifier)
        CostPresentation.Unavailable -> HCloudSectionState(SectionPresentation(SectionKind.EMPTY,
            message = stringResource(R.string.ds_cost_unknown)), modifier)
        is CostPresentation.Error -> HCloudSectionState(SectionPresentation(SectionKind.ERROR, state.safeMessage), modifier, onRetry)
        is CostPresentation.Available -> {
            val cost = state.snapshot
            HCloudSectionState(SectionPresentation(SectionKind.CONTENT, updatedAt = cost.retrievedAt), modifier) {
                Text(stringResource(R.string.ds_cost_rates), style = MaterialTheme.typography.titleMedium)
                Text(listOf(cost.typeLabel, cost.locationLabel).joinToString(" · "))
                if (cost.incomplete) Text(stringResource(R.string.ds_cost_incomplete), color = MaterialTheme.colorScheme.error)
                val unknown = stringResource(R.string.ds_cost_unknown)
                Text(stringResource(R.string.ds_cost_hourly, formatCostAmount(cost.hourly) ?: unknown, cost.currency))
                Text(stringResource(R.string.ds_cost_monthly, formatCostAmount(cost.monthly, digits = 2) ?: unknown, cost.currency))
                Text(stringResource(if (cost.gross) R.string.ds_cost_gross else R.string.ds_cost_net))
                Text(stringResource(R.string.ds_cost_vat, formatCostVat(cost.vatRate) ?: unknown))
                cost.components.forEach { component ->
                    Text(stringResource(when (component.coverage) {
                        CostComponentCoverage.INCLUDED -> R.string.ds_cost_included
                        CostComponentCoverage.EXCLUDED -> R.string.ds_cost_excluded
                        CostComponentCoverage.UNKNOWN -> R.string.ds_cost_component_unknown
                    }, component.label))
                }
                Text(stringResource(R.string.ds_cost_not_spending), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
