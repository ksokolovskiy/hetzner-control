package com.hcloudcontrol.core.domain.model

/** The product intentionally supports only the current 64-bit Linux rescue environment. */
enum class RescueSystemType(val apiValue: String) { LINUX64("linux64") }

data class EnableRescueSpec(
    val type: RescueSystemType = RescueSystemType.LINUX64,
    val sshKeyIds: List<Long> = emptyList(),
) {
    init {
        require(sshKeyIds.all { it > 0 }) { "Rescue SSH key IDs must be positive" }
        require(sshKeyIds.distinct().size == sshKeyIds.size) { "Rescue SSH key IDs must not repeat" }
    }
}

/**
 * In-memory, consumable secret returned by Hetzner once. It is deliberately neither serializable
 * nor a data class; callers must not retain it in SavedState, logs, analytics, or persistent storage.
 */
class OneTimeRootPassword private constructor(private var secret: String?) {
    fun revealOnce(): String? = secret.also { secret = null }
    override fun toString(): String = "OneTimeRootPassword([REDACTED])"

    companion object {
        /** Network boundary only; never persist or log the supplied response value. */
        fun fromApiResponse(value: String): OneTimeRootPassword = OneTimeRootPassword(value)
    }
}

data class ResetRootPasswordResult(val action: CloudAction, val password: OneTimeRootPassword)
data class EnableRescueResult(val action: CloudAction, val password: OneTimeRootPassword)
