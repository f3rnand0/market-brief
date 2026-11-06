# Market brief vocabulary

A daily report of the most important global investment-market news, generated
from free web sources and market data.

## Language

**Report**:
The daily output: a structured JSON document and its rendered HTML page. It
carries the summary (including `otherNews` for GLOBAL macro and market moves),
the snapshot, regional news, and company/sector news.
_Avoid_: digest, bulletin

**Snapshot**:
The structured report section that lists market numbers and day-over-day
changes by asset class. Numbers come from a market-data API, never from
article prose. In the domain, `Snapshot` is a flat list of `InstrumentQuote`s
plus the requested `Instrument`s that could not be resolved; grouping by asset
class happens at render time.
_Avoid_: market data (that's the raw input, not the section)

**Instrument**:
One market number tracked in the snapshot: a display name and an asset class
(e.g. "US 10Y", "S&P 500"). Adapters map an `Instrument` to their own ticker
symbol.
_Avoid_: ticker, symbol

**AssetClass**:
The grouping of an `Instrument` in the snapshot: equities, fixed income, FX &
volatility, commodities, or crypto.

**InstrumentQuote**:
An `Instrument`'s latest value with its absolute day change and percentage
change. Fixed income renders the change in basis points and percent; other
asset classes render the percentage change.
_Avoid_: price (a yield is not a price)

**NewsItem**:
The typed unit of information extracted from an article: a single market move,
company item, sector item, or macro item.
_Avoid_: event, fact, claim

**Assertion**:
The single natural-language claim carried by a NewsItem. Every assertion must
be backed by a source.
_Avoid_: statement, summary

**Source**:
The mandatory citation attached to a NewsItem: one article id and one or more
quote spans intended to support the assertion. The domain checks that the
citation is non-empty. Evaluation checks quote presence and uses a judge for
semantic support; a manual sample is included in the Eval report.
_Avoid_: provenance, reference

**Outlet**:
The organization that published an article (Reuters, SCMP, …).
_Avoid_: source (reserved for the NewsItem citation)

**Article**:
A single news piece: headline, article text in `body`, outlet, URL, and
publication date. For live news, `body` must contain text extracted from the
publisher page; a headline or RSS teaser alone is not an Article for extraction.

**Region**:
A geographic category on a NewsItem: US, China, Europe, Latam, Asia, or Global.

**Subject**:
What a NewsItem is about: a company/ticker, a sector, an index, or an asset
class.

**Metric**:
A number with a unit extracted inside a NewsItem (e.g. "−1.2%", "$42B"). Must
be source-backed.
_Avoid_: value, figure

**Gold set**:
The manually labeled ground truth used to score extraction. It contains
expected NewsItems for each Article. Snapshot values are tested at the
market-data adapter, not as LLM extraction output.

**Extraction usage**:
Token counts and estimated cost for one LLM call, when token counts and
configured rates are available. Extraction usage is recorded once per Article
extraction call, regardless of how many NewsItems it returns; claim-support
judgment calls also contribute to Eval usage totals. The Eval report's
per-NewsItem cost is the aggregate estimated cost divided by predicted
NewsItems.

**Claim support**:
The judge result for whether a Source supports a NewsItem assertion: supported,
unsupported, or indeterminate.

**Eval report**:
A versioned record of extraction metrics, stored as JSON and Markdown.

**Previous day**:
The calendar day before the report date; this defines the report's news window.
