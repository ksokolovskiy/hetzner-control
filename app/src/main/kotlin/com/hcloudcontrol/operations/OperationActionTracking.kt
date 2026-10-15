package com.hcloudcontrol.operations

import com.hcloudcontrol.awaitCloudAction
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Aggregate original action evidence; one successful primary action never completes the bundle. */
suspend fun awaitOriginalOperationActions(session: OperationSession, repository: HCloudRepository,
    initialActions: List<CloudAction>? = null, onProgress: (String) -> Unit = {}) {
    val ids = checkNotNull(session.record).originalActionIds
    check(ids.isNotEmpty()) { "No action identity available; outcome remains unknown" }
    val actions = (initialActions ?: ids.map { repository.getAction(it) }).distinctBy { it.id }
    check(actions.map { it.id }.toSet() == ids.toSet()) { "Original action evidence changed" }
    val observed = actions.associateBy { it.id }.toMutableMap()
    session.observeOriginalActions(observed.values.toList())
    actions.forEach { action ->
        awaitCloudAction(action, { id -> repository.getAction(id).also {
            observed[id] = it
            session.observeOriginalActions(observed.values.toList())
        } }, onProgress)
    }
}
