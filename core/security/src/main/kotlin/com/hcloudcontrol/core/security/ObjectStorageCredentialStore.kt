package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hcloudcontrol.core.domain.model.ObjectStorageCredentials
import com.hcloudcontrol.core.domain.model.ObjectStorageLocation
import java.util.UUID

/** Non-secret Object Storage profile metadata. Access and secret keys are deliberately absent. */
data class ObjectStorageConnection(
    val id: String,
    val displayName: String,
    val location: ObjectStorageLocation,
)

/**
 * Dedicated encrypted boundary for S3 credentials. This must never be folded into either
 * Cloud-project Bearer credentials or Storage Box credentials.
 */
interface ObjectStorageCredentialStore {
    fun connections(): List<ObjectStorageConnection>
    fun activeConnectionId(): String?
    fun activeCredentials(): ObjectStorageCredentials?
    fun selectConnection(connectionId: String)
    fun saveConnection(
        displayName: String,
        location: ObjectStorageLocation,
        accessKey: String,
        secretKey: String,
    ): ObjectStorageConnection
    fun removeConnection(connectionId: String)
}

class EncryptedObjectStorageCredentialStore(context: Context) : ObjectStorageCredentialStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun connections(): List<ObjectStorageConnection> = publicSnapshot().connections

    private fun readConnections(): List<ObjectStorageConnection> =
        preferences.getStringSet(CONNECTION_IDS_KEY, emptySet()).orEmpty().mapNotNull { id ->
            val name = preferences.getString(nameKey(id), null) ?: return@mapNotNull null
            val location = preferences.getString(locationKey(id), null)?.let { raw ->
                runCatching { ObjectStorageLocation.fromId(raw) }.getOrNull()
            } ?: return@mapNotNull null
            ObjectStorageConnection(id, name, location)
        }.sortedBy { it.displayName.lowercase() }

    override fun activeConnectionId(): String? = publicSnapshot().activeId

    override fun activeCredentials(): ObjectStorageCredentials? = profileGuard.credential { activeConnectionId()?.let { id ->
        val connection = connections().firstOrNull { it.id == id } ?: return@let null
        val accessKey = preferences.getString(accessKey(id), null) ?: return@let null
        val secretKey = preferences.getString(secretKey(id), null) ?: return@let null
        ObjectStorageCredentials(accessKey, secretKey, connection.location)
    } }

    override fun selectConnection(connectionId: String) {
        profileGuard.checked(publicSnapshot()) {
            require(readConnections().any { it.id == connectionId }) { "Unknown Object Storage connection" }
            checkedPreferenceUpdate(preferences, setOf(ACTIVE_CONNECTION_KEY)) { putString(ACTIVE_CONNECTION_KEY, connectionId) }
        }
    }

    override fun saveConnection(
        displayName: String,
        location: ObjectStorageLocation,
        accessKey: String,
        secretKey: String,
    ): ObjectStorageConnection {
        require(displayName.isNotBlank()) { "Object Storage connection name must not be blank" }
        require(accessKey.isNotBlank()) { "S3 access key must not be blank" }
        require(secretKey.isNotBlank()) { "S3 secret key must not be blank" }
        val connection = ObjectStorageConnection(UUID.randomUUID().toString(), displayName.trim(), location)
        val ids = preferences.getStringSet(CONNECTION_IDS_KEY, emptySet()).orEmpty().toMutableSet().apply { add(connection.id) }
        profileGuard.checked(publicSnapshot()) {
        checkedPreferenceUpdate(preferences, setOf(CONNECTION_IDS_KEY, nameKey(connection.id), locationKey(connection.id), accessKey(connection.id), secretKey(connection.id), ACTIVE_CONNECTION_KEY)) {
            putStringSet(CONNECTION_IDS_KEY, ids)
            .putString(nameKey(connection.id), connection.displayName)
            .putString(locationKey(connection.id), location.id)
            .putString(accessKey(connection.id), accessKey.trim())
            .putString(secretKey(connection.id), secretKey.trim())
            .putString(ACTIVE_CONNECTION_KEY, connection.id)
        }
        }
        return connection
    }

    override fun removeConnection(connectionId: String) {
        profileGuard.checked(publicSnapshot()) {
        val remaining = readConnections().filterNot { it.id == connectionId }
        val next = activeConnectionId()?.takeIf { current -> remaining.any { it.id == current } } ?: remaining.firstOrNull()?.id
        checkedPreferenceUpdate(preferences, setOf(CONNECTION_IDS_KEY, nameKey(connectionId), locationKey(connectionId), accessKey(connectionId), secretKey(connectionId), ACTIVE_CONNECTION_KEY)) {
            putStringSet(CONNECTION_IDS_KEY, remaining.mapTo(mutableSetOf()) { it.id })
            .remove(nameKey(connectionId)).remove(locationKey(connectionId))
            .remove(accessKey(connectionId)).remove(secretKey(connectionId))
            .apply { if (next == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, next) }
        }
        }
    }

    private fun publicSnapshot() = profileGuard.publicView { PublicProfileSnapshot(readConnections(), preferences.getString(ACTIVE_CONNECTION_KEY, null)) }
    private data class PublicProfileSnapshot(val connections: List<ObjectStorageConnection>, val activeId: String?)

    private companion object {
        val profileGuard = ProfilePersistenceGuard<PublicProfileSnapshot>()
        const val FILE_NAME = "object_storage_secure_credentials"
        const val CONNECTION_IDS_KEY = "object_storage_connection_ids"
        const val ACTIVE_CONNECTION_KEY = "active_object_storage_connection"
        fun nameKey(id: String) = "object_storage_name_$id"
        fun locationKey(id: String) = "object_storage_location_$id"
        fun accessKey(id: String) = "object_storage_s3_access_key_$id"
        fun secretKey(id: String) = "object_storage_s3_secret_key_$id"
    }
}
