package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BillingRateCardContractTest {
    @Test
    fun `pricing preserves exact decimal strings and only uses the HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{
                      "pricing": {
                        "currency":"EUR", "vat_rate":"19.00",
                        "server_types":[{
                          "id":22, "name":"cpx21", "prices":[{
                            "location":"fsn1",
                            "price_hourly":{"net":"0.0083000000","gross":"0.0098770000"},
                            "price_monthly":{"net":"5.4900000000","gross":"6.5331000000"}
                          }]
                        }]
                      }
                    }""".trimIndent(),
                ),
            )
            val hCloudToken = "hcloud-project-token"
            val storageBoxToken = "storage-box-token"
            val repository = RetrofitBillingRateCardRepository(
                HCloudApiFactory.create(
                    tokenProvider = { hCloudToken },
                    configProvider = {
                        HCloudConnectionConfig(
                            kind = ConnectionKind.REAL,
                            baseUrl = server.url("/v1").toString(),
                        )
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val rateCard = repository.getRateCard()

            assertEquals("EUR", rateCard.currency)
            assertEquals("19.00", rateCard.vatRate)
            assertEquals("0.0083000000", rateCard.serverTypes.single().locations.single().hourly.net)
            assertEquals("0.0098770000", rateCard.serverTypes.single().locations.single().hourly.gross)
            assertEquals("5.4900000000", rateCard.serverTypes.single().locations.single().monthly.net)
            assertEquals("6.5331000000", rateCard.serverTypes.single().locations.single().monthly.gross)
            val request = requireNotNull(server.takeRequest())
            assertEquals("/v1/pricing", request.path)
            assertEquals("Bearer $hCloudToken", request.getHeader("Authorization"))
            assertFalse(request.toString().contains(storageBoxToken))
        }
    }
}
