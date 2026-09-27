# Series preload yields to playback + paces itself

Worktree: `/home/andre/Workspace/mediagram-web-preload`, branch `fix/series-preload-yields`.
No commit made (lead reviews/commits). No player started, no Telegram contact.

## What changed

1. **`web/src/telegram/download-gate.ts`** — `DownloadGate.run(task, { background? })`.
   Two waiting queues (foreground/background) plus a `foregroundActive` counter.
   `canStart(background)`: foreground starts whenever a slot is free; background
   only when a slot is free *and* no foreground task is running or queued. On
   release: a waiting foreground task always gets the freed slot first; a
   background task gets it only if `foregroundActive === 0`; otherwise the slot
   is simply freed. Foreground-only FIFO behaviour is unchanged (verified: the 3
   pre-existing tests pass untouched). Doc comment updated to explain why.

2. **`web/src/telegram/part-fetch.ts`** — added `backgroundFetcher(connection, messageId)`,
   identical to `connectionFetcher` except it calls `downloads.run(..., { background: true })`.
   Re-exported from `web/src/telegram/source.ts`.

3. **`web/src/cache/series-preload.ts`** — `pacedFetch()` wraps the fetcher passed
   to `reader.fill`: splits any requested range into ≤`CACHE_CHUNK` (512 KiB)
   slices, awaits `pause(PRELOAD_REQUEST_INTERVAL_MS)` (1000 ms) before *every*
   slice — including the first of a call — so pacing holds across run/part
   boundaries, not just inside one `fillRun` call. `stopped` is checked before
   each slice so `stop()` aborts promptly. `pause` is injectable via
   `SeriesPreloadOptions.pause` (defaults to a real `setTimeout`); tests inject
   an instant one. Top-of-file doc comment updated.

4. **`web/src/index.ts`** — `SeriesPreload`'s `fetcherFor` now uses
   `backgroundFetcher` instead of `connectionFetcher` (the now-unused
   `connectionFetcher` import was removed from this file; it's still used/exported
   elsewhere).

5. **Tests** (red confirmed before implementation, then green):
   - `web/test/download-gate.test.ts` — 3 new tests: background waits while
     foreground runs; foreground queued behind a full gate still goes before an
     earlier background waiter; background runs at once on an idle gate. All 3
     pre-existing tests pass unmodified.
   - `web/test/series-preload.test.ts` — 2 new tests under `describe("SeriesPreload pacing")`:
     every request ≤ `CACHE_CHUNK`, `pause` called with `PRELOAD_REQUEST_INTERVAL_MS`
     once per request, bytes arrive intact/in order; and `stop()` mid-fill stops
     further slices (fetch count stays at the slice in flight, not the whole run).
   - `web/test/application-media-shutdown.test.ts` — **pre-existing test updated**,
     not just left passing incidentally: "shutdown drains the active preload range
     before disconnecting" asserted a single upstream fetch of the *whole* 4 MiB
     run (`MAX_RUN_BYTES`); that assumption is now wrong by design (each request
     is capped at `CACHE_CHUNK`). Added `pause: async () => {}` to keep the test
     instant, and changed the expected fetched length from `MAX_RUN_BYTES` to
     `CACHE_CHUNK`. The test's actual intent (shutdown drains in-flight work,
     discards the rest, disconnects) is unchanged and still verified.

## Docs

- `docs/web-player.md` — new paragraph after the module-map prose (no prior
  section described `DownloadGate`/`SeriesPreload` design at all — this is the
  first). Explains the shared gate, the `background` lane's priority rule, and
  the 1 req/s pacing, with file pointers.
- `docs/project-changelog.md` — new `## 0.68.2` entry at the top: what the
  viewer saw (recurring `FLOOD_WAIT` on `upload.GetFile`, possible stalls) and
  what changed (gate priority lane + preload's own pacing).

## Version bumps (0.68.1 → 0.68.2, by pattern not exact string)

- `Cargo.toml` (workspace root): `version = "0.68.2"`
- `web/package.json`: `"version": "0.68.2"`
- `android/app/build.gradle.kts`: `versionName = "0.68.2"`
- `cargo check -q -p mediagram` run afterward; `Cargo.lock` picked up the new
  version for `mediagram`, `mediagram-cache`, `mediagram-core` (workspace members
  inheriting the root version).

## Mutation checks (temporarily broken, confirmed red, restored, confirmed identical to backup + full suite green after)

- `download-gate.ts`: disabling the background gating check (`canStart` always
  `true`) broke "a background task waits while a foreground task runs". Then,
  with gating restored, reordering the release handoff to prefer
  `backgroundWaiting` over `foregroundWaiting` broke "a foreground task queued
  behind a full gate goes before a background waiter". Both restored;
  post-restore diff against a pre-mutation backup is empty.
- `series-preload.ts`: removing both the `CACHE_CHUNK` cap and the `pause`
  call (`take = length - at`, no `await this.pause(...)`) broke both new
  pacing tests (chunk-size assertion and the stop-mid-fill assertion).
  Restored; diff against backup is empty.

## Verify

- `cd web && bun test` — **2168 pass, 0 fail** (whole suite, 170 files).
- `bun run typecheck` (`tsc --noEmit -p .`) — clean.
- `bun run lint` (`eslint public --max-warnings 0`) — clean (scoped to `public/`,
  unaffected by these changes; ran anyway per instructions).
- `code-standards.test.ts` line ceilings: all touched `src/` files well under
  their ceiling (`download-gate.ts` 78, `part-fetch.ts` 121, `series-preload.ts`
  158 lines; `index.ts` unchanged in line count, still under its listed 435).
  Test files aren't covered by that ratchet (only `src/`, `public/`, `scripts/`).

## Files changed

- `Cargo.lock`, `Cargo.toml`
- `android/app/build.gradle.kts`
- `docs/project-changelog.md`, `docs/web-player.md`
- `web/package.json`
- `web/src/cache/series-preload.ts`
- `web/src/index.ts`
- `web/src/telegram/download-gate.ts`
- `web/src/telegram/part-fetch.ts`
- `web/src/telegram/source.ts`
- `web/test/application-media-shutdown.test.ts`
- `web/test/download-gate.test.ts`
- `web/test/series-preload.test.ts`

`git status` in this worktree shows only the files listed above — nothing
untouched or unrelated in `web/src/transcode/`.

## Unresolved questions

None. Scope matched the decided fix exactly; no open design questions.

**Status:** DONE
