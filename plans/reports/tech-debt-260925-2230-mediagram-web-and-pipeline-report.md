# Tech debt: mediagram (web player, pipeline, ops), 2026-09-25

Priority = (Impact + Risk) × (6 − Effort). Each score is 1–5.

## Ranked items

| # | Item | Type | I | R | E | Score |
|---|------|------|---|---|---|-------|
| 1 | `push-index` can silently drop titles | Infra / data | 3 | 5 | 2 | **32** |
| 2 | No committed UI preview harness; the live player hot-reloads on edits | Infra / test | 3 | 4 | 2 | **28** |
| 3 | No typecheck gate; one standing `tsc` error | Test | 2 | 3 | 1 | **25** |
| 4 | Package readers refuse newer schemas by membership | Architecture | 2 | 3 | 2 | **20** |
| 5 | Phone description store can record a lower schema after a downgrade | Code | 1 | 3 | 1 | **20** |
| 6 | Whole-page redraw on every state change | Code / perf | 3 | 3 | 3 | **18** |
| 7 | TMDB popularity and taglines never refresh | Code / data | 2 | 2 | 2 | **16** |
| 8 | Stray untracked tooling in `web/` | Infra | 1 | 2 | 1 | **15** |
| 10 | Docs over budget | Docs | 2 | 1 | 2 | **12** |
| 12 | Channel merge instead of wholesale push | Architecture | 3 | 3 | 4 | **12** |
| 9 | Oversized modules; no web line-limit check | Code | 3 | 2 | 4 | **10** |
| 11 | Pull-quote has no length bound | Code / UX | 1 | 1 | 1 | **10** |
| 13 | Android parity for the magazine home, pin and backdrops | Architecture | 4 | 3 | 5 | **7** |

## Details and justification

1. **`push-index` can silently drop titles.** `telegram/index_publish.rs` publishes the local
   index wholesale. On 2026-09-25 the channel held 1161 sets and this machine 925, so a push
   from here would have removed 257 titles from the web and the phone. Only memory notes
   prevented it. **Fix (small):** before sending, read the current channel snapshot's
   `sets.set_id`. Refuse with a count ("channel has 257 sets this index lacks; pull or merge
   first") unless `--force`. **Why:** the one known way to lose the library for every viewer
   with a single command.
2. **No committed UI preview harness.** Every UI check needs a hand-written scratch server
   (`startServer` + a copied snapshot + fake bytes). `web` dev runs `bun --watch` on the real
   player, so edits reload the process holding the Telegram key. **Fix:** commit
   `web/scripts/preview.ts` (read-only snapshot copy, real posters, scratch state, port
   argument) and a `bun run preview` script. **Why:** removes the recurring risk of breaking
   uploads or playback while styling, and makes visual QA repeatable.
3. **No typecheck gate.** `web/package.json` has `test` and `lint` but no `tsc --noEmit`;
   `test/search-shared-fixtures.test.ts:31` has failed typechecking for a while unnoticed.
   **Fix:** fix that one error (narrow `matched` to its union) and add a `typecheck` script
   to the pre-commit routine. **Why:** the `.d.ts` contracts (`library.d.ts`,
   `home-shelves.d.ts`) only protect anything if something checks them.
4. **Package readers refuse newer schemas by membership.** `pointer_is_readable` and the web
   `pointerReadabilityRefusal` refuse any schema not listed. Every installed build therefore
   rejects the next additive bump (v8 did this to `[6,7]` builds). The channel path already
   accepts ≥ oldest. **Fix:** accept `schema >= OLDEST_READABLE_SCHEMA` for packages too;
   keep a hard ceiling only for a declared breaking change. **Why:** each bump otherwise
   needs an install-before-publish dance on every device.
5. **Downgrade trap in the phone's description store** (reported by review, not reproduced
   here). An older app opening a newer sidecar records its own lower version; the next newer
   open re-runs `ADD COLUMN` and fails on every start. **Fix:** never lower a recorded
   version. **Why:** a one-line guard against a device that stops describing titles.
6. **Whole-page redraw on every state change.** `invalidateShelf()` → `route()` rebuilds
   `main` for any watch-state, pin or catalog notice. This caused today's flicker class of
   problems: images re-requested, cover and reveal restarting. It was mitigated
   (redraw-aware reveal, cover index kept, a single startup draw), not removed. **Fix:**
   targeted updates for the common cases (progress rule, counts, pin label), with a full
   `route()` only when the rows themselves change. **Why:** smoother UI with less network
   use while a household is watching on several devices.
7. **Provider data never refreshes.** `DiskCachedApi` entries never expire, and `metadata`
   rewrites from the cache. "Trending on TMDB" is therefore frozen at first-cache time and
   drifts from reality over months. **Fix:** a `metadata --refresh-older-than 30d` that
   re-asks TMDB for stale entries (rate-limited). **Why:** keeps the Trending label honest.
8. **Stray untracked tooling in `web/`:** `.github/workflows/react-doctor.yml` (grants
   `pull-requests: write`, for a React tool; this is not a React app), the `doctor` script
   line, `web/.claude/`, and `skills-lock.json`. **Fix:** delete, or `.gitignore` the local
   agent folders. **Why:** noise in every commit and a workflow that would never do
   anything useful.
9. **Oversized modules.** `public/lib/playback/player.js` 955, `public/app.js` 831,
   `src/state/store.ts` 796, `public/lib/watch-state.js` 495, `playback/transport.js` 471.
   The 200-line rule is enforced for `crates/` (`code_standards.rs`) but not for `web/`.
   **Fix:** add the same check for `web/src` with an allow-list of today's offenders, then
   split one per change: `app.js` router and startup, `store.ts` per table group.
   **Why:** new changes (like the editor's-choice store methods) keep landing in the
   biggest files.
10. **Docs over budget:** `docs/system-architecture.md` 859 lines (max 800);
    `project-changelog.md` 1900. **Fix:** move the web player section to
    `docs/web-player.md`; archive changelog entries before 0.43.
11. **Pull-quote length:** a long tagline (e.g. *Lincoln*) runs to eight lines and leaves a
    gap under Continue Watching. **Fix:** prefer taglines ≤ 90 characters in
    `editorial-picks.js` `quoteOf`.
12. **Channel merge.** The real cure behind #1: merge the channel snapshot into the local
    index (sets, parts, shows, assets) before pushing, so either machine can publish. Larger;
    #1's guard makes it non-urgent.
13. **Android parity:** the phone lacks the cover story, features, editor's-choice pin and
    backdrops (it deliberately skips `-bg` today). Owed under § Surface Parity. Large; needs
    its own plan.

## Remediation plan, alongside feature work

- **Now (one sitting, all small):** #1 the push guard, #3 the typecheck gate and its fix,
  #5 the sidecar guard, #8 the stray files, #11 the quote bound.
- **Next (one small change each):** #2 the preview harness, #4 package schema ≥ oldest,
  #7 the metadata refresh flag, #10 the docs split.
- **When touching those areas:** #6 targeted redraws, next time `app.js` or watch state is
  edited. #9 the web line-limit check with an allow-list, splitting whichever file a
  change lands in.
- **Planned projects:** #12 channel merge, #13 Android magazine parity, each with its own
  `plans/` entry.

## Unresolved questions

- Should `push-index --force` exist at all, or should a merge be the only way past the guard?
- Is dependency age in `crates/` worth tracking? Not checked here; `cargo outdated` is not
  installed. Web dependencies are current.
