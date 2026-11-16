# Evaluation improvements

These are planned improvements to the evaluation workflow. They are not
implemented behavior.

- When changing Gold labels or Article membership, update
  `src/main/resources/gold/version.txt` and retain the version and Article IDs
  in the resulting Eval report.
- Confirm reviewer capacity to independently check every Article and label in
  the current Gold set. Only then define and adopt a labeling guide, re-review
  the full set, and resolve disagreements. Until that capacity exists, keep the
  existing labels as the current baseline without implying protocol-based
  review.
- Expand the labeled corpus across topics, regions, outlets, and writing
  styles when reviewers can label and review the added Articles. Establish a
  separately reviewed holdout set before using the expanded corpus for tuning.
- After reviewer capacity is available, record human verdicts for a
  representative citation sample and compare them with judge outcomes to
  measure judge quality.
- Consider judge/provider combinations beyond the current separate models.
  Evaluate candidate judges against human-reviewed examples before relying on
  their verdicts.
