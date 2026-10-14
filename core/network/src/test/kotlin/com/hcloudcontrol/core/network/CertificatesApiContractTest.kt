package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CertificatesApiContractTest {
    @Test
    fun `certificate inventory detail and action history paginate with only HCloud credential and no certificate payload exposure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"certificates":[{"id":701,"name":"managed-web","type":"managed","created":"2026-10-01T00:00:00Z","not_valid_before":"2026-10-01T00:00:00Z","not_valid_after":"2027-10-01T00:00:00Z","domain_names":["example.test"],"fingerprint":"AA:BB:CC","status":{"issuance":"completed","renewal":"scheduled","error":null},"used_by":[{"id":901,"type":"load_balancer"}],"labels":{"environment":"production"},"certificate":"-----BEGIN CERTIFICATE-----never-map-me"}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"certificates":[{"id":702,"name":"uploaded-api","type":"uploaded","domain_names":["api.example.test"],"labels":{}}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"certificate":{"id":701,"name":"managed-web","type":"managed","domain_names":["example.test"],"labels":{}}}"""))
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":1501,"command":"retry_certificate_issuance","status":"running","progress":50}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":1502,"command":"retry_certificate_issuance","status":"error","error":{"code":"action_failed","message":"Issuance failed"}}],"meta":{"pagination":{"next_page":null}}}"""))
            val cloudToken = "project-cloud-token"
            val storageBoxToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val certificates = repository.getCertificates()
            val detail = repository.getCertificate(701)
            val actions = repository.getCertificateActions(701)

            assertEquals(listOf(701L, 702L), certificates.map { it.id })
            assertEquals("completed", certificates.first().status?.issuance)
            assertEquals(901L, certificates.first().usedBy.single().id)
            assertEquals("managed-web", detail.name)
            assertEquals(listOf(1501L, 1502L), actions.map { it.id })
            assertEquals("action_failed", actions.last().errorCode)
            val requests = List(5) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/certificates?page=1&per_page=50", requests[0].path)
            assertEquals("/v1/certificates?page=2&per_page=50", requests[1].path)
            assertEquals("/v1/certificates/701", requests[2].path)
            assertEquals("/v1/certificates/701/actions?page=1&per_page=50", requests[3].path)
            assertEquals("/v1/certificates/701/actions?page=2&per_page=50", requests[4].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(storageBoxToken))
            }
        }
    }
}
