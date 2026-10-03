# Shared contract — viewing stats

Every phase implements against this file. A phase that needs a name, type,
key or rule not listed here stops and asks — it does not invent one. The web
is authoritative: `web/test/fixtures/watch-state/stats-*.json` and
`achievements.json` pin the Rust core to it. Decisions behind this file:
`plan.md` § Decisions (1–11, user).

## 1. Recording — what one position write does

Both state engines record, inside their own position write: web
`WatchState.setProgress` (`web/src/state/store.ts:215`), core
`StateDb::set_progress_counted`, a transaction around the unchanged
`rows::set_progress` (`crates/mediagram-core/src/state/rows.rs:49`) — so the
import path and the existing callers can never record. Positions
merged in from other devices (`importMerged` / `import_merged`) are **never**
recorded — only this device's own writes.

**The last tick** is per engine process, in memory, never stored or synced:
`lastTick: (profileId, setId) → { at: seconds, wallMs }`. It is set on every
write and dropped when the title is marked finished (`setWatched(…, true)`).
A restarted process starts empty; its first write per title counts 0 s.

**`stepSeconds(prev, at, nowMs)`** — pure, pinned by `stats-step.json`:

```
prev missing                       → 0
dPos  = at - prev.at
dWall = (nowMs - prev.wallMs) / 1000
dPos <= 0 or dWall <= 0            → 0
otherwise                          → min(dPos, dWall, STEP_CAP_SECONDS)
STEP_CAP_SECONDS = 15              (1.5 × the 10 s save tick)
```

So a seek forward counts the wall time spent (min), a pause counts only the
10 s after resuming (min), 2× speed counts wall time, a seek back counts 0.
Known ceiling: a throttled background tab saving once a minute counts 15 s
per minute.

**Start classification** — before the progress upsert, the engine reads
whether a progress row already existed for (profile, set) on this device
(`hadProgress`) and whether a live `watched` row exists
(`watchedLive` = row present and `removed_at IS NULL`):

```
againNow = !hadProgress && watchedLive     // a finished title started over
```

**Then, in the same transaction as the progress upsert:**

1. Own title row `(profile, set, this device)`: insert with
   `started_at = now` if missing; `seconds += step`; `last_watched_at = now`;
   `again_at = now` when `againNow`; `updated_at = MAX(now, stored + 1)`.
2. Own day row `(profile, day, this device)`, only when `step > 0`:
   `seconds += step`; `updated_at = MAX(now, stored + 1)`.

The `MAX(now, stored + 1)` clamp (amended 2026-10-03, the `setWatched` /
preference-stamp pattern): a clock that steps back must never give this
device's newer row an older stamp than a copy other devices already hold,
or the next import would overwrite newer seconds with older ones.

`day` is the recording engine's local date as `YYYY-MM-DD`: the web server's
local date (`new Date(nowMs)` local fields — the household's server); the
core receives it from Kotlin (`LocalDate.now().toString()`) as a new
`set_progress` argument. A step that spans midnight counts on the day of the
write that ends it.

## 2. Local tables (both engines, new migration group each)

```sql
CREATE TABLE IF NOT EXISTS stats_titles(
  profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  set_id TEXT NOT NULL,
  device TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  last_watched_at INTEGER NOT NULL,
  seconds REAL NOT NULL,
  again_at INTEGER,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(profile_id, set_id, device)
);
CREATE TABLE IF NOT EXISTS stats_days(
  profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  day TEXT NOT NULL,
  device TEXT NOT NULL,
  seconds REAL NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(profile_id, day, device)
);
```

Match the engine's existing `progress` table style (the core may not use
`REFERENCES`; follow what its `progress` does). `device` is the engine's own
sync device id (web `state.deviceId()`, core `sync/device.rs`). Rows of other
devices live in the same tables, written only by import. Kept forever — no
retention (decision 5).

