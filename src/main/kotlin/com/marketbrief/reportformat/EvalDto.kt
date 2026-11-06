package com.marketbrief.reportformat

import com.marketbrief.domain.EvalReport
import kotlinx.serialization.Serializable

@Serializable
data class EvalReportDto(
    val id: String,
    val generatedAt: String,
    val version: Int = 1,
    val metrics: Map<String, Double>,
)

fun EvalReport.toDto() = EvalReportDto(id, generatedAt.toString(), version, metrics)
