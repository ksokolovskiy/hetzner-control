package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.BillingDecimalAmount
import com.hcloudcontrol.core.domain.model.BillingLocationRate
import com.hcloudcontrol.core.domain.model.BillingRateCard
import com.hcloudcontrol.core.domain.model.BillingServerTypeRate
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.repository.BillingRateCardRepository

/** Uses the HCloud API client only; Storage Box hosts and credentials are never accepted here. */
internal class RetrofitBillingRateCardRepository(private val api: HCloudApi) : BillingRateCardRepository {
    override suspend fun getRateCard(): BillingRateCard = api.getPricing().pricing.toBillingRateCard()
}

object BillingRateCardRepositoryFactory {
    fun create(
        hCloudTokenProvider: () -> String?,
        hCloudConfigProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
    ): BillingRateCardRepository = RetrofitBillingRateCardRepository(
        HCloudApiFactory.create(hCloudTokenProvider, hCloudConfigProvider),
    )
}

private fun PricingDto.toBillingRateCard() = BillingRateCard(
    currency = currency,
    vatRate = vatRate,
    serverTypes = serverTypes.map { type ->
        BillingServerTypeRate(
            id = type.id,
            name = type.name,
            locations = type.prices.map { price ->
                BillingLocationRate(
                    location = price.location,
                    hourly = price.priceHourly.toBillingDecimalAmount(),
                    monthly = price.priceMonthly.toBillingDecimalAmount(),
                )
            },
        )
    },
)

private fun MoneyDto.toBillingDecimalAmount() = BillingDecimalAmount(net = net, gross = gross)
