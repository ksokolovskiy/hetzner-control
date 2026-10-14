package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ConnectionKind
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HCloudApiDiagnosticsInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `success log redacts IDs and excludes query values and request body`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("X-Request-ID", "req-42").setBody("{}"))
        val diagnostics = mutableListOf<HCloudApiDiagnostic>()
        val client = client(diagnostics)
        val secret = "never-log-this-server-name-or-password"
        val request = Request.Builder()
            .url(server.url("/v1/servers/42?name=$secret&token=never-log-token"))
            .post("{\"root_password\":\"$secret\"}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response -> assertEquals(200, response.code) }

        val event = diagnostics.single()
        assertEquals("POST", event.method)
        assertEquals("/v1/servers/{id}", event.redactedPath)
        assertEquals(200, event.httpStatus)
        assertEquals("req-42", event.requestId)
        assertNull(event.apiCode)
        assertFalse(event.networkFailure)
        assertFalse(event.toString().contains(secret))
        assertFalse(event.toString().contains("never-log-token"))
    }

    @Test
    fun `HTTP error log is passive and exposes only safe parsed detail`() {
        val token = "never-log-token"
        val rootPassword = "never-log-root-password"
        server.enqueue(
            MockResponse().setResponseCode(422).setHeader("X-Request-ID", "request-422").setBody(
                """{
                  "error": {
                    "code": "invalid_input",
                    "message": "Authorization: Bearer $token; X-Session: opaque-header-value; ssh_key=ssh-rsa-do-not-log; root_password=$rootPassword; request={\"name\":\"secret-host\"}",
                    "details": { "fields": [{ "name": "server_type" }, { "name": "location" }, { "name": "ssh-rsa-do-not-log" }] }
                  }
                }""".trimIndent(),
            ),
        )
        val diagnostics = mutableListOf<HCloudApiDiagnostic>()
        val client = client(diagnostics)

        client.newCall(Request.Builder().url(server.url("/v1/servers/77?label_selector=secret=value")).build()).execute().use { response ->
            // The interceptor is only a diagnostic observer.  It must return non-2xx responses
            // to Retrofit instead of throwing on OkHttp's dispatcher thread.
            assertEquals(422, response.code)
        }

        val event = diagnostics.single()
        assertEquals("/v1/servers/{id}", event.redactedPath)
        assertEquals(422, event.httpStatus)
        assertEquals("invalid_input", event.apiCode)
        assertEquals("request-422", event.requestId)
        assertEquals("Hetzner API error code: invalid_input", event.apiMessage)
        assertTrue(event.fieldNames.isEmpty())
        assertFalse(event.toString().contains(token))
        assertFalse(event.toString().contains(rootPassword))
        assertFalse(event.toString().contains("secret-host"))
        assertFalse(event.toString().contains("opaque-header-value"))
        assertFalse(event.toString().contains("ssh-rsa-do-not-log"))
    }

    @Test
    fun `network failure logs safe attempt without response data`() {
        val diagnostics = mutableListOf<HCloudApiDiagnostic>()
        val client = OkHttpClient.Builder()
            .addInterceptor(HCloudApiDiagnosticsInterceptor(HCloudApiDiagnosticsLogger(diagnostics::add)))
            .addInterceptor { throw IOException("network failed for never-log-token") }
            .build()

        val thrown = runCatching {
            client.newCall(Request.Builder().url("https://example.invalid/v1/servers/42?token=never-log-token").build()).execute()
        }.exceptionOrNull()

        assertTrue(thrown is IOException)
        val event = diagnostics.single()
        assertEquals("GET", event.method)
        assertEquals("/v1/servers/{id}", event.redactedPath)
        assertNull(event.httpStatus)
        assertNull(event.apiCode)
        assertNull(event.apiMessage)
        assertTrue(event.networkFailure)
        assertFalse(event.toString().contains("never-log-token"))
    }

    @Test
    fun `new resource endpoint diagnostics keep documented segments while redacting identifiers`() {
        assertEquals(
            "/v1/load_balancer_types/{id}",
            redactHetznerPath("/v1/load_balancer_types/2"),
        )
        assertEquals(
            "/v1/networks/{id}/members",
            redactHetznerPath("/v1/networks/44/members"),
        )
        assertEquals(
            "/v1/firewalls/{id}/actions/apply_to_resources",
            redactHetznerPath("/v1/firewalls/7/actions/apply_to_resources"),
        )
    }

    @Test
    fun `Storage Box factory maps non2xx to a safe typed error and diagnostics`() = runBlocking {
        val token = "storage-box-secret-token"
        val rootPassword = "never-log-root-password"
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("X-Request-ID", "storage-request-7")
                .setBody("""{"storage_boxes":[],"meta":{"pagination":{"next_page":null}}}"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(422).setHeader("X-Request-ID", "storage-error-8").setBody(
                """{"error":{"code":"invalid_input","message":"Bearer $token root_password=$rootPassword request={\"name\":\"secret\"}"}}""",
            ),
        )
        val diagnostics = mutableListOf<HCloudApiDiagnostic>()
        val api = StorageBoxApiFactory.create(
            tokenProvider = { token },
            baseUrl = server.url("/v1/").toString(),
            diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
        )
        val repository = RetrofitStorageBoxRepository(api)

        repository.getStorageBoxes()
        val failure = runCatching { repository.getStorageBoxes() }.exceptionOrNull()

        assertTrue(failure is HCloudApiException)
        val typedFailure = (failure as HCloudApiException).failure
        assertEquals(422, typedFailure.httpStatus)
        assertEquals("invalid_input", typedFailure.code)
        assertEquals("storage-error-8", typedFailure.requestId)
        assertFalse(typedFailure.apiMessage!!.contains(token))
        assertFalse(typedFailure.apiMessage!!.contains(rootPassword))
        assertEquals(2, diagnostics.size)
        val success = diagnostics[0]
        assertEquals("GET", success.method)
        assertEquals("/v1/storage_boxes", success.redactedPath)
        assertEquals(200, success.httpStatus)
        assertEquals("storage-request-7", success.requestId)
        val error = diagnostics[1]
        assertEquals(422, error.httpStatus)
        assertEquals("invalid_input", error.apiCode)
        assertEquals("Hetzner API error code: invalid_input", error.apiMessage)
        assertEquals("storage-error-8", error.requestId)
        assertFalse(error.toString().contains(token))
        assertFalse(error.toString().contains(rootPassword))
        assertFalse(error.toString().contains("secret"))
    }

    @Test
    fun `unsupported endpoint response is resumed to caller and mapped at repository boundary`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("X-Request-ID", "route-not-found")
                .setBody("""{"error":{"code":"not_found","message":"route is unavailable"}}"""),
        )
        val diagnostics = mutableListOf<HCloudApiDiagnostic>()
        val api = HCloudApiFactory.create(
            tokenProvider = { "test-token" },
            configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1/").toString()) },
            diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
        )

        val failure = runCatching { safeHCloudApiCall { api.getServers() } }.exceptionOrNull()

        assertTrue(failure is HCloudApiException)
        assertEquals(404, (failure as HCloudApiException).failure.httpStatus)
        assertEquals("not_found", failure.failure.code)
        assertEquals("route-not-found", failure.failure.requestId)
        assertEquals(1, diagnostics.size)
        assertEquals(404, diagnostics.single().httpStatus)
    }

    private fun client(diagnostics: MutableList<HCloudApiDiagnostic>): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HCloudApiDiagnosticsInterceptor(HCloudApiDiagnosticsLogger(diagnostics::add)))
        .build()
}
