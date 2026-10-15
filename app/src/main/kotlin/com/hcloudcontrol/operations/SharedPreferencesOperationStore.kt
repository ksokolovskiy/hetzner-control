package com.hcloudcontrol.operations

import android.content.Context
import android.util.Base64

/** Device-global private nonsecret journal. commit() completes before a provider write is allowed. */
class SharedPreferencesOperationStore(context: Context) : OperationStore {
    private val preferences = context.applicationContext.getSharedPreferences("operation_journal_v1", Context.MODE_PRIVATE)
    private val codec = OperationPersistence(
        readBytes = { preferences.getString("records", null)?.let { Base64.decode(it, Base64.NO_WRAP) } },
        replaceBytesAtomically = { bytes ->
            check(preferences.edit().putString("records", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) {
                "Operation identity could not be persisted; submission blocked"
            }
        },
    )
    override fun load(): List<PersistedOperation> = codec.load()
    override fun save(records: List<PersistedOperation>) = codec.save(records)
}
