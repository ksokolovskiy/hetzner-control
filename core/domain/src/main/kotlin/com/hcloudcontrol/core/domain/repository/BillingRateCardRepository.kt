package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.BillingRateCard

/** Read-only HCloud rate-card boundary. It intentionally exposes no billing-account statements. */
interface BillingRateCardRepository {
    suspend fun getRateCard(): BillingRateCard
}
