package com.hcloudcontrol

import androidx.compose.runtime.Composable
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Legacy entry aliases the canonical ID-owned detail; it has no separate request path. */
@Composable
internal fun NetworkDetailDialog(network: PrivateNetworkInventory, repository: HCloudRepository, onDismiss: () -> Unit) =
    PrivateNetworkDetailScreen(network.id, repository, onBack = onDismiss)
