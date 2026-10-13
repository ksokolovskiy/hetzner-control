package com.hcloudcontrol.core.security

/** Metadata for one locally configured Hetzner Cloud project connection. */
data class CloudProjectConnection(
    val id: String,
    val displayName: String,
)

/** Keeps the credential boundary explicit. Callers should never persist tokens themselves. */
interface HCloudTokenStore {
    /** The token for the selected project. It is never exposed to UI state. */
    fun token(): String?
    fun connections(): List<CloudProjectConnection>
    fun activeConnectionId(): String?
    fun selectConnection(connectionId: String)
    fun saveConnection(displayName: String, token: String): CloudProjectConnection
    fun removeConnection(connectionId: String)
}
