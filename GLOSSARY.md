# Market Summary Service

A daily report of the most important global investment-market news, generated
from free web sources and market data.

## Language

**Report**:
The daily output: a structured JSON document and its rendered HTML page.
_Avoid_: digest, bulletin

**Snapshot**:
The structured report section that lists market numbers and day-over-day
percentages by asset class. Numbers come from a market-data API, never from
article prose.
_Avoid_: market data (that's the raw input, not the section)

**NewsItem**:
The typed unit of information extracted from an article: a single market move,
company item, sector item, or macro item.
_Avoid_: event, fact, claim

**Assertion**:
The single natural-language claim carried by a NewsItem. Every assertion must
be backed by a source.
_Avoid_: statement, summary

**Source**:
The citation attached to a NewsItem: which article(s) and which span(s) of text
support the assertion. Mandatory.
_Avoid_: provenance, reference

**Outlet**:
The organization that published an article (Reuters, SCMP, …).
_Avoid_: source (reserved for the NewsItem citation)

**Article**:
A single news piece: headline, body, outlet, URL, and publication date.

**Region**:
A continent tag on a NewsItem: US, China, Europe, Latam, Asia, or Global.

**Subject**:
What a NewsItem is about: a company/ticker, a sector, an index, or an asset
class.

**Metric**:
A number with a unit extracted inside a NewsItem (e.g. "−1.2%", "$42B"). Must
be source-backed.
_Avoid_: value, figure

**Gold set**:
The manually labeled ground truth: per-article expected NewsItems and expected
snapshot facts, used to score the extractor.

**Previous day**:
The 24-hour window ending at report generation time (09:00 Europe/Berlin).
