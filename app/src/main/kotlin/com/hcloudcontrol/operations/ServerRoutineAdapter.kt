package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.CancellationException

enum class ServerRoutineAction {
    POWER_ON, SHUTDOWN, HARD_OFF, REBOOT, HARD_RESET, BACKUPS_ENABLE, BACKUPS_DISABLE, SNAPSHOT,
    ISO_ATTACH, ISO_DETACH, METADATA;
    fun identity(draft: ServerRoutineDraft): String = name.lowercase() +
        when (this) {
            ISO_ATTACH -> "_${requireNotNull(draft.iso).id}"
            METADATA -> "_" + java.security.MessageDigest.getInstance("SHA-256").digest(
                requireNotNull(draft.metadata).let { patch ->
                    listOf(patch.name, patch.labels?.toSortedMap()?.entries?.joinToString("\u0000") { "${it.key}=${it.value}" }, patch.placementGroupId?.toString()).joinToString("\u0001")
                }.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
            else -> ""
        }
}
data class ServerRoutineDraft(val snapshotDescription: String = "", val iso: IsoOption? = null,
    val metadata: ServerConfigurationPatch? = null)

fun isoReviewIdentity(iso: IsoOption): String = "${iso.name} · #${iso.id}"

fun serverRoutineEligible(server: Server, action: ServerRoutineAction): Boolean = when (action) {
    ServerRoutineAction.POWER_ON -> server.status == ServerStatus.OFF
    ServerRoutineAction.SHUTDOWN, ServerRoutineAction.HARD_OFF, ServerRoutineAction.REBOOT, ServerRoutineAction.HARD_RESET -> server.status == ServerStatus.RUNNING
    ServerRoutineAction.BACKUPS_ENABLE -> server.backupWindow == null
    ServerRoutineAction.BACKUPS_DISABLE -> server.backupWindow != null
    ServerRoutineAction.SNAPSHOT -> server.status in setOf(ServerStatus.RUNNING, ServerStatus.OFF)
    ServerRoutineAction.ISO_ATTACH -> server.attachedIso == null
    ServerRoutineAction.ISO_DETACH -> server.attachedIso != null
    ServerRoutineAction.METADATA -> true
}

/** Typed feature eligibility adapter; every transition and poll remains in shared operations. */
class ServerRoutineAdapter(private val repository: HCloudRepository,
    private val currentContext: () -> OperationContext?, private val changesAllowed: () -> Boolean) {
    suspend fun submit(session: OperationSession, review: OperationReview, expected: Server,
        action: ServerRoutineAction, draft: ServerRoutineDraft, onSending: suspend () -> Unit = {}) {
        val identity = review.intent.resource
        require(identity.resourceType == "server" && identity.resourceId == expected.id.toString())
        require(review.intent.action == action.identity(draft))
        check(currentContext() == identity.context && changesAllowed())
        val fresh = repository.getServer(expected.id)
        check(fresh.id == expected.id && fresh.name == expected.name && serverRoutineEligible(fresh, action))
        when (action) {
            ServerRoutineAction.POWER_ON, ServerRoutineAction.SHUTDOWN, ServerRoutineAction.HARD_OFF,
            ServerRoutineAction.REBOOT, ServerRoutineAction.HARD_RESET -> check(fresh.status == expected.status)
            ServerRoutineAction.BACKUPS_ENABLE, ServerRoutineAction.BACKUPS_DISABLE -> check(fresh.backupWindow == expected.backupWindow)
            ServerRoutineAction.ISO_ATTACH, ServerRoutineAction.ISO_DETACH -> check(fresh.attachedIso?.id == expected.attachedIso?.id)
            ServerRoutineAction.METADATA -> {
                check(fresh.labels == expected.labels && fresh.placementGroupId == expected.placementGroupId)
                val patch = requireNotNull(draft.metadata)
                require(patch.placementGroupId == null)
                require(patch.name?.isNotBlank() != false && patch.labels?.all { validResourceLabel(it.key, it.value) } != false)
                patch.placementGroupId?.let { id -> check(repository.getPlacementGroups().any { it.id == id }) }
            }
            ServerRoutineAction.SNAPSHOT -> require(draft.snapshotDescription.isNotBlank())
        }
        if (action == ServerRoutineAction.ISO_ATTACH) {
            val selected = requireNotNull(draft.iso)
            val actual = repository.getIso(selected.id)
            check(actual == selected)
            val arch = fresh.hardware?.architecture ?: repository.getServerTypes().firstOrNull { it.name == fresh.serverType }?.architecture
            check(arch != null && actual.architecture == arch)
        }
        session.beginReviewed(review, changesAllowed(), currentContext())
        try {
            onSending()
            // These callbacks are dynamic; closing the presentation does not grant provider rights.
            check(currentContext() == identity.context && changesAllowed())
            if (action == ServerRoutineAction.METADATA) {
                val updated = repository.updateServerConfiguration(expected.id, requireNotNull(draft.metadata))
                check(updated.id == expected.id)
                session.succeed()
                return
            }
            val result = when (action) {
                ServerRoutineAction.POWER_ON -> repository.powerOn(expected.id)
                ServerRoutineAction.SHUTDOWN -> repository.shutdown(expected.id)
                ServerRoutineAction.HARD_OFF -> repository.powerOff(expected.id)
                ServerRoutineAction.REBOOT -> repository.reboot(expected.id)
                ServerRoutineAction.HARD_RESET -> repository.reset(expected.id)
                ServerRoutineAction.BACKUPS_ENABLE -> repository.enableBackups(expected.id)
                ServerRoutineAction.BACKUPS_DISABLE -> repository.disableBackups(expected.id)
                ServerRoutineAction.SNAPSHOT -> repository.createSnapshot(expected.id, draft.snapshotDescription.trim())
                ServerRoutineAction.ISO_ATTACH -> requireNotNull(draft.iso).let { repository.attachIso(expected.id, IsoSelection(it.id, it.name)) }
                ServerRoutineAction.ISO_DETACH -> repository.detachIso(expected.id)
                ServerRoutineAction.METADATA -> error("Handled above")
            }
            session.accepted(result)
            session.observeOriginalAction(result)
        } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
          catch (failure: Throwable) { session.rejectOrUnknown(failure) }
    }

    suspend fun reconcile(session: OperationSession, action: ServerRoutineAction, draft: ServerRoutineDraft) {
        val record = requireNotNull(session.record)
        check(currentContext() == record.intent.resource.context)
        check(record.intent.action == action.identity(draft))
        if (record.originalActionIds.isNotEmpty()) { awaitOriginalOperationActions(session, repository); return }
        val fresh = repository.getServer(requireNotNull(record.intent.resource.resourceId).toLong())
        val confirmed = when (action) {
            ServerRoutineAction.POWER_ON -> fresh.status == ServerStatus.RUNNING
            ServerRoutineAction.SHUTDOWN, ServerRoutineAction.HARD_OFF -> fresh.status == ServerStatus.OFF
            ServerRoutineAction.BACKUPS_ENABLE -> fresh.backupWindow != null
            ServerRoutineAction.BACKUPS_DISABLE -> fresh.backupWindow == null
            ServerRoutineAction.ISO_ATTACH -> fresh.attachedIso?.id == draft.iso?.id && draft.iso != null
            ServerRoutineAction.ISO_DETACH -> fresh.attachedIso == null
            ServerRoutineAction.METADATA -> draft.metadata?.let { (it.name == null || fresh.name == it.name) &&
                (it.labels == null || fresh.labels == it.labels) && (it.placementGroupId == null || fresh.placementGroupId == it.placementGroupId) } == true
            ServerRoutineAction.REBOOT, ServerRoutineAction.HARD_RESET, ServerRoutineAction.SNAPSHOT -> false
        }
        session.reconcileExistingEffect(confirmed)
    }
}

/** Official Cloud OpenAPI label rules. Empty values are valid; reserved provider prefix is not. */
fun validResourceLabel(key: String, value: String): Boolean {
    val namePattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,61}[A-Za-z0-9])?")
    val parts = key.split('/')
    if (parts.size !in 1..2 || !parts.last().matches(namePattern)) return false
    if (value.isNotEmpty() && !value.matches(namePattern)) return false
    if (parts.size == 2) {
        val prefix = parts.first()
        if (prefix.length !in 1..253 || prefix.equals("hetzner.cloud", true)) return false
        if (prefix.split('.').any { it.length !in 1..63 || !it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) }) return false
    }
    return true
}
fun parseResourceLabels(text: String): Map<String, String>? = buildMap {
    text.lineSequence().filter(String::isNotBlank).forEach { line ->
        val split = line.indexOf('=')
        if (split <= 0) return null
        val key = line.substring(0, split).trim(); val value = line.substring(split + 1).trim()
        if (!validResourceLabel(key, value) || containsKey(key)) return null
        put(key, value)
    }
}
