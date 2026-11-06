package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtraction

internal class NewsItemMetricsEvaluator {
    fun evaluate(results: List<Pair<Article, NewsItemExtraction>>, goldSet: GoldSet): ItemMetrics {
        val predicted = results.flatMap { it.second.items }
        val expected = results.flatMap { goldSet.expectedFor(it.first.id) }
        val exactMatches = results.sumOf { (article, extraction) ->
            countOneToOneExactMatches(extraction.items, goldSet.expectedFor(article.id))
        }
        val fieldMatches = results.sumOf { (article, extraction) ->
            countOneToOneFieldMatches(extraction.items, goldSet.expectedFor(article.id))
        }
        val missing = (expected.size - exactMatches).coerceAtLeast(0)
        return ItemMetrics(
            metrics = mapOf(
                "accuracy.precision" to ratio(fieldMatches, predicted.size * FIELD_COUNT),
                "accuracy.recall" to ratio(fieldMatches, expected.size * FIELD_COUNT),
                "precision" to ratio(exactMatches, predicted.size),
                "recall" to ratio(exactMatches, expected.size),
                "missingValues" to ratio(missing, expected.size),
                "predictedItems" to predicted.size.toDouble(),
                "expectedItems" to expected.size.toDouble(),
                "missingCount" to missing.toDouble(),
            ),
            predictedCount = predicted.size,
        )
    }

    private fun NewsItem.sameClaimAs(other: NewsItem): Boolean = type == other.type && region == other.region &&
        subjects == other.subjects && assertion == other.assertion && direction == other.direction &&
        cause == other.cause && metrics == other.metrics

    private fun NewsItem.fieldMatches(other: NewsItem): Int = listOf(
        type == other.type,
        region == other.region,
        subjects == other.subjects,
        assertion == other.assertion,
        direction == other.direction,
        cause == other.cause,
        metrics == other.metrics,
    ).count { it }

    private fun countOneToOneExactMatches(predicted: List<NewsItem>, expected: List<NewsItem>): Int {
        val unmatched = expected.toMutableList()
        return predicted.count { item ->
            val index = unmatched.indexOfFirst { expectedItem -> item.sameClaimAs(expectedItem) }
            if (index == -1) {
                false
            } else {
                unmatched.removeAt(index)
                true
            }
        }
    }

    private fun countOneToOneFieldMatches(predicted: List<NewsItem>, expected: List<NewsItem>): Int {
        if (predicted.isEmpty() || expected.isEmpty()) return 0
        val weights = Array(predicted.size) { predictedIndex ->
            IntArray(expected.size + predicted.size) { expectedIndex ->
                if (expectedIndex >= expected.size) {
                    0
                } else {
                    val predictedItem = predicted[predictedIndex]
                    val expectedItem = expected[expectedIndex]
                    if (predictedItem.subjects.none { it in expectedItem.subjects }) {
                        0
                    } else {
                        predictedItem.fieldMatches(expectedItem)
                    }
                }
            }
        }
        val assignments = maximumWeightAssignment(weights)
        return assignments.indices.sumOf { predictedIndex ->
            val expectedIndex = assignments[predictedIndex]
            if (expectedIndex in expected.indices) weights[predictedIndex][expectedIndex] else 0
        }
    }

    private fun maximumWeightAssignment(weights: Array<IntArray>): IntArray {
        val rows = weights.size
        val columns = weights.first().size
        val rowPotentials = IntArray(rows + 1)
        val columnPotentials = IntArray(columns + 1)
        val assignedRow = IntArray(columns + 1)
        val previousColumn = IntArray(columns + 1)

        for (row in 1..rows) {
            assignedRow[0] = row
            var currentColumn = 0
            val minimumReducedCost = IntArray(columns + 1) { Int.MAX_VALUE }
            val used = BooleanArray(columns + 1)
            do {
                used[currentColumn] = true
                val currentRow = assignedRow[currentColumn]
                var delta = Int.MAX_VALUE
                var nextColumn = 0
                for (column in 1..columns) {
                    if (used[column]) continue
                    val cost = FIELD_COUNT - weights[currentRow - 1][column - 1]
                    val reducedCost = cost - rowPotentials[currentRow] - columnPotentials[column]
                    if (reducedCost < minimumReducedCost[column]) {
                        minimumReducedCost[column] = reducedCost
                        previousColumn[column] = currentColumn
                    }
                    if (minimumReducedCost[column] < delta) {
                        delta = minimumReducedCost[column]
                        nextColumn = column
                    }
                }
                for (column in 0..columns) {
                    if (used[column]) {
                        rowPotentials[assignedRow[column]] += delta
                        columnPotentials[column] -= delta
                    } else if (column > 0) {
                        minimumReducedCost[column] -= delta
                    }
                }
                currentColumn = nextColumn
            } while (assignedRow[currentColumn] != 0)

            do {
                val previous = previousColumn[currentColumn]
                assignedRow[currentColumn] = assignedRow[previous]
                currentColumn = previous
            } while (currentColumn != 0)
        }

        return IntArray(rows) { -1 }.also { assignments ->
            for (column in 1..columns) {
                val row = assignedRow[column]
                if (row > 0) assignments[row - 1] = column - 1
            }
        }
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private companion object {
        const val FIELD_COUNT = 7
    }
}

internal data class ItemMetrics(val metrics: Map<String, Double>, val predictedCount: Int)
