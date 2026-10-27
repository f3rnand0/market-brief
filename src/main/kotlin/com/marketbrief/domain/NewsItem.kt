package com.marketbrief.domain

enum class NewsItemType { MARKET_MOVE, COMPANY, SECTOR, MACRO }

enum class Region { US, CHINA, EUROPE, LATAM, ASIA, GLOBAL }

enum class Direction { UP, DOWN, FLAT }

data class Subject(val name: String) {
    init {
        require(name.isNotBlank()) { "Subject name must not be blank" }
    }
}

data class Metric(val value: String, val unit: String) {
    init {
        require(value.isNotBlank()) { "Metric value must not be blank" }
        require(unit.isNotBlank()) { "Metric unit must not be blank" }
    }
}

data class Source(val articleId: String, val quotes: List<String>) {
    init {
        require(articleId.isNotBlank()) { "Source articleId must not be blank" }
        require(quotes.isNotEmpty()) { "Source must cite at least one quote span" }
        require(quotes.all { it.isNotBlank() }) { "Source quotes must not be blank" }
    }
}

data class NewsItem(
    val type: NewsItemType,
    val region: Region,
    val subjects: List<Subject>,
    val assertion: String,
    val direction: Direction? = null,
    val cause: String? = null,
    val metrics: List<Metric> = emptyList(),
    val source: Source,
) {
    init {
        require(assertion.isNotBlank()) { "Assertion must not be blank" }
        require(cause == null || type == NewsItemType.MARKET_MOVE) {
            "Only MARKET_MOVE items may carry a cause"
        }
        require(cause == null || cause.isNotBlank()) { "Cause must not be blank when present" }
    }
}
