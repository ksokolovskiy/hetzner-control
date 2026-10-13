package com.hcloudcontrol.core.security

/** Keeps the credential boundary explicit. Callers should never persist tokens themselves. */
interface HCloudTokenStore {
    fun token(): String?
    fun save(token: String)
    fun clear()
}
