package com.marketbrief.config

import kotlinx.serialization.Serializable

/**
 * Runtime configuration: everything non-sensitive lives in a YAML file.
 * Secrets are read from the environment, where 1Password's `op run` injects
 * them (see README).
 */
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
