package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.CloudImageInventory
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.repository.HCloudRepository

internal fun CloudImageInventory.canDeleteImage(): Boolean = type in setOf("snapshot", "backup") && status == "available" && deleteProtected == false
internal fun CloudImageInventory.canProtectImage(): Boolean = type == "snapshot" && status == "available" && deleteProtected != null
internal fun CloudImageInventory.reviewIdentity(): String = name?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() } ?: "#$id"

/** Eligibility follows https://docs.hetzner.cloud/cloud.spec.json: delete backup/snapshot, protect snapshot only. */
internal class ImageDeletionAdapter(private val repository: HCloudRepository, private val reviewed: CloudImageInventory) {
    suspend fun validateFresh() {
        val fresh = repository.getImage(reviewed.id)
        check(fresh.id == reviewed.id && fresh.name == reviewed.name && fresh.description == reviewed.description &&
            fresh.type == reviewed.type && fresh.status == reviewed.status && fresh.deleteProtected == reviewed.deleteProtected)
        check(fresh.canDeleteImage())
    }
    suspend fun delete() { repository.deleteImage(reviewed.id) }
    suspend fun deleted(): Boolean = try { repository.getImage(reviewed.id); false } catch (failure: HCloudApiException) {
        if (failure.failure.httpStatus == 404) true else throw failure
    }
}
