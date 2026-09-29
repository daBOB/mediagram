# Planner report — web server phases 01 and 02 (profile roles, PINs, kids limits)

Plans: `plans/260928-0047-profile-roles-pins-kids-age-limits/phase-01-web-schema-sync-merge.md`,
`…/phase-02-web-rules-pin-routes.md`. Written against `shared-contract.md` as amended
2026-09-28 (create-first, the 7-step order, grown-up-only admin, kid-only parent, the
`max(now, stored + 1)` local-write stamp, no age on a Kids tombstone).

## What was planned

- **Phase 01 (7 commits + gate):** schema v11; `profiles.ts` (row read once, `Profile` §8 shape,
  delegates kept on `WatchState`); `record-scalars.ts` + `roles-record.ts` (parse); `roles-merge.ts`
  (merge hook in `mergeStates`); Kids mark age in `setKids`/`kidsFromSix`/`lists-exchange.ts`;
  `roles-exchange.ts` (export/import, run after the import loop); `profile-roles-merge.json`
  (16 cases) + runner, added last so it passes on first run as the runner's header requires.
- **Phase 02 (7 commits + gate):** `profiles-pin.ts` + `pin-hash.json`; `profiles-wait.ts`
  (injectable clock); `profiles-rules.ts` + `profile-rules.json` (39 cases, generator given);
  `ProfileManager` in `profiles-manage.ts` (create-first, unlock, claim-admin, set-pin, then
  create grown-up/kid, remove with kid cascade, set-kids-age); `profiles-routes.ts` +
  `route-json.ts`; rename removed; `/api/kids` age; ceilings lowered. Held for push with phase 03.

## Dry-run verification (scratch copy of `web/`, never the repo)

Every code block was applied by script straight from the plan files, deps installed:
`bun run typecheck` clean after each phase; `bun run lint` clean; full suite 2438 pass, the only
3 failures are the docs-reading codec-policy tests (the scratch copy has no `docs/`) and fail
identically on an untouched copy. Sizes below are measured, not estimated.

## File-size decisions

| File | Before | After 01 | After 02 | Limit | Decision |
|---|---|---|---|---|---|
| `src/state/store.ts` | 799 | 775 | 777 | 800 → lowered to 777 | profile code out to `profiles.ts`; one-line delegates kept (≈60 test call sites) |
| `src/state/schema.ts` | 244 | 264 | 264 | 245 → **raised to 264** | dated CEILINGS sentence (house precedent); no split migration list |
| `src/state/sync-record.ts` | 328 | 322 | 322 | 328 → lowered to 322 | readers → `record-scalars.ts`, role keys → `roles-record.ts` |
| `src/state/routes.ts` | 267 | 267 | 235 | 267 → lowered to 235 | profile routes → `profiles-routes.ts`, helpers → `route-json.ts` |
| `src/state/merge.ts` (unlisted) | 184 | 190 | 190 | 200 | role merge in `roles-merge.ts`, 5-line hook |
| `src/state/lists-exchange.ts` | 178 | 190 | 190 | 200 | in place |
| `src/server.ts` | 269 | — | 269 | 269 | DELETE-body edit is line-neutral |
| `src/state/profiles-manage.ts` | new | — | 189 | 200 | first draft measured 200; `writePin` moved to `profiles.ts` |
| `src/state/profiles.ts` | new | 164 | 186 | 200 | |
| other new files | — | 30–99 | 22–90 | 200 | |

## Implementation facts beyond the contract (decided in the plan, not open)

- `server.ts:162` never read a `DELETE` body; §8's `DELETE { actorId, pin }` needs it — changed, line-neutral; body-less DELETE test still green.
- `refuseUnsafeBrowserWrite`'s DELETE exemption comment corrected (the exemption itself stays sound).
- `POST …/unlock` excluded from `writeWorthSyncing` (`src/routes.ts`) — it changes nothing a document says.
- The wrong-PIN count lives on `WatchState` (one per process, `index.ts:135`), not in the state router, which is rebuilt on every catalog swap.

## Contract questions

1. **`create-first` is not in `profile-rules.json`.** §2 has no row for it and `allowed` refuses a missing actor by its first line; its condition is structural (§3 step 3) and is covered by manager and HTTP outcome tests instead. If it must be in the shared fixture, §2 needs a row (e.g. "allowed when no grown-up exists; actor ignored") and the core an `Action` variant.
2. **What counts as "no `actorId`"** for `create-first`: planned as the key absent (`undefined`). A present non-string `actorId` goes the create-grown-up/kid path and answers `not-found`.
3. **The 5th wrong PIN answers `wrong-pin`**; the next comparing call answers `wait` (§4 read literally). Core should mirror.
4. **`PUT /api/kids/:setId` with `age` present but not 6/12** → bodiless 400 (§8 defines only absent = 12).
5. **`claim-admin` on an unknown id with a malformed PIN** → `not-found`: the PIN is format-checked only when it would become the target's PIN, which needs the target.
6. **plan.md ownership column** under-states both phases (phase 01 creates `profiles.ts`, inside phase 02's `profiles*.ts` glob; phase 02 also edits `profiles.ts`, `store.ts`, `server.ts`, `http/browser-write.ts`, `src/routes.ts`, `code-standards.test.ts`). Sequential, so no conflict — table not edited.
7. Pre-existing, not widened: two local profiles with one normalised name (e.g. a kid "Mia" and a grown-up "mia") merge into one viewer on sync.
