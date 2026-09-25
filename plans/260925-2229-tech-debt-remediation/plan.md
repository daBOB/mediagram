# Tech-debt remediation

Source: `plans/reports/tech-debt-260925-2230-mediagram-web-and-pipeline-report.md`.
User asked "apply fix for all", 2026-09-25.

| # | Item | Status |
|---|------|--------|
| 1 | `push-index` refuses when the channel holds sets the local index lacks (`--force` overrides) | done `7a72d99` |
| 2 | Committed preview harness (`bun run preview`) | done `ee42206` |
| 3 | Typecheck gate plus the standing `tsc` error | done `7a72d99` |
| 4 | Package readers accept schema ≥ oldest | done `7a72d99` |
| 5 | Sidecar never records a lower schema | done `7a72d99` |
| 6 | Targeted redraws instead of rebuilding the page on every state change | done `23ecdac` |
| 7 | `metadata --refresh-older-than` | done `36f35f8` |
| 8 | Stray tooling ignored or removed | done `7a72d99` |
| 9 | Web line-limit check with an allow-list | done `ee42206` |
| 10 | Docs split (architecture ≤ 800 lines) | done `ee42206` |
| 11 | Pull-quote prefers short taglines | done `7a72d99` |
| 12 | Channel merge | done `5cb0254` (`pull-index`, `push-index --merge`); first real run is the user's |
| 13 | Android magazine parity | done `95f379c` (merge); TV phase deferred with the foundation plan |

Decision: #1 keeps `--force` as the escape hatch until #12 exists.
