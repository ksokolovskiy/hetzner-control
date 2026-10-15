package com.hcloudcontrol.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class RootContentStateKeyTest {
    @Test fun `cloud switch retires only exact previous owner roots never independent S3`() {
        val cloud = ResourceContext("cloud-a", "https://api.example", ContextKind.CLOUD)
        val next = cloud.copy(ownerId = "cloud-b")
        val s3 = ResourceContext("s3-a", "https://s3.example", ContextKind.S3)
        val retired = retiredCloudRootKeys(cloud, next)
        assertEquals(5, retired.size)
        assertTrue(retired.contains(rootContentStateKey(cloud, RootDestination.CREATE)))
        assertFalse(retired.contains(rootContentStateKey(next, RootDestination.CREATE)))
        assertFalse(retired.contains(rootContentStateKey(s3, RootDestination.MORE)))
        assertTrue(retiredCloudRootKeys(cloud, cloud).isEmpty())
        assertTrue(retiredCloudRootKeys(s3, cloud).isEmpty())
        assertNotEquals(rootContentStateKey(cloud, RootDestination.CREATE), rootContentStateKey(cloud.copy(endpoint = "https://other.example"), RootDestination.CREATE))
    }
}
