package com.hcloudcontrol.core.security

import android.content.Context

/**
 * Explicit, device-local consent for actions that can incur cost, change service
 * availability, or permanently remove Cloud data. It is deliberately independent
 * from a project's API token: switching a project never silently grants consent.
 *
 * This is deliberately a non-secret preference: it contains neither credentials
 * nor resource data. Tokens remain in encrypted storage; no token or action details
 * are stored here.
 */
class HighImpactOperationsCapability private constructor(
    val isAllowed: Boolean,
) {
    companion object {
        val Disabled = HighImpactOperationsCapability(isAllowed = false)
        val Allowed = HighImpactOperationsCapability(isAllowed = true)
    }
}

interface HighImpactOperationsStore {
    fun capability(): HighImpactOperationsCapability

    fun setAllowed(allowed: Boolean)
}

class PersistentHighImpactOperationsStore(context: Context) : HighImpactOperationsStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun capability(): HighImpactOperationsCapability = if (
        preferences.getBoolean(ALLOWED_KEY, false)
    ) {
        HighImpactOperationsCapability.Allowed
    } else {
        HighImpactOperationsCapability.Disabled
    }

    override fun setAllowed(allowed: Boolean) {
        preferences.edit().putBoolean(ALLOWED_KEY, allowed).apply()
    }

    private companion object {
        const val FILE_NAME = "hcloud_high_impact_operations"
        const val ALLOWED_KEY = "high_impact_operations_allowed"
    }
}
