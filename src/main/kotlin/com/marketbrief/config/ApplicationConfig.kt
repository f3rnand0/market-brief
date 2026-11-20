package com.marketbrief.config

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationConfig(val llm: LlmConfig, val storage: StorageConfig = StorageConfig()) {

    @Serializable
    data class StorageConfig(val reportsDir: String = "data/reports", val evalsDir: String = "data/evals") {
        init {
            require(reportsDir.isNotBlank()) { "storage.reportsDir must not be blank" }
            require(evalsDir.isNotBlank()) { "storage.evalsDir must not be blank" }
        }
    }

    @Serializable
    data class LlmConfig(
        val baseUrl: String,
        val model: String,
        val apiKeyEnv: String = "OPENCODE_API_KEY",
        val requestTimeoutSeconds: Long = 300,
        val maxRetries: Int = 2,
        val inputCostPerMillionUsd: Double? = null,
        val outputCostPerMillionUsd: Double? = null,
        val claimSupportJudgeModel: String? = null,
        val claimSupportJudgeInputCostPerMillionUsd: Double? = null,
        val claimSupportJudgeOutputCostPerMillionUsd: Double? = null,
    ) {
        init {
            require(apiKeyEnv.isNotBlank()) { "llm.apiKeyEnv must not be blank" }
            require(claimSupportJudgeModel == null || claimSupportJudgeModel.isNotBlank()) {
                "llm.claimSupportJudgeModel must not be blank"
            }
            require((inputCostPerMillionUsd == null) == (outputCostPerMillionUsd == null)) {
                "llm input and output token rates must both be set or both be absent"
            }
            require(
                inputCostPerMillionUsd == null || (inputCostPerMillionUsd.isFinite() && inputCostPerMillionUsd >= 0),
            ) {
                "llm.inputCostPerMillionUsd must be finite and non-negative"
            }
            require(
                outputCostPerMillionUsd == null || (outputCostPerMillionUsd.isFinite() && outputCostPerMillionUsd >= 0),
            ) {
                "llm.outputCostPerMillionUsd must be finite and non-negative"
            }
            require(
                (claimSupportJudgeInputCostPerMillionUsd == null) ==
                    (claimSupportJudgeOutputCostPerMillionUsd == null),
            ) {
                "claim-support judge input and output token rates must both be set or both be absent"
            }
            require(
                claimSupportJudgeInputCostPerMillionUsd == null ||
                    (
                        claimSupportJudgeInputCostPerMillionUsd.isFinite() &&
                            claimSupportJudgeInputCostPerMillionUsd >= 0
                        ),
            ) { "llm.claimSupportJudgeInputCostPerMillionUsd must be finite and non-negative" }
            require(
                claimSupportJudgeOutputCostPerMillionUsd == null ||
                    (
                        claimSupportJudgeOutputCostPerMillionUsd.isFinite() &&
                            claimSupportJudgeOutputCostPerMillionUsd >= 0
                        ),
            ) { "llm.claimSupportJudgeOutputCostPerMillionUsd must be finite and non-negative" }
        }
    }
}
