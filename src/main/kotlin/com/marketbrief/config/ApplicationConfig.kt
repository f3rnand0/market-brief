package com.marketbrief.config

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationConfig(val llm: LlmConfig, val storage: StorageConfig = StorageConfig()) {

    @Serializable
    data class StorageConfig(val reportsDir: String = "data/reports") {
        init {
            require(reportsDir.isNotBlank()) { "storage.reportsDir must not be blank" }
        }
    }

    @Serializable
    data class LlmConfig(
        val baseUrl: String,
        val model: String,
        val apiKeyEnv: String = "LLM_API_KEY",
        val requestTimeoutSeconds: Long = 120,
        val maxRetries: Int = 2,
    ) {
        init {
            require(apiKeyEnv.isNotBlank()) { "llm.apiKeyEnv must not be blank" }
        }
    }
}
