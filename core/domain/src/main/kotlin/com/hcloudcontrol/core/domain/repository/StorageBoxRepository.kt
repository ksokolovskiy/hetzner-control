package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.StorageBox
import com.hcloudcontrol.core.domain.model.StorageBoxSnapshot
import com.hcloudcontrol.core.domain.model.CreateStorageBoxSnapshotSpec
import com.hcloudcontrol.core.domain.model.CreatedStorageBoxSnapshot
import com.hcloudcontrol.core.domain.model.StorageBoxSubaccount
import com.hcloudcontrol.core.domain.model.CloudAction

/** Separate project-scoped boundary for the Hetzner Storage Box API. */
interface StorageBoxRepository {
    /** Original provider action ID, on the Storage Box API host rather than Cloud API. */
    suspend fun getStorageBoxAction(actionId: Long): CloudAction {
        throw UnsupportedOperationException("Storage Box action status is unavailable")
    }
    suspend fun getStorageBoxes(): List<StorageBox>
    suspend fun getStorageBox(storageBoxId: Long): StorageBox
    /** Relative folder names returned by the Storage Box API; this is read-only. */
    suspend fun getStorageBoxFolders(storageBoxId: Long, path: String? = null): List<String>
    suspend fun getStorageBoxSnapshots(storageBoxId: Long): List<StorageBoxSnapshot>
    suspend fun getStorageBoxSnapshot(storageBoxId: Long, snapshotId: Long): StorageBoxSnapshot
    /** Creates a manual snapshot only. It never rolls back, updates, or deletes snapshots. */
    suspend fun createStorageBoxSnapshot(
        storageBoxId: Long,
        spec: CreateStorageBoxSnapshotSpec = CreateStorageBoxSnapshotSpec(),
    ): CreatedStorageBoxSnapshot
    suspend fun getStorageBoxSubaccounts(storageBoxId: Long): List<StorageBoxSubaccount>
    suspend fun getStorageBoxSubaccount(storageBoxId: Long, subaccountId: Long): StorageBoxSubaccount
}
