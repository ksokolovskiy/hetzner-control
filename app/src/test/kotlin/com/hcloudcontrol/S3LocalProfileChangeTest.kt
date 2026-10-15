package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.ObjectStorageCredentials
import com.hcloudcontrol.core.domain.model.ObjectStorageLocation
import com.hcloudcontrol.core.security.ObjectStorageConnection
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import org.junit.Assert.*
import org.junit.Test

class S3LocalProfileChangeTest {
    private class Store : ObjectStorageCredentialStore {
        var profiles = listOf(ObjectStorageConnection("original", "Fixture", ObjectStorageLocation.FALKENSTEIN),
            ObjectStorageConnection("other", "Other fixture", ObjectStorageLocation.HELSINKI))
        var active = "original"
        var fail = false
        var calls = 0
        override fun connections() = profiles
        override fun activeConnectionId(): String? = active.takeIf { it.isNotEmpty() }
        override fun activeCredentials(): ObjectStorageCredentials? = error("Credentials must not be read for a local metadata change")
        override fun selectConnection(connectionId: String) {
            calls++
            if (fail) error("Synthetic persistence failure")
            active = connectionId
        }
        override fun removeConnection(connectionId: String) {
            calls++
            if (fail) error("Synthetic persistence failure")
            profiles = profiles.filterNot { it.id == connectionId }
            if (active == connectionId) active = profiles.firstOrNull()?.id.orEmpty()
        }
        override fun saveConnection(displayName: String, location: ObjectStorageLocation, accessKey: String, secretKey: String): ObjectStorageConnection =
            error("No new profile, credentials or provider operation may be created")
    }
    @Test fun `failed selection returns only sanitized failure no new selected UI snapshot`() {
        val store = Store().apply { fail = true }
        assertEquals(S3ProfileChangeResult.Failed, changeS3Profile(store, S3LocalProfileChange.Select("other")))
        assertEquals("original", store.activeConnectionId())
        assertEquals(1, store.calls)
    }
    @Test fun `failed removal does not claim disappearance or change original connection identity`() {
        val store = Store().apply { fail = true }
        assertEquals(S3ProfileChangeResult.Failed, changeS3Profile(store, S3LocalProfileChange.Remove("original")))
        assertEquals(listOf("original", "other"), store.connections().map { it.id })
        assertEquals("original", store.activeConnectionId())
    }
    @Test fun `only successful checked local operations expose refreshed metadata`() {
        val store = Store()
        val selected = changeS3Profile(store, S3LocalProfileChange.Select("other")) as S3ProfileChangeResult.Applied
        assertEquals("other", selected.snapshot.activeId)
        val removed = changeS3Profile(store, S3LocalProfileChange.Remove("other")) as S3ProfileChangeResult.Applied
        assertEquals("original", removed.snapshot.activeId)
        assertEquals(listOf("original"), removed.snapshot.connections.map { it.id })
        assertEquals(2, store.calls)
    }
}
