package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
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
        val available = List(stored.length()) { index ->
            stored.getJSONObject(index).let { profile ->
                StoredProfile(profile.getString("id"), profile.getString("name"), profile.optString("kind"))
            }
        }.mapNotNull { profile ->
            profile.toConnectionOrNull(tokenFor(profile.id))
        }

        // Older releases could write a local sandbox profile without a credential.
        // It must never be reinterpreted as a real profile: doing so would dispatch a
        // request without an Authorization header during app start. Persist only usable,
        // explicitly supported connections and point the active id at one of them.
        val activeId = preferences.getString(ACTIVE_CONNECTION_KEY, null)
        if (available.size != stored.length() || activeId !in available.map { it.id }) {
            preferences.edit()
                .putString(CONNECTIONS_KEY, connectionsJson(available))
                .apply {
                    val replacement = available.firstOrNull()?.id
                    if (replacement == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, replacement)
                }
                .apply()
        }
        available
    }.getOrDefault(emptyList())

    override fun activeConnectionId(): String? = preferences.getString(ACTIVE_CONNECTION_KEY, null)

    override fun selectConnection(connectionId: String) {
        require(connections().any { it.id == connectionId }) { "Unknown Cloud project connection" }
        preferences.edit().putString(ACTIVE_CONNECTION_KEY, connectionId).apply()
    }

    override fun saveConnection(
        displayName: String,
        token: String,
        config: HCloudConnectionConfig,
    ): CloudProjectConnection {
        require(displayName.isNotBlank()) { "Project name must not be blank" }
        require(token.isNotBlank()) { "API token must not be blank" }
        val connection = CloudProjectConnection(UUID.randomUUID().toString(), displayName.trim(), config)
        val allConnections = connections() + connection
        preferences.edit()
            .putString(tokenKey(connection.id), token.trim())
            .putString(CONNECTIONS_KEY, connectionsJson(allConnections))
            .putString(ACTIVE_CONNECTION_KEY, connection.id)
            .apply()
        return connection
    }

    override fun removeConnection(connectionId: String) {
        val remaining = connections().filterNot { it.id == connectionId }
        val nextActive = activeConnectionId()?.takeIf { it != connectionId } ?: remaining.firstOrNull()?.id
        preferences.edit()
            .remove(tokenKey(connectionId))
            .putString(CONNECTIONS_KEY, connectionsJson(remaining))
            .apply {
                if (nextActive == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, nextActive)
            }
            .apply()
    }

    private fun tokenFor(connectionId: String): String? = preferences.getString(tokenKey(connectionId), null)

    private fun connectionsJson(connections: List<CloudProjectConnection>): String = JSONArray().apply {
        connections.forEach {
            put(JSONObject()
                .put("id", it.id)
                .put("name", it.displayName)
                .put("kind", it.config.kind.name)
                .put("base_url", it.config.baseUrl))
        }
    }.toString()

    private companion object {
        const val FILE_NAME = "hcloud_secure_credentials"
        const val CONNECTIONS_KEY = "cloud_project_connections"
        const val ACTIVE_CONNECTION_KEY = "active_cloud_project_connection"
        fun tokenKey(connectionId: String) = "api_token_$connectionId"
    }
}

/** Non-secret legacy profile metadata. Kept pure so migration behaviour is unit-testable. */
internal data class StoredProfile(val id: String, val displayName: String, val kind: String)

internal fun StoredProfile.toConnectionOrNull(token: String?): CloudProjectConnection? {
    // A blank kind predates routing and denotes a real profile. Unsupported kinds are
    // retired sandbox/demo records and are deliberately not normalized to REAL.
    val config = when (kind) {
        "", ConnectionKind.REAL.name -> HCloudConnectionConfig.real()
        ConnectionKind.TEST_MOCK.name -> HCloudConnectionConfig.testMock()
        else -> return null
    }
    return token?.takeIf(String::isNotBlank)?.let { CloudProjectConnection(id, displayName, config) }
}
