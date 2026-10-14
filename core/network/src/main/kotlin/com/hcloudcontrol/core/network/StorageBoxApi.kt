package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.StorageBox
import com.hcloudcontrol.core.domain.model.StorageBoxSnapshot
import com.hcloudcontrol.core.domain.model.StorageBoxSnapshotStats
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.CreateStorageBoxSnapshotSpec
import com.hcloudcontrol.core.domain.model.CreatedStorageBoxSnapshot
import com.hcloudcontrol.core.domain.model.StorageBoxSubaccount
import com.hcloudcontrol.core.domain.model.StorageBoxStats
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

/** API surface intentionally separate from HCloudApi: it has another host but uses the active project token. */
internal interface StorageBoxApi {
    @GET("storage_boxes")
    suspend fun getStorageBoxes(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): StorageBoxesResponse

    @GET("storage_boxes/{id}")
    suspend fun getStorageBox(@retrofit2.http.Path("id") storageBoxId: Long): StorageBoxResponse

    @GET("storage_boxes/{id}/folders")
    suspend fun getStorageBoxFolders(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @Query("path") path: String? = null,
    ): StorageBoxFoldersResponse

    @GET("storage_boxes/{id}/snapshots")
    suspend fun getStorageBoxSnapshots(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): StorageBoxSnapshotsResponse

    @GET("storage_boxes/{id}/snapshots/{snapshot_id}")
    suspend fun getStorageBoxSnapshot(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @retrofit2.http.Path("snapshot_id") snapshotId: Long,
    ): StorageBoxSnapshotResponse

    @POST("storage_boxes/{id}/snapshots")
    suspend fun createStorageBoxSnapshot(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @Body request: CreateStorageBoxSnapshotRequestDto,
    ): CreateStorageBoxSnapshotResponse

    @GET("storage_boxes/{id}/subaccounts")
    suspend fun getStorageBoxSubaccounts(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
    ): StorageBoxSubaccountsResponse

    @GET("storage_boxes/{id}/subaccounts/{subaccount_id}")
    suspend fun getStorageBoxSubaccount(
        @retrofit2.http.Path("id") storageBoxId: Long,
        @retrofit2.http.Path("subaccount_id") subaccountId: Long,
    ): StorageBoxSubaccountResponse
}

internal data class StorageBoxesResponse(
    @Json(name = "storage_boxes") val storageBoxes: List<StorageBoxDto>,
    val meta: StorageBoxMetaDto? = null,
)

internal data class StorageBoxMetaDto(val pagination: StorageBoxPaginationDto? = null)
internal data class StorageBoxPaginationDto(@Json(name = "next_page") val nextPage: Int? = null)
internal data class StorageBoxResponse(@Json(name = "storage_box") val storageBox: StorageBoxDto)
/** The official folders operation returns relative path strings, not file-content metadata. */
internal data class StorageBoxFoldersResponse(val folders: List<String>)
internal data class StorageBoxSnapshotsResponse(
    val snapshots: List<StorageBoxSnapshotDto>,
    val meta: StorageBoxMetaDto? = null,
)
internal data class StorageBoxSnapshotResponse(val snapshot: StorageBoxSnapshotDto)
internal data class CreateStorageBoxSnapshotResponse(
    val snapshot: StorageBoxSnapshotDto,
    val action: StorageBoxActionDto,
)
internal data class StorageBoxSubaccountsResponse(
    @Json(name = "subaccounts") val subaccounts: List<StorageBoxSubaccountDto>,
    val meta: StorageBoxMetaDto? = null,
)
internal data class StorageBoxSubaccountResponse(
    @Json(name = "subaccount") val subaccount: StorageBoxSubaccountDto,
)

/** Fields intentionally limited to the read-only summary confirmed for this first slice. */
internal data class StorageBoxDto(
    val id: Long,
    val name: String? = null,
    val status: String? = null,
    val stats: StorageBoxStatsDto? = null,
)

internal data class StorageBoxStatsDto(
    val size: Long,
    @Json(name = "size_data") val sizeData: Long,
    @Json(name = "size_snapshots") val sizeSnapshots: Long,
)

/** Conservative read-only subset of the documented snapshot representation. */
internal data class StorageBoxSnapshotDto(
    val id: Long,
    val name: String? = null,
    val description: String? = null,
    @Json(name = "is_automatic") val isAutomatic: Boolean? = null,
    val created: String? = null,
    val stats: StorageBoxSnapshotStatsDto? = null,
    val labels: Map<String, String> = emptyMap(),
)

internal data class StorageBoxSnapshotStatsDto(
    val size: Long,
    @Json(name = "size_filesystem") val sizeFilesystem: Long,
)

/** Both fields are optional: Moshi omits nulls rather than sending explicit null values. */
internal data class CreateStorageBoxSnapshotRequestDto(
    val description: String? = null,
    val labels: Map<String, String>? = null,
)

