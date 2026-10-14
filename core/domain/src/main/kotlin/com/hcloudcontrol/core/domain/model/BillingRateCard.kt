package com.hcloudcontrol.core.domain.model

/**
 * Project-scoped HCloud price catalogue returned by GET /pricing.
 * It is not an invoice, current usage, forecast, or amount due.
 */
data class BillingRateCard(
    val currency: String,
    val vatRate: String,
    val serverTypes: List<BillingServerTypeRate>,
)

data class BillingServerTypeRate(
    val id: Long,
    val name: String,
    val locations: List<BillingLocationRate>,
)

data class BillingLocationRate(
    val location: String,
    val hourly: BillingDecimalAmount,
    val monthly: BillingDecimalAmount,
)

/** Exact API decimal text. Deliberately not Float/Double and never converted or rounded here. */
data class BillingDecimalAmount(val net: String, val gross: String)
