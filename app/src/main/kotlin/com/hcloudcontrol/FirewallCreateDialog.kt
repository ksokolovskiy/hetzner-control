package com.hcloudcontrol

import androidx.compose.runtime.Composable
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Legacy entry delegates to the same reviewed empty-firewall workflow. */
@Composable
internal fun FirewallCreateDialog(repository: HCloudRepository, onDismiss: () -> Unit, onCreated: () -> Unit,
    onCreatedId: ((Long) -> Unit)? = null) {
    FirewallMutationDialog(repository, null, FirewallMutationMode.CREATE, onDismiss, onCompleted = { id ->
        if (id != null && onCreatedId != null) onCreatedId(id) else onCreated()
    })
}
