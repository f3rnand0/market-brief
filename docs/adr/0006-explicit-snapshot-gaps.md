# Snapshot gaps are explicit and never rendered as market facts

Missing market data stays explicit so the report distinguishes an unknown value
from a known absence of movement. A summary-index request can fail (provider
outage, rate limit, schema change) while the pipeline continues. Rendering that
gap as "No major index moves." would assert an unobserved market state (PRD
§§1–2, ADR-0001).

Decision: `Snapshot` carries the set of requested `Instrument`s that could not
be resolved. Section assembly renders each missing summary index as
`"<index>: data unavailable."`; "No major index moves." is reserved for a
complete snapshot with no index quotes. The `missing` set is persisted and
rendered so consumers can distinguish a gap from a complete snapshot. Eval does
not score snapshot gaps; market-data adapters have separate fixture tests.

Consequence: adapters that omit instruments must record them in `missing`;
`CompositeMarketDataFetcher` does so. `Snapshot` construction rejects an
instrument that is both missing and quoted. The `missing` constructor default
(`emptySet()`) is reserved for callers that know the snapshot is complete; it
cannot assert completeness on behalf of a producer.
