package com.hcloudcontrol.core.domain.model

/** The only Hetzner Object Storage regions exposed by the mobile client. */
enum class ObjectStorageLocation(val id: String, val host: String) {
    FALKENSTEIN("fsn1", "fsn1.your-objectstorage.com"),
    NUREMBERG("nbg1", "nbg1.your-objectstorage.com"),
    HELSINKI("hel1", "hel1.your-objectstorage.com"),
    ;

    companion object {
        fun fromId(id: String): ObjectStorageLocation = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unsupported Object Storage location")
    }
}

/** Secret S3 credentials are supplied only in memory by the encrypted profile store. */
data class ObjectStorageCredentials(
    val accessKey: String,
    val secretKey: String,
    val location: ObjectStorageLocation,
)

data class ObjectStorageBucket(
    val name: String,
    val createdAt: String? = null,
)

/** Bucket naming validation shared by review UI and the S3 client. */
fun isValidObjectStorageBucketName(value: String): Boolean {
    if (value.length !in 3..63 || value != value.lowercase()) return false
    if (!value.matches(Regex("[a-z0-9][a-z0-9.-]*[a-z0-9]"))) return false
    if (".." in value || value.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return false
    return true
}
