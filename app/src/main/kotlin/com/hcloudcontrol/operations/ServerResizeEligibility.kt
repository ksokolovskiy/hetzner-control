package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerTypeOption

/** Official constraints: same architecture/storage kind and no shrinking of the actual boot disk. */
fun serverResizeEligible(server: Server, source: ServerTypeOption?, target: ServerTypeOption): Boolean {
    val architecture = server.hardware?.architecture ?: source?.architecture ?: return false
    val storage = server.hardware?.storageType ?: source?.storageType ?: return false
    val actualDisk = server.primaryDiskSizeGb ?: return false
    if (!actualDisk.isFinite() || actualDisk <= 0) return false
    return target.name != server.serverType && server.location in target.availableLocations &&
        target.architecture == architecture && target.storageType == storage && target.diskGb.toDouble() >= actualDisk
}
