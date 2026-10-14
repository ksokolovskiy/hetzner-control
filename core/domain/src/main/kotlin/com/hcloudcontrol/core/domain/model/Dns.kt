package com.hcloudcontrol.core.domain.model

/**
 * Explicit capability required before the Cloud DNS client can be constructed.
 * It cannot be instantiated outside this file and grants read-only access only.
 */
class DnsScope private constructor() {
    val readOnlyAcknowledged: Boolean get() = true

    companion object {
        fun acknowledgeReadOnly(): DnsScope = DnsScope()
    }
}

/** Conservative Cloud DNS zone metadata; no zonefile/action/write fields are exposed. */
data class DnsZone(
    val id: Long,
    val name: String,
    val mode: String?,
    val ttl: Int?,
    val labels: Map<String, String>,
)

/** A read-only resource-record set. Values are raw DNS presentation values from Hetzner. */
data class DnsRrSet(
    val name: String,
    val type: String,
    val ttl: Int?,
    val records: List<DnsRecord>,
    val labels: Map<String, String>,
)

data class DnsRecord(val value: String, val comment: String?)
