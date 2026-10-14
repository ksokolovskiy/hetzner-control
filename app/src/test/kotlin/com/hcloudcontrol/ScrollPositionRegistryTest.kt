package com.hcloudcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScrollPositionRegistryTest {
    @Test
    fun `registry keeps the exact viewport and serializes deterministically`() {
        val registry = ScrollPositionRegistry(emptyMap())
        registry.update("project-b:servers", index = 8, offset = 23)
        registry.update("project-a:catalog", index = 3, offset = 7)

        assertEquals(ScrollPosition(8, 23), registry.positionFor("project-b:servers"))
        assertEquals(arrayListOf("project-a:catalog", "3", "7", "project-b:servers", "8", "23"), registry.save())
    }

    @Test
    fun `same screen in different projects cannot share a viewport`() {
        val firstProject = scrollPositionKey("project-a", "servers")
        val secondProject = scrollPositionKey("project-b", "servers")

        assertNotEquals(firstProject, secondProject)
    }
}
