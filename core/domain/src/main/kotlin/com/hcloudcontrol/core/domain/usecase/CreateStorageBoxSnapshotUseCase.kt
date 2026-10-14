package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CreateStorageBoxSnapshotSpec
import com.hcloudcontrol.core.domain.model.CreatedStorageBoxSnapshot
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository

/** Explicit boundary for the only enabled Storage Box snapshot mutation. */
class CreateStorageBoxSnapshotUseCase(private val repository: StorageBoxRepository) {
    suspend operator fun invoke(
        storageBoxId: Long,
        spec: CreateStorageBoxSnapshotSpec = CreateStorageBoxSnapshotSpec(),
    ): CreatedStorageBoxSnapshot = repository.createStorageBoxSnapshot(storageBoxId, spec)
}
