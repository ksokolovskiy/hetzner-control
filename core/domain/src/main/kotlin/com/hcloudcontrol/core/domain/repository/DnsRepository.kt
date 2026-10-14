package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.DnsRrSet
import com.hcloudcontrol.core.domain.model.DnsZone

/** Separate, read-only boundary for the Cloud DNS API. */
interface DnsRepository {
    suspend fun getZones(): List<DnsZone>
    suspend fun getZone(zoneIdOrName: String): DnsZone
    suspend fun getRrSets(zoneIdOrName: String): List<DnsRrSet>
    suspend fun getRrSet(zoneIdOrName: String, rrName: String, rrType: String): DnsRrSet
}
