package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageActionsContractTest {
    @Test fun `image history uses documented scoped endpoint`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":71,"command":"change_protection","status":"success","progress":100}],"meta":{"pagination":{"next_page":null}}}"""))
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create({ "token" }, { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) }, HCloudApiDiagnosticsLogger { }))
            assertEquals(listOf(71L), repository.getImageActions(15).map { it.id })
            assertEquals("/v1/images/15/actions?page=1&per_page=50", requireNotNull(server.takeRequest()).path)
        }
    }
}
