# Every NewsItem carries a mandatory source citation

Each NewsItem must carry a `source`: the article id and quote span(s) that
support its assertion. An assertion with no backing source is an extraction
failure, not a valid output. This is what makes the "unsupported claims" eval
dimension measurable. Consequence: extraction is stricter — prompts and parsing
must produce citations, which adds friction in exchange for auditability.

Enforcement, per the accepted architecture (ARCHITECTURE.md §2.3): the
invariant lives in the type — `NewsItem` cannot be constructed without a
`source` (parse, don't validate). Dropping and recording invalid candidates is
an internal seam of the extractor adapter; only valid `NewsItem`s cross the
`NewsItemExtractor` port. `RunEval` re-verifies every citation independently
against the article text and the gold set — it never trusts the adapter's own
drop counts.
