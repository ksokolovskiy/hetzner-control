package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.*

internal enum class PublicIpKind(val identity: String) { PRIMARY("primary_ip"), FLOATING("floating_ip") }
internal data class PublicIpSnapshot(val id: Long, val name: String, val address: String, val family: String,
    val location: String?, val assigneeId: Long?, val assigneeType: String?, val protected: Boolean,
    val pointers: List<DnsPointer>, val primary: PrimaryIpInventory? = null, val floating: FloatingIpInventory? = null, val protectionKnown: Boolean = true)
internal fun PrimaryIpInventory.snapshot() = PublicIpSnapshot(id, name, address, type, location, assigneeId, assigneeType, deleteProtected, dnsPointers, primary = this, protectionKnown = protectionKnown)
internal fun FloatingIpInventory.snapshot() = PublicIpSnapshot(id, name ?: address, address, type, homeLocation, assigneeId, assigneeType, deleteProtected, dnsPointers, floating = this, protectionKnown = protectionKnown)
internal enum class PublicIpMutation { ASSIGN, UNASSIGN, DELETE }

/** Fresh immutable preconditions; deliberately never auto-unassigns or disables protection. */
internal class PublicIpMutationAdapter(val repository: HCloudRepository, val kind: PublicIpKind, val id: Long) {
    suspend fun read(): PublicIpSnapshot = if (kind == PublicIpKind.PRIMARY) repository.getPrimaryIp(id).snapshot() else repository.getFloatingIp(id).snapshot()
    suspend fun validate(expected: PublicIpSnapshot, mutation: PublicIpMutation, target: Server?): PublicIpSnapshot {
        val fresh = read()
        check(fresh == expected)
        when (mutation) {
            PublicIpMutation.DELETE -> check(fresh.protectionKnown && fresh.assigneeId == null && !fresh.protected)
            PublicIpMutation.UNASSIGN -> {
                check(fresh.assigneeId != null && fresh.assigneeType == "server")
                if (kind == PublicIpKind.PRIMARY) check(repository.getServer(checkNotNull(fresh.assigneeId)).status == ServerStatus.OFF)
            }
            PublicIpMutation.ASSIGN -> {
                val selected = checkNotNull(target)
                val current = repository.getServer(selected.id)
                check(current == selected)
                check(if (kind == PublicIpKind.PRIMARY) primaryIpAssignmentBlock(checkNotNull(fresh.primary), current) == null
                    else floatingIpAssignmentBlock(checkNotNull(fresh.floating), current, repository.getLocations()) == null)
            }
        }
        return fresh
    }
    suspend fun execute(mutation: PublicIpMutation, targetId: Long?): CloudAction? = when (mutation) {
        PublicIpMutation.ASSIGN -> if (kind == PublicIpKind.PRIMARY) repository.assignPrimaryIp(id, checkNotNull(targetId)) else repository.assignFloatingIp(id, checkNotNull(targetId))
        PublicIpMutation.UNASSIGN -> if (kind == PublicIpKind.PRIMARY) repository.unassignPrimaryIp(id) else repository.unassignFloatingIp(id)
        PublicIpMutation.DELETE -> { if (kind == PublicIpKind.PRIMARY) repository.deletePrimaryIp(id) else repository.deleteFloatingIp(id); null }
    }
    suspend fun deleted(): Boolean = try { read(); false } catch (error: HCloudApiException) { if (error.failure.httpStatus == 404) true else throw error }
}
