package com.marketbrief

import com.marketbrief.adapters.llm.OpenAiCompatibleConfig
import com.marketbrief.config.ApplicationConfig

/**
 * Composition-root boundary: only `main` may turn runtime config into an
 * adapter config, so the config package stays adapter-agnostic (ARCHITECTURE §1).
 */
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
