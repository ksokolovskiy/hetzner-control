package com.hcloudcontrol.network

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*

internal enum class NetworkMutationKind { CREATE, ADD_SUBNET, DELETE_SUBNET, ADD_ROUTE, DELETE_ROUTE, ATTACH, DETACH, DELETE }
internal data class NetworkMutationDraft(val kind: NetworkMutationKind, val name: String = "", val range: String = "",
    val subnet: PrivateNetworkSubnetSpec? = null, val route: PrivateNetworkRouteSpec? = null, val serverId: Long? = null) {
    fun identity(): String = kind.name.lowercase() + "_" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(toString().toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
}
internal data class NetworkSnapshot(val network: PrivateNetworkInventory, val members: List<PrivateNetworkMember>)

/** Structured topology only. No implicit subnet allocation, IP assignment or dependent cleanup. */
internal class NetworkTopologyAdapter(private val repository: HCloudRepository,
    private val coordinator: OperationCoordinator, private val currentContext: () -> OperationContext?, private val allowed: () -> Boolean) {
    suspend fun readSnapshot(id: Long): NetworkSnapshot {
        val network = repository.getPrivateNetwork(id)
        val members = repository.getPrivateNetworkMembers(id)
        return NetworkSnapshot(network.copy(serverIds = network.serverIds.sorted(),
            subnets = network.subnets.sortedWith(compareBy({ it.ipRange }, { it.type }, { it.networkZone })),
            routes = network.routes.sortedWith(compareBy({ it.destination }, { it.gateway }))),
            members.sortedWith(compareBy({ it.type }, { it.id })))
    }

    suspend fun submit(review: OperationReview, draft: NetworkMutationDraft, expected: NetworkSnapshot?,
        onSending: suspend () -> Unit = {}): PersistedOperation {
        val intent = review.intent
        require(intent.resource.resourceType == "network" && intent.action == draft.identity())
        check(currentContext() == intent.resource.context && allowed())
        if (draft.kind == NetworkMutationKind.CREATE) {
            require(intent.resource.resourceId == null && draft.name.isNotBlank() && draft.name.length <= 128 && validPrivateNetworkCidr(draft.range))
            repository.getPrivateNetworks() // Read access and original project binding checked before any write.
        } else {
            val id = requireNotNull(intent.resource.resourceId).toLong()
            val snapshot = readSnapshot(id)
            check(snapshot == expected)
            val network = snapshot.network
            when (draft.kind) {
                NetworkMutationKind.ADD_SUBNET -> {
                    val zones = repository.getLocations().mapNotNull { it.networkZone }.toSet()
                    check(validCloudSubnet(network, requireNotNull(draft.subnet), zones))
                }
                NetworkMutationKind.DELETE_SUBNET -> {
                    val subnet = requireNotNull(draft.subnet)
                    check(network.subnets.any { it.ipRange == subnet.ipRange && it.networkZone == subnet.networkZone })
                    val range = requireNotNull(ipv4Range(subnet.ipRange))
                    check(snapshot.members.none { member -> ipv4Address(member.ip)?.let(range::contains) == true })
                }
                NetworkMutationKind.ADD_ROUTE -> check(validNetworkRoute(network, requireNotNull(draft.route)))
                NetworkMutationKind.DELETE_ROUTE -> {
                    val route = requireNotNull(draft.route)
                    check(network.routes.any { it.destination == route.destination && it.gateway == route.gateway })
                }
                NetworkMutationKind.ATTACH -> {
                    val server = repository.getServer(requireNotNull(draft.serverId))
                    check(eligibleNetworkMember(network, server) && snapshot.members.none { it.type == "server" && it.id == server.id })
                }
                NetworkMutationKind.DETACH -> check(snapshot.members.any { it.type == "server" && it.id == draft.serverId })
                NetworkMutationKind.DELETE -> check(network.protectionKnown && !network.deleteProtected)
                NetworkMutationKind.CREATE -> error("Handled separately")
            }
        }
        coordinator.beginSubmission(review, checkNotNull(currentContext()), intent.revision, allowed())
        try {
            onSending()
            check(currentContext() == intent.resource.context && allowed())
            val id = intent.resource.resourceId?.toLong()
            var createdId: String? = null
            val action = when (draft.kind) {
                NetworkMutationKind.CREATE -> repository.createPrivateNetwork(CreatePrivateNetworkSpec(draft.name.trim(), draft.range)).let {
                    createdId = it.network.id.toString(); it.action
                }
                NetworkMutationKind.ADD_SUBNET -> repository.addPrivateNetworkSubnet(requireNotNull(id), requireNotNull(draft.subnet))
                NetworkMutationKind.DELETE_SUBNET -> repository.deletePrivateNetworkSubnet(requireNotNull(id), requireNotNull(draft.subnet))
                NetworkMutationKind.ADD_ROUTE -> repository.addPrivateNetworkRoute(requireNotNull(id), requireNotNull(draft.route))
                NetworkMutationKind.DELETE_ROUTE -> repository.deletePrivateNetworkRoute(requireNotNull(id), requireNotNull(draft.route))
                NetworkMutationKind.ATTACH -> repository.attachServerToPrivateNetwork(requireNotNull(id), requireNotNull(draft.serverId))
                NetworkMutationKind.DETACH -> repository.detachServerFromPrivateNetwork(requireNotNull(id), requireNotNull(draft.serverId))
                NetworkMutationKind.DELETE -> repository.deletePrivateNetwork(requireNotNull(id))
            }
            coordinator.accepted(intent.intentId, action?.id, createdId)
            if (action != null) observe(intent.intentId, action)
            else if (draft.kind in setOf(NetworkMutationKind.CREATE, NetworkMutationKind.DELETE)) coordinator.succeeded(intent.intentId)
            else coordinator.outcomeUnknown(intent.intentId)
        } catch (error: HCloudApiException) {
            val status = error.failure.httpStatus
            if (coordinator.get(intent.intentId)?.stage == OperationStage.SENDING && status in 400..499 && status != 408) {
                coordinator.failed(intent.intentId, OperationDiagnostic(
                    if (status in setOf(401, 403)) OperationDiagnostic.Code.PROVIDER_DENIED else OperationDiagnostic.Code.PROVIDER_FAILED, status))
            } else coordinator.outcomeUnknown(intent.intentId)
        } catch (_: Throwable) { coordinator.outcomeUnknown(intent.intentId) }
        return checkNotNull(coordinator.get(intent.intentId))
    }

    suspend fun reconcile(record: PersistedOperation): PersistedOperation {
        check(currentContext() == record.intent.resource.context)
        record.providerActionId?.let { return observe(record.intent.intentId, repository.getAction(it)) }
        if (record.intent.action.startsWith("delete_") && record.intent.action.substringBeforeLast('_') == "delete") {
            try { repository.getPrivateNetwork(requireNotNull(record.intent.resource.resourceId).toLong()) }
            catch (error: HCloudApiException) {
                if (error.failure.httpStatus == 404 && error.failure.code == "not_found") return coordinator.reconcile(record.intent.intentId,
                    OperationCoordinator.Reconciliation.ExistingResourceEffect(record.intent.resource, true))
                throw error
            }
        }
        return coordinator.reconcile(record.intent.intentId, OperationCoordinator.Reconciliation.Ambiguous)
    }

    private fun observe(id: String, action: CloudAction): PersistedOperation = coordinator.reconcile(id,
        OperationCoordinator.Reconciliation.ActionStatus(action.id, when (action.status.lowercase()) {
            "success" -> OperationCoordinator.Reconciliation.Status.SUCCEEDED
            "error" -> OperationCoordinator.Reconciliation.Status.FAILED
            else -> OperationCoordinator.Reconciliation.Status.RUNNING
        }))
}
