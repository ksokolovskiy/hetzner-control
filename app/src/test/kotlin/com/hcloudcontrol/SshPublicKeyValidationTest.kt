package com.hcloudcontrol

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SshPublicKeyValidationTest {
    private fun key(type: String = "ssh-ed25519", bytes: ByteArray = ByteArray(32) { (it + 1).toByte() }): String {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { writer ->
            val encodedType = type.toByteArray()
            writer.writeInt(encodedType.size); writer.write(encodedType)
            writer.writeInt(bytes.size); writer.write(bytes)
        }
        return "$type ${Base64.getEncoder().encodeToString(out.toByteArray())} fixture@invalid"
    }

    @Test fun `valid key gives a fingerprint independent of comment`() {
        val result = validateSshPublicKey(key()) as SshPublicKeyValidation.Valid
        assertTrue(result.fingerprint.startsWith("SHA256:"))
        assertEquals(result, validateSshPublicKey(key().substringBefore(" fixture@invalid") + " other-comment"))
    }

    @Test fun `private material is rejected before parsing`() {
        // Construct only a synthetic header; no private key material belongs in fixtures.
        val privateHeader = "-----BEGIN OPENSSH " + "PRIVATE KEY-----\nsynthetic"
        assertEquals(SshPublicKeyValidation.PrivateMaterial, validateSshPublicKey(privateHeader))
        assertEquals(SshPublicKeyValidation.PrivateMaterial, validateSshPublicKey("PuTTY-User-Key-File: synthetic"))
    }

    @Test fun `malformed wire data and mismatched type cannot reach review`() {
        assertEquals(SshPublicKeyValidation.Invalid, validateSshPublicKey("ssh-ed25519 invalid"))
        assertEquals(SshPublicKeyValidation.Invalid, validateSshPublicKey(key(bytes = ByteArray(31))))
        assertEquals(SshPublicKeyValidation.Invalid, validateSshPublicKey(key().replaceFirst("ssh-ed25519", "ssh-rsa")))
        assertEquals(SshPublicKeyValidation.Invalid, validateSshPublicKey(key() + "\n" + key()))
    }
}
