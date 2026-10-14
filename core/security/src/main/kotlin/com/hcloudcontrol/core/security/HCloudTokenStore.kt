package com.hcloudcontrol.core.security

import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig

/** Metadata for one locally configured Hetzner Cloud project connection. */
data class CloudProjectConnection(
    val id: String,
    val displayName: String,
    val config: HCloudConnectionConfig = HCloudConnectionConfig.real(),
)

/** Keeps the credential boundary explicit. Callers should never persist tokens themselves. */
interface HCloudTokenStore {
    /** The token for the selected project. It is never exposed to UI state. */
    fun token(): String?
    /** Returns only non-secret metadata for the selected connection. */
    fun activeConnection(): CloudProjectConnection? = activeConnectionId()?.let(::connection)
    fun connection(connectionId: String): CloudProjectConnection? = connections().firstOrNull { it.id == connectionId }
    fun connections(): List<CloudProjectConnection>
    fun activeConnectionId(): String?
    fun selectConnection(connectionId: String)
    fun saveConnection(
        displayName: String,
        token: String,
        config: HCloudConnectionConfig = HCloudConnectionConfig.real(),
    ): CloudProjectConnection
    fun removeConnection(connectionId: String)
}
