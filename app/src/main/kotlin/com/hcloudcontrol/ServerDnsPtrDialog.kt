package com.hcloudcontrol

import androidx.compose.runtime.Composable
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Server entry point delegates to the same PTR flow as public IP resources. */
@Composable
internal fun ServerDnsPtrDialog(server: Server, repository: HCloudRepository,
    onDismiss: () -> Unit, onActionCompleted: () -> Unit) {
    ReverseDnsDialog(repository, "server", server.id, server.name,
        readAddresses = { repository.getServer(server.id).publicDnsPointers },
        write = { address, hostname -> repository.changeServerReverseDns(server.id, address, hostname) },
        onDismiss = onDismiss, onCompleted = onActionCompleted)
}
