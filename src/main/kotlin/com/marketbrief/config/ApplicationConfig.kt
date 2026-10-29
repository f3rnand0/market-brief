package com.marketbrief.config

import com.marketbrief.adapters.llm.OpenAiCompatibleConfig
import kotlinx.serialization.Serializable

/**
 * Runtime configuration: everything non-sensitive lives in a YAML file.
 * Secrets are read from the environment, where 1Password's `op run` injects
 * them (see README).
 */
@Serializable
data class ApplicationConfig(val llm: LlmConfig) {

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

fun ApplicationConfig.toOpenAiCompatibleConfig(env: Map<String, String> = System.getenv()): OpenAiCompatibleConfig {
    val apiKey = env[llm.apiKeyEnv]?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException(
            "Missing LLM API key: environment variable ${llm.apiKeyEnv} is not set. " +
                "Inject it with 1Password: op run --env-file=config/secrets.env -- <command>",
        )
    return OpenAiCompatibleConfig(
        baseUrl = llm.baseUrl,
        model = llm.model,
        apiKey = apiKey,
        requestTimeoutSeconds = llm.requestTimeoutSeconds,
        maxRetries = llm.maxRetries,
    )
}
