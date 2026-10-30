package com.marketbrief.domain

import java.time.Instant

data class EvalReport(
    val id: String,
    val generatedAt: Instant,
    val metrics: Map<String, Double>,
    val version: Int = 1,
    val markdown: String = "",
) {
    init {
        require(id.isNotBlank()) { "EvalReport id must not be blank" }
        require(version > 0) { "EvalReport version must be positive" }
    }
}
