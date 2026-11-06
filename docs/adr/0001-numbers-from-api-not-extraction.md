# Market numbers come from a data API, never from LLM extraction

The snapshot's levels and moves come from market-data APIs; the LLM does not
extract market numbers from article prose. The summary may use cited news prose
for a move's cause. This keeps provider-reported values separate from
extracted claims.

Yahoo Finance is primary and CNBC fills fixed-income gaps. When both return an
instrument, the Yahoo quote wins. The sources run concurrently.
