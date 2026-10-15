package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import java.net.InetAddress

internal data class FirewallTcpDraft(val port: String, val sources: String, val description: String) {
    fun rule(): FirewallRuleSpec? {
        val parts = port.trim().split('-')
        if (parts.size !in 1..2 || parts.any { part -> part.toIntOrNull()?.let { it in 1..65535 } != true }) return null
        if (parts.size == 2 && parts[0].toInt() > parts[1].toInt()) return null
        val value = parts.joinToString("-") { it.toInt().toString() }
        val addresses = sources.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (addresses.isEmpty() || addresses.size > 100 || addresses.any { !validFirewallSource(it) } || description.length > 255) return null
        val cidrs = addresses.map { if ('/' in it) it else "$it/${if (':' in it) 128 else 32}" }
        return FirewallRuleSpec("in", "tcp", value, sourceIps = cidrs,
            description = description.trim().takeIf { it.isNotEmpty() })
    }
    val broad: Boolean get() = rule()?.sourceIps?.any { it.endsWith("/0") } == true
}

/** Literal parsing only: a user's source must never trigger DNS resolution. */
internal fun validFirewallSource(value: String): Boolean {
    val pieces = value.split('/')
    if (pieces.size !in 1..2) return false
    val address = pieces[0]
    val ipv6 = ':' in address
    val valid = if (ipv6) {
        address.matches(Regex("[0-9a-fA-F:.]+")) && runCatching { InetAddress.getByName(address) }.isSuccess
    } else {
        val octets = address.split('.')
        octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 3 &&
            (it.length == 1 || !it.startsWith('0')) && it.toIntOrNull()?.let { number -> number in 0..255 } == true }
    }
    return valid && (pieces.size == 1 || pieces[1].toIntOrNull()?.let { it in 0..(if (ipv6) 128 else 32) } == true)
}

internal sealed interface FirewallChange {
    data class Create(val name: String) : FirewallChange
    data class AddTcp(val before: FirewallInventory, val rule: FirewallRuleSpec) : FirewallChange
    data class Target(val before: FirewallInventory, val serverId: Long, val serverName: String, val remove: Boolean) : FirewallChange
    data class Delete(val before: FirewallInventory) : FirewallChange
}
internal data class FirewallMutationResult(val actions: List<CloudAction> = emptyList(), val createdId: Long? = null)

/** Official /firewalls actions only. Rules/attachments are compared before any write. */
internal class FirewallMutationAdapter(private val repository: HCloudRepository) {
    suspend fun validate(change: FirewallChange) {
        if (change is FirewallChange.Create) {
            require(change.name.isNotBlank() && change.name.length <= 128)
            check(repository.getFirewalls().none { it.name == change.name })
            return
        }
        val before = when (change) {
            is FirewallChange.AddTcp -> change.before
            is FirewallChange.Target -> change.before
            is FirewallChange.Delete -> change.before
            else -> error("Unsupported firewall operation")
        }
        val current = repository.getFirewall(before.id)
        check(current == before) { "Firewall changed; review again" }
        when (change) {
            is FirewallChange.AddTcp -> {
                require(change.rule.direction == "in" && change.rule.protocol == "tcp" && change.rule.destinationIps.isEmpty())
                require(FirewallTcpDraft(change.rule.port.orEmpty(), change.rule.sourceIps.joinToString(","), change.rule.description.orEmpty()).rule() == change.rule)
                check(current.rules.size < 50)
            }
            is FirewallChange.Delete -> check(current.appliedTo.isEmpty()) // API refuses resource_in_use; never auto-detach.
            is FirewallChange.Target -> {
                val server = repository.getServer(change.serverId)
                check(server.id == change.serverId && server.name == change.serverName)
                check(server.ipv4Address != null || server.networkDevices.ipv4Address != null || server.networkDevices.ipv6Network != null)
                val direct = current.appliedTo.any { it.serverId == server.id && it.type == "server" }
                val selector = current.appliedTo.filter { it.type == "label_selector" }
                    .flatMap { it.appliedToResources }.any { it.serverId == server.id }
                check(if (change.remove) direct && !selector else !direct && !selector)
            }
            else -> Unit
        }
    }
    suspend fun execute(change: FirewallChange): FirewallMutationResult = when (change) {
        is FirewallChange.Create -> repository.createFirewall(CreateFirewallSpec(change.name)).let { FirewallMutationResult(it.actions, it.firewall.id) }
        is FirewallChange.AddTcp -> FirewallMutationResult(listOf(repository.setFirewallRules(change.before.id,
            change.before.rules.map { FirewallRuleSpec(it.direction, it.protocol, it.port, it.sourceIps, it.destinationIps, it.description) } + change.rule)))
        is FirewallChange.Target -> FirewallMutationResult(if (change.remove) repository.removeFirewallFromServer(change.before.id, change.serverId)
            else repository.applyFirewallToServer(change.before.id, change.serverId))
        is FirewallChange.Delete -> { repository.deleteFirewall(change.before.id); FirewallMutationResult() }
    }
    suspend fun deleted(id: Long): Boolean = try { repository.getFirewall(id); false } catch (error: HCloudApiException) {
        if (error.failure.httpStatus == 404) true else throw error
    }
}