## 3. Wire — two new optional keys on `ProfileState`

`SYNC_FORMAT` stays 1 (a bump makes old readers reject the whole document).
Older readers drop the keys (the `unwatched` precedent, commit `fce5a757`).

```ts
interface TitleStatRow { setId: string; device: string; startedAt: number;
  lastWatchedAt: number; seconds: number; againAt?: number; updatedAt: number }
interface DayStatRow { day: string; device: string; seconds: number; updatedAt: number }
// on ProfileState:
titleStats?: TitleStatRow[];
dayStats?: DayStatRow[];
```

- **Export:** every row of the profile in both tables — all devices, not only
  this one (gossip, so a device that goes away keeps its minutes). A key is
  **omitted** when it has no rows, so every existing fixture and every
  pre-stats document stays byte-identical.
- **Parse (hostile input, row by row — a bad row is dropped, never the
  document):** `setId`/`device` non-empty strings; `startedAt`,
  `lastWatchedAt`, `updatedAt`, `seconds` finite and ≥ 0; `againAt` absent, `null` (= absent, amended
  2026-10-03) or finite ≥ 0; numbers must be JSON numbers (no numeric
  strings); `setId`/`device` are trimmed; `day` matches `^\d{4}-\d{2}-\d{2}$`; a `DayStatRow` with
  `seconds > 86400` is dropped (one device cannot watch more than a day per
  day). A key that is present but not an array → treated as absent.
- **Merge:** per viewer (the existing `normalName` match), keep the row with
  the newest `updatedAt` per `(setId, device)` / `(day, device)`, ties by the
  existing `keep()` tie-break (`web/src/state/tie-break.ts`,
  `merge/tie_break.rs`). Merging A+B and B+A gives the same result.
  `MergedProfile` gains `titleStats` / `dayStats` (omitted when empty).
- **Import:** upsert each merged row whose `updatedAt` is newer than the local
  row's (or that is missing); never deletes. A row whose `device` is this
  device and that is newer than local is imported too (a reinstall that kept
  its device id gets its minutes back).
- **Totals are sums across devices** of rows each device owns, so a merge can
  never double-count.

## 4. Summary — `summarize(input) → StatsSummary`

Pure, pinned by `stats-summary.json`. Web: TS; Android: Rust in the core.

```ts
interface SummaryInput {
  today: string;                 // YYYY-MM-DD, the reading engine's local date
  titles: TitleStatRow[];        // every device
  days: DayStatRow[];            // every device
  watched: { setId: string; finishedAt: number }[];  // live rows only
}
interface StatsSummary {
  weekSeconds: number;           // Monday of today's ISO week .. today, inclusive
  monthSeconds: number;          // today's calendar month
  allSeconds: number;            // every day row
  last30: DayBar[];              // 30 entries, oldest first, ending today, zero-filled; DayBar = { day: string; seconds: number }
  history: HistoryEntry[];       // newest first
}
interface HistoryEntry {
  kind: "started" | "finished" | "again";
  setId: string;
  at: number;                    // epoch ms
  seconds: number;               // the title's total across devices (0 if no title row)
}
```

- Day sums add every device's row for that day. Days after `today` are
  ignored for week/month/last30 but count in `allSeconds`.
- History, per `setId`: **started** at the minimum `startedAt` over its title
  rows; **again** at the maximum `againAt` (only if some row has one);
  **finished** at the watched row's `finishedAt` (also for titles with no
  stats rows — finishes from before stats existed are real history).
- Sort: `at` descending, then `setId` ascending, then kind
  `finished` < `again` < `started`.
- Kept forever; the UI shows every entry (lazy list).

## 5. API

- **Web:** `GET /api/profiles/{profileId}/stats` → `200` JSON `StatsSummary`
  (phase 06 adds `achievements`), `404` for an unknown profile, `today` = the
  server's local date. In a new `web/src/state/stats-routes.ts`, dispatched
  from `routes.ts` without growing it past its ceiling.
