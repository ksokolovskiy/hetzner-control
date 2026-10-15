package com.hcloudcontrol.core.security

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class CheckedPreferencesTest {
    internal class Fixture(val values: MutableMap<String, Any?>, private val results: MutableList<Boolean>) {
        val preferences: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args -> when (method.name) {
                "getAll" -> values.toMap()
                "getBoolean" -> values[args!![0]] as? Boolean ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preference read")
            } } as SharedPreferences
        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            lateinit var instance: SharedPreferences.Editor
            instance = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { _, method, args -> when {
                    method.name.startsWith("put") -> { pending[args!![0] as String] = args[1]; instance }
                    method.name == "remove" -> { pending[args!![0] as String] = null; instance }
                    method.name == "commit" -> {
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        if (results.isEmpty()) true else results.removeAt(0)
                    }
                    else -> error("Unexpected editor operation")
                } } as SharedPreferences.Editor
            return instance
        }
    }
    @Test fun `confirmed commit preserves update`() {
        val fixture = Fixture(mutableMapOf("active" to "old"), mutableListOf(true))
        checkedPreferenceUpdate(fixture.preferences, setOf("active")) { putString("active", "new") }
        assertEquals("new", fixture.values["active"])
    }
    @Test fun `failed selection commit restores memory and exposes fixed error without cause`() {
        val fixture = Fixture(mutableMapOf("active" to "old", "untouched" to "public-fixture"), mutableListOf(false, true))
        val failure = runCatching { checkedPreferenceUpdate(fixture.preferences, setOf("active")) { putString("active", "new") } }.exceptionOrNull()
        assertTrue(failure is LocalPersistenceException)
        assertNull(failure?.cause)
        assertEquals("old", fixture.values["active"])
        assertEquals("public-fixture", fixture.values["untouched"])
    }
    @Test fun `failed removal and permission commit restore all touched values`() {
        val before = mutableMapOf<String, Any?>("active" to "old", "metadata" to "fixture", "allowed" to false)
        val fixture = Fixture(before.toMutableMap(), mutableListOf(false, true))
        assertTrue(runCatching { checkedPreferenceUpdate(fixture.preferences, before.keys) {
            remove("metadata"); putString("active", "new"); putBoolean("allowed", true)
        } }.isFailure)
        assertEquals(before, fixture.values)
    }
}
