package com.marketbrief.domain

data class GoldCase(val articleId: String, val expectedItems: List<NewsItem>) {
    init {
        require(articleId.isNotBlank()) { "GoldCase articleId must not be blank" }
    }
}

data class GoldSet(val cases: List<GoldCase>) {
    private val byArticleId = cases.associateBy { it.articleId }

    init {
        require(cases.map { it.articleId }.toSet().size == cases.size) { "GoldSet article IDs must be unique" }
    }

    fun expectedFor(articleId: String): List<NewsItem> = byArticleId[articleId]?.expectedItems.orEmpty()
}
