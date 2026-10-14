package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.LocationDetails
import com.hcloudcontrol.core.domain.model.LocationOption

/**
 * Presents a Hetzner Location using only metadata returned by the Cloud API.
 * `name` remains the machine identifier needed in requests (for example `nbg1`).
 * No local code-to-city table is maintained, so incomplete API data stays code-only.
 */
internal fun locationDisplayName(
    name: String,
    city: String? = null,
    description: String? = null,
): String {
    val humanName = city?.trim().takeUnless { it.isNullOrEmpty() }
        ?: description?.trim().takeUnless { it.isNullOrEmpty() }
    return if (humanName == null || humanName.equals(name, ignoreCase = true)) name else "$humanName ($name)"
}

internal fun LocationOption.displayName(): String = locationDisplayName(name, city, description)
internal fun LocationDetails.displayName(): String = locationDisplayName(name, city, description)
