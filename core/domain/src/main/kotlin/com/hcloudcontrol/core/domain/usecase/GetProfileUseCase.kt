package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.Profile
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class GetProfileUseCase(private val repository: HCloudRepository) {
    suspend operator fun invoke(): Profile = repository.getProfile()
}
