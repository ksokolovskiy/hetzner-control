package com.hcloudcontrol.core.domain.model

/** The account associated with the current Hetzner Cloud API token. */
data class Profile(
    val id: Long,
    val name: String,
    val email: String,
)
