package com.hcloudcontrol.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectStorageTest {
    @Test fun `only documented Hetzner locations are selectable`() {
        assertTrue(ObjectStorageLocation.entries.map { it.id }.containsAll(listOf("fsn1", "nbg1", "hel1")))
        assertFalse(runCatching { ObjectStorageLocation.fromId("custom") }.isSuccess)
    }

    @Test fun `bucket validation rejects unsafe or invalid S3 names`() {
        assertTrue(isValidObjectStorageBucketName("control-plane-01"))
        assertFalse(isValidObjectStorageBucketName("ABCD"))
        assertFalse(isValidObjectStorageBucketName("192.168.1.1"))
        assertFalse(isValidObjectStorageBucketName("double..dot"))
    }
}
