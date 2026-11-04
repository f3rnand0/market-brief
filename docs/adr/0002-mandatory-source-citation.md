# Every NewsItem carries a mandatory source citation

Each NewsItem must carry a `source`: the article id and quote span(s) that
support its assertion. An assertion with no backing source is an extraction
failure, not a valid output. This is what makes the "unsupported claims" eval
dimension measurable. Consequence: extraction is stricter — prompts and parsing
must produce citations, which adds friction in exchange for auditability.

Enforcement, per the accepted architecture (ARCHITECTURE.md §2.3): the
invariant lives in the type — `NewsItem` cannot be constructed without a
non-empty `source` (parse, don't validate). The extractor drops candidates that
fail domain construction; only valid `NewsItem`s cross the `NewsItemExtractor`
port. `RunEval` currently checks that each quoted span occurs in the article's
headline or body, independently of the adapter's drop count. It does not assess
whether the quote supports the assertion or compare it against a gold citation.
