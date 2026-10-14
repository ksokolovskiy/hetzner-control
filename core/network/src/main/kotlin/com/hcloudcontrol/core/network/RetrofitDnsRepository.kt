package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.DnsRecord
import com.hcloudcontrol.core.domain.model.DnsRrSet
import com.hcloudcontrol.core.domain.model.DnsScope
import com.hcloudcontrol.core.domain.model.DnsZone
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.repository.DnsRepository

internal class RetrofitDnsRepository(private val api: DnsApi) : DnsRepository {
    override suspend fun getZones(): List<DnsZone> = allZonePages().map { it.toDomain() }

    override suspend fun getZone(zoneIdOrName: String): DnsZone {
        require(zoneIdOrName.isNotBlank()) { "Zone ID or name must not be blank" }
        return api.getZone(zoneIdOrName).zone.toDomain()
    }

    override suspend fun getRrSets(zoneIdOrName: String): List<DnsRrSet> {
        require(zoneIdOrName.isNotBlank()) { "Zone ID or name must not be blank" }
        return allRrSetPages(zoneIdOrName).map { it.toDomain() }
    }

    override suspend fun getRrSet(zoneIdOrName: String, rrName: String, rrType: String): DnsRrSet {
        require(zoneIdOrName.isNotBlank()) { "Zone ID or name must not be blank" }
        require(rrName.isNotBlank()) { "RRSet name must not be blank" }
        require(rrType.isNotBlank()) { "RRSet type must not be blank" }
        return api.getRrSet(zoneIdOrName, rrName, rrType).rrset.toDomain()
    }

    private suspend fun allZonePages(): List<DnsZoneDto> {
        val result = mutableListOf<DnsZoneDto>()
        var page = 1
        do {
            val response = api.getZones(page)
            result += response.zones
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }

    private suspend fun allRrSetPages(zoneIdOrName: String): List<DnsRrSetDto> {
        val result = mutableListOf<DnsRrSetDto>()
        var page = 1
        do {
            val response = api.getRrSets(zoneIdOrName, page)
            result += response.rrsets
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        return result
    }
}

/** Public construction requires an explicit read-only scope acknowledgment before token/base input. */
object DnsRepositoryFactory {
    fun create(
        scope: DnsScope,
        hCloudTokenProvider: () -> String?,
        hCloudConfigProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
    ): DnsRepository = RetrofitDnsRepository(
        DnsApiFactory.create(scope, hCloudTokenProvider, hCloudConfigProvider),
    )
}

private fun DnsZoneDto.toDomain() = DnsZone(id, name, mode, ttl, labels)
private fun DnsRrSetDto.toDomain() = DnsRrSet(
    name = name,
    type = type,
    ttl = ttl,
    records = records.map { DnsRecord(it.value, it.comment) },
    labels = labels,
)
