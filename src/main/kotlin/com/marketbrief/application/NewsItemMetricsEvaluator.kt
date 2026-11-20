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
        val normalizedExactMatches = results.sumOf { (article, extraction) ->
            countOneToOneNormalizedExactMatches(extraction.items, goldSet.expectedFor(article.id))
        }
        val fieldMatches = results.sumOf { (article, extraction) ->
            countOneToOneFieldMatches(extraction.items, goldSet.expectedFor(article.id))
        }
        val missing = (expected.size - exactMatches).coerceAtLeast(0)
        val articleAlignments = results.map { (article, extraction) ->
            alignArticle(article.id, extraction.items, goldSet.expectedFor(article.id))
        }
        val claimAlignments = articleAlignments.flatMap { it.candidates }
        return ItemMetrics(
            metrics = mapOf(
                "accuracy.precision" to ratio(fieldMatches, predicted.size * FIELD_COUNT),
                "accuracy.recall" to ratio(fieldMatches, expected.size * FIELD_COUNT),
                "precision" to ratio(exactMatches, predicted.size),
                "recall" to ratio(exactMatches, expected.size),
                "normalizedExact.precision" to ratio(normalizedExactMatches, predicted.size),
                "normalizedExact.recall" to ratio(normalizedExactMatches, expected.size),
                "claimAlignment.candidatePrecision" to ratio(claimAlignments.size, predicted.size),
                "claimAlignment.candidateRecall" to ratio(claimAlignments.size, expected.size),
                "missingValues" to ratio(missing, expected.size),
                "predictedItems" to predicted.size.toDouble(),
                "expectedItems" to expected.size.toDouble(),
                "missingCount" to missing.toDouble(),
            ),
            predictedCount = predicted.size,
            unalignedPredictions = articleAlignments.flatMap { it.unmatchedPredictions },
            unalignedExpected = articleAlignments.flatMap { it.unmatchedExpected },
            claimAlignments = claimAlignments,
        )
    }

    private fun countOneToOneNormalizedExactMatches(predicted: List<NewsItem>, expected: List<NewsItem>): Int {
        val unmatched = expected.toMutableList()
        return predicted.count { item ->
            val index = unmatched.indexOfFirst { gold -> item.normalizedExactMatch(gold) }
            if (index < 0) {
                false
            } else {
                unmatched.removeAt(index)
                true
            }
        }
    }

    private fun NewsItem.normalizedExactMatch(other: NewsItem): Boolean =
        type == other.type && region == other.region &&
            subjects.map { it.name.normalized() }.toSet() == other.subjects.map { it.name.normalized() }.toSet() &&
            direction == other.direction && cause.normalizedOptional() == other.cause.normalizedOptional() &&
            metrics.toSet() == other.metrics.toSet() && assertion.normalized() == other.assertion.normalized()

    private fun alignArticle(articleId: String, predicted: List<NewsItem>, expected: List<NewsItem>): ArticleAlignment {
        if (predicted.isEmpty() || expected.isEmpty()) {
            return ArticleAlignment(
                emptyList(),
                predicted.map { articleId to it },
                expected.map { articleId to it },
            )
        }
        val scores = Array(predicted.size) { predictedIndex ->
            IntArray(expected.size + predicted.size) { expectedIndex ->
                if (expectedIndex !in expected.indices) {
                    0
                } else {
                    val prediction = predicted[predictedIndex]
                    val gold = expected[expectedIndex]
                    val assertionSimilarity = tokenSimilarity(prediction.assertion, gold.assertion)
                    val subjectSimilarity = tokenSimilarity(
                        prediction.subjects.joinToString(" ") { it.name },
                        gold.subjects.joinToString(" ") { it.name },
                    )
                    if (isClaimAlignmentCandidate(prediction, gold, assertionSimilarity, subjectSimilarity)) {
                        ALIGNMENT_BASE_WEIGHT + (assertionSimilarity * ALIGNMENT_SIMILARITY_WEIGHT).toInt()
                    } else {
                        0
                    }
                }
            }
        }
        val assignments = maximumWeightAssignment(scores)
        val matchedPredictions = mutableSetOf<Int>()
        val matchedExpected = mutableSetOf<Int>()
        val candidates = assignments.indices.mapNotNull { predictionIndex ->
            val expectedIndex = assignments[predictionIndex]
            if (expectedIndex !in expected.indices || scores[predictionIndex][expectedIndex] == 0) {
                null
            } else {
                matchedPredictions += predictionIndex
                matchedExpected += expectedIndex
                val prediction = predicted[predictionIndex]
                val gold = expected[expectedIndex]
                ClaimAlignmentCandidate(
                    articleId,
                    prediction,
                    gold,
                    tokenSimilarity(prediction.assertion, gold.assertion),
                    differingFields(prediction, gold),
                )
            }
        }
        return ArticleAlignment(
            candidates,
            predicted.filterIndexed { index, _ -> index !in matchedPredictions }.map { articleId to it },
            expected.filterIndexed { index, _ -> index !in matchedExpected }.map { articleId to it },
        )
    }

    private fun isClaimAlignmentCandidate(
        prediction: NewsItem,
        gold: NewsItem,
        assertionSimilarity: Double,
        subjectSimilarity: Double,
    ): Boolean {
        val directionsConflict = prediction.direction != null && gold.direction != null &&
            prediction.direction != gold.direction
        val predictionMetrics = prediction.metrics.map { it.value.normalized() to it.unit.normalized() }.toSet()
        val goldMetrics = gold.metrics.map { it.value.normalized() to it.unit.normalized() }.toSet()
        val metricsConflict = predictionMetrics.isNotEmpty() && goldMetrics.isNotEmpty() &&
            predictionMetrics.intersect(goldMetrics).isEmpty()
        return !directionsConflict && !metricsConflict &&
            (
                assertionSimilarity >= MIN_ASSERTION_SIMILARITY ||
                    (
                        assertionSimilarity >= MIN_SUBJECT_ASSERTION_SIMILARITY &&
                            subjectSimilarity >= MIN_SUBJECT_SIMILARITY
                        )
                )
    }

    private fun differingFields(prediction: NewsItem, gold: NewsItem): List<String> = buildList {
        if (prediction.type != gold.type) add("type")
        if (prediction.region != gold.region) add("region")
        if (prediction.subjects.map { it.name.normalized() }.toSet() !=
            gold.subjects.map { it.name.normalized() }.toSet()
        ) {
            add("subjects")
        }
        if (prediction.assertion != gold.assertion) add("assertion wording")
        if (prediction.direction != gold.direction) add("direction")
        if (prediction.cause.normalizedOptional() != gold.cause.normalizedOptional()) add("cause")
        if (prediction.metrics.toSet() != gold.metrics.toSet()) add("metrics")
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        val firstTokens = first.normalized().split(WHITESPACE).filter(String::isNotBlank).toSet()
        val secondTokens = second.normalized().split(WHITESPACE).filter(String::isNotBlank).toSet()
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return 0.0
        return firstTokens.intersect(secondTokens).size.toDouble() / firstTokens.union(secondTokens).size
    }

    private fun String.normalized(): String = lowercase().replace(Regex("[^\\p{L}\\p{N}%]+"), " ").trim()
    private fun String?.normalizedOptional(): String? = this?.normalized()

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
        const val ALIGNMENT_BASE_WEIGHT = 4
        const val ALIGNMENT_SIMILARITY_WEIGHT = 3
        const val MIN_ASSERTION_SIMILARITY = 0.45
        const val MIN_SUBJECT_ASSERTION_SIMILARITY = 0.25
        const val MIN_SUBJECT_SIMILARITY = 0.6
        val WHITESPACE = Regex("\\s+")
    }
}

private data class ArticleAlignment(
    val candidates: List<ClaimAlignmentCandidate>,
    val unmatchedPredictions: List<Pair<String, NewsItem>>,
    val unmatchedExpected: List<Pair<String, NewsItem>>,
)

internal data class ClaimAlignmentCandidate(
    val articleId: String,
    val prediction: NewsItem,
    val expected: NewsItem,
    val assertionSimilarity: Double,
    val differingFields: List<String>,
)

internal data class ItemMetrics(
    val metrics: Map<String, Double>,
    val predictedCount: Int,
    val unalignedPredictions: List<Pair<String, NewsItem>>,
    val unalignedExpected: List<Pair<String, NewsItem>>,
    val claimAlignments: List<ClaimAlignmentCandidate>,
)
