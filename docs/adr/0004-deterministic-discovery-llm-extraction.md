# Discovery is deterministic; the LLM only extracts

The report's inputs are **discovered** deterministically (Google News RSS /
GDELT + a curated RSS list; a static corpus for the PoC) and **extracted** by
an LLM. We deliberately do not use an AI agent to browse the open web for
articles.

Rationale: browsing is more expensive (token volume spent on discarded pages),
non-deterministic (breaks the eval loop's reproducibility and auditability),
and unnecessary — the instrument set and regions are fixed, so the required
outlets are enumerable. Discovery stays deterministic; the LLM spend is
confined to extraction, where it earns its keep.
