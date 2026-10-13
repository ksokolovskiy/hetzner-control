package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Token storage encrypted at rest. The encryption key is held by Android Keystore.
 * No token values are ever logged by this class.
 */
class EncryptedHCloudTokenStore(context: Context) : HCloudTokenStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun token(): String? = activeConnectionId()?.let(::tokenFor)

    override fun connections(): List<CloudProjectConnection> = runCatching {
        val stored = JSONArray(preferences.getString(CONNECTIONS_KEY, "[]"))
        List(stored.length()) { index ->
            stored.getJSONObject(index).let { CloudProjectConnection(it.getString("id"), it.getString("name")) }
        }
    }.getOrDefault(emptyList())

    override fun activeConnectionId(): String? = preferences.getString(ACTIVE_CONNECTION_KEY, null)

    override fun selectConnection(connectionId: String) {
        require(connections().any { it.id == connectionId }) { "Unknown Cloud project connection" }
        preferences.edit().putString(ACTIVE_CONNECTION_KEY, connectionId).apply()
    }

    override fun saveConnection(displayName: String, token: String): CloudProjectConnection {
        require(displayName.isNotBlank()) { "Project name must not be blank" }
        require(token.isNotBlank()) { "API token must not be blank" }
        val connection = CloudProjectConnection(UUID.randomUUID().toString(), displayName.trim())
        val allConnections = connections() + connection
        preferences.edit()
            .putString(tokenKey(connection.id), token.trim())
            .putString(CONNECTIONS_KEY, JSONArray().apply {
                allConnections.forEach { put(JSONObject().put("id", it.id).put("name", it.displayName)) }
            }.toString())
            .putString(ACTIVE_CONNECTION_KEY, connection.id)
            .apply()
        return connection
    }

    override fun removeConnection(connectionId: String) {
        val remaining = connections().filterNot { it.id == connectionId }
        val nextActive = activeConnectionId()?.takeIf { it != connectionId } ?: remaining.firstOrNull()?.id
        preferences.edit()
            .remove(tokenKey(connectionId))
            .putString(CONNECTIONS_KEY, JSONArray().apply {
                remaining.forEach { put(JSONObject().put("id", it.id).put("name", it.displayName)) }
            }.toString())
            .apply {
                if (nextActive == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, nextActive)
            }
            .apply()
    }

    private fun tokenFor(connectionId: String): String? = preferences.getString(tokenKey(connectionId), null)

    private companion object {
        const val FILE_NAME = "hcloud_secure_credentials"
        const val CONNECTIONS_KEY = "cloud_project_connections"
        const val ACTIVE_CONNECTION_KEY = "active_cloud_project_connection"
        fun tokenKey(connectionId: String) = "api_token_$connectionId"
    }
}
