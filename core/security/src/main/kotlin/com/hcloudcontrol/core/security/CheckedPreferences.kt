package com.hcloudcontrol.core.security

import android.content.SharedPreferences

/** Fixed diagnostic only: never retain a preference value or the platform exception. */
class LocalPersistenceException : IllegalStateException("Local settings could not be saved")

/** commit(false) can already change memory; restore touched values before reporting failure. */
internal fun checkedPreferenceUpdate(preferences: SharedPreferences, keys: Set<String>, update: SharedPreferences.Editor.() -> Unit) {
    val before = try { preferences.all.filterKeys { it in keys } } catch (_: Exception) { throw LocalPersistenceException() }
    try {
        if (preferences.edit().apply(update).commit()) return
    } catch (_: Exception) { /* Report only the fixed diagnostic after best-effort restoration. */ }
    try {
        val rollback = preferences.edit()
        keys.forEach { key ->
            when (val value = before[key]) {
                null -> rollback.remove(key)
                is String -> rollback.putString(key, value)
                is Boolean -> rollback.putBoolean(key, value)
                is Int -> rollback.putInt(key, value)
                is Long -> rollback.putLong(key, value)
                is Float -> rollback.putFloat(key, value)
                is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> rollback.remove(key)
            }
        }
        rollback.commit()
    } catch (_: Exception) { /* Failure is still surfaced; callers must keep a safe in-memory view. */ }
    throw LocalPersistenceException()
}
