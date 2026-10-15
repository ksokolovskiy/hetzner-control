package com.hcloudcontrol

import java.net.IDN

/** DNS names only, never a URL, address, wildcard or arbitrary provider payload. */
internal fun normalizePtrHostname(value: String): String? {
    val input = value.trim()
    if (input.isEmpty()) return null // serialized as explicit dns_ptr:null, not omitted
    require(!input.endsWith('.'))
    val ascii = IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).lowercase()
    require(ascii.length <= 253 && ascii.contains('.'))
    require(ascii.split('.').all { it.length in 1..63 && it.first() != '-' && it.last() != '-' })
    require(!ascii.split('.').all { it.toIntOrNull() in 0..255 })
    return ascii
}
