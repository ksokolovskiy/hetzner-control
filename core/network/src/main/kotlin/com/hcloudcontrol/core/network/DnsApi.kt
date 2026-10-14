package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.DnsScope
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Dedicated read-only surface; it intentionally contains no POST/PUT/DELETE endpoints. */
internal interface DnsApi {
    @GET("zones")
    suspend fun getZones(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): DnsZonesResponse

    @GET("zones/{id_or_name}")
    suspend fun getZone(@Path("id_or_name") zoneIdOrName: String): DnsZoneResponse

    @GET("zones/{id_or_name}/rrsets")
    suspend fun getRrSets(
        @Path("id_or_name") zoneIdOrName: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): DnsRrSetsResponse

    @GET("zones/{id_or_name}/rrsets/{rr_name}/{rr_type}")
    suspend fun getRrSet(
        @Path("id_or_name") zoneIdOrName: String,
        @Path("rr_name") rrName: String,
        @Path("rr_type") rrType: String,
    ): DnsRrSetResponse
}

internal data class DnsZonesResponse(val zones: List<DnsZoneDto>, val meta: MetaDto? = null)
internal data class DnsZoneResponse(val zone: DnsZoneDto)
internal data class DnsZoneDto(
    val id: Long,
    val name: String,
    val mode: String? = null,
    val ttl: Int? = null,
    val labels: Map<String, String> = emptyMap(),
)
internal data class DnsRrSetsResponse(val rrsets: List<DnsRrSetDto>, val meta: MetaDto? = null)
internal data class DnsRrSetResponse(val rrset: DnsRrSetDto)
internal data class DnsRrSetDto(
    val name: String,
    val type: String,
    val ttl: Int? = null,
    val records: List<DnsRecordDto> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)
internal data class DnsRecordDto(val value: String, val comment: String? = null)

/**
 * Uses the Cloud API host and the selected project's encrypted token provider only after a
 * caller deliberately acknowledges [DnsScope]. It never accepts a legacy DNS Console token.
 */
internal object DnsApiFactory {
    fun create(
        scope: DnsScope,
        hCloudTokenProvider: () -> String?,
        hCloudConfigProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
        diagnosticsLogger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    ): DnsApi {
        check(scope.readOnlyAcknowledged) { "Cloud DNS read-only scope must be acknowledged" }
        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(hCloudConfigProvider))
            .addInterceptor(HCloudApiDiagnosticsInterceptor(diagnosticsLogger))
            .addInterceptor(BearerTokenInterceptor(hCloudTokenProvider))
            .build()
        return Retrofit.Builder()
            .baseUrl(HCloudApiFactory.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
            .build()
            .create(DnsApi::class.java)
    }
}
