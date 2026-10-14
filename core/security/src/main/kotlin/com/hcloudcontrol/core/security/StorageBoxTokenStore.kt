package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
/** Non-secret metadata retained only so a user can explicitly erase an old credential. */
data class LegacyStorageBoxCredential(val id: String, val displayName: String)

/**
 * Read-only migration boundary for versions which incorrectly asked users for a second token.
 *
 * Storage Box lifecycle requests at `api.hetzner.com` use the selected Hetzner project API token.
 * Existing encrypted values are deliberately not exposed, copied, or reused: they are retained
 * until the user explicitly removes them from the migration notice.
 */
interface LegacyStorageBoxCredentialStore {
    fun credentials(): List<LegacyStorageBoxCredential>
    fun removeCredential(credentialId: String)
}

/** Reads the legacy encrypted preference file without ever returning its secret values. */
class EncryptedLegacyStorageBoxCredentialStore(context: Context) : LegacyStorageBoxCredentialStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun credentials(): List<LegacyStorageBoxCredential> = preferences.getStringSet(CONNECTION_IDS_KEY, emptySet())
        .orEmpty()
        .mapNotNull { id -> preferences.getString(nameKey(id), null)?.let { LegacyStorageBoxCredential(id, it) } }
        .sortedBy { it.displayName.lowercase() }

    override fun removeCredential(credentialId: String) {
        val remaining = credentials().filterNot { it.id == credentialId }
        val activeConnectionId = preferences.getString(ACTIVE_CONNECTION_KEY, null)
        val nextActive = activeConnectionId?.takeIf { it != credentialId } ?: remaining.firstOrNull()?.id
        preferences.edit()
            .putStringSet(CONNECTION_IDS_KEY, remaining.mapTo(mutableSetOf()) { it.id })
            .remove(nameKey(credentialId))
            .remove(tokenKey(credentialId))
            .apply {
                if (nextActive == null) remove(ACTIVE_CONNECTION_KEY) else putString(ACTIVE_CONNECTION_KEY, nextActive)
            }
            .apply()
    }

    private companion object {
        const val FILE_NAME = "storage_box_secure_credentials"
        const val CONNECTION_IDS_KEY = "storage_box_connection_ids"
        const val ACTIVE_CONNECTION_KEY = "active_storage_box_connection"
        fun nameKey(connectionId: String) = "storage_box_name_$connectionId"
        fun tokenKey(connectionId: String) = "storage_box_api_token_$connectionId"
    }
}
