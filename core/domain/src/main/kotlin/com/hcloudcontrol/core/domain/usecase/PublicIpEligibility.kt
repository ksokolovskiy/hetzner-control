package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.*

/** Conditions documented by Cloud API, shared by picker and dispatch validation. */
enum class PublicIpAssignmentBlock { ALREADY_ASSIGNED, UNKNOWN_LOCATION, DIFFERENT_LOCATION, SERVER_NOT_OFF, FAMILY_OCCUPIED, MISSING_PRIMARY_IP, UNKNOWN_ZONE, DIFFERENT_ZONE, UNSUPPORTED_ASSIGNEE }

fun primaryIpAssignmentBlock(ip: PrimaryIpInventory, target: Server): PublicIpAssignmentBlock? {
    if (ip.assigneeId != null) return PublicIpAssignmentBlock.ALREADY_ASSIGNED
    val location = target.locationDetails?.name ?: target.location
    if (location.isBlank() || ip.location.isBlank()) return PublicIpAssignmentBlock.UNKNOWN_LOCATION
    if (ip.location != location) return PublicIpAssignmentBlock.DIFFERENT_LOCATION
    if (target.status != ServerStatus.OFF) return PublicIpAssignmentBlock.SERVER_NOT_OFF
    if (target.hasPrimaryFamily(ip.type)) return PublicIpAssignmentBlock.FAMILY_OCCUPIED
    return null
}

/** Floating assignment is zone-scoped, not location-scoped, and does not require OFF. */
fun floatingIpAssignmentBlock(ip: FloatingIpInventory, target: Server, locations: List<LocationOption>): PublicIpAssignmentBlock? {
    if (!target.hasPrimaryFamily(ip.type)) return PublicIpAssignmentBlock.MISSING_PRIMARY_IP
    val ipZone = locations.firstOrNull { it.name == ip.homeLocation }?.networkZone
    val targetZone = target.locationDetails?.networkZone
        ?: locations.firstOrNull { it.name == (target.locationDetails?.name ?: target.location) }?.networkZone
    if (ipZone.isNullOrBlank() || targetZone.isNullOrBlank()) return PublicIpAssignmentBlock.UNKNOWN_ZONE
    if (ipZone != targetZone) return PublicIpAssignmentBlock.DIFFERENT_ZONE
    return null
}

private fun Server.hasPrimaryFamily(type: String): Boolean = when (type.lowercase()) {
    "ipv4" -> !networkDevices.ipv4Address.isNullOrBlank() || !ipv4Address.isNullOrBlank()
    "ipv6" -> !networkDevices.ipv6Network.isNullOrBlank()
    else -> false
}
