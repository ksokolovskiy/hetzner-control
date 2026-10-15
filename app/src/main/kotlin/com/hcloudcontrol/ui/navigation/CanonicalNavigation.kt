package com.hcloudcontrol.ui.navigation

import java.net.URI

/** Stable identity only: display names and credentials must never enter navigation state. */
data class ResourceContext(val ownerId: String, val endpoint: String, val kind: ContextKind) {
    init {
        require(ownerId.isNotBlank())
        val address = URI(endpoint)
        require(address.scheme in setOf("https", "http") && !address.host.isNullOrBlank())
        require(address.userInfo == null && address.query == null && address.fragment == null)
    }
}

enum class ContextKind { CLOUD, S3 }
enum class RootDestination { SERVERS, CREATE, STORAGE, NETWORK, MORE }
enum class DestinationKind { ROOT, LIST, DETAIL, FORM }
enum class ResourceType {
    SERVER, SERVER_TYPE, LOCATION, IMAGE, VOLUME, STORAGE_BOX, BUCKET, NETWORK, FIREWALL,
    PRIMARY_IP, FLOATING_IP, PUBLIC_IPS, LOAD_BALANCER, LOAD_BALANCER_TYPE, CERTIFICATE,
    SSH_KEY, PLACEMENT_GROUP, DNS_ZONE, PROJECT, SETTINGS, PRICING, S3_CONNECTIONS,
}

/** All aliases construct the same route; the origin is intentionally not part of identity. */
data class CanonicalDestination(
    val root: RootDestination,
    val kind: DestinationKind = DestinationKind.ROOT,
    val context: ResourceContext? = null,
    val resourceType: ResourceType? = null,
    val resourceId: String? = null,
) {
    init {
        require(kind == DestinationKind.ROOT || context != null ||
            (kind == DestinationKind.LIST && resourceType == ResourceType.S3_CONNECTIONS))
        require(kind != DestinationKind.DETAIL || !resourceId.isNullOrBlank())
        require(kind == DestinationKind.ROOT || resourceType != null)
    }
}

data class NavigationState(val stack: List<CanonicalDestination> = listOf(CanonicalDestination(RootDestination.SERVERS))) {
    init {
        require(stack.isNotEmpty())
        require(stack.first().kind == DestinationKind.ROOT)
        require(stack.drop(1).none { it.kind == DestinationKind.ROOT })
    }
    val current: CanonicalDestination get() = stack.last()
    val showBottomNavigation: Boolean get() = stack.size == 1 && current.kind == DestinationKind.ROOT

    fun selectRoot(root: RootDestination) = NavigationState(listOf(CanonicalDestination(root)))

    /** Repeated taps or aliases return to the existing canonical detail, without stacking copies. */
    fun open(destination: CanonicalDestination): NavigationState {
        if (destination.kind == DestinationKind.ROOT) return selectRoot(destination.root)
        val existing = stack.indexOf(destination)
        return if (existing >= 0) copy(stack = stack.take(existing + 1)) else copy(stack = stack + destination)
    }

    fun up(): NavigationState = if (stack.size > 1) copy(stack = stack.dropLast(1)) else this

    /** Flat saveable values survive recreation without persisting provider objects or secrets. */
    fun snapshot(): ArrayList<String> = ArrayList<String>().also { result ->
        stack.forEach { destination ->
            result.addAll(listOf(
                destination.root.name, destination.kind.name,
                destination.context?.ownerId.orEmpty(), destination.context?.endpoint.orEmpty(),
                destination.context?.kind?.name.orEmpty(), destination.resourceType?.name.orEmpty(),
                destination.resourceId.orEmpty(),
            ))
        }
    }

    /** Pending operations live outside this model and keep their immutable original context. */
    fun switchCloudContext(context: ResourceContext): NavigationState {
        require(context.kind == ContextKind.CLOUD)
        // An S3 connection is independent of the active Cloud project.
        if (current.context?.kind == ContextKind.S3 || current.resourceType == ResourceType.S3_CONNECTIONS) {
            return copy(stack = listOf(stack.first()) + stack.drop(1).filter {
                it.context?.kind == ContextKind.S3 || it.resourceType == ResourceType.S3_CONNECTIONS
            })
        }
        return selectRoot(current.root)
    }

    companion object {
        /** Corrupt or obsolete restoration must fall back safely, never resubmit an operation. */
        fun restore(snapshot: List<String>): NavigationState = runCatching {
            require(snapshot.isNotEmpty() && snapshot.size % 7 == 0)
            NavigationState(snapshot.chunked(7).map { values ->
                CanonicalDestination(
                    root = RootDestination.valueOf(values[0]),
                    kind = DestinationKind.valueOf(values[1]),
                    context = if (values[4].isEmpty()) null else ResourceContext(
                        values[2], values[3], ContextKind.valueOf(values[4]),
                    ),
                    resourceType = values[5].takeIf(String::isNotEmpty)?.let(ResourceType::valueOf),
                    resourceId = values[6].takeIf(String::isNotEmpty),
                )
            })
        }.getOrElse { NavigationState() }
    }
}

data class BackEnvironment(
    val keyboardVisible: Boolean = false,
    val overlayVisible: Boolean = false,
    val dirtyForm: Boolean = false,
)

enum class BackDecision { HIDE_KEYBOARD, CLOSE_OVERLAY, ASK_RETAIN_OR_DISCARD, POP, OPEN_SERVERS, EXIT }

fun NavigationState.decideBack(environment: BackEnvironment = BackEnvironment()): BackDecision = when {
    environment.keyboardVisible -> BackDecision.HIDE_KEYBOARD
    environment.overlayVisible -> BackDecision.CLOSE_OVERLAY
    environment.dirtyForm -> BackDecision.ASK_RETAIN_OR_DISCARD
    stack.size > 1 -> BackDecision.POP
    current.root != RootDestination.SERVERS -> BackDecision.OPEN_SERVERS
    else -> BackDecision.EXIT
}
