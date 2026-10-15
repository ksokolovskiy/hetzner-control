package com.hcloudcontrol

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class AppLocaleManifestContractTest {
    private val android = "http://schemas.android.com/apk/res/android"
    @Test fun `merged manifest opts into AndroidX legacy locale storage on the disabled service`() {
        val file = File(checkNotNull(System.getProperty("hcloud.mergedManifest")))
        assertTrue("Merged manifest must be built before this test", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(file)
        val services = document.getElementsByTagName("service")
        val matched = (0 until services.length).map { services.item(it) as Element }
            .filter { it.getAttributeNS(android, "name") == "androidx.appcompat.app.AppLocalesMetadataHolderService" }
        assertEquals(1, matched.size)
        val service = matched.single()
        assertEquals("false", service.getAttributeNS(android, "enabled"))
        assertEquals("false", service.getAttributeNS(android, "exported"))
        val metadata = service.getElementsByTagName("meta-data")
        val storage = (0 until metadata.length).map { metadata.item(it) as Element }
            .single { it.getAttributeNS(android, "name") == "autoStoreLocales" }
        assertEquals("true", storage.getAttributeNS(android, "value"))
        val application = document.getElementsByTagName("application").item(0)
        val children = application.childNodes
        assertFalse((0 until children.length).mapNotNull { children.item(it) as? Element }.any {
            it.tagName == "meta-data" && it.getAttributeNS(android, "name") == "autoStoreLocales"
        })
    }
}
