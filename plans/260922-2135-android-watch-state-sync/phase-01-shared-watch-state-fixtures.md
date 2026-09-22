# Phase 01 — Shared fixtures from the web's behaviour

## Context links

- `tasks/lessons.md` 2026-09-18 — two implementations of one decision drift unless a test reads both
- `crates/mediagram/tests/shared_playable_sql.rs:14-24` — the pattern: Rust test reads a web file, skips if absent
- `web/src/state/merge.ts:59-136`, `web/src/state/sync-record.ts:63-152`
- `web/public/lib/resume-point.js:14-116`, `web/public/lib/home-shelves.js:96-130`
- `web/test/state-merge.test.ts`, `web/test/state-sync-record.test.ts`, `web/test/resume-point.test.ts`, `web/test/home-shelves.test.ts` — existing cases to lift

## Overview

- Priority: P1 (gate for 02, 05, 06). Status: pending.
- Turn the web's already-tested behaviour into language-neutral JSON cases. Web tests assert the web passes them **before** anything else is written, so the fixture is the web's truth, not a new spec.

## Key insights

- The web is the reference (CLAUDE.md § Surface Parity). Fixtures are derived from current web output; no web source changes here.
- Four decisions must match exactly across TS, Rust and Kotlin: record parsing, merge, resume thresholds, Next up choice. One fixture file each.
- Tie-break compares device ids with `>` (`merge.ts:133`). JS compares UTF-16 units, Rust bytes; identical for ASCII. Fixtures use ASCII device ids; real ids are UUIDs.

## Requirements

- Functional: fixtures cover every rule named in the doc comments: LWW per title, device tie-break, `watched >= position` tombstone (equal ms included), order independence (each case also run reversed), name normalisation (NFC, trim, case), first-seen display name, hostile rows dropped individually, future format rejected, missing device rejected, `at = 0` kept, `duration <= 0` → null.
- Resume: glance `min(30, 10%)`, credits `min(5%, 60)`, unknown runtime cases, `trustedRuntime` cases.
- Next up: nothing watched → none; resume beats finished; walk forward past watched; finished show → none; glance-only → none; `touchedAt` ordering; Continue excludes what Next up shows; totals.
- Non-functional: plain JSON, no comments, readable by `serde_json`, `JSON.parse`, and `org.json`/kotlinx.

## Architecture

```
web/test/fixtures/watch-state/
  record-parse.json   [{name, input: <raw text>, expect: <SyncRecord|null>}]
  merge.json          [{name, records: [SyncRecord], expect: MergedState (sorted)}]
  resume-point.json   [{name, fn: resumeAt|isFinished|watchedFraction|trustedRuntime, args, expect}]
  next-up.json        [{name, order: [setId], progress: [...], watched: {setId: ms}, expect}]
```

`expect` for merge is canonicalised: profiles sorted by name, rows by setId, so every runner compares sets not Map iteration order.
`next-up.json` uses a flat play order, not a nested collection: flattening is tested separately in 06 against `Shelves.kt`.

## Related code files

- Create: the four JSON files above; `web/test/shared-watch-state-fixtures.test.ts` (runs all four against the web modules)
- Modify: none. Delete: none.

## Implementation steps

1. Lift cases from the four existing test files into JSON; add the missing ones listed in Requirements.
2. Write `shared-watch-state-fixtures.test.ts`: for merge, run `mergeStates(records)` and `mergeStates(records.reverse())`, canonicalise, compare. For next-up, build a one-division collection whose episode numbers follow `order`, call `homeShelves`.
3. `cd web && bun test` green. Any case that fails against the web is a wrong fixture, never a web fix in this phase.

## Todo

- [ ] record-parse.json
- [ ] merge.json (incl. reversed run)
- [ ] resume-point.json
- [ ] next-up.json
- [ ] shared-watch-state-fixtures.test.ts green

## Success criteria

- `bun test test/shared-watch-state-fixtures.test.ts` passes; ≥ 40 cases total.
- Deliberately flipping `>=` to `>` in `merge.ts:98` locally makes a fixture case fail (mutation check, reverted).

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Fixture encodes a web bug as truth | L×M | Parity rule says web wins; flag anything suspicious as an open question, do not fix here |
| `Number()` coercions (`"12"`, `null`, `true`) differ in Rust | M×L | Include those inputs so 02 must match them |

## Security

Fixtures contain no ids, names or tokens from the real channel. Synthetic set ids only.

## Next steps

02 (Rust) and 05/06 (Kotlin) read these files by relative path.
