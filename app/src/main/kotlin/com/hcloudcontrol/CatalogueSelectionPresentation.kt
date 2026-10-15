package com.hcloudcontrol

import com.hcloudcontrol.ui.designsystem.SectionKind

internal fun catalogueMatches(query: String, vararg fields: String): Boolean =
    query.trim().split(Regex("\\s+")).filter(String::isNotBlank).all { term ->
        fields.any { it.contains(term, ignoreCase = true) }
    }

/** Initial/error and true empty inventory belong to the shared read owner, not the filter. */
internal fun cataloguePickerEmptyKind(hasResponse: Boolean, inventorySize: Int, eligibleSize: Int, visibleSize: Int): SectionKind? =
    when {
        !hasResponse || inventorySize == 0 -> null
        eligibleSize == 0 -> SectionKind.NO_ELIGIBLE
        visibleSize == 0 -> SectionKind.NO_RESULTS
        else -> null
    }
