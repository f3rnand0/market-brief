package com.marketbrief.domain

import java.time.Instant

/** The previous-day data window (GLOSSARY.md). */
data class DayWindow(val date: java.time.LocalDate)

/**
 * Versioned eval result. Metrics are named scores
 * (accuracy, unsupported-claims, missing-values, cost).
 */
data class EvalReport(
    val id: String,
    val generatedAt: Instant,
    val metrics: Map<String, Double>,
) {
    init {
        require(id.isNotBlank()) { "EvalReport id must not be blank" }
    }
}
