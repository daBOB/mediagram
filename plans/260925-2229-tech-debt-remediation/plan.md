# Tech-debt remediation

Source: `plans/reports/tech-debt-260925-2230-mediagram-web-and-pipeline-report.md`.
User asked "apply fix for all", 2026-09-25.

| # | Item | Status |
|---|------|--------|
| 1 | `push-index` refuses when the channel holds sets the local index lacks (`--force` overrides) | todo |
| 2 | Committed preview harness (`bun run preview`) | todo |
| 3 | Typecheck gate plus the standing `tsc` error | todo |
| 4 | Package readers accept schema ≥ oldest | todo |
| 5 | Sidecar never records a lower schema | todo |
| 6 | Targeted redraws instead of rebuilding the page on every state change | todo |
| 7 | `metadata --refresh-older-than` | todo |
| 8 | Stray tooling ignored or removed | todo |
| 9 | Web line-limit check with an allow-list | todo |
| 10 | Docs split (architecture ≤ 800 lines) | todo |
| 11 | Pull-quote prefers short taglines | todo |
| 12 | Channel merge | plan only, then check in (large) |
| 13 | Android magazine parity | plan only, then check in (large) |

Decision: #1 keeps `--force` as the escape hatch until #12 exists.
