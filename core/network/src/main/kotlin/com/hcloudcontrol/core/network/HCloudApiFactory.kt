package com.hcloudcontrol.core.network

import com.squareup.moshi.Moshi
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal object HCloudApiFactory {
    const val BASE_URL = "https://api.hetzner.cloud/v1/"

    fun create(
        tokenProvider: () -> String?,
        configProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
        diagnosticsLogger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    ): HCloudApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(configProvider))
            .addInterceptor(HCloudApiDiagnosticsInterceptor(diagnosticsLogger))
            .addInterceptor(BearerTokenInterceptor(tokenProvider))
            .build()
        val moshi = Moshi.Builder()
            .add(ServerDnsPtrRequestDtoJsonAdapter())
            .add(ChangeDnsPtrRequestDtoJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HCloudApi::class.java)
    }
}

internal class BaseUrlInterceptor(private val configProvider: () -> HCloudConnectionConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = (configProvider().baseUrl + "/").toHttpUrl()
        val original = chain.request().url
        val suffix = original.encodedPath.removePrefix("/v1")
        val routed = original.newBuilder().scheme(base.scheme).host(base.host).port(base.port)
            .encodedPath(base.encodedPath.removeSuffix("/") + suffix).build()
        return chain.proceed(chain.request().newBuilder().url(routed).build())
    }
}

internal class BearerTokenInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()?.takeIf(String::isNotBlank)
            ?: throw MissingApiTokenException()
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}

/**
 * OkHttp reports interceptor failures to Retrofit through its I/O failure path. Keeping this a
 * typed IOException makes a missing local credential observable by the active screen instead of
 * becoming an uncaught dispatcher exception.
 */
class MissingApiTokenException : IOException("Hetzner Cloud API token is not configured")
