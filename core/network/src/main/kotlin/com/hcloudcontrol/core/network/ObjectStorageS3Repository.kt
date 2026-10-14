package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ObjectStorageBucket
import com.hcloudcontrol.core.domain.model.ObjectStorageCredentials
import com.hcloudcontrol.core.domain.model.isValidObjectStorageBucketName
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.xmlpull.v1.XmlPullParserFactory

/** Safe error intentionally omits S3 XML, signatures, authorization and any credential material. */
class ObjectStorageRequestException(val safeMessage: String) : Exception(safeMessage)
class ObjectStorageBucketNotEmptyException : Exception("Bucket is not empty")

/**
 * Minimal Hetzner S3 client. Hosts are derived exclusively from [ObjectStorageCredentials]
 * locations; no Cloud API base URL or Bearer token is accepted here.
 */
class ObjectStorageS3Repository(
    private val credentialsProvider: () -> ObjectStorageCredentials?,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) : ObjectStorageRepository {
    override suspend fun listBuckets(): List<ObjectStorageBucket> = executeXml("GET", null, emptyMap()).let(::parseBuckets)

    override suspend fun createBucket(name: String) {
        require(isValidObjectStorageBucketName(name)) { "Invalid bucket name" }
        executeXml("PUT", name, emptyMap())
    }

    override suspend fun deleteBucket(name: String) {
        require(isValidObjectStorageBucketName(name)) { "Invalid bucket name" }
        // Freshly prove the bucket is empty. DeleteBucket alone returns a generic S3 error that
        // is deliberately not surfaced because it can echo request details.
        val check = executeXml("GET", name, mapOf("list-type" to "2", "max-keys" to "1"))
        if (!isBucketEmpty(check)) throw ObjectStorageBucketNotEmptyException()
        executeXml("DELETE", name, emptyMap())
    }

    private fun executeXml(method: String, bucket: String?, query: Map<String, String>): String {
        val credentials = credentialsProvider() ?: throw ObjectStorageRequestException("No Object Storage connection selected")
        val host = credentials.location.host
        val canonicalPath = bucket?.let { "/$it" } ?: "/"
        val canonicalQuery = query.entries.sortedBy { it.key }.joinToString("&") { (key, value) ->
            "${rfc3986(key)}=${rfc3986(value)}"
        }
        val url = ("https://$host$canonicalPath" + if (canonicalQuery.isEmpty()) "" else "?$canonicalQuery").toHttpUrl()
        val now = Instant.now(clock)
        val amzDate = AMZ_DATE.format(now)
        val shortDate = SHORT_DATE.format(now)
        val payloadHash = sha256("")
        val canonicalHeaders = "host:$host\n" +
            "x-amz-content-sha256:$payloadHash\n" +
            "x-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = listOf(method, canonicalPath, canonicalQuery, canonicalHeaders, signedHeaders, payloadHash).joinToString("\n")
        val scope = "$shortDate/${credentials.location.id}/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256(canonicalRequest)}"
        val signingKey = hmac(hmac(hmac(hmac("AWS4${credentials.secretKey}", shortDate), credentials.location.id), "s3"), "aws4_request")
        val signature = hmac(signingKey, stringToSign).joinToString("") { "%02x".format(it) }
        val authorization = "AWS4-HMAC-SHA256 Credential=${credentials.accessKey}/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        val request = Request.Builder().url(url).method(method, null)
            .header("Host", host)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("Authorization", authorization)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ObjectStorageRequestException(
                when (response.code) {
                    401, 403 -> "Object Storage credentials were rejected"
                    404 -> "Object Storage bucket was not found"
                    409 -> "Bucket operation conflicts with its current state"
                    else -> "Object Storage request failed (${response.code})"
                },
            )
            return response.body?.string().orEmpty()
        }
    }

    private fun parseBuckets(xml: String): List<ObjectStorageBucket> = parseXml(xml) { name, text, values ->
        if (name == "Bucket") values += ObjectStorageBucket(text["Name"].orEmpty(), text["CreationDate"])
    }.filter { it.name.isNotBlank() }.sortedBy { it.name }

    private fun isBucketEmpty(xml: String): Boolean = !xml.contains("<Contents>") && !xml.contains("<Key>")

    private fun parseXml(xml: String, end: (String, Map<String, String>, MutableList<ObjectStorageBucket>) -> Unit): List<ObjectStorageBucket> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        val values = mutableListOf<ObjectStorageBucket>()
        val texts = ArrayDeque<MutableMap<String, String>>()
        val names = ArrayDeque<String>()
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> { names.addLast(parser.name); texts.addLast(mutableMapOf()) }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (texts.isNotEmpty()) texts.last()[names.last()] = parser.text.orEmpty()
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    val name = names.removeLast()
                    val text = texts.removeLast()
                    end(name, text, values)
                    if (texts.isNotEmpty()) texts.last().putAll(text)
                }
            }
            parser.next()
        }
        return values
    }

    private companion object {
        val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        fun hmac(key: String, value: String): ByteArray = hmac(key.toByteArray(), value)
        fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value.toByteArray()) }
        fun rfc3986(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%7E", "~")
    }
}

object ObjectStorageRepositoryFactory {
    fun create(credentialsProvider: () -> ObjectStorageCredentials?): ObjectStorageRepository = ObjectStorageS3Repository(credentialsProvider)
}
