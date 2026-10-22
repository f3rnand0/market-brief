# Every NewsItem carries a mandatory source citation

Each NewsItem must carry a `source`: the article id and quote span(s) that
support its assertion. An assertion with no backing source is an extraction
failure, not a valid output. This is what makes the "unsupported claims" eval
dimension measurable. Consequence: extraction is stricter — prompts and parsing
must produce (and we must validate) citations, which adds friction in exchange
for auditability.