- **Core (uniffi):** `set_progress(profile_id, set_id, at, duration,
  local_day: String)` — the new last argument; `stats(profile_id: String,
  today: String) -> StatsSummary` (a `uniffi::Record`, async → Kotlin `suspend`; `last30` items are `DayBar`;
  a storage failure answers an empty summary, the core's convention; `kind` as a uniffi
  enum `HistoryKind { Started, Finished, Again }`). Phase 07 adds
  `achievements(profile_id, today, utc_offset_minutes)`.

## 6. The page (web, phone/tablet, TV — Surface Parity, web is reference)

- **Rail item "Stats"** between Genres and Settings (decision 9): web
  `index.html` rail `<a href="#/stats" data-section="stats" class="kept">`;
  Android `RailItem.STATS` declared between `GENRES` and `SETTINGS`.
- **Totals row:** "This week", "This month", "All time".
- **Last 30 days:** one bar per day, height ∝ seconds, today rightmost,
  weekday initial under each bar on wide screens only.
- **History:** newest first, one line per entry:
  `Started · Der Pate · Sat 21:14 · 42 min`, kinds labelled `Started`,
  `Finished`, `Watched again`. Title = the library's own display name for the
  set (an episode as its show's episode label, the way Continue watching names
  it); a set the library no longer holds shows as "No longer in the library".
- **Empty:** when `history` is empty, only the heading and "Nothing watched
  yet." — no zero totals, no empty chart.
- **Failure:** "Could not read your stats: <reason>" (Android: the reason as
  the platform gives it, or "Could not read your stats." when there is none).
- **Bars:** each bar's label/accessibility text "3 Oct · 42 min"; weekday
  initials under the bars hidden below 768 px wide (Android: hidden at
  compact/medium width, shown at expanded width and on TV).
- **Title of a set:** a film by its title; an episode or lesson by its show
  and episode label, the way Continue watching names it
  (`web/public/lib/catalog/home-resume.js:32-33`): "Crime 101 S1E4",
  "Geldhochschule 3".
- **Rail icon:** `<path d="M4.5 20.5h15M7 17v-4.5M12 17V7M17 17v-7.5">` in a
  24×24 box, stroked like its siblings (Android vector drawable, same path).
- **Durations:** `< 1 min` → "under a minute" (also a total of 0 s);
  `< 60 min` → "42 min"; otherwise "3 h 12 min" (minutes floored; "3 h" when
  minutes are 0). A history line whose title has 0 s (a finish from before
  stats existed) shows no duration at all.
- **Times:** today → "today 21:14"; 1–6 calendar days back → weekday + 24 h
  time ("Sat 21:14"); older → "21 Sep" (day unpadded); another year →
  "21 Sep 2025". English, hand-rolled, not locale-formatted.
- Each profile sees only its own page (decision 4).

## 7. Achievements (phases 06–07) — derived, never stored or synced

Pure `achievements(input) → { earned: Earned[], next: Next[] }`, pinned by
`achievements.json`.

```ts
interface AchievementInput {
  today: string; utcOffsetMinutes: number; kids: boolean;
  days: DayStatRow[];
  watched: { setId: string; finishedAt: number }[];      // live rows
  library: { setId: string; kind: "movie"|"ep"|"tut"|"doc"|"docu";
             genres: string[]; collection: string | null }[];
  collections: { id: string; setIds: string[] }[];         // a show's episodes, a course's lessons
}
interface Earned { id: string; earnedAt: number }           // epoch ms
interface Next { id: string; have: number; need: number }
```

A finish's local day = `floor((finishedAt + utcOffsetMinutes·60000) / 86400000)`
as a date. A day-based achievement's `earnedAt` = that day's local midnight in
ms (`dayStart − utcOffsetMinutes·60000`). Finishes of sets the library no
longer holds count for nothing.

