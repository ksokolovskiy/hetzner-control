package com.hcloudcontrol.connections

import com.hcloudcontrol.core.network.HCloudRepositoryFactory
import com.hcloudcontrol.core.security.HCloudTokenStore

/** Uses the existing HTTP/logging boundary, never another network client or persisted token. */
fun verifiedCloudConnectionCoordinator(tokenStore: HCloudTokenStore): VerifiedCloudConnectionCoordinator =
    VerifiedCloudConnectionCoordinator(
        checkRead = { token, config ->
            HCloudRepositoryFactory.create(tokenProvider = { token }, configProvider = { config }).getServers()
        },
        secureSave = { name, token, config -> tokenStore.saveConnection(name, token, config).id },
    )
