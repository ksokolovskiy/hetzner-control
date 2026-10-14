package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.CreateStorageBoxSnapshotSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StorageBoxApiContractTest {
    @Test
    fun `list uses separate bearer credential and follows api pagination`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{
                    "storage_boxes":[{"id":7,"name":"archive","status":"active","stats":{"size":1000,"size_data":200,"size_snapshots":30}}],
                    "meta":{"pagination":{"next_page":2}}
                    }""".trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"storage_boxes":[{"id":8,"name":"cold","stats":{"size":2000,"size_data":0,"size_snapshots":0}}],"meta":{"pagination":{"next_page":null}}}""",
                ),
            )
            val repository = RetrofitStorageBoxRepository(
                StorageBoxApiFactory.create(
                    tokenProvider = { "storage-box-token" },
                    baseUrl = server.url("/v1/").toString(),
                ),
            )

            val boxes = repository.getStorageBoxes()

            assertEquals(listOf(7L, 8L), boxes.map { it.id })
            assertEquals(200L, boxes.first().stats?.sizeData)
            assertEquals(30L, boxes.first().stats?.sizeSnapshots)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            assertEquals("/v1/storage_boxes?page=1&per_page=50", first.path)
            assertEquals("/v1/storage_boxes?page=2&per_page=50", second.path)
            assertEquals("Bearer storage-box-token", first.getHeader("Authorization"))
            assertFalse(first.headers.names().any { it.equals("X-HCloud-Token", ignoreCase = true) })
            assertFalse(first.toString().contains("cloud-token"))
        }
    }

    @Test
    fun `detail and folders use documented read-only paths and relative path query`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"storage_box":{"id":7,"name":"archive","status":"active","stats":{"size":1000,"size_data":200,"size_snapshots":30}}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"folders":["daily","daily/2026-10-13"]}"""))
            val repository = RetrofitStorageBoxRepository(
                StorageBoxApiFactory.create(
                    tokenProvider = { "storage-box-token" },
                    baseUrl = server.url("/v1/").toString(),
                ),
            )

            val detail = repository.getStorageBox(7)
            val folders = repository.getStorageBoxFolders(7, path = "daily")

            assertEquals("archive", detail.name)
            assertEquals(listOf("daily", "daily/2026-10-13"), folders)
            val detailRequest = requireNotNull(server.takeRequest())
            val foldersRequest = requireNotNull(server.takeRequest())
            assertEquals("/v1/storage_boxes/7", detailRequest.path)
            assertEquals("/v1/storage_boxes/7/folders?path=daily", foldersRequest.path)
            assertEquals("Bearer storage-box-token", detailRequest.getHeader("Authorization"))
            assertEquals("Bearer storage-box-token", foldersRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun `snapshots paginate and detail stays on separate Storage Box credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{
                    "snapshots":[{"id":101,"name":"before-upgrade","description":"safe point","is_automatic":false,"created":"2026-10-13T08:00:00+00:00","stats":{"size":300,"size_filesystem":200}}],
                    "meta":{"pagination":{"next_page":2}}
                    }""".trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"snapshots":[{"id":102,"name":"nightly","is_automatic":true,"stats":{"size":310,"size_filesystem":205}}],"meta":{"pagination":{"next_page":null}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"snapshot":{"id":101,"name":"before-upgrade","description":"safe point","is_automatic":false,"created":"2026-10-13T08:00:00+00:00","stats":{"size":300,"size_filesystem":200}}}""",
                ),
            )
            val repository = RetrofitStorageBoxRepository(
                StorageBoxApiFactory.create(
                    tokenProvider = { "storage-box-token" },
                    baseUrl = server.url("/v1/").toString(),
                ),
            )

            val snapshots = repository.getStorageBoxSnapshots(7)
            val detail = repository.getStorageBoxSnapshot(7, 101)

            assertEquals(listOf(101L, 102L), snapshots.map { it.id })
            assertEquals(200L, snapshots.first().stats?.sizeFilesystem)
            assertEquals("safe point", detail.description)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            val third = requireNotNull(server.takeRequest())
            assertEquals("/v1/storage_boxes/7/snapshots?page=1&per_page=50", first.path)
            assertEquals("/v1/storage_boxes/7/snapshots?page=2&per_page=50", second.path)
            assertEquals("/v1/storage_boxes/7/snapshots/101", third.path)
            listOf(first, second, third).forEach { request ->
                assertEquals("Bearer storage-box-token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains("cloud-token"))
            }
        }
    }

    @Test
    fun `manual snapshot POST omits absent options and returns snapshot plus pending action`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"snapshot":{"id":103,"name":"manual-103","description":null,"is_automatic":false,"stats":{"size":320,"size_filesystem":210},"labels":{"purpose":"before-upgrade"}},"action":{"id":501,"command":"create_snapshot","status":"running","progress":0}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"snapshot":{"id":104,"name":"manual-104","is_automatic":false,"stats":{"size":321,"size_filesystem":211},"labels":{}},"action":{"id":502,"status":"success","progress":100}}""",
                ),
            )
            val repository = RetrofitStorageBoxRepository(
                StorageBoxApiFactory.create(
                    tokenProvider = { "storage-box-token" },
                    baseUrl = server.url("/v1/").toString(),
                ),
            )

            val labelled = repository.createStorageBoxSnapshot(
                7,
                CreateStorageBoxSnapshotSpec(labels = mapOf("purpose" to "before-upgrade")),
            )
            val defaults = repository.createStorageBoxSnapshot(7)

            assertEquals(103L, labelled.snapshot.id)
            assertEquals("before-upgrade", labelled.snapshot.labels["purpose"])
            assertEquals("running", labelled.action.status)
            assertEquals(0, labelled.action.progress)
            assertEquals(104L, defaults.snapshot.id)
            assertEquals("success", defaults.action.status)
            val labelledRequest = requireNotNull(server.takeRequest())
            val defaultRequest = requireNotNull(server.takeRequest())
            assertEquals("POST", labelledRequest.method)
            assertEquals("/v1/storage_boxes/7/snapshots", labelledRequest.path)
            assertEquals("{\"labels\":{\"purpose\":\"before-upgrade\"}}", labelledRequest.body.readUtf8())
            assertEquals("Bearer storage-box-token", labelledRequest.getHeader("Authorization"))
            assertEquals("POST", defaultRequest.method)
            assertEquals("{}", defaultRequest.body.readUtf8())
            assertEquals("Bearer storage-box-token", defaultRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun `subaccounts paginate and preserve distinct nullable name and username`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"subaccounts":[{"id":201,"name":"nightly backups","username":"u12345-sub1"},{"id":202,"name":null,"username":"u12345-sub2"}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"subaccounts":[{"id":203,"name":"reports","username":null}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"subaccount":{"id":201,"name":"nightly backups","username":"u12345-sub1"}}"""))
            val repository = RetrofitStorageBoxRepository(
                StorageBoxApiFactory.create(
                    tokenProvider = { "storage-box-token" },
                    baseUrl = server.url("/v1/").toString(),
                ),
            )

            val subaccounts = repository.getStorageBoxSubaccounts(7)
            val detail = repository.getStorageBoxSubaccount(7, 201)

            assertEquals(listOf(201L, 202L, 203L), subaccounts.map { it.id })
            assertEquals("nightly backups", subaccounts.first().name)
            assertEquals("u12345-sub1", subaccounts.first().username)
            assertEquals(null, subaccounts[1].name)
            assertEquals(null, subaccounts[2].username)
            assertEquals("u12345-sub1", detail.username)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            val third = requireNotNull(server.takeRequest())
            assertEquals("/v1/storage_boxes/7/subaccounts?page=1&per_page=50", first.path)
            assertEquals("/v1/storage_boxes/7/subaccounts?page=2&per_page=50", second.path)
            assertEquals("/v1/storage_boxes/7/subaccounts/201", third.path)
            listOf(first, second, third).forEach { request ->
                assertEquals("Bearer storage-box-token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains("cloud-token"))
            }
        }
    }
}
