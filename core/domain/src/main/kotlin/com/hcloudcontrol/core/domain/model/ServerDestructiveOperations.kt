package com.hcloudcontrol.core.domain.model

/**
 * Rebuild deliberately requires a caller-selected image.  The client must never infer an
 * operating-system image because this operation overwrites the server's system disk.
 */
data class ServerRebuildSpec(
    val image: String,
    val sshKeyIds: List<Long> = emptyList(),
) {
    init {
        require(image.isNotBlank()) { "A rebuild image must be selected" }
        require(sshKeyIds.all { it > 0 }) { "SSH key IDs must be positive" }
    }
}

/** A password is returned only when no SSH key was supplied; it is intentionally in-memory. */
data class RebuiltServerResult(
    val action: CloudAction,
    val password: OneTimeRootPassword?,
)
