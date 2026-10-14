package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig

/**
 * A validated, non-persistent request from the connection form.  Keeping this
 * decision outside the composable means the primary action has one path for a
 * pointer tap, keyboard activation and accessibility activation.
 */
internal data class OnboardingConnectionRequest(
    val displayName: String,
    val token: String,
    val config: HCloudConnectionConfig,
)

internal fun onboardingConnectionRequest(
    connectionKind: ConnectionKind,
    projectName: String,
    testProfileName: String,
    token: String,
): OnboardingConnectionRequest? {
    if (token.isBlank()) return null
    return if (connectionKind == ConnectionKind.TEST_MOCK) {
        OnboardingConnectionRequest(testProfileName, token, HCloudConnectionConfig.testMock())
    } else {
        projectName.takeIf(String::isNotBlank)?.let {
            OnboardingConnectionRequest(it, token, HCloudConnectionConfig.real())
        }
    }
}
