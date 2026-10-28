# Snapshot gaps are explicit and never rendered as market facts

The report must not turn missing market data into a claim. A request for the
summary indices can fail (feed outage, rate limit, schema change) while the
pipeline continues. Rendering that absence as "No major index moves." asserts a
market state that was never observed and conflicts with the integrity goal
(PRD §4, ADR-0001).

Decision: `Snapshot` carries the set of requested `Instrument`s that could not
be resolved. Section assembly renders each missing summary index as
`"<index>: data unavailable."`; the "No major index moves." sentence is
reserved for a snapshot known to be complete with no index quotes. The
completeness data stays available to M5's persistence/rendering layer and to
the eval loop, so a gap remains observable rather than being silently rewritten.

Consequence: adapters that omit instruments must record them in `missing`;
`CompositeMarketDataFetcher` does so. `Snapshot` construction rejects an
instrument that is both missing and quoted.
