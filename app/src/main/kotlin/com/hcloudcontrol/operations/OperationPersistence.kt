package com.hcloudcontrol.operations

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned allowlist codec. The host supplies an atomic private-file/preferences replacement. */
class OperationPersistence(
    private val readBytes: () -> ByteArray?,
    private val replaceBytesAtomically: (ByteArray) -> Unit,
) : OperationStore {
    override fun load(): List<PersistedOperation> {
        val bytes = readBytes() ?: return emptyList()
        require(bytes.size <= MAX_BYTES) { "Invalid operation state size" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version in 1..VERSION) { "Unsupported operation state version" }
            val count = input.readInt()
            require(count in 0..MAX_RECORDS)
            val records = List(count) {
                val context = OperationContext(input.readUTF(), input.readUTF(), OperationContext.Environment.valueOf(input.readUTF()))
                val resource = ResourceIdentity(context, input.readUTF(), input.optionalText())
                val intent = OperationIntent(input.readUTF(), resource, input.readUTF(), input.readLong())
                PersistedOperation(intent, OperationStage.valueOf(input.readUTF()), input.readLong(),
                    input.readLong().takeUnless { it == -1L }, input.optionalText(),
                    if (input.readBoolean()) OperationDiagnostic(OperationDiagnostic.Code.valueOf(input.readUTF()),
                        input.readInt().takeUnless { it == -1 }) else null,
                    input.readBoolean()).let { record ->
                        if (version == 1) record else {
                            val relatedCount = input.readInt()
                            require(relatedCount in 0..100)
                            record.copy(relatedActionIds = List(relatedCount) { input.readLong() }, secretIssued = input.readBoolean())
                        }
                    }
            }
            require(input.available() == 0) { "Unexpected operation state fields" }
            records
        }
    }

    override fun save(records: List<PersistedOperation>) {
        require(records.size <= MAX_RECORDS)
        val bytes = ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(VERSION)
                data.writeInt(records.size)
                records.forEach { record ->
                    with(data) {
                        with(record.intent.resource.context) { writeUTF(connectionId); writeUTF(endpoint); writeUTF(environment.name) }
                        writeUTF(record.intent.resource.resourceType)
                        optionalText(record.intent.resource.resourceId)
                        writeUTF(record.intent.intentId)
                        writeUTF(record.intent.action)
                        writeLong(record.intent.revision)
                        writeUTF(record.stage.name)
                        writeLong(record.updatedAtMillis)
                        writeLong(record.providerActionId ?: -1)
                        optionalText(record.createdResourceId)
                        writeBoolean(record.diagnostic != null)
                        record.diagnostic?.let { writeUTF(it.code.name); writeInt(it.httpStatus ?: -1) }
                        writeBoolean(record.refreshFailed)
                        writeInt(record.relatedActionIds.size)
                        record.relatedActionIds.forEach(::writeLong)
                        writeBoolean(record.secretIssued)
                    }
                }
            }
            output.toByteArray()
        }
        require(bytes.size <= MAX_BYTES)
        replaceBytesAtomically(bytes)
    }

    private fun DataInputStream.optionalText(): String? = if (readBoolean()) readUTF() else null
    private fun DataOutputStream.optionalText(value: String?) { writeBoolean(value != null); if (value != null) writeUTF(value) }
    private companion object { const val VERSION = 2; const val MAX_RECORDS = 1_000; const val MAX_BYTES = 2_000_000 }
}
