package com.hcloudcontrol

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.operations.OperationContext
import com.hcloudcontrol.operations.PersistedOperation

/** Only public bucket search/name. Credentials, reveal state, confirmations and results are excluded. */
internal data class S3PublicDraft(val query: String = "", val bucketName: String = "") {
    fun snapshot(context: OperationContext?): ArrayList<String> = arrayListOf("s3-public-draft-v1",
        context?.connectionId.orEmpty(), context?.endpoint.orEmpty(), context?.environment?.name.orEmpty(), query, bucketName)
    companion object {
        fun restore(context: OperationContext?, values: List<String>): S3PublicDraft {
            val identity = listOf(context?.connectionId.orEmpty(), context?.endpoint.orEmpty(), context?.environment?.name.orEmpty())
            return if (values.size == 6 && values[0] == "s3-public-draft-v1" && values.subList(1, 4) == identity)
                S3PublicDraft(values[4], values[5]) else S3PublicDraft()
        }
    }
}

@Composable
internal fun rememberS3PublicDraft(context: OperationContext?): MutableState<S3PublicDraft> {
    val saver = remember(context) { Saver<MutableState<S3PublicDraft>, ArrayList<String>>(
        save = { it.value.snapshot(context) }, restore = { mutableStateOf(S3PublicDraft.restore(context, it)) }) }
    return rememberSaveable(context, saver = saver) { mutableStateOf(S3PublicDraft()) }
}

/** A read may update the journal after Close, but must not resurrect a dismissed result. */
internal fun shouldPublishS3Result(visibleIntentId: String?, readIntentId: String,
    currentContext: OperationContext?, readContext: OperationContext): Boolean =
    visibleIntentId == readIntentId && currentContext == readContext

/** A new profile UUID is never rebound to an earlier operation, even if its host matches. */
internal fun unavailableS3Operations(records: List<PersistedOperation>, available: Set<OperationContext>): List<PersistedOperation> =
    pendingS3Operations(records).filter { it.intent.resource.context !in available }

internal fun pendingS3Operations(records: List<PersistedOperation>): List<PersistedOperation> =
    records.filter { it.isPending && it.intent.resource.resourceType == "s3_bucket" &&
        it.intent.resource.context.environment == OperationContext.Environment.S3 }.sortedByDescending { it.updatedAtMillis }
