package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.DnsRrSet
import com.hcloudcontrol.core.domain.repository.DnsRepository
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

internal data class DnsRrSetIdentity(val name: String, val type: String)
internal fun dnsRrSelectionSnapshot(scope: String, identity: DnsRrSetIdentity?): ArrayList<String> =
    arrayListOf("dns-rrset-v1", scope, identity?.name.orEmpty(), identity?.type.orEmpty())
internal fun restoreDnsRrSelection(scope: String, snapshot: List<String>): DnsRrSetIdentity? =
    if (snapshot.size == 4 && snapshot[0] == "dns-rrset-v1" && snapshot[1] == scope && snapshot[3].isNotBlank())
        DnsRrSetIdentity(snapshot[2], snapshot[3]) else null

@Composable
internal fun rememberDnsRrSetIdentity(scope: String): MutableState<DnsRrSetIdentity?> {
    val saver = remember(scope) { Saver<MutableState<DnsRrSetIdentity?>, ArrayList<String>>(
        save = { dnsRrSelectionSnapshot(scope, it.value) },
        restore = { mutableStateOf(restoreDnsRrSelection(scope, it)) }) }
    return rememberSaveable(scope, saver = saver) { mutableStateOf(null) }
}

/** Defense at the read boundary in addition to composing no loader before acknowledgement. */
internal suspend fun readAcknowledgedDnsZones(acknowledged: Boolean, repository: DnsRepository) =
    if (acknowledged) repository.getZones() else error("DNS scope not acknowledged")

internal fun dnsRrSetMatches(rrset: DnsRrSet, query: String): Boolean {
    val normalized = query.trim()
    return normalized.isEmpty() || rrset.name.contains(normalized, true) || rrset.type.contains(normalized, true) ||
        rrset.records.any { it.value.contains(normalized, true) || it.comment.orEmpty().contains(normalized, true) }
}
