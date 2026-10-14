package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/** Non-secret metadata for an independent Storage Box API connection. */
data class StorageBoxConnection(val id: String, val displayName: String)

/**
 * A credential boundary separate from [HCloudTokenStore]. Storage Box credentials must never
 * be read from, or written to, the Cloud profile store.
 */
interface StorageBoxTokenStore {
    fun token(): String?
    fun connections(): List<StorageBoxConnection>
    fun activeConnectionId(): String?
    fun selectConnection(connectionId: String)
    fun saveConnection(displayName: String, token: String): StorageBoxConnection
    fun removeConnection(connectionId: String)
}

/** Token storage encrypted at rest with a separate Android Keystore-backed preference file. */
class EncryptedStorageBoxTokenStore(context: Context) : StorageBoxTokenStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun token(): String? = activeConnectionId()?.let(::tokenFor)

    override fun connections(): List<StorageBoxConnection> = preferences.getStringSet(CONNECTION_IDS_KEY, emptySet())
        .orEmpty()
        .mapNotNull { id -> preferences.getString(nameKey(id), null)?.let { StorageBoxConnection(id, it) } }
        .sortedBy { it.displayName.lowercase() }

    override fun activeConnectionId(): String? = preferences.getString(ACTIVE_CONNECTION_KEY, null)

    override fun selectConnection(connectionId: String) {
        require(connections().any { it.id == connectionId }) { "Unknown Storage Box connection" }
        preferences.edit().putString(ACTIVE_CONNECTION_KEY, connectionId).apply()
    }

    override fun saveConnection(displayName: String, token: String): StorageBoxConnection {
        require(displayName.isNotBlank()) { "Storage Box connection name must not be blank" }
        require(token.isNotBlank()) { "Storage Box API token must not be blank" }
        val connection = StorageBoxConnection(UUID.randomUUID().toString(), displayName.trim())
        val ids = preferences.getStringSet(CONNECTION_IDS_KEY, emptySet()).orEmpty().toMutableSet().apply { add(connection.id) }
        preferences.edit()
            .putStringSet(CONNECTION_IDS_KEY, ids)
            .putString(nameKey(connection.id), connection.displayName)
            .putString(tokenKey(connection.id), token.trim())
            .putString(ACTIVE_CONNECTION_KEY, connection.id)
            .apply()
        return connection
    }

    override fun removeConnection(connectionId: String) {
        val remaining = connections().filterNot { it.id == connectionId }
        val nextActive = activeConnectionId()?.takeIf { it != connectionId } ?: remaining.firstOrNull()?.id
        preferences.edit()
            .putStringSet(CONNECTION_IDS_KEY, remaining.mapTo(mutableSetOf()) { it.id })
            .remove(nameKey(connectionId))
            .remove(tokenKey(connectionId))
            .apply {
                if (nextActive == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, nextActive)
            }
            .apply()
    }

    private fun tokenFor(connectionId: String): String? = preferences.getString(tokenKey(connectionId), null)

    private companion object {
        const val FILE_NAME = "storage_box_secure_credentials"
        const val CONNECTION_IDS_KEY = "storage_box_connection_ids"
        const val ACTIVE_CONNECTION_KEY = "active_storage_box_connection"
        fun nameKey(connectionId: String) = "storage_box_name_$connectionId"
        fun tokenKey(connectionId: String) = "storage_box_api_token_$connectionId"
    }
}
