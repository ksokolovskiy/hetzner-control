package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoadBalancerTypesContractTest {
    @Test
    fun `Load Balancer type catalog uses documented paginated Cloud GET paths only`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"load_balancer_types":[{"id":1,"name":"lb11","description":"small","max_services":5,"max_targets":25,"max_connections":20000,"prices":[{"location":"fsn1","price_hourly":{"net":"0.0083","gross":"0.0099"},"price_monthly":{"net":"5.49","gross":"6.53"}}]}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"load_balancer_types":[{"id":2,"name":"lb21","description":"medium","max_services":10,"prices":[]}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"load_balancer_type":{"id":2,"name":"lb21","description":"medium","max_services":10,"max_targets":50,"max_ssl_certificates":10,"prices":[{"location":"nbg1","price_hourly":{"net":"0.016","gross":"0.019"},"price_monthly":{"net":"10.59","gross":"12.60"}}]}}"""))
            val cloudToken = "project-cloud-token"
            val storageBoxToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val types = repository.getLoadBalancerTypes()
            val detail = repository.getLoadBalancerType(2)

            assertEquals(listOf("lb11", "lb21"), types.map { it.name })
            assertEquals(5, types.first().maxServices)
            assertEquals("medium", detail.description)
            assertEquals(10, detail.maxSslCertificates)
            val requests = List(3) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/load_balancer_types?page=1&per_page=50", requests[0].path)
            assertEquals("/v1/load_balancer_types?page=2&per_page=50", requests[1].path)
            assertEquals("/v1/load_balancer_types/2", requests[2].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(storageBoxToken))
            }
        }
    }
}
