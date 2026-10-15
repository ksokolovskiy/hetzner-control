package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.ServerTypeOption

enum class ProvisioningStep { RESOURCES, ACCESS, REVIEW }
enum class DraftReset { IMAGE_ARCHITECTURE, TYPE_LOCATION, LOCATION_TYPE, UNAVAILABLE_SELECTION }
data class DraftSelection(val draft: ProvisioningDraft, val resets: Set<DraftReset>)

/** Called only with successfully refreshed inventories; never invents a replacement resource. */
internal fun reconcileCatalogueDraft(draft: ProvisioningDraft, types: List<ServerTypeOption>,
    images: List<ImageOption>, locations: List<LocationOption>, keys: List<com.hcloudcontrol.core.domain.model.SshKeyOption>?): DraftSelection {
    val type = types.firstOrNull { it.id == draft.typeId }
    val image = images.firstOrNull { it.id == draft.imageId }
    val location = locations.firstOrNull { it.id == draft.locationId }
    val missing = (draft.typeId != null && type == null) || (draft.imageId != null && image == null) ||
        (draft.locationId != null && location == null)
    val remainingKeys = keys?.map { it.id }?.toSet()?.let { draft.sshKeyIds.intersect(it) } ?: draft.sshKeyIds
    var updated = draft.copy(typeId = type?.id, imageId = image?.id, locationId = location?.id, sshKeyIds = remainingKeys)
    val notices = mutableSetOf<DraftReset>()
    if (missing || remainingKeys != draft.sshKeyIds) notices += DraftReset.UNAVAILABLE_SELECTION
    if (type != null) {
        val compatible = updated.selectType(type, image, location)
        updated = compatible.draft
        notices += compatible.resets
    }
    if (notices.isNotEmpty()) updated = updated.copy(step = ProvisioningStep.RESOURCES)
    return DraftSelection(updated, notices)
}

/** Saved drafts contain user-entered names and public resource IDs, never credentials/results. */
data class ProvisioningDraft(
    val name: String = "", val typeId: Long? = null, val imageId: Long? = null, val locationId: Long? = null,
    val sshKeyIds: Set<Long> = emptySet(), val ipv4: Boolean = true, val ipv6: Boolean = true,
    val step: ProvisioningStep = ProvisioningStep.RESOURCES,
) {
    fun selectType(type: ServerTypeOption, image: ImageOption?, location: LocationOption?): DraftSelection {
        val resetImage = image != null && image.architecture != null && type.architecture != null && image.architecture != type.architecture
        val resetLocation = location != null && location.name !in type.availableLocations
        return DraftSelection(copy(typeId = type.id, imageId = if (resetImage) null else imageId,
            locationId = if (resetLocation) null else locationId), buildSet {
            if (resetImage) add(DraftReset.IMAGE_ARCHITECTURE)
            if (resetLocation) add(DraftReset.LOCATION_TYPE)
        })
    }
    fun selectLocation(location: LocationOption, type: ServerTypeOption?): DraftSelection {
        val reset = type != null && location.name !in type.availableLocations
        return DraftSelection(copy(locationId = location.id, typeId = if (reset) null else typeId),
            if (reset) setOf(DraftReset.TYPE_LOCATION) else emptySet())
    }
    fun snapshot(): ArrayList<String> = arrayListOf(name, typeId?.toString().orEmpty(), imageId?.toString().orEmpty(),
        locationId?.toString().orEmpty(), sshKeyIds.sorted().joinToString(","), ipv4.toString(), ipv6.toString(), step.name)
    fun snapshotFor(context: OperationContext?): ArrayList<String> = arrayListOf(
        context?.connectionId.orEmpty(), context?.endpoint.orEmpty(), context?.environment?.name.orEmpty(),
    ).apply { addAll(snapshot()) }
    companion object {
        fun restoreFor(context: OperationContext?, values: List<String>): ProvisioningDraft {
            val expected = listOf(context?.connectionId.orEmpty(), context?.endpoint.orEmpty(), context?.environment?.name.orEmpty())
            return if (values.size == 11 && values.take(3) == expected) restore(values.drop(3)) else ProvisioningDraft()
        }
        fun restore(values: List<String>): ProvisioningDraft = runCatching {
            require(values.size == 8)
            ProvisioningDraft(values[0], values[1].toLongOrNull(), values[2].toLongOrNull(), values[3].toLongOrNull(),
                values[4].split(',').filter { it.isNotEmpty() }.map { it.toLong().also { id -> require(id > 0) } }.toSet(),
                values[5].toBooleanStrict(), values[6].toBooleanStrict(), ProvisioningStep.valueOf(values[7]))
        }.getOrDefault(ProvisioningDraft())
    }
}

data class ServerTypeFilters(val query: String = "", val architecture: String? = null, val category: String? = null,
    val cores: Int? = null, val memoryGb: Double? = null, val diskGb: Int? = null) {
    fun apply(types: List<ServerTypeOption>): List<ServerTypeOption> = types.filter { type ->
        (query.isBlank() || listOf(type.name, type.description).any { it.contains(query.trim(), ignoreCase = true) }) &&
            (architecture == null || type.architecture == architecture) && (category == null || type.category == category) &&
            (cores == null || type.cores == cores) && (memoryGb == null || type.memoryGb == memoryGb) && (diskGb == null || type.diskGb == diskGb)
    }.sortedWith(compareBy<ServerTypeOption> { it.cores }.thenBy { it.memoryGb }.thenBy { it.diskGb }.thenBy { it.name })
}
