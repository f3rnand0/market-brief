package com.marketbrief.reportformat

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Direction
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Report
import com.marketbrief.domain.Snapshot
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object ReportMapper {

    val format = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(report: Report): String = format.encodeToString(ReportDto.serializer(), toDto(report))

    fun decode(text: String): Report = toDomain(format.decodeFromString(ReportDto.serializer(), text))

    fun toDto(report: Report): ReportDto = ReportDto(
        date = report.date.toString(),
        summary = report.summary,
        snapshot = snapshotGroups(report.snapshot),
        regionalNews = regionalNews(report.regionalNews),
        companySectorNews = report.companySectorNews.map(::toDto),
        otherNews = report.otherNews.map(::toDto),
    )

    fun toDomain(dto: ReportDto): Report = Report(
        date = LocalDate.parse(dto.date),
        summary = dto.summary,
        snapshot = Snapshot(
            quotes = dto.snapshot.flatMap { group ->
                group.quotes.map { quote ->
                    InstrumentQuote(
                        instrument = Instrument(quote.instrument, AssetClass.valueOf(quote.assetClass)),
                        value = quote.value.toBigDecimal(),
                        change = quote.change.toBigDecimal(),
                        changePercent = quote.changePercent.toBigDecimal(),
                    )
                }
            },
            missing = dto.snapshot.flatMap { group ->
                val assetClass = AssetClass.valueOf(group.assetClass)
                group.missing.map { Instrument(it, assetClass) }
            }.toSet(),
        ),
        regionalNews = dto.regionalNews.mapKeys { Region.valueOf(it.key) }.mapValues { (_, items) ->
            items.map(::toDomain)
        },
        companySectorNews = dto.companySectorNews.map(::toDomain),
        otherNews = dto.otherNews.map(::toDomain),
    )

    internal fun formatQuote(quote: InstrumentQuote): String = when (quote.instrument.assetClass) {
        AssetClass.FIXED_INCOME ->
            "${quote.value.toPlainString()} (${basisPoints(quote.change)} bp, ${percent(quote.changePercent)}%)"

        else -> "${quote.value.toPlainString()} (${percent(quote.changePercent)}%)"
    }

    private fun snapshotGroups(snapshot: Snapshot): List<SnapshotGroupDto> {
        val quotesByClass = snapshot.quotes.groupBy { it.instrument.assetClass }
        val missingByClass = snapshot.missing.groupBy { it.assetClass }
        return AssetClass.entries.mapNotNull { assetClass ->
            val quotes = quotesByClass[assetClass].orEmpty()
            val missing = missingByClass[assetClass].orEmpty()
            if (quotes.isEmpty() && missing.isEmpty()) {
                null
            } else {
                SnapshotGroupDto(
                    assetClass = assetClass.name,
                    quotes = quotes.map(::toDto),
                    missing = missing.map { it.name },
                )
            }
        }
    }

    private fun toDto(quote: InstrumentQuote): QuoteDto = QuoteDto(
        instrument = quote.instrument.name,
        assetClass = quote.instrument.assetClass.name,
        value = quote.value.toPlainString(),
        change = quote.change.toPlainString(),
        changePercent = quote.changePercent.toPlainString(),
        display = formatQuote(quote),
    )

    private fun regionalNews(news: Map<Region, List<NewsItem>>): Map<String, List<NewsItemDto>> = Region.entries
        .mapNotNull { region -> news[region]?.let { region.name to it.map(::toDto) } }
        .toMap()

    private fun toDto(item: NewsItem): NewsItemDto = NewsItemDto(
        type = item.type.name,
        region = item.region.name,
        subjects = item.subjects.map { it.name },
        assertion = item.assertion,
        direction = item.direction?.name,
        cause = item.cause,
        metrics = item.metrics.map { MetricDto(it.value, it.unit) },
        source = SourceDto(item.source.articleId, item.source.quotes),
    )

    private fun toDomain(item: NewsItemDto): NewsItem = NewsItem(
        type = NewsItemType.valueOf(item.type),
        region = Region.valueOf(item.region),
        subjects = item.subjects.map(::Subject),
        assertion = item.assertion,
        direction = item.direction?.let(Direction::valueOf),
        cause = item.cause,
        metrics = item.metrics.map { Metric(it.value, it.unit) },
        source = Source(item.source.articleId, item.source.quotes),
    )

    private fun percent(value: BigDecimal): String = signed(value.setScale(2, RoundingMode.HALF_UP))

    private fun basisPoints(change: BigDecimal): String = signed(change.multiply(BigDecimal(100)).stripTrailingZeros())

    private fun signed(value: BigDecimal): String = when {
        value.signum() > 0 -> "+${value.toPlainString()}"
        else -> value.toPlainString()
    }
}
