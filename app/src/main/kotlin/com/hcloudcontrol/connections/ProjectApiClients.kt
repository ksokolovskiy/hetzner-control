package com.hcloudcontrol.connections

import com.hcloudcontrol.core.domain.model.DnsScope
import com.hcloudcontrol.core.domain.repository.*
import com.hcloudcontrol.core.network.*
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.core.security.HCloudTokenStore

/** No credential is copied into composition/saved state; lookup is pinned to immutable owner ID. */
internal class PinnedCloudConnection(private val store: HCloudTokenStore, val connection: CloudProjectConnection) {
    fun token(): String? = store.tokenForConnection(connection.id)
    val config get() = connection.config
    override fun toString(): String = "PinnedCloudConnection(credentials=redacted)"
}

internal class ProjectApiClients(binding: PinnedCloudConnection) {
    val cloud: HCloudRepository = HCloudRepositoryFactory.create(binding::token) { binding.config }
    val storageBoxes: StorageBoxRepository = StorageBoxRepositoryFactory.create(binding::token)
    val billing: BillingRateCardRepository = BillingRateCardRepositoryFactory.create(binding::token) { binding.config }
    val serverMetrics: ServerMetricsRepository = ServerMetricsRepositoryFactory.create(binding::token) { binding.config }
    val loadBalancerMetrics: LoadBalancerMetricsRepository = LoadBalancerMetricsRepositoryFactory.create(binding::token) { binding.config }
    val dns: DnsRepository = DnsRepositoryFactory.create(DnsScope.acknowledgeReadOnly(), binding::token) { binding.config }
}
