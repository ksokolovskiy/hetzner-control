package com.hcloudcontrol.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    override fun token(): String? = preferences.getString(TOKEN_KEY, null)

    override fun save(token: String) {
        require(token.isNotBlank()) { "API token must not be blank" }
        preferences.edit().putString(TOKEN_KEY, token.trim()).apply()
    }

    override fun clear() {
        preferences.edit().remove(TOKEN_KEY).apply()
    }

    private companion object {
        const val FILE_NAME = "hcloud_secure_credentials"
        const val TOKEN_KEY = "api_token"
    }
}
