package com.hcloudcontrol.network

import com.hcloudcontrol.core.domain.model.*

/** Numeric-only parsing: never resolves DNS or silently normalizes a host address into a range. */
internal data class Ipv4Range(val first: Long, val prefix: Int) {
    val last: Long get() = first + (1L shl (32 - prefix)) - 1
    fun contains(value: Long) = value in first..last
    fun contains(other: Ipv4Range) = contains(other.first) && contains(other.last)
    fun overlaps(other: Ipv4Range) = first <= other.last && other.first <= last
}

internal fun ipv4Address(text: String): Long? {
    val parts = text.split('.')
    if (parts.size != 4) return null
    var number = 0L
    for (part in parts) {
        if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' } || (part.length > 1 && part.first() == '0')) return null
        val byte = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        number = (number shl 8) or byte.toLong()
    }
    return number
}

internal fun ipv4Range(text: String): Ipv4Range? {
    val pieces = text.split('/')
    if (pieces.size != 2 || pieces[1].any { it !in '0'..'9' }) return null
    val address = ipv4Address(pieces[0]) ?: return null
    val prefix = pieces[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return null
    val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    if ((address and mask) != address) return null
    return Ipv4Range(address, prefix)
}

// RFC 1918 boundaries are numeric protocol constants, not configured endpoints.
private fun Ipv4Range.isPrivate() = listOf(
    Ipv4Range(0x0a000000L, 8),
    Ipv4Range(0xac100000L, 12),
    Ipv4Range(0xc0a80000L, 16),
).any { it.contains(this) }

internal fun validPrivateNetworkCidr(text: String): Boolean = ipv4Range(text)?.let { it.prefix <= 24 && it.isPrivate() } == true

internal fun validCloudSubnet(network: PrivateNetworkInventory, spec: PrivateNetworkSubnetSpec, zones: Set<String>): Boolean {
    val parent = ipv4Range(network.ipRange) ?: return false
    val range = ipv4Range(spec.ipRange) ?: return false
    return range.prefix <= 30 && parent.contains(range) && spec.networkZone in zones && spec.gateway == null &&
        network.subnets.none { ipv4Range(it.ipRange)?.overlaps(range) == true } &&
        network.routes.none { ipv4Range(it.destination)?.overlaps(range) == true }
}

internal fun validNetworkRoute(network: PrivateNetworkInventory, spec: PrivateNetworkRouteSpec): Boolean {
    val destination = ipv4Range(spec.destination) ?: return false
    val gateway = ipv4Address(spec.gateway) ?: return false
    val parent = ipv4Range(network.ipRange) ?: return false
    val reserved = 0xac1f0101L // Provider-reserved gateway, in the same numeric address representation.
    if (!parent.contains(gateway) || gateway == parent.first || gateway == reserved) return false
    if (network.subnets.any { it.type == "vswitch" && ipv4Range(it.ipRange)?.contains(gateway) == true }) return false
    // Default route is explicitly supported by the API; its broad prefix is not an overlap claim.
    if (destination.prefix == 0) return network.routes.none { it.destination == "0.0.0.0/0" }
    return destination.isPrivate() && !destination.contains(reserved) &&
        network.subnets.none { ipv4Range(it.ipRange)?.overlaps(destination) == true } &&
        network.routes.none { ipv4Range(it.destination)?.overlaps(destination) == true }
}

internal fun eligibleNetworkMember(network: PrivateNetworkInventory, server: Server): Boolean {
    val zone = server.locationDetails?.networkZone ?: return false
    return server.id !in network.serverIds && network.subnets.any {
        it.type in setOf("cloud", "server") && it.networkZone == zone
    }
}
