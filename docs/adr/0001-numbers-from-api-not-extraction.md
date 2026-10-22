# Market numbers come from a data API, never from LLM extraction

The report's snapshot and index moves need numbers (levels, percentages). We
fetch them from a free market-data API (Yahoo Finance primary, with
per-instrument-type fallbacks) and never parse them out of article prose.
Extracting numbers from prose is the dominant source of hallucinated or
unsupported values, which directly conflicts with the integrity goal. This
departs from the original "extract everything from the web" framing, so it's
recorded here.