internal data class StorageBoxActionDto(
    val id: Long,
    val status: String,
    val command: String? = null,
    val started: String? = null,
    val finished: String? = null,
    val progress: Int? = null,
    val error: StorageBoxActionErrorDto? = null,
)
internal data class StorageBoxActionErrorDto(val code: String? = null, val message: String? = null)

/** The Storage Box reference marks response properties nullable; keep identity fields nullable. */
internal data class StorageBoxSubaccountDto(
    val id: Long? = null,
    val name: String? = null,
    val username: String? = null,
)

internal class RetrofitStorageBoxRepository(private val api: StorageBoxApi) : StorageBoxRepository {
    override suspend fun getStorageBoxes(): List<StorageBox> = safeHCloudApiCall {
        val result = mutableListOf<StorageBox>()
        var page = 1
        do {
            val response = api.getStorageBoxes(page = page)
            result += response.storageBoxes.map { it.toDomain() }
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        result
    }

    override suspend fun getStorageBox(storageBoxId: Long): StorageBox = safeHCloudApiCall {
        api.getStorageBox(storageBoxId).storageBox.toDomain()
    }

    override suspend fun getStorageBoxFolders(storageBoxId: Long, path: String?): List<String> = safeHCloudApiCall {
        api.getStorageBoxFolders(storageBoxId, path).folders
    }

    override suspend fun getStorageBoxSnapshots(storageBoxId: Long): List<StorageBoxSnapshot> = safeHCloudApiCall {
        val result = mutableListOf<StorageBoxSnapshot>()
        var page = 1
        do {
            val response = api.getStorageBoxSnapshots(storageBoxId, page = page)
            result += response.snapshots.map { it.toDomain() }
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        result
    }

    override suspend fun getStorageBoxSnapshot(storageBoxId: Long, snapshotId: Long): StorageBoxSnapshot = safeHCloudApiCall {
        api.getStorageBoxSnapshot(storageBoxId, snapshotId).snapshot.toDomain()
    }

    override suspend fun createStorageBoxSnapshot(
        storageBoxId: Long,
        spec: CreateStorageBoxSnapshotSpec,
    ): CreatedStorageBoxSnapshot = safeHCloudApiCall {
        api.createStorageBoxSnapshot(
            storageBoxId,
            CreateStorageBoxSnapshotRequestDto(spec.description, spec.labels),
        ).let { response ->
            CreatedStorageBoxSnapshot(response.snapshot.toDomain(), response.action.toDomain())
        }
    }

    override suspend fun getStorageBoxSubaccounts(storageBoxId: Long): List<StorageBoxSubaccount> = safeHCloudApiCall {
        val result = mutableListOf<StorageBoxSubaccount>()
        var page = 1
        do {
            val response = api.getStorageBoxSubaccounts(storageBoxId, page = page)
            result += response.subaccounts.map { it.toDomain() }
            page = response.meta?.pagination?.nextPage ?: 0
        } while (page != 0)
        result
    }

    override suspend fun getStorageBoxSubaccount(storageBoxId: Long, subaccountId: Long): StorageBoxSubaccount = safeHCloudApiCall {
        api.getStorageBoxSubaccount(storageBoxId, subaccountId).subaccount.toDomain()
    }
}

internal fun StorageBoxDto.toDomain() = StorageBox(
    id = id,
    name = name,
    status = status,
    stats = stats?.let { StorageBoxStats(it.size, it.sizeData, it.sizeSnapshots) },
)

internal fun StorageBoxSnapshotDto.toDomain() = StorageBoxSnapshot(
    id = id,
    name = name,
    description = description,
    isAutomatic = isAutomatic,
    created = created,
    stats = stats?.let { StorageBoxSnapshotStats(it.size, it.sizeFilesystem) },
    labels = labels,
)

internal fun StorageBoxActionDto.toDomain() = CloudAction(
    id = id,
    status = status,
    command = command,
    started = started,
    finished = finished,
    progress = progress,
    errorCode = error?.code,
    errorMessage = error?.message,
)

internal fun StorageBoxSubaccountDto.toDomain() = StorageBoxSubaccount(
    id = id,
    name = name,
    username = username,
)

object StorageBoxRepositoryFactory {
    fun create(tokenProvider: () -> String?): StorageBoxRepository =
        RetrofitStorageBoxRepository(StorageBoxApiFactory.create(tokenProvider))
}

internal object StorageBoxApiFactory {
    const val BASE_URL = "https://api.hetzner.com/v1/"

    fun create(
        tokenProvider: () -> String?,
        baseUrl: String = BASE_URL,
        diagnosticsLogger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    ): StorageBoxApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(StorageBoxApiDiagnosticsInterceptor(diagnosticsLogger))
            .addInterceptor(StorageBoxBearerTokenInterceptor(tokenProvider))
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StorageBoxApi::class.java)
    }
}

private class StorageBoxBearerTokenInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()?.takeIf(String::isNotBlank) ?: throw MissingStorageBoxApiTokenException()
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/** See [MissingApiTokenException]: local credential failures must travel through Retrofit safely. */
class MissingStorageBoxApiTokenException : IOException("Active project API token is not configured for Storage Box")