| id | kids | earned when | earnedAt |
|---|---|---|---|
| `films-1`, `films-10`, `films-50`, `films-100` | yes | N finished `movie` sets | the Nth smallest `finishedAt` |
| `whole-show` | yes | every set of one collection finished | min over complete collections of max(`finishedAt`) |
| `genres-5`, `genres-10` | yes | finished titles span N distinct genres | the `finishedAt` (ascending) that reaches N |
| `docs-10` | yes | 10 finished `docu` sets | the 10th smallest `finishedAt` |
| `hours-10`, `hours-100`, `hours-500` | **no** | cumulative day seconds ≥ N·3600 | first day (ascending) reaching it |
| `streak-7`, `streak-30` | **no** | N consecutive days with seconds > 0 | the day completing the first such run |
| `binge-5` | **no** | ≥ 5 `ep` finishes on one local day | first such day |

- `earned`: sorted by `earnedAt` descending, then `id`.
- `next`: for each ladder (`films`, `genres`, `docs`, `hours`, `streak`) its
  lowest unearned rung, plus `whole-show` (have/need of the most-finished
  collection) and `binge-5` (have = the most episode finishes on one day)
  while unearned; drop kids-excluded ones; sort by `have/need` descending then
  `id`; keep the first 3. `hours` have/need in whole hours (floored); `streak`
  have = the longest run so far.
- **New-badge dot** on the Stats rail item when an earned id is not in this
  device's "seen" set for the profile; opening the Stats page marks every
  earned id seen. Seen is per device, never synced: web `localStorage`
  `mediagram.stats-seen.<profileId>` (JSON array of ids), Android
  per-profile `SharedPreferences` key. No pop-ups, ever.
- Labels: `films-1` "First film", `films-N` "N films", `whole-show` "A whole
  series", `genres-N` "N genres", `docs-10` "10 documentaries", `hours-N`
  "N hours", `streak-N` "N-day streak", `binge-5` "5 episodes in a day";
  progress line "7 of 10 films".

## 8. Constraints for every phase

- No plan references (phase numbers, decision numbers) in code, test names or
  commit messages.
- Web `CEILINGS` (`web/test/code-standards.test.ts`) are never raised; make
  room by extracting. New files ≤ 200 lines. Rust: ≤ 200 lines per non-test
  file (`cargo test -p mediagram --test code_standards`).
- Versions: all three manifests bumped by pattern, once per phase on its last
  commit (CLAUDE.md § Versioning; memory "bump versions by pattern").
- Branch `feat/viewing-stats` in a worktree off `main`.

## 9. Achievement details settled while planning (2026-10-03)

- A day row dated after `today` counts toward `hours-*` and streaks, as it
  does toward `allSeconds`.
- "Most-finished collection" = highest have/need, a tie to the larger `have`.
- Progress units: "0 of 1 film", "4 of 7 days", "3 of 5 episodes", "8 of 10"
  (whole series); an unknown id shows as itself — pinned in
  `achievement-labels.json`.
- Earned dates show the date only (day-based ones are dated at local midnight).
- Page section between "Last 30 days" and "History", headed "Achievements",
  then "Next".
- Web: the stats route moves to `createRouter` once it needs the catalog.
- A day row with an impossible date (month 13, day 0) counts toward no
  day-based achievement, though it still counts in `allSeconds`.
- Every historic finish is read at today's UTC offset; a finish within an hour
  of midnight from the other DST season can land on the neighbouring day.
- Android reads genres from the channel index only (the web's source), not the
  device-fetched sidecar.
- The dot re-reads on a profile switch and 15 s after the last state change;
  a sync that brings only day rows lights it at the next change. A compact
  phone shows the dot on the root header's Stats button, not in a pushed
  frame's ⋮ menu.
- **Open for the user:** a rewatch moves `finishedAt` later, so a `films-N`
  date can move later (the watched row keeps only the latest finish).
