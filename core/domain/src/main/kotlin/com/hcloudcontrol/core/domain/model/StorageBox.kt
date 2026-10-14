package com.hcloudcontrol.core.domain.model

/** Read-only summary returned by the separate Hetzner Storage Box API. */
data class StorageBox(
    val id: Long,
    val name: String?,
    val status: String?,
    val stats: StorageBoxStats?,
)

/** Byte counters supplied by the Storage Box API; no unit conversion is applied. */
data class StorageBoxStats(
    val size: Long,
    val sizeData: Long,
    val sizeSnapshots: Long,
)

/** Read-only Storage Box snapshot. Timestamps stay in their API representation. */
data class StorageBoxSnapshot(
    val id: Long,
    val name: String?,
    val description: String?,
    val isAutomatic: Boolean?,
    val created: String?,
    val stats: StorageBoxSnapshotStats?,
    val labels: Map<String, String> = emptyMap(),
)

/** Byte counters supplied by the API; no conversion or derived usage is applied. */
data class StorageBoxSnapshotStats(
    val size: Long,
    val sizeFilesystem: Long,
)

/** Request for a manual snapshot. Null optional fields are intentionally absent on the wire. */
data class CreateStorageBoxSnapshotSpec(
    val description: String? = null,
    val labels: Map<String, String>? = null,
)

/** API result: the created resource reference and its asynchronous action. */
data class CreatedStorageBoxSnapshot(
    val snapshot: StorageBoxSnapshot,
    val action: CloudAction,
)

/** Read-only Storage Box subaccount identity. No password or access-secret material is exposed. */
data class StorageBoxSubaccount(
    val id: Long?,
    /** Optional display name; it is distinct from the login username. */
    val name: String?,
    val username: String?,
)
