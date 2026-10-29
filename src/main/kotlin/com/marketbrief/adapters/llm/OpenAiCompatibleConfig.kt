package com.marketbrief.adapters.llm

data class OpenAiCompatibleConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val requestTimeoutSeconds: Long,
    val maxRetries: Int,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(requestTimeoutSeconds > 0) { "requestTimeoutSeconds must be positive" }
        require(maxRetries >= 0) { "maxRetries must not be negative" }
    }

    override fun toString(): String = "OpenAiCompatibleConfig(baseUrl=$baseUrl, model=$model, apiKey=***, " +
        "requestTimeoutSeconds=$requestTimeoutSeconds, maxRetries=$maxRetries)"
}
