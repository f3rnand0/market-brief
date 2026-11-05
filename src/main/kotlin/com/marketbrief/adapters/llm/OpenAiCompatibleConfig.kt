package com.marketbrief.adapters.llm

data class OpenAiCompatibleConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val requestTimeoutSeconds: Long,
    val maxRetries: Int,
    val inputCostPerMillionUsd: Double? = null,
    val outputCostPerMillionUsd: Double? = null,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(requestTimeoutSeconds > 0) { "requestTimeoutSeconds must be positive" }
        require(maxRetries >= 0) { "maxRetries must not be negative" }
        require((inputCostPerMillionUsd == null) == (outputCostPerMillionUsd == null)) {
            "input and output token rates must both be set or both be absent"
        }
        require(inputCostPerMillionUsd == null || (inputCostPerMillionUsd.isFinite() && inputCostPerMillionUsd >= 0)) {
            "inputCostPerMillionUsd must be finite and non-negative"
        }
        require(
            outputCostPerMillionUsd == null || (outputCostPerMillionUsd.isFinite() && outputCostPerMillionUsd >= 0),
        ) {
            "outputCostPerMillionUsd must be finite and non-negative"
        }
    }

    override fun toString(): String = "OpenAiCompatibleConfig(baseUrl=$baseUrl, model=$model, apiKey=***, " +
        "requestTimeoutSeconds=$requestTimeoutSeconds, maxRetries=$maxRetries)"
}
