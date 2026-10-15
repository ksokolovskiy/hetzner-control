package com.hcloudcontrol.core.security

import java.util.concurrent.atomic.AtomicBoolean

/** Process lifetime, not Activity lifetime. This does not promise protection after disk/process loss. */
internal class PersistenceFailureLatch {
    private val failed = AtomicBoolean(false)
    val blocked: Boolean get() = failed.get()
    fun markFailed() { failed.set(true) }
    fun confirmedExplicitWrite() { failed.set(false) }
}

/** T must contain public profile metadata only, never credentials or exception details. */
internal class ProfilePersistenceGuard<T> {
    private val latch = PersistenceFailureLatch()
    private var previous: T? = null
    val blocked: Boolean get() = latch.blocked
    @Synchronized fun publicSnapshot(): T? = if (blocked) previous else null
    @Synchronized fun publicView(read: () -> T): T = if (blocked) checkNotNull(previous) else read()
    @Synchronized fun <R> credential(read: () -> R): R? = if (blocked) null else read()
    @Synchronized fun checked(before: T, write: () -> Unit) {
        try {
            write()
            latch.confirmedExplicitWrite()
            previous = null
        } catch (_: Exception) {
            if (!blocked) previous = before
            latch.markFailed()
            throw LocalPersistenceException()
        }
    }
}
