package com.marketbrief.domain

import java.time.Instant

data class EvalReport(
    val id: String,
    val generatedAt: Instant,
    val metrics: Map<String, Double>,
) {
    init {
        require(id.isNotBlank()) { "EvalReport id must not be blank" }
    }
}
