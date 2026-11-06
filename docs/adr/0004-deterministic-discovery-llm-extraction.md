# Discovery is deterministic; the LLM only extracts

The report's live inputs are **discovered** deterministically from configured
RSS feeds and GDELT; the evaluation pipeline uses a packaged corpus. An LLM
extracts NewsItems from those Articles. We do not use an AI agent to browse the
open web for articles.

Rationale: browsing is more expensive (token volume spent on discarded pages),
non-deterministic (breaks the eval loop's reproducibility and auditability),
and unnecessary — the instrument set and regions are fixed, so the required
outlets are enumerable. Discovery stays deterministic; the LLM spend is
confined to extraction, where it earns its keep.
