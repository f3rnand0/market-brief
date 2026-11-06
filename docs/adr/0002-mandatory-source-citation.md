# Every NewsItem carries a mandatory source citation

Each `NewsItem` carries a `source`: an Article ID and quote spans intended to
support its assertion. An assertion without a citation is an extraction
failure. Requiring citations makes unsupported claims measurable.

The `NewsItem` type enforces citation shape, and the extractor drops candidates
that fail construction. [`RunEval`](../ARCHITECTURE.md#6-evaluation-pipeline)
checks quote presence and uses a judge to score semantic support. It does not
compare citations against gold citations.
