package com.hcloudcontrol.core.domain.model

/** Real Hetzner Cloud and an explicitly marked external test mock only. */
enum class ConnectionKind {
    REAL,
    TEST_MOCK,
}

/** Non-secret routing metadata; credentials remain in encrypted per-profile storage. */
data class HCloudConnectionConfig(val kind: ConnectionKind, val baseUrl: String) {
    companion object {
        const val REAL_BASE_URL = "https://api.hetzner.cloud/v1"
        const val TEST_MOCK_BASE_URL = "https://tst.pumpkin.co.il/hcloud-mock/v1"
        fun real(): HCloudConnectionConfig = HCloudConnectionConfig(ConnectionKind.REAL, REAL_BASE_URL)
        fun testMock(): HCloudConnectionConfig = HCloudConnectionConfig(ConnectionKind.TEST_MOCK, TEST_MOCK_BASE_URL)
    }
}
