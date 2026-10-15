package com.hcloudcontrol

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/** OpenSSH public-key wire format only; never decode, retain or echo private-key input. */
internal sealed interface SshPublicKeyValidation {
    data class Valid(val fingerprint: String, val md5Fingerprint: String) : SshPublicKeyValidation
    data object Invalid : SshPublicKeyValidation
    data object PrivateMaterial : SshPublicKeyValidation
}

internal fun validateSshPublicKey(input: String): SshPublicKeyValidation {
    if (input.contains("PRIVATE KEY", ignoreCase = true) || input.trimStart().startsWith("PuTTY-User-Key-File:")) {
        return SshPublicKeyValidation.PrivateMaterial
    }
    if (input.length !in 1..65536 || input.trim().contains('\n') || input.trim().contains('\r')) return SshPublicKeyValidation.Invalid
    return runCatching {
        val fields = input.trim().split(Regex("\\s+"), limit = 3)
        require(fields.size >= 2)
        val wire = Base64.getDecoder().decode(fields[1])
        val buffer = ByteBuffer.wrap(wire)
        fun field(): ByteArray {
            require(buffer.remaining() >= 4)
            val size = buffer.int
            require(size > 0 && size <= buffer.remaining())
            return ByteArray(size).also { buffer.get(it) }
        }
        val type = field().toString(Charsets.US_ASCII)
        require(type == fields[0])
        when (type) {
            "ssh-ed25519" -> require(field().size == 32)
            "ssh-rsa" -> { require(field().any { it != 0.toByte() }); require(field().any { it != 0.toByte() }) }
            "ssh-dss" -> repeat(4) { require(field().any { it != 0.toByte() }) }
            "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521" -> {
                val curve = type.removePrefix("ecdsa-sha2-")
                require(field().toString(Charsets.US_ASCII) == curve)
                val point = field()
                require(point.first() == 4.toByte() && point.size == when (curve) { "nistp256" -> 65; "nistp384" -> 97; else -> 133 })
            }
            "sk-ssh-ed25519@openssh.com" -> { require(field().size == 32); field() }
            "sk-ecdsa-sha2-nistp256@openssh.com" -> {
                require(field().toString(Charsets.US_ASCII) == "nistp256")
                val point = field(); require(point.size == 65 && point.first() == 4.toByte()); field()
            }
            else -> error("Unsupported public-key encoding")
        }
        require(!buffer.hasRemaining())
        SshPublicKeyValidation.Valid("SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(wire)),
            MessageDigest.getInstance("MD5").digest(wire).joinToString(":") { "%02x".format(it) })
    }.getOrDefault(SshPublicKeyValidation.Invalid)
}
