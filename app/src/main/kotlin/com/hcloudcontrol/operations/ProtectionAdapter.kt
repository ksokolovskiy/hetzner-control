package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.DeleteProtectionPatch
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.model.ImageProtectionPatch
import com.hcloudcontrol.reviewIdentity
import com.hcloudcontrol.core.domain.repository.HCloudRepository

enum class ProtectionResource(val identity: String) {
    SERVER("server"), VOLUME("volume"), NETWORK("network"), LOAD_BALANCER("load_balancer"), PRIMARY_IP("primary_ip"), FLOATING_IP("floating_ip"), IMAGE("image")
}
enum class ProtectionField { DELETE, REBUILD }
data class ProtectionSnapshot(val id: Long, val name: String, val protected: Boolean) {
    fun requireUnchanged(reviewed: ProtectionSnapshot) { check(this == reviewed) { "Protection review invalidated" } }
}

/** Changes exactly one reviewed flag, never dependencies or another protection flag. */
class ProtectionAdapter(val resource: ProtectionResource, val id: Long, val field: ProtectionField, val repository: HCloudRepository) {
    init { require(id > 0); require(field != ProtectionField.REBUILD || resource == ProtectionResource.SERVER) }
    val action: String get() = "protection_${this.field.name.lowercase()}"
    suspend fun read(): ProtectionSnapshot = when (resource) {
        ProtectionResource.SERVER -> repository.getServer(id).let {
            val flags = checkNotNull(it.protection) { "Protection state unavailable" }
            ProtectionSnapshot(it.id, it.name, if (field == ProtectionField.DELETE) flags.delete else flags.rebuild)
        }
        ProtectionResource.VOLUME -> repository.getVolume(id).let { check(it.protectionKnown); ProtectionSnapshot(it.id, it.name, it.deleteProtected) }
        ProtectionResource.NETWORK -> repository.getPrivateNetwork(id).let { check(it.protectionKnown); ProtectionSnapshot(it.id, it.name, it.deleteProtected) }
        ProtectionResource.LOAD_BALANCER -> repository.getLoadBalancer(id).let { check(it.protectionKnown); ProtectionSnapshot(it.id, it.name, it.deleteProtected) }
        ProtectionResource.PRIMARY_IP -> repository.getPrimaryIp(id).let { check(it.protectionKnown); ProtectionSnapshot(it.id, it.name, it.deleteProtected) }
        ProtectionResource.FLOATING_IP -> repository.getFloatingIp(id).let { check(it.protectionKnown); ProtectionSnapshot(it.id, it.name ?: it.address, it.deleteProtected) }
        ProtectionResource.IMAGE -> repository.getImage(id).let {
            check(it.type == "snapshot" && it.status == "available") { "Image protection unavailable" }
            ProtectionSnapshot(it.id, it.reviewIdentity(), checkNotNull(it.deleteProtected))
        }
    }
    suspend fun applyDesired(protected: Boolean): CloudAction = when (resource) {
        ProtectionResource.SERVER -> repository.changeServerProtection(id, if (field == ProtectionField.DELETE) ServerProtectionPatch(delete = protected) else ServerProtectionPatch(rebuild = protected))
        ProtectionResource.VOLUME -> repository.changeVolumeProtection(id, DeleteProtectionPatch(protected))
        ProtectionResource.NETWORK -> repository.changePrivateNetworkProtection(id, DeleteProtectionPatch(protected))
        ProtectionResource.LOAD_BALANCER -> repository.changeLoadBalancerProtection(id, DeleteProtectionPatch(protected))
        ProtectionResource.PRIMARY_IP -> repository.changePrimaryIpProtection(id, DeleteProtectionPatch(protected))
        ProtectionResource.FLOATING_IP -> repository.changeFloatingIpProtection(id, DeleteProtectionPatch(protected))
        ProtectionResource.IMAGE -> repository.changeImageProtection(id, ImageProtectionPatch(protected))
    }
}
