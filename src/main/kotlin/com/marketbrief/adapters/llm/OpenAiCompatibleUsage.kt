package com.marketbrief.adapters.llm

import com.marketbrief.domain.ExtractionUsage
import dev.langchain4j.model.output.TokenUsage
import java.math.BigDecimal
import java.math.RoundingMode

internal fun TokenUsage.toExtractionUsage(
    inputCostPerMillionUsd: Double?,
    outputCostPerMillionUsd: Double?,
): ExtractionUsage {
    val input = inputTokenCount()?.toLong()
    val output = outputTokenCount()?.toLong()
    val estimatedCost = if (
        input != null && output != null && inputCostPerMillionUsd != null && outputCostPerMillionUsd != null
    ) {
        BigDecimal.valueOf(input)
            .multiply(BigDecimal.valueOf(inputCostPerMillionUsd))
            .add(BigDecimal.valueOf(output).multiply(BigDecimal.valueOf(outputCostPerMillionUsd)))
            .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP)
    } else {
        null
    }
    return ExtractionUsage(input, output, estimatedCost)
}
