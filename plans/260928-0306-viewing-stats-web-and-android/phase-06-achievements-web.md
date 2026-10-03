# Phase 06 — Achievements, web: shared fixtures, derivation, the Stats section and the rail dot

**Goal:** the web player works out each profile's achievements from rows it already keeps — every
device's day rows, the live watched marks — and the library it serves, answers them with the
profile's stats, shows them as a section of the Stats page (earned with the day, the next three
with "7 of 10 films"), and puts a dot on the rail's Stats link while something earned is unseen
in this browser. Never stored, never synced, no pop-ups. The JSON fixture written here is what
phase 07 holds the Rust core to.

**Architecture (data flow):**

```
GET /api/profiles/{p}/stats ─ src/routes.ts createRouter (the one router holding the catalog db AND the state)
  ├─ library()  = achievementLibrary(db) — read on the first stats request, kept per router (a catalog swap builds a new one)
  └─ statsRoute(state, request, library) ─▶ WatchState.stats(p, library())
        └─ stats-exchange.readStats(db, p, now, library)
              ├─ summarize({ today: localDay(now), titles, days, watched })            (unchanged summary)
              └─ achievements({ today, utcOffsetMinutes(now), kids, days, watched, ...library })
                    rules: achievements.ts + achievement-rungs.ts   ◀── pinned by achievements.json ──▶ core port (phase 07)
#/stats ─ stats-page.renderStats ─ fetch ─▶ totals · last 30 days · achievementsSection · history
                                         └─▶ markSeen(p, earned)   localStorage "mediagram.stats-seen.<p>" = JSON ids
app.js ─ watchStatsDot(state)  (state = watch-state.js)
   subscribeChanges ─▶ profile switched? read now : read 15 s after the last change (> the 10 s save tick)
   read = GET …/stats ─▶ #stats-dot.hidden = !(earned ⊄ seen)
```

The rules take the contract's `AchievementInput` (§7) and nothing else; the server builds the
library side once per catalog from the same provider rows its catalog route already reads, so a
title counts the genres its own page shows.

## Context links

- Contract (every name, rule, string): [shared-contract.md](shared-contract.md) §5 (route), §6 (page,
  time rules), §7 (achievements), §8 (constraints). Decisions: [plan.md](plan.md) 7 (achievements),
  9 (rail item), 4 (own stats only) — the user's; never reversed here.
- Builds on phase 01 (`stats-record.ts` `DayStatRow`, `stats-summary.ts` `summarize`), phase 02
  (`stats-exchange.ts` `statsRows`/`readSummary`, `stats-recorder.ts` `localDay`, `store.ts`
  `stats()`, `stats-routes.ts` `statsRoute`, `stats-format.js` `whenLabel`, `stats-page.js`
  `renderStats`, `stats.css`, the rail link in `index.html`). Their code is quoted where this phase
  edits it; line numbers in those files are not known yet, so edits are anchored on text.
- Ported by [phase-07-achievements-core-and-android.md](phase-07-achievements-core-and-android.md).
- Code this phase reads (verified 2026-10-03):
  - `web/src/routes.ts:83-102` — `createRouter` holds `db` and builds `stateRoute`; `route()` asks `settings` first.
  - `web/src/catalog/routes.ts:48-75` — `forBrowser`: `posterKeyFor(set.kind, tmdb, set.show ?? set.title)` → `providerFactsByShow(db).get(key)?.genres`.
  - `web/src/catalog.ts:149-153` `listPlayable`; `web/src/catalog/shows.ts:97-136` `providerFactsByShow` (comma split, trimmed; empty map without a `shows` table); `web/src/package/posters.ts:57-66` `posterKeyFor`.
  - `web/public/lib/library.js:248-305` — shelves group episodes by `show`, courses (`tut` + `doc`) by `show`; a course's count excludes documents. `web/public/lib/documentaries.js` groups `docu` by show too (not a series or course).
  - `web/public/lib/watch-state.js:27-37` `subscribeChanges`; `:184-192` `useProfile` → `changed()`; `:211-232` `refreshState` → `changed()` when positions/marks/lists moved (another device's rows pulled by the server).
  - `web/public/app.js:60-80` — `loadPlayer` and its doc (21 lines incl. the blank after), the extraction this phase makes; `:388` `state.subscribeChanges(onShelfAffectingChange);`.
  - `web/public/styles/theme.css:92,147` `--held` (`#a3d3a4` / `#2f6a35` — Android's `tertiary`, `Palette.Sage`/`LightSage`, the held dot's colour); `:171` `.sr-only`; `shell.css:53,199-218` rail link padding 12 px / 10 px (≤ 900 px) / 8 px (≤ 480 px), icon 21 px.
- UI check: `cd web && bun run preview` (copies, no Telegram — memory "verify player UI with a stub harness"), screenshots with the gstack `/browse` skill.

## Global constraints (contract §8)

- No plan references (phase numbers, decision numbers, finding codes) in code, test names or commit messages.
- Web `CEILINGS` (`web/test/code-standards.test.ts`) are never raised; room is made by extracting. New files ≤ 200 lines.
- Versions: all three manifests bumped by pattern (minor), once, on this phase's last commit.
- Branch `feat/viewing-stats` in a worktree off `main`.

**Line budget (after phase 02):** `app.js` 717 → 699 (the lazy player loader moves out; ceiling
lowered to the measured size); `src/state/routes.ts` 246 → 243 (the stats dispatch moves to
`src/routes.ts`; ceiling lowered); `store.ts` 799 → 799 (two lines edited, none added). New files:
`achievements.ts` 118, `achievement-rungs.ts` 144, `achievement-library.ts` 44,
`stats-achievements.js` 90, `stats-dot.js` 91, `player-loader.js` 21.

**File ownership:** `web/**` only, plus the three manifests and `Cargo.lock` on the last commit.
Phase 07 owns `crates/**` and `android/**`; phase 05 owns `docs/**`.

## Review focus

| # | Risk | Test (task) |
|---|---|---|
| R1 | A kids profile sees hours, a streak or a binge — earned or next | fixture "a kids profile is offered no hours, streak or binge, earned or next" (6.1); route "a kids profile is offered no hours, streak or binge, whatever its rows hold" (6.3) |
| R2 | A rewatch moves a film's `finishedAt` later (the watched row keeps the newest finish) | fixture "a rewatch moves a film's finish later, and with it the dates of the rungs it counted for" (6.1) — still earned; the date moves (known ceiling, Questions) |
| R3 | A finish of a set the library no longer holds | fixtures "films are dated by the Nth finish, and a film the library no longer holds counts for nothing", "a collection the library shrank is complete with the episodes it still holds" (6.1); `achievement-library.test.ts` "a set the catalog will not play is not in the library" (6.2) |
| R4 | `utcOffset` at a DST boundary | fixture "every finish is read at the offset passed in, even one from before a clock change" (6.1); route "the offset is this server's at the moment asked, across both clock changes" (6.3) |
| R5 | The dot misses an achievement a sync brought from another device | `stats-dot.test.ts` "lights once a sync brings an achievement earned on another device" (6.5) |
| R6 | One profile's dot shown to another | `stats-dot.test.ts` "a switch puts the last profile's dot out before the new answer arrives", "an answer for a profile no longer chosen is dropped", "seen is kept per profile…" (6.5) |
| R7 | A playing title re-reads the stats every 10 s save tick | `stats-dot.test.ts` "positions saved while a title plays do not re-read the stats" (6.5) |
| R8 | Ordering differs from the Rust port (locale collation, float ratios) | fixtures "earned is newest first, and achievements earned at the same moment sort by id", "next keeps the three closest, equal shares by id", "…equal shares go to the one further in" (6.1); code-unit compare and cross-multiplied ratios in both |
| R9 | A day-based achievement printed as "Sat 00:00" | `stats-achievements-section.test.ts` "earned achievements carry the day they were earned, without a clock time" (6.4) |
| R10 | Seen state unreadable / corrupt in `localStorage` | `stats-dot.test.ts` "an unreadable seen entry counts as nothing shown" (6.5) |
| R11 | Opening the page marks seen even when the answer lands after the viewer left | `stats-page-achievements.test.ts` "an answer that lands after the viewer left records nothing" (6.5) |
| R12 | `app.js` or `src/state/routes.ts` grow past their ceilings | `code-standards.test.ts` (6.3, 6.5) |

---

## Task 6.1: The shared fixture and the pure rules

**Files:**
- Create: `web/test/fixtures/watch-state/achievements.json`
- Create: `web/src/state/achievements.ts`
- Create: `web/src/state/achievement-rungs.ts`
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one import, one `describe`, header sentence)

**Interfaces:**
- Consumes: `DayStatRow` (`web/src/state/stats-record.ts`, phase 01).
- Produces: `achievements(input: AchievementInput): Achievements`; types `TitleKind`, `LibraryTitle`, `LibraryCollection`, `AchievementLibrary`, `AchievementInput` (contract §7, `days: DayStatRow[]`), `Earned`, `Next`, `Achievements`.
- Rules the contract leaves open, decided here and pinned by the fixture (see Questions): `today` is read by no rule (a day row dated after today counts, as it does in `allSeconds`); `whole-show`'s "most-finished collection" = the highest have/need, a tie going to the larger `have` (equal share and equal have are the same answer); a row whose `day` names no date (month 13, day 0) counts toward no day-based achievement — the core's `calendar::day_number` rejects the same rows.

- [ ] **Step 1: The fixture** — `web/test/fixtures/watch-state/achievements.json` (19 cases; every `expect` worked by hand from the contract's table and then checked against the code below; times are real instants so a reader can follow them: `1788559200000` is 2026-09-04T22:00Z, Saturday 5 September 00:00 in CEST)

```json
[
  {
    "name": "nothing watched offers the first rung of every ladder, nearest first",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": ["Drama"], "collection": null},
        {"setId": "e1", "kind": "ep", "genres": ["Drama"], "collection": "ep:Severance"},
        {"setId": "e2", "kind": "ep", "genres": ["Drama"], "collection": "ep:Severance"}
      ],
      "collections": [
        {"id": "ep:Severance", "setIds": ["e1", "e2"]}
      ]
    },
    "expect": {
      "earned": [],
      "next": [
        {"id": "binge-5", "have": 0, "need": 5}, {"id": "docs-10", "have": 0, "need": 10},
        {"id": "films-1", "have": 0, "need": 1}
      ]
    }
  },
  {
    "name": "a kids profile is offered no hours, streak or binge, earned or next",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": true,
      "days": [
        {"day": "2026-09-01", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-02", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-03", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-04", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-05", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-06", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-07", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-08", "device": "tv-1", "seconds": 7200, "updatedAt": 1}
      ],
      "watched": [
        {"setId": "m1", "finishedAt": 1788379200000}, {"setId": "e1", "finishedAt": 1788595200000},
        {"setId": "e2", "finishedAt": 1788598800000}, {"setId": "e3", "finishedAt": 1788602400000},
        {"setId": "e4", "finishedAt": 1788606000000}, {"setId": "e5", "finishedAt": 1788609600000}
      ],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": ["Drama", "Crime"], "collection": null},
        {"setId": "e1", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e3", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e4", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e5", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2", "e3", "e4", "e5"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "whole-show", "earnedAt": 1788609600000}, {"id": "films-1", "earnedAt": 1788379200000}
      ],
      "next": [
        {"id": "genres-5", "have": 3, "need": 5}, {"id": "films-10", "have": 1, "need": 10},
        {"id": "docs-10", "have": 0, "need": 10}
      ]
    }
  },
  {
    "name": "the same rows on a grown-up profile earn hours, the streak and the binge too",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-09-01", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-02", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-03", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-04", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-05", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-06", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-07", "device": "tv-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-08", "device": "tv-1", "seconds": 7200, "updatedAt": 1}
      ],
      "watched": [
        {"setId": "m1", "finishedAt": 1788379200000}, {"setId": "e1", "finishedAt": 1788595200000},
        {"setId": "e2", "finishedAt": 1788598800000}, {"setId": "e3", "finishedAt": 1788602400000},
        {"setId": "e4", "finishedAt": 1788606000000}, {"setId": "e5", "finishedAt": 1788609600000}
      ],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": ["Drama", "Crime"], "collection": null},
        {"setId": "e1", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e3", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e4", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"},
        {"setId": "e5", "kind": "ep", "genres": ["Comedy"], "collection": "ep:Bluey"}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2", "e3", "e4", "e5"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "streak-7", "earnedAt": 1788732000000}, {"id": "whole-show", "earnedAt": 1788609600000},
        {"id": "binge-5", "earnedAt": 1788559200000}, {"id": "hours-10", "earnedAt": 1788559200000},
        {"id": "films-1", "earnedAt": 1788379200000}
      ],
      "next": [
        {"id": "genres-5", "have": 3, "need": 5}, {"id": "streak-30", "have": 8, "need": 30},
        {"id": "hours-100", "have": 16, "need": 100}
      ]
    }
  },
  {
    "name": "films are dated by the Nth finish, and a film the library no longer holds counts for nothing",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "f1", "finishedAt": 1788285600000}, {"setId": "f2", "finishedAt": 1788372000000},
        {"setId": "f3", "finishedAt": 1788458400000}, {"setId": "f4", "finishedAt": 1788544800000},
        {"setId": "f5", "finishedAt": 1788631200000}, {"setId": "f6", "finishedAt": 1788717600000},
        {"setId": "f7", "finishedAt": 1788804000000}, {"setId": "f8", "finishedAt": 1788890400000},
        {"setId": "f9", "finishedAt": 1788976800000}, {"setId": "f10", "finishedAt": 1789063200000},
        {"setId": "f11", "finishedAt": 1789149600000}
      ],
      "library": [
        {"setId": "f1", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f2", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f3", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f5", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f6", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f7", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f8", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f9", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f10", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f11", "kind": "movie", "genres": [], "collection": null}
      ],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "films-10", "earnedAt": 1789149600000}, {"id": "films-1", "earnedAt": 1788285600000}
      ],
      "next": [
        {"id": "films-50", "have": 10, "need": 50}, {"id": "binge-5", "have": 0, "need": 5},
        {"id": "docs-10", "have": 0, "need": 10}
      ]
    }
  },
  {
    "name": "a rewatch moves a film's finish later, and with it the dates of the rungs it counted for",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "f1", "finishedAt": 1789149600000}, {"setId": "f2", "finishedAt": 1788372000000},
        {"setId": "f3", "finishedAt": 1788458400000}, {"setId": "f4", "finishedAt": 1788544800000},
        {"setId": "f5", "finishedAt": 1788631200000}, {"setId": "f6", "finishedAt": 1788717600000},
        {"setId": "f7", "finishedAt": 1788804000000}, {"setId": "f8", "finishedAt": 1788890400000},
        {"setId": "f9", "finishedAt": 1788976800000}, {"setId": "f10", "finishedAt": 1789063200000}
      ],
      "library": [
        {"setId": "f1", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f2", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f3", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f4", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f5", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f6", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f7", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f8", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f9", "kind": "movie", "genres": [], "collection": null},
        {"setId": "f10", "kind": "movie", "genres": [], "collection": null}
      ],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "films-10", "earnedAt": 1789149600000}, {"id": "films-1", "earnedAt": 1788372000000}
      ],
      "next": [
        {"id": "films-50", "have": 10, "need": 50}, {"id": "binge-5", "have": 0, "need": 5},
        {"id": "docs-10", "have": 0, "need": 10}
      ]
    }
  },
  {
    "name": "a whole series is dated by the first collection completed",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "a1", "finishedAt": 1788285600000}, {"setId": "a2", "finishedAt": 1788372000000},
        {"setId": "b1", "finishedAt": 1788458400000}, {"setId": "b2", "finishedAt": 1788544800000},
        {"setId": "a3", "finishedAt": 1788631200000}, {"setId": "c1", "finishedAt": 1788717600000}
      ],
      "library": [
        {"setId": "a1", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "a2", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "a3", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "b1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "b2", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "c1", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c2", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c3", "kind": "tut", "genres": [], "collection": "tut:Rust"}
      ],
      "collections": [
        {"id": "ep:Andor", "setIds": ["a1", "a2", "a3"]}, {"id": "ep:Bluey", "setIds": ["b1", "b2"]},
        {"id": "tut:Rust", "setIds": ["c1", "c2", "c3"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "whole-show", "earnedAt": 1788544800000}
      ],
      "next": [
        {"id": "binge-5", "have": 1, "need": 5}, {"id": "docs-10", "have": 0, "need": 10},
        {"id": "films-1", "have": 0, "need": 1}
      ]
    }
  },
  {
    "name": "the next whole series is the closest collection, a course as much as a show; equal shares go to the one further in",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "andor1", "finishedAt": 1785607200000}, {"setId": "andor2", "finishedAt": 1785693600000},
        {"setId": "andor3", "finishedAt": 1785780000000}, {"setId": "bluey1", "finishedAt": 1785866400000},
        {"setId": "c1", "finishedAt": 1785952800000}, {"setId": "c2", "finishedAt": 1786039200000},
        {"setId": "c3", "finishedAt": 1786125600000}, {"setId": "c4", "finishedAt": 1786212000000},
        {"setId": "c5", "finishedAt": 1786298400000}, {"setId": "c6", "finishedAt": 1786384800000},
        {"setId": "c7", "finishedAt": 1786471200000}, {"setId": "dark1", "finishedAt": 1786557600000},
        {"setId": "dark2", "finishedAt": 1786644000000}, {"setId": "dark3", "finishedAt": 1786730400000},
        {"setId": "dark4", "finishedAt": 1786816800000}, {"setId": "dark5", "finishedAt": 1786903200000},
        {"setId": "dark6", "finishedAt": 1786989600000}
      ],
      "library": [
        {"setId": "andor1", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "andor2", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "andor3", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "andor4", "kind": "ep", "genres": [], "collection": "ep:Andor"},
        {"setId": "bluey1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "bluey2", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "c1", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c2", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c3", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c4", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c5", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c6", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c7", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c8", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c9", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "c10", "kind": "tut", "genres": [], "collection": "tut:Rust"},
        {"setId": "dark1", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark2", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark3", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark4", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark5", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark6", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark7", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "dark8", "kind": "ep", "genres": [], "collection": "ep:Dark"}
      ],
      "collections": [
        {"id": "ep:Andor", "setIds": ["andor1", "andor2", "andor3", "andor4"]},
        {"id": "ep:Bluey", "setIds": ["bluey1", "bluey2"]},
        {"id": "tut:Rust", "setIds": ["c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10"]},
        {"id": "ep:Dark", "setIds": ["dark1", "dark2", "dark3", "dark4", "dark5", "dark6", "dark7", "dark8"]}
      ]
    },
    "expect": {
      "earned": [],
      "next": [
        {"id": "whole-show", "have": 6, "need": 8}, {"id": "binge-5", "have": 1, "need": 5},
        {"id": "docs-10", "have": 0, "need": 10}
      ]
    }
  },
  {
    "name": "a collection the library shrank is complete with the episodes it still holds",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "s1", "finishedAt": 1788285600000}, {"setId": "s3", "finishedAt": 1788372000000},
        {"setId": "s2", "finishedAt": 1788458400000}
      ],
      "library": [
        {"setId": "s1", "kind": "ep", "genres": [], "collection": "ep:Dark"},
        {"setId": "s2", "kind": "ep", "genres": [], "collection": "ep:Dark"}
      ],
      "collections": [
        {"id": "ep:Dark", "setIds": ["s1", "s2"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "whole-show", "earnedAt": 1788458400000}
      ],
      "next": [
        {"id": "binge-5", "have": 1, "need": 5}, {"id": "docs-10", "have": 0, "need": 10},
        {"id": "films-1", "have": 0, "need": 1}
      ]
    }
  },
  {
    "name": "genres count as finishes bring new ones, an episode bringing its show's",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "m1", "finishedAt": 1788285600000}, {"setId": "m2", "finishedAt": 1788372000000},
        {"setId": "e1", "finishedAt": 1788458400000}, {"setId": "d1", "finishedAt": 1788544800000},
        {"setId": "m3", "finishedAt": 1788631200000}
      ],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": ["Drama", "Crime"], "collection": null},
        {"setId": "m2", "kind": "movie", "genres": ["Drama"], "collection": null},
        {"setId": "e1", "kind": "ep", "genres": ["Comedy", "Romance"], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": ["Comedy", "Romance"], "collection": "ep:Bluey"},
        {"setId": "d1", "kind": "docu", "genres": ["Documentary", "History"], "collection": null},
        {"setId": "m3", "kind": "movie", "genres": ["Action"], "collection": null}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "genres-5", "earnedAt": 1788544800000}, {"id": "films-1", "earnedAt": 1788285600000}
      ],
      "next": [
        {"id": "genres-10", "have": 7, "need": 10}, {"id": "whole-show", "have": 1, "need": 2},
        {"id": "films-10", "have": 3, "need": 10}
      ]
    }
  },
  {
    "name": "ten finished documentaries",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "d1", "finishedAt": 1788285600000}, {"setId": "d2", "finishedAt": 1788372000000},
        {"setId": "d3", "finishedAt": 1788458400000}, {"setId": "d4", "finishedAt": 1788544800000},
        {"setId": "d5", "finishedAt": 1788631200000}, {"setId": "d6", "finishedAt": 1788717600000},
        {"setId": "d7", "finishedAt": 1788804000000}, {"setId": "d8", "finishedAt": 1788890400000},
        {"setId": "d9", "finishedAt": 1788976800000}, {"setId": "d10", "finishedAt": 1789063200000}
      ],
      "library": [
        {"setId": "d1", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d2", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d3", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d4", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d5", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d6", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d7", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d8", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d9", "kind": "docu", "genres": [], "collection": null},
        {"setId": "d10", "kind": "docu", "genres": [], "collection": null}
      ],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "docs-10", "earnedAt": 1789063200000}
      ],
      "next": [
        {"id": "binge-5", "have": 0, "need": 5}, {"id": "films-1", "have": 0, "need": 1},
        {"id": "genres-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "hours sum every device's rows per day and are dated at that day's local midnight",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-09-01", "device": "tv-1", "seconds": 18000, "updatedAt": 1},
        {"day": "2026-09-01", "device": "phone-1", "seconds": 7200, "updatedAt": 1},
        {"day": "2026-09-03", "device": "tv-1", "seconds": 10800, "updatedAt": 1}
      ],
      "watched": [],
      "library": [],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "hours-10", "earnedAt": 1788386400000}
      ],
      "next": [
        {"id": "streak-7", "have": 1, "need": 7}, {"id": "hours-100", "have": 10, "need": 100},
        {"id": "binge-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "a gap restarts a streak, a day with no seconds is a gap, and the longest run is the progress",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-09-01", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-02", "device": "tv-1", "seconds": 300, "updatedAt": 1},
        {"day": "2026-09-02", "device": "phone-1", "seconds": 300, "updatedAt": 1},
        {"day": "2026-09-03", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-04", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-06", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-07", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-08", "device": "tv-1", "seconds": 600, "updatedAt": 1},
        {"day": "2026-09-09", "device": "tv-1", "seconds": 0, "updatedAt": 1},
        {"day": "2026-09-10", "device": "tv-1", "seconds": 600, "updatedAt": 1}
      ],
      "watched": [],
      "library": [],
      "collections": []
    },
    "expect": {
      "earned": [],
      "next": [
        {"id": "streak-7", "have": 4, "need": 7}, {"id": "hours-10", "have": 1, "need": 10},
        {"id": "binge-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "a seven-day streak is dated by the day completing it, across a month's end",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-09-28", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-09-29", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-09-30", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-10-01", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-10-02", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-10-03", "device": "tv-1", "seconds": 1200, "updatedAt": 1},
        {"day": "2026-10-04", "device": "tv-1", "seconds": 1200, "updatedAt": 1}
      ],
      "watched": [],
      "library": [],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "streak-7", "earnedAt": 1791064800000}
      ],
      "next": [
        {"id": "streak-30", "have": 7, "need": 30}, {"id": "hours-10", "have": 2, "need": 10},
        {"id": "binge-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "five episodes on one local day at the reader's offset are a binge",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "e1", "finishedAt": 1788559800000}, {"setId": "e2", "finishedAt": 1788588000000},
        {"setId": "e3", "finishedAt": 1788609600000}, {"setId": "e4", "finishedAt": 1788631200000},
        {"setId": "e5", "finishedAt": 1788645000000}
      ],
      "library": [
        {"setId": "e1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e3", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e4", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e5", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e6", "kind": "ep", "genres": [], "collection": "ep:Bluey"}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2", "e3", "e4", "e5", "e6"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "binge-5", "earnedAt": 1788559200000}
      ],
      "next": [
        {"id": "whole-show", "have": 5, "need": 6}, {"id": "docs-10", "have": 0, "need": 10},
        {"id": "films-1", "have": 0, "need": 1}
      ]
    }
  },
  {
    "name": "the same five finishes split over two days at offset zero are not",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 0, "kids": false,
      "days": [],
      "watched": [
        {"setId": "e1", "finishedAt": 1788559800000}, {"setId": "e2", "finishedAt": 1788588000000},
        {"setId": "e3", "finishedAt": 1788609600000}, {"setId": "e4", "finishedAt": 1788631200000},
        {"setId": "e5", "finishedAt": 1788645000000}
      ],
      "library": [
        {"setId": "e1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e3", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e4", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e5", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e6", "kind": "ep", "genres": [], "collection": "ep:Bluey"}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2", "e3", "e4", "e5", "e6"]}
      ]
    },
    "expect": {
      "earned": [],
      "next": [
        {"id": "whole-show", "have": 5, "need": 6}, {"id": "binge-5", "have": 4, "need": 5},
        {"id": "docs-10", "have": 0, "need": 10}
      ]
    }
  },
  {
    "name": "every finish is read at the offset passed in, even one from before a clock change",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "e1", "finishedAt": 1768084200000}, {"setId": "e2", "finishedAt": 1768118400000},
        {"setId": "e3", "finishedAt": 1768125600000}, {"setId": "e4", "finishedAt": 1768132800000},
        {"setId": "e5", "finishedAt": 1768140000000}
      ],
      "library": [
        {"setId": "e1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e3", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e4", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e5", "kind": "ep", "genres": [], "collection": "ep:Bluey"}
      ],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "binge-5", "earnedAt": 1768082400000}
      ],
      "next": [
        {"id": "docs-10", "have": 0, "need": 10}, {"id": "films-1", "have": 0, "need": 1},
        {"id": "genres-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "earned is newest first, and achievements earned at the same moment sort by id",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [],
      "watched": [
        {"setId": "m1", "finishedAt": 1788285600000}
      ],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": ["Action", "Comedy", "Drama", "Horror", "Western"], "collection": null}
      ],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "films-1", "earnedAt": 1788285600000}, {"id": "genres-5", "earnedAt": 1788285600000}
      ],
      "next": [
        {"id": "genres-10", "have": 5, "need": 10}, {"id": "films-10", "have": 1, "need": 10},
        {"id": "binge-5", "have": 0, "need": 5}
      ]
    }
  },
  {
    "name": "next keeps the three closest, equal shares by id",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-09-01", "device": "tv-1", "seconds": 18000, "updatedAt": 1}
      ],
      "watched": [
        {"setId": "m1", "finishedAt": 1788285600000}, {"setId": "m2", "finishedAt": 1788372000000},
        {"setId": "m3", "finishedAt": 1788458400000}, {"setId": "m4", "finishedAt": 1788544800000},
        {"setId": "m5", "finishedAt": 1788631200000}, {"setId": "e1", "finishedAt": 1788717600000}
      ],
      "library": [
        {"setId": "m1", "kind": "movie", "genres": [], "collection": null},
        {"setId": "m2", "kind": "movie", "genres": [], "collection": null},
        {"setId": "m3", "kind": "movie", "genres": [], "collection": null},
        {"setId": "m4", "kind": "movie", "genres": [], "collection": null},
        {"setId": "m5", "kind": "movie", "genres": [], "collection": null},
        {"setId": "e1", "kind": "ep", "genres": [], "collection": "ep:Bluey"},
        {"setId": "e2", "kind": "ep", "genres": [], "collection": "ep:Bluey"}
      ],
      "collections": [
        {"id": "ep:Bluey", "setIds": ["e1", "e2"]}
      ]
    },
    "expect": {
      "earned": [
        {"id": "films-1", "earnedAt": 1788285600000}
      ],
      "next": [
        {"id": "films-10", "have": 5, "need": 10}, {"id": "hours-10", "have": 5, "need": 10},
        {"id": "whole-show", "have": 1, "need": 2}
      ]
    }
  },
  {
    "name": "a day row dated after today counts like any other",
    "input": {
      "today": "2026-10-03", "utcOffsetMinutes": 120, "kids": false,
      "days": [
        {"day": "2026-10-04", "device": "phone-1", "seconds": 36000, "updatedAt": 1}
      ],
      "watched": [],
      "library": [],
      "collections": []
    },
    "expect": {
      "earned": [
        {"id": "hours-10", "earnedAt": 1791064800000}
      ],
      "next": [
        {"id": "streak-7", "have": 1, "need": 7}, {"id": "hours-100", "have": 10, "need": 100},
        {"id": "binge-5", "have": 0, "need": 5}
      ]
    }
  }
]
```

- [ ] **Step 2: The runner** — `web/test/shared-watch-state-fixtures.test.ts`

After the last import add:

```ts
import { achievements, type AchievementInput, type Achievements } from "../src/state/achievements";
```

In the header comment, replace `against the web's own` + `record parsing, merge, resume and Next up logic.` (lines 2-3) with `against the web's own` + `record parsing, merge, resume, Next up and achievements logic.`

Append:

```ts
describe("achievements fixtures", () => {
  interface Case {
    name: string;
    input: AchievementInput;
    expect: Achievements;
  }

  for (const one of load<Case[]>("achievements.json")) {
    test(one.name, () => {
      expect(achievements(one.input)).toEqual(one.expect);
    });
  }
});
```

- [ ] **Step 3: Run, expect a failure**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — `Cannot find module '../src/state/achievements'`.

- [ ] **Step 4: Implement** — `web/src/state/achievements.ts`

```ts
/**
 * Achievements, worked out on every read from rows both engines already
 * keep — every device's day rows, the live watched marks and the library —
 * and never stored or synced: an achievement is a fact about those rows, so
 * two devices holding the same rows cannot disagree about one.
 *
 * `crates/mediagram-core/src/state/stats/achievements.rs` is a port of this
 * file; `test/fixtures/watch-state/achievements.json` pins the two together.
 */

import { binge, counted, DAY_MS, dayTotals, genreArrivals, hours, streak, wholeShow, type Finish, type Rung } from "./achievement-rungs";
import type { DayStatRow } from "./stats-record";

export type TitleKind = "movie" | "ep" | "tut" | "doc" | "docu";

/** One set the library holds, as the rules read it. */
export interface LibraryTitle {
  setId: string;
  kind: TitleKind;
  /** The provider's genres; an episode carries its show's. */
  genres: string[];
  /** The show or course it belongs to, or `null`. */
  collection: string | null;
}

/** A show's episodes or a course's lessons, as the library holds them now. */
export interface LibraryCollection {
  id: string;
  setIds: string[];
}

export interface AchievementLibrary {
  library: LibraryTitle[];
  collections: LibraryCollection[];
}

export interface AchievementInput extends AchievementLibrary {
  /** The reading engine's local date, `YYYY-MM-DD`. */
  today: string;
  /** The reading engine's offset from UTC now, in minutes: `120` in CEST. */
  utcOffsetMinutes: number;
  /** A kids profile is offered finishing and exploring achievements only. */
  kids: boolean;
  /** Every device's day rows. */
  days: DayStatRow[];
  /** Live watched rows only. */
  watched: { setId: string; finishedAt: number }[];
}

export interface Earned {
  id: string;
  /** Epoch milliseconds. */
  earnedAt: number;
}

export interface Next {
  id: string;
  have: number;
  need: number;
}

export interface Achievements {
  earned: Earned[];
  next: Next[];
}

const FILMS = [1, 10, 50, 100];
const GENRES = [5, 10];
const DOCS = [10];
const HOURS = [10, 100, 500];
const STREAKS = [7, 30];
const BINGE = 5;
/** How many of the closest unearned achievements a page shows. */
const NEXT_SHOWN = 3;

/** Plain code-unit order, the same as Rust's `str` ordering — never the locale's. */
const byId = (a: { id: string }, b: { id: string }) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);

export function achievements(input: AchievementInput): Achievements {
  const offsetMs = input.utcOffsetMinutes * 60_000;
  // A day's local midnight, at the offset the reader is at now — applied to
  // every day alike, so a day from before a clock change reads an hour out.
  const midnight = (day: number) => day * DAY_MS - offsetMs;
  const bySet = new Map(input.library.map((title) => [title.setId, title]));
  // A finish of a set the library no longer holds counts for nothing.
  const finishes: Finish[] = input.watched
    .flatMap((row) => {
      const title = bySet.get(row.setId);
      return title ? [{ title, at: row.finishedAt }] : [];
    })
    .sort((a, b) => a.at - b.at);
  const timesOf = (kind: TitleKind) => finishes.filter((finish) => finish.title.kind === kind).map((finish) => finish.at);
  const finishedAt = new Map(finishes.map((finish) => [finish.title.setId, finish.at]));

  const ladders: Rung[][] = [
    counted("films", FILMS, timesOf("movie")),
    counted("genres", GENRES, genreArrivals(finishes)),
    counted("docs", DOCS, timesOf("docu")),
    wholeShow(input.collections, finishedAt),
  ];
  if (!input.kids) {
    const days = dayTotals(input.days);
    ladders.push(hours(HOURS, days, midnight), streak(STREAKS, days, midnight), [binge(BINGE, finishes, offsetMs, midnight)]);
  }

  const earned: Earned[] = [];
  const next: Next[] = [];
  for (const ladder of ladders) {
    for (const { id, earnedAt } of ladder) if (earnedAt !== null) earned.push({ id, earnedAt });
    const open = ladder.find((rung) => rung.earnedAt === null);
    if (open) next.push({ id: open.id, have: open.have, need: open.need });
  }
  earned.sort((a, b) => b.earnedAt - a.earnedAt || byId(a, b));
  // Closest first: have/need compared without dividing.
  next.sort((a, b) => b.have * a.need - a.have * b.need || byId(a, b));
  return { earned, next: next.slice(0, NEXT_SHOWN) };
}
```

`web/src/state/achievement-rungs.ts`

```ts
/**
 * The rules behind each achievement, one ladder each — split out of
 * `achievements.ts`, which gathers them. Every function answers the same
 * shape, a {@link Rung} per id: when it was earned, or `null`, and the
 * progress towards it.
 */

import type { LibraryCollection, LibraryTitle } from "./achievements";

export const DAY_MS = 86_400_000;

/** One achievement of a ladder: earned at `earnedAt`, or not yet (`null`). */
export interface Rung {
  id: string;
  earnedAt: number | null;
  have: number;
  need: number;
}

/** A finish the library still holds, oldest first. */
export interface Finish {
  title: LibraryTitle;
  at: number;
}

/** One local day's seconds across every device, as days since 1970-01-01. */
export interface DayTotal {
  day: number;
  seconds: number;
}

/** A finish's local day at `offsetMs`, as days since 1970-01-01. */
const finishDay = (at: number, offsetMs: number) => Math.floor((at + offsetMs) / DAY_MS);

const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Days since 1970-01-01 for a `YYYY-MM-DD` date, or `null` for one naming no
 * month or day — the core's `calendar::day_number`, which rolls a 31
 * February into March exactly as `Date.UTC` does.
 */
function epochDay(date: string): number | null {
  const parts = DATE.exec(date);
  if (!parts) return null;
  const [year, month, day] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  return Date.UTC(year, month - 1, day) / DAY_MS;
}

/**
 * Every device's rows summed per local day, oldest day first. A row whose
 * day names no date counts toward no day-based achievement.
 */
export function dayTotals(rows: { day: string; seconds: number }[]): DayTotal[] {
  const byDay = new Map<number, number>();
  for (const row of rows) {
    const day = epochDay(row.day);
    if (day !== null) byDay.set(day, (byDay.get(day) ?? 0) + row.seconds);
  }
  return [...byDay].map(([day, seconds]) => ({ day, seconds })).sort((a, b) => a.day - b.day);
}

/** Rung N of a counting ladder is earned by the Nth time in `times`, which is ascending. */
export function counted(name: string, rungs: number[], times: number[]): Rung[] {
  return rungs.map((need) => ({ id: `${name}-${need}`, earnedAt: times[need - 1] ?? null, have: times.length, need }));
}

/** When each new distinct genre arrived: entry K is the finish that brought the (K+1)th. */
export function genreArrivals(finishes: Finish[]): number[] {
  const seen = new Set<string>();
  const arrivals: number[] = [];
  for (const { title, at } of finishes) {
    for (const genre of title.genres) seen.add(genre);
    while (arrivals.length < seen.size) arrivals.push(at);
  }
  return arrivals;
}

/** Cumulative watching across days; a rung is earned on the day the total reaches it. */
export function hours(rungs: number[], days: DayTotal[], midnight: (day: number) => number): Rung[] {
  let total = 0;
  const reached = new Map<number, number>();
  for (const { day, seconds } of days) {
    total += seconds;
    for (const need of rungs) if (!reached.has(need) && total >= need * 3600) reached.set(need, midnight(day));
  }
  const have = Math.floor(total / 3600);
  return rungs.map((need) => ({ id: `hours-${need}`, earnedAt: reached.get(need) ?? null, have, need }));
}

/** Consecutive days with any watching; a rung is earned on the day completing the first such run. */
export function streak(rungs: number[], days: DayTotal[], midnight: (day: number) => number): Rung[] {
  let run = 0;
  let longest = 0;
  let previous = Number.NaN;
  const reached = new Map<number, number>();
  for (const { day, seconds } of days) {
    if (seconds <= 0) continue;
    run = day === previous + 1 ? run + 1 : 1;
    previous = day;
    longest = Math.max(longest, run);
    for (const need of rungs) if (run === need && !reached.has(need)) reached.set(need, midnight(day));
  }
  return rungs.map((need) => ({ id: `streak-${need}`, earnedAt: reached.get(need) ?? null, have: longest, need }));
}

/** Episodes finished on one local day; earned on the first day reaching `need`. */
export function binge(need: number, finishes: Finish[], offsetMs: number, midnight: (day: number) => number): Rung {
  const perDay = new Map<number, number>();
  let most = 0;
  let first: number | null = null;
  for (const { title, at } of finishes) {
    if (title.kind !== "ep") continue;
    const day = finishDay(at, offsetMs);
    const count = (perDay.get(day) ?? 0) + 1;
    perDay.set(day, count);
    most = Math.max(most, count);
    if (count === need && first === null) first = day;
  }
  return { id: `binge-${need}`, earnedAt: first === null ? null : midnight(first), have: most, need };
}

/**
 * Every set of one collection finished. Earned when the first collection was
 * completed; until then, the progress of the one closest to it. Nothing at
 * all for a library with no collections.
 */
export function wholeShow(collections: LibraryCollection[], finishedAt: Map<string, number>): Rung[] {
  let earnedAt: number | null = null;
  let best: { have: number; need: number } | null = null;
  for (const { setIds } of collections) {
    const need = setIds.length;
    if (need === 0) continue;
    const times = setIds.flatMap((setId) => finishedAt.get(setId) ?? []);
    const have = times.length;
    if (have === need) earnedAt = Math.min(earnedAt ?? Infinity, Math.max(...times));
    // Closest first; equal shares with equal counts are equal answers.
    if (best === null || have * best.need > best.have * need || (have * best.need === best.have * need && have > best.have)) {
      best = { have, need };
    }
  }
  if (best === null) return [];
  return [{ id: "whole-show", earnedAt, ...best }];
}
```

- [ ] **Step 5: Run, expect PASS**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts test/code-standards.test.ts && bun run typecheck`
Expected: 0 fail, the 19 `achievements fixtures` cases among the passes; `tsc` silent. `wc -l src/state/achievements.ts src/state/achievement-rungs.ts` → 118, 144.

- [ ] **Step 6: Commit**

```bash
git add web/src/state/achievements.ts web/src/state/achievement-rungs.ts \
  web/test/fixtures/watch-state/achievements.json web/test/shared-watch-state-fixtures.test.ts
git commit -m "feat(web): derive achievements from viewing-stats rows, pinned by shared fixtures"
```

---

## Task 6.2: The library the achievements count, from the catalog

**Files:**
- Create: `web/src/state/achievement-library.ts`
- Create: `web/test/achievement-library.test.ts`

**Interfaces:**
- Consumes: `listPlayable` (`src/catalog.ts:149`), `providerFactsByShow` (`src/catalog/shows.ts:97`), `posterKeyFor` (`src/package/posters.ts:57`).
- Produces: `achievementLibrary(db: Database): AchievementLibrary`; `collectionOf(kind, show): string | null` (`"ep:<show>"`, `"tut:<show>"`, else `null`).
- Decisions (the shelves' own, `library.js:248-305`): episodes group by `show`, lessons by `show`; a course's documents (`doc`) are not lessons; an episode with no show joins no collection (the shelves' "Unknown show" is a place to file it, not a series to finish); documentary collections (`docu` by show) are neither a series nor a course. Genres come from the same provider row the catalog route attaches (`posterKeyFor(kind, tmdb, show ?? title)`), so only titles with a provider id carry any.

- [ ] **Step 1: Failing tests** — `web/test/achievement-library.test.ts`

```ts
import { describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { achievementLibrary } from "../src/state/achievement-library";
import { emptyIndex } from "./index-fixture";

const SHOWS = `CREATE TABLE shows(
    source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL,
    lang TEXT NOT NULL DEFAULT '',
    overview TEXT, tagline TEXT, genres TEXT, rating REAL,
    network TEXT, status TEXT, first_air TEXT, last_air TEXT,
    total_seasons INTEGER, total_episodes INTEGER,
    PRIMARY KEY(source, kind, id))`;

/** A set with no parts, which `PLAYABLE_SQL` plays: zero parts done of zero, zero bytes of zero. */
function add(db: Database, setId: string, kind: string, show: string | null = null, tmdb: number | null = null, status = "complete") {
  db.run(
    `INSERT INTO sets(set_id, kind, title, show, tmdb, container, total, part_count, status, created_at, spec_version)
     VALUES (?1, ?2, ?1, ?3, ?4, 'mkv', 0, 0, ?5, 1, 3)`,
    [setId, kind, show, tmdb, status],
  );
}

function described(db: Database, kind: "movie" | "tv", id: number, genres: string) {
  db.run("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', ?1, ?2, ?3)", [kind, id, genres]);
}

const sorted = (facts: ReturnType<typeof achievementLibrary>) => ({
  library: [...facts.library].sort((a, b) => a.setId.localeCompare(b.setId)),
  collections: facts.collections.map((c) => ({ ...c, setIds: [...c.setIds].sort() })).sort((a, b) => a.id.localeCompare(b.id)),
});

describe("the library the achievements count", () => {
  test("a film carries its own genres and an episode its show's", () => {
    const db = emptyIndex();
    db.run(SHOWS);
    add(db, "heat", "movie", null, 949);
    add(db, "bb1", "ep", "Breaking Bad", 1396);
    described(db, "movie", 949, "Crime, Drama");
    described(db, "tv", 1396, "Drama");
    expect(sorted(achievementLibrary(db)).library).toEqual([
      { setId: "bb1", kind: "ep", genres: ["Drama"], collection: "ep:Breaking Bad" },
      { setId: "heat", kind: "movie", genres: ["Crime", "Drama"], collection: null },
    ]);
  });

  test("episodes group by show and lessons by course; documents, documentaries and a set with no show join none", () => {
    const db = emptyIndex();
    add(db, "e1", "ep", "Dark");
    add(db, "e2", "ep", "Dark");
    add(db, "lone", "ep");
    add(db, "l1", "tut", "Rust");
    add(db, "handout", "doc", "Rust");
    add(db, "terra", "docu", "Terra X");
    const facts = sorted(achievementLibrary(db));
    expect(facts.collections).toEqual([{ id: "ep:Dark", setIds: ["e1", "e2"] }, { id: "tut:Rust", setIds: ["l1"] }]);
    expect(facts.library.filter((title) => title.collection === null).map((title) => title.setId)).toEqual(["handout", "lone", "terra"]);
  });

  test("a set the catalog will not play is not in the library", () => {
    const db = emptyIndex();
    add(db, "ready", "movie");
    add(db, "uploading", "movie", null, null, "pending");
    expect(achievementLibrary(db).library.map((title) => title.setId)).toEqual(["ready"]);
  });

  test("an index with no provider table still lists its titles, without genres", () => {
    const db = emptyIndex();
    add(db, "heat", "movie", null, 949);
    expect(achievementLibrary(db).library).toEqual([{ setId: "heat", kind: "movie", genres: [], collection: null }]);
  });
});
```

- [ ] **Step 2: Run, expect a failure**

Run: `cd web && bun test test/achievement-library.test.ts`
Expected: FAIL — `Cannot find module '../src/state/achievement-library'`.

- [ ] **Step 3: Implement** — `web/src/state/achievement-library.ts`

```ts
/**
 * What the achievement rules need from the catalog: each playable set's
 * kind, genres and collection, and each show's episodes and course's
 * lessons. Read once per catalog, by the router that serves it, the way the
 * catalog router reads its own provider facts.
 *
 * `crates/mediagram-core/src/catalog_achievements.rs` builds the same from
 * the same index, so both surfaces count one library alike.
 */

import type { Database } from "bun:sqlite";
import { listPlayable } from "../catalog";
import { providerFactsByShow } from "../catalog/shows";
import { posterKeyFor } from "../package/posters";
import type { AchievementLibrary, LibraryTitle, TitleKind } from "./achievements";

export function achievementLibrary(db: Database): AchievementLibrary {
  const provider = providerFactsByShow(db);
  const library: LibraryTitle[] = [];
  const members = new Map<string, string[]>();
  for (const set of listPlayable(db)) {
    // The key the catalog route files a set's provider facts under, so a
    // title counts the genres its own page shows.
    const key = posterKeyFor(set.kind, set.tmdb, set.show ?? set.title);
    const collection = collectionOf(set.kind, set.show);
    // The index holds the five kinds `mlib_spec` defines and no other.
    library.push({ setId: set.setId, kind: set.kind as TitleKind, genres: (key && provider.get(key)?.genres) || [], collection });
    if (collection === null) continue;
    const list = members.get(collection);
    if (list) list.push(set.setId);
    else members.set(collection, [set.setId]);
  }
  return { library, collections: [...members].map(([id, setIds]) => ({ id, setIds })) };
}

/**
 * A show's episodes or a course's lessons, by name, the way the shelves
 * group them (`collections` in `public/lib/library.js`). A course's
 * documents are not lessons, and a set with no show belongs to none: the
 * shelves' "Unknown show" is a place to file it, not a series to finish.
 */
export function collectionOf(kind: string, show: string | null): string | null {
  return (kind === "ep" || kind === "tut") && show ? `${kind}:${show}` : null;
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `cd web && bun test test/achievement-library.test.ts && bun run typecheck`
Expected: 4 pass; `tsc` silent.

- [ ] **Step 5: Commit**

```bash
git add web/src/state/achievement-library.ts web/test/achievement-library.test.ts
git commit -m "feat(web): read the library achievements count from the catalog"
```

---

## Task 6.3: The stats answer carries `achievements`

The stats route now needs the catalog as well as the state. `createStateRouter` holds only the
state (and is at its ceiling, 246/246 after phase 02); `createRouter` in `src/routes.ts` holds
both and is rebuilt with every catalog swap. So the stats dispatch moves there, ahead of the
state router — still ahead of its write gate, which is what phase 02's route test pins — and the
library facts are read once per router, on the first stats request.

**Files:**
- Modify: `web/src/state/stats-recorder.ts` (`utcOffsetMinutes` beside `localDay`)
- Modify: `web/src/state/stats-exchange.ts` (`readSummary` becomes `readStats`; `NO_LIBRARY`)
- Modify: `web/src/state/store.ts` (two existing lines; no line added)
- Modify: `web/src/state/stats-routes.ts` (the `library` argument)
- Modify: `web/src/state/routes.ts` (the stats dispatch leaves: −3 lines)
- Modify: `web/src/routes.ts` (the library, read once per router; the stats dispatch)
- Modify: `web/test/state-stats-route.test.ts` (through `createRouter`)
- Modify: `web/test/code-standards.test.ts` (`src/state/routes.ts` 246 → 243)
- Create: `web/test/stats-achievements-route.test.ts`

**Interfaces:**
- Produces: `utcOffsetMinutes(ms): number` (`0 - new Date(ms).getTimezoneOffset()`: `120` in CEST, a plain `0` in UTC, never `-0`); `NO_LIBRARY: AchievementLibrary`; `readStats(db, profileId, nowMs, library): StatsSummary & { achievements: Achievements }`; `WatchState.stats(profileId, library = NO_LIBRARY)`; `statsRoute(state, request, library: () => AchievementLibrary)`.
- The JSON answer is phase 02's `StatsSummary` plus `achievements: { earned: Earned[], next: Next[] }` (contract §5 "phase 06 adds `achievements`").
- `kids` is read from the store's own `profiles` row (`kids` column, schema v7) — a viewer made a kids profile on another device arrives through `importMerged`'s upgrade (`store.ts:436-440`).

- [ ] **Step 1: Failing tests** — `web/test/stats-achievements-route.test.ts`

```ts
/**
 * The Stats answer's `achievements`, through the router that serves it: the
 * kids flag read from the store, the library read from the catalog, the
 * rules from `achievements.ts`, the offset from this server's clock.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { ByteSource } from "../src/http/stream";
import { createRouter } from "../src/routes";
import type { Achievements } from "../src/state/achievements";
import type { MergedProfile } from "../src/state/merge";
import { utcOffsetMinutes } from "../src/state/stats-recorder";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const SHOWS = `CREATE TABLE shows(
    source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL,
    lang TEXT NOT NULL DEFAULT '',
    overview TEXT, tagline TEXT, genres TEXT, rating REAL,
    network TEXT, status TEXT, first_air TEXT, last_air TEXT,
    total_seasons INTEGER, total_episodes INTEGER,
    PRIMARY KEY(source, kind, id))`;
const FILM = "01SET0000000000000000001";
const FINISHED = Date.parse("2026-09-02T20:00:00Z");

/** One playable film the provider calls Crime and Drama. */
function catalog(): Database {
  const db = emptyIndex();
  db.run(SHOWS);
  db.run(
    `INSERT INTO sets(set_id, kind, title, tmdb, container, total, part_count, status, created_at, spec_version)
     VALUES (?1, 'movie', 'Heat', 949, 'mkv', 0, 0, 'complete', 1, 3)`,
    [FILM],
  );
  db.run("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 949, 'Crime, Drama')");
  return db;
}

/** A viewer as another device's document brings them: the film finished, eight days of two hours on the television. */
function viewer(displayName: string, kids: boolean): MergedProfile {
  return {
    name: displayName.toLowerCase(),
    displayName,
    ...(kids ? { kids: true as const } : {}),
    progress: [],
    watched: [{ setId: FILM, updatedAt: FINISHED }],
    dayStats: Array.from({ length: 8 }, (_, i) => ({ day: `2026-09-0${i + 1}`, device: "tv-1", seconds: 7200, updatedAt: FINISHED })),
  };
}

let dir: string;
let state: WatchState;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "mediagram-achievements-route-"));
  state = new WatchState(join(dir, "state.db"));
});
afterEach(() => {
  state.close();
  rmSync(dir, { recursive: true, force: true });
});

async function achievementsOf(name: string): Promise<Achievements> {
  const id = state.profiles().find((profile) => profile.name === name)!.id;
  const route = createRouter({ db: catalog(), source: NO_BYTES, state });
  const response = await route({ method: "GET", path: `/api/profiles/${id}/stats`, range: null });
  expect(response.status).toBe(200);
  return JSON.parse(await new Response(response.body).text()).achievements;
}

const idsOf = ({ earned, next }: Achievements) => [...earned, ...next].map((achievement) => achievement.id);

test("a kids profile is offered no hours, streak or binge, whatever its rows hold", async () => {
  state.importMerged({ profiles: [viewer("Kid", true)] });
  const ids = idsOf(await achievementsOf("Kid"));
  expect(ids).toContain("films-1");
  expect(ids.filter((id) => /^(hours|streak|binge)-/.test(id))).toEqual([]);
});

test("a grown-up's answer earns from the same rows, the film counting its catalog genres", async () => {
  state.importMerged({ profiles: [viewer("Grown", false)] });
  const achievements = await achievementsOf("Grown");
  expect(achievements.earned.map((achievement) => achievement.id)).toEqual(
    expect.arrayContaining(["films-1", "hours-10", "streak-7"]),
  );
  expect(achievements.next).toContainEqual({ id: "genres-5", have: 2, need: 5 });
});

test("the offset is this server's at the moment asked, across both clock changes", () => {
  const before = process.env.TZ;
  try {
    process.env.TZ = "Europe/Berlin";
    expect(utcOffsetMinutes(Date.parse("2026-03-29T00:59:00Z"))).toBe(60);
    expect(utcOffsetMinutes(Date.parse("2026-03-29T01:00:00Z"))).toBe(120);
    expect(utcOffsetMinutes(Date.parse("2026-10-25T00:59:00Z"))).toBe(120);
    expect(utcOffsetMinutes(Date.parse("2026-10-25T01:00:00Z"))).toBe(60);
    process.env.TZ = "UTC";
    expect(utcOffsetMinutes(0)).toBe(0);
  } finally {
    // Bun reads TZ on every date call; leaving it set would move every later test's clock.
    if (before === undefined) delete process.env.TZ;
    else process.env.TZ = before;
  }
});
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/stats-achievements-route.test.ts`
Expected: FAIL — `utcOffsetMinutes` is not exported, and the answer has no `achievements`.

- [ ] **Step 3: Implement**

`web/src/state/stats-recorder.ts` — after `localDay`'s closing `}` add:

```ts

/**
 * This machine's offset from UTC at `ms`, in minutes — `120` in CEST. The
 * one in force at that moment, so a clock change is taken as it happens.
 * `0 -` rather than a bare minus keeps UTC a plain `0`, not `-0`.
 */
export const utcOffsetMinutes = (ms: number) => 0 - new Date(ms).getTimezoneOffset();
```

`web/src/state/stats-exchange.ts`:
- header comment: replace `Viewing stats between the tables and the wire, and the summary the page` + `reads.` with `Viewing stats between the tables and the wire, and what the stats page` + `reads: the summary and the achievements.`
- imports: after `import { summarize, type StatsSummary } from "./stats-summary";` add

```ts
import { achievements, type AchievementLibrary, type Achievements } from "./achievements";
import { localDay, utcOffsetMinutes } from "./stats-recorder";
```

- replace the whole `readSummary` function (its doc comment `/** What the stats page shows for \`profileId\`, as of \`today\`. */` through its closing `}`) with:

```ts
/** No catalog to count against: the achievements that need one count nothing. */
export const NO_LIBRARY: AchievementLibrary = { library: [], collections: [] };

/**
 * What the stats page shows for `profileId` at `nowMs`: the summary, as of
 * that day on this machine's calendar, and the achievements, counted against
 * `library` at this machine's offset from UTC at that moment.
 */
export function readStats(
  db: Database | null,
  profileId: string,
  nowMs: number,
  library: AchievementLibrary,
): StatsSummary & { achievements: Achievements } {
  const today = localDay(nowMs);
  const { titles, days } = statsRows(db, profileId);
  const watched = (db
    ?.query("SELECT set_id AS setId, finished_at AS finishedAt FROM watched WHERE profile_id = ?1 AND removed_at IS NULL")
    .all(profileId) ?? []) as { setId: string; finishedAt: number }[];
  const profile = db?.query("SELECT kids FROM profiles WHERE id = ?1").get(profileId) as { kids: number } | null | undefined;
  return {
    ...summarize({ today, titles, days, watched }),
    achievements: achievements({
      today,
      utcOffsetMinutes: utcOffsetMinutes(nowMs),
      kids: (profile?.kids ?? 0) !== 0,
      days,
      watched,
      ...library,
    }),
  };
}
```

`web/src/state/store.ts` — two lines change, none is added (store.ts stays 799/800):
- replace `import { exportStats, importStats, readSummary } from "./stats-exchange";` with
  `import { exportStats, importStats, NO_LIBRARY, readStats } from "./stats-exchange";`
- replace `import { localDay, tickKey, writeProgress, type Ticks } from "./stats-recorder";` with
  `import { tickKey, writeProgress, type Ticks } from "./stats-recorder";` — first check
  `grep -n 'localDay' web/src/state/store.ts` lists only that import and `stats()`; if anything
  else uses it, leave the import as it is.
- replace phase 02's `stats` method (doc line, signature, body) with:

```ts
  /** This profile's viewing stats and achievements, as of now on this machine's calendar. */
  stats(profileId: string, library = NO_LIBRARY) {
    return readStats(this.db, profileId, Date.now(), library);
  }
```

`web/src/state/stats-routes.ts` — replace the module doc's second paragraph (`A read, so it sits ahead of the write checks in \`routes.ts\`, which` + `dispatches here. …`) with:

```ts
 * Dispatched from `src/routes.ts`'s `createRouter`, the one router that holds
 * both the state and the catalog the achievements are counted against —
 * ahead of the state router and its write checks. Each profile asks for its
 * own; nothing here lists anyone else's.
```

add `import type { AchievementLibrary } from "./achievements";` to its imports, and replace the
function with:

```ts
/** The answer for a stats path, or `null` when the path is not this one. */
export function statsRoute(
  state: WatchState,
  request: PlayerRequest,
  library: () => AchievementLibrary,
): PlayerResponse | null {
  const matched = STATS.exec(request.path);
  if (!matched) return null;
  if (request.method !== "GET" && request.method !== "HEAD") return status(405);
  const profileId = matched[1]!;
  if (!state.has(profileId)) return status(404);
  return json(JSON.stringify(state.stats(profileId, library())), request.method === "HEAD");
}
```

`web/src/state/routes.ts` — delete the line `import { statsRoute } from "./stats-routes";` and
the two lines phase 02 added before `// Past here everything writes, so everything is checked.`:

```ts
    const stats = statsRoute(state, request);
    if (stats) return stats;
```

`wc -l web/src/state/routes.ts` → 243.

`web/src/routes.ts`:
- after `import { createStateRouter } from "./state/routes";` add

```ts
import { achievementLibrary } from "./state/achievement-library";
import type { AchievementLibrary } from "./state/achievements";
import { statsRoute } from "./state/stats-routes";
```

- after the `const stateRoute = options.state ? … : null;` statement (it spans lines 88-90) add

```ts
  // Read on the first stats request and kept for this router's catalog: a
  // swap builds a new router, and with it a new library.
  let libraryFacts: AchievementLibrary | null = null;
  const library = () => (libraryFacts ??= achievementLibrary(db));
```

- after `    if (settings) return settings;` (inside `route`) add

```ts
    // The stats answer reads the state and this catalog both, so it is
    // answered here, where both are — ahead of the state router's write gate.
    const stats = options.state ? statsRoute(options.state, request, library) : null;
    if (stats) return stats;
```

- [ ] **Step 4: Phase 02's route test, through the router that now serves it** — replace the
  whole of `web/test/state-stats-route.test.ts` with:

```ts
/** `GET /api/profiles/{profileId}/stats`, through the router that serves it. */

import { afterEach, expect, setSystemTime, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { ByteSource } from "../src/http/stream";
import { createRouter } from "../src/routes";
import { WatchState } from "../src/state/store";
import { emptyIndex } from "./index-fixture";

const NO_BYTES: ByteSource = { stream: () => new ReadableStream({ start: (c) => c.close() }) };
const dirs: string[] = [];
function routed() {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-stats-route-"));
  dirs.push(dir);
  const state = new WatchState(join(dir, "state.db"));
  const me = state.createProfile("André")!.id;
  const route = createRouter({ db: emptyIndex(), source: NO_BYTES, state });
  const ask = (method: string, path = `/api/profiles/${me}/stats`) => route({ method, path, range: null });
  return { state, me, ask };
}
afterEach(() => {
  setSystemTime();
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const bodyOf = (response: { body: unknown }) => JSON.parse(new TextDecoder().decode(response.body as Uint8Array));

test("answers the profile's summary, as of today on this server's calendar", async () => {
  const { state, me, ask } = routed();
  setSystemTime(new Date(2026, 9, 3, 21, 0, 0));
  state.setProgress(me, "01FILM", 100, 7200);
  setSystemTime(new Date(2026, 9, 3, 21, 0, 10));
  state.setProgress(me, "01FILM", 110, 7200);

  const response = await ask("GET");
  expect(response.status).toBe(200);
  expect(response.headers["content-type"]).toBe("application/json");
  const summary = bodyOf(response);
  expect(summary).toMatchObject({ weekSeconds: 10, monthSeconds: 10, allSeconds: 10 });
  expect(summary.last30.at(-1)).toEqual({ day: "2026-10-03", seconds: 10 });
  expect(summary.history).toEqual([
    { kind: "started", setId: "01FILM", at: new Date(2026, 9, 3, 21, 0, 0).getTime(), seconds: 10 },
  ]);
});

test("an unknown profile is a 404, a write is a 405, a HEAD has no body", async () => {
  const { ask } = routed();
  expect((await ask("GET", "/api/profiles/nobody/stats")).status).toBe(404);
  expect((await ask("POST")).status).toBe(405);
  expect((await ask("DELETE")).status).toBe(405);
  const head = await ask("HEAD");
  expect(head.status).toBe(200);
  expect(head.body).toBeNull();
});
```

  (Same two cases as phase 02 wrote them; only the router and the `await`s change. If phase 02's
  file had drifted from its plan, keep its cases and change only `routed()` and the awaits.)

- [ ] **Step 5: Lower the ceiling this task shrank** — `web/test/code-standards.test.ts`: set
  `"src/state/routes.ts": 243,` and append to the `CEILINGS` doc comment, before ` */`:

```ts
 * Lowered again for `src/state/routes.ts`, once the stats route moved to
 * `src/routes.ts`, the one router that holds the catalog its achievements
 * are counted against.
```

- [ ] **Step 6: Run, expect PASS**

Run: `cd web && bun test test/stats-achievements-route.test.ts test/state-stats-route.test.ts test/state-stats-exchange.test.ts test/state-http.test.ts test/settings-admin-gate.test.ts test/code-standards.test.ts && bun run typecheck`
Expected: 0 fail; `tsc` silent.

- [ ] **Step 7: Commit**

```bash
git add web/src/state/stats-recorder.ts web/src/state/stats-exchange.ts web/src/state/store.ts \
  web/src/state/stats-routes.ts web/src/state/routes.ts web/src/routes.ts \
  web/test/stats-achievements-route.test.ts web/test/state-stats-route.test.ts web/test/code-standards.test.ts
git commit -m "feat(web): answer a profile's achievements with its stats"
```

---

## Task 6.4: Names, and the Achievements section on the Stats page

**Files:**
- Create: `web/test/fixtures/watch-state/achievement-labels.json`
- Create: `web/public/lib/catalog/stats-achievements.js`
- Create: `web/test/stats-achievements-section.test.ts`
- Create: `web/test/stats-page-achievements.test.ts`
- Modify: `web/test/shared-watch-state-fixtures.test.ts` (one import, one `describe`)
- Modify: `web/public/lib/catalog/stats-page.js`
- Modify: `web/public/styles/stats.css`
- Modify: `web/test/stats-page.test.ts` (phase 02's `SUMMARY` gains an empty `achievements`)

**Interfaces:**
- Produces: `achievementLabel(id): string`, `progressLine({ id, have, need }): string`, `achievementsSection(achievements, when: (at) => string): HTMLElement`.
- Strings: the contract's labels (§7) exactly. Progress units, which the contract gives only by example ("7 of 10 films"), are pinned in `achievement-labels.json` for the Android port: films (one: "film"), genres, documentaries, hours, days (streak), episodes (binge), none for a whole series (its count is a show's episodes as often as a course's lessons), none for an id this build does not know (which shows as itself rather than vanishing — the shelves' own rule for the unrecognised).
- Dates: the history's own wording (`whenLabel`, contract §6) without its clock time — "today", "Sat", "21 Sep", "21 Sep 2025" — because a day-based achievement is dated at that day's local midnight and "Sat 00:00" would read as a moment. Flagged in Questions.
- Placement: after "Last 30 days", before "History" (the history has no end). Headings: "Achievements", then "Next" over the closest three. No section at all when there is nothing earned and nothing to come.

- [ ] **Step 1: The names fixture** — `web/test/fixtures/watch-state/achievement-labels.json`

```json
[
  { "id": "films-1", "have": 0, "need": 1, "label": "First film", "progress": "0 of 1 film" },
  { "id": "films-10", "have": 7, "need": 10, "label": "10 films", "progress": "7 of 10 films" },
  { "id": "films-100", "have": 64, "need": 100, "label": "100 films", "progress": "64 of 100 films" },
  { "id": "whole-show", "have": 8, "need": 10, "label": "A whole series", "progress": "8 of 10" },
  { "id": "genres-5", "have": 3, "need": 5, "label": "5 genres", "progress": "3 of 5 genres" },
  { "id": "genres-10", "have": 7, "need": 10, "label": "10 genres", "progress": "7 of 10 genres" },
  { "id": "docs-10", "have": 4, "need": 10, "label": "10 documentaries", "progress": "4 of 10 documentaries" },
  { "id": "hours-500", "have": 123, "need": 500, "label": "500 hours", "progress": "123 of 500 hours" },
  { "id": "streak-30", "have": 12, "need": 30, "label": "30-day streak", "progress": "12 of 30 days" },
  { "id": "binge-5", "have": 3, "need": 5, "label": "5 episodes in a day", "progress": "3 of 5 episodes" },
  { "id": "marathon-3", "have": 1, "need": 3, "label": "marathon-3", "progress": "1 of 3" }
]
```

- [ ] **Step 2: Failing tests**

`web/test/shared-watch-state-fixtures.test.ts` — after the achievements import add

```ts
import { achievementLabel, progressLine } from "../public/lib/catalog/stats-achievements.js";
```

and append:

```ts
describe("achievement-labels fixtures", () => {
  interface Case {
    id: string;
    have: number;
    need: number;
    label: string;
    progress: string;
  }

  for (const one of load<Case[]>("achievement-labels.json")) {
    test(one.id, () => {
      expect(achievementLabel(one.id)).toBe(one.label);
      expect(progressLine(one)).toBe(one.progress);
    });
  }
});
```

`web/test/stats-achievements-section.test.ts`

```ts
/** The Stats page's Achievements section, as drawn. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { achievementLabel, achievementsSection, progressLine } from "../public/lib/catalog/stats-achievements.js";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => { env = browserEnvironment(); });
afterEach(() => env.restore());

/** The history's wording for three instants, the way the page's own formatter words them. */
const when = (at: number) => ({ 1: "today 08:15", 2: "Wed 00:00", 3: "21 Sep", 4: "21 Sep 2025" })[at] ?? "?";
/** The fake document's nodes, under the DOM type the module's JSDoc declares. */
const nodesOf = (section: unknown) => descendants(section as Node);
const byClass = (section: unknown, name: string) =>
  nodesOf(section).filter((node) => node.className === name).map((node) => textOf(node));

describe("the Achievements section", () => {
  test("earned achievements carry the day they were earned, without a clock time", () => {
    const section = achievementsSection({
      earned: [{ id: "films-1", earnedAt: 1 }, { id: "streak-7", earnedAt: 2 }, { id: "genres-5", earnedAt: 3 }, { id: "docs-10", earnedAt: 4 }],
      next: [],
    }, when);
    expect(byClass(section, "achievement-name")).toEqual(["First film", "7-day streak", "5 genres", "10 documentaries"]);
    expect(byClass(section, "achievement-when")).toEqual(["today", "Wed", "21 Sep", "21 Sep 2025"]);
  });

  test("the next ones carry how far along each is, words and bar", () => {
    const section = achievementsSection({ earned: [], next: [{ id: "films-10", have: 7, need: 10 }] }, when);
    expect(byClass(section, "achievement-progress")).toEqual(["7 of 10 films"]);
    const bar = nodesOf(section).find((node) => node.tagName === "PROGRESS")!;
    expect([Number(bar.value), Number(bar.max)]).toEqual([7, 10]);
    expect(bar.attributes.get("aria-hidden")).toBe("true");
  });

  test("nothing earned yet draws no earned list, only what is next", () => {
    const section = achievementsSection({ earned: [], next: [{ id: "films-1", have: 0, need: 1 }] }, when);
    expect(byClass(section, "achievements-earned")).toEqual([]);
    expect(nodesOf(section).some((node) => node.tagName === "H3" && node.textContent === "Next")).toBe(true);
  });

  test("names and progress for one id only this build would not know", () => {
    expect(achievementLabel("marathon-3")).toBe("marathon-3");
    expect(progressLine({ id: "marathon-3", have: 1, need: 3 })).toBe("1 of 3");
  });
});
```

`web/test/stats-page-achievements.test.ts`

```ts
/** The Stats page with its Achievements section in place, and what drawing it records. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderStats } from "../public/lib/catalog/stats-page.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { textOf } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
let stored: Map<string, string>;
beforeEach(async () => {
  env = browserEnvironment();
  stored = new Map();
  Object.assign(env.window, {
    localStorage: {
      getItem: (key: string) => stored.get(key) ?? null,
      setItem: (key: string, value: string) => void stored.set(key, value),
      removeItem: (key: string) => void stored.delete(key),
    },
  });
  env.respondWith(async () => Response.json({}));
  await state.useProfile("viewer");
});
afterEach(async () => {
  await settle();
  await state.useProfile(null);
  env.restore();
});

const SUMMARY = {
  weekSeconds: 600,
  monthSeconds: 600,
  allSeconds: 600,
  last30: Array.from({ length: 30 }, (_, i) => ({ day: `2026-09-${String(i + 1).padStart(2, "0")}`, seconds: i === 29 ? 600 : 0 })),
  history: [{ kind: "finished", setId: "01FILM", at: 0, seconds: 600 }],
  achievements: { earned: [{ id: "films-1", earnedAt: 0 }], next: [{ id: "films-10", have: 1, need: 10 }] },
};

test("Achievements sit between the last thirty days and the history", async () => {
  env.respondWith(async () => Response.json(SUMMARY));
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(main.children.map((node) => node.className)).toEqual([
    "shelf-head", "stats-totals", "stats-days", "stats-achievements", "stats-history",
  ]);
  expect(textOf(main.children[3]!)).toContain("First film");
  expect(textOf(main.children[3]!)).toContain("1 of 10 films");
});

test("nothing earned and nothing to come draws no section", async () => {
  env.respondWith(async () => Response.json({ ...SUMMARY, achievements: { earned: [], next: [] } }));
  const main = env.node("main");
  await renderStats(main, { byId: new Map() }, () => true);
  expect(main.children.map((node) => node.className)).not.toContain("stats-achievements");
});
```

- [ ] **Step 3: Run, expect failures**

Run: `cd web && bun test test/stats-achievements-section.test.ts test/stats-page-achievements.test.ts test/shared-watch-state-fixtures.test.ts`
Expected: FAIL — `stats-achievements.js` does not exist; the page draws no section.

- [ ] **Step 4: Implement** — `web/public/lib/catalog/stats-achievements.js`

```js
/**
 * The Stats page's Achievements section: what this profile has earned, with
 * the day it was earned, and the few closest to come, with how far along
 * each is. The server works both out (`src/state/achievements.ts`); this only
 * names and draws them. The Android pages name them through a port of
 * {@link achievementLabel} and {@link progressLine}, held to these by
 * `test/fixtures/watch-state/achievement-labels.json`.
 */

import { el } from "../dom.js";

const RUNG = /^([a-z]+)-(\d+)$/;

/** What each ladder's rung is called, from the rung's number. */
const NAMES = new Map([
  ["films", (n) => (n === "1" ? "First film" : `${n} films`)],
  ["genres", (n) => `${n} genres`],
  ["docs", (n) => `${n} documentaries`],
  ["hours", (n) => `${n} hours`],
  ["streak", (n) => `${n}-day streak`],
  ["binge", (n) => `${n} episodes in a day`],
]);

/**
 * What a progress line counts. A whole series names nothing: its count is a
 * show's episodes as often as a course's lessons.
 */
const UNITS = new Map([
  ["films", "films"],
  ["genres", "genres"],
  ["docs", "documentaries"],
  ["hours", "hours"],
  ["streak", "days"],
  ["binge", "episodes"],
]);

/** An achievement's name. An id this build does not know shows as itself rather than vanishing. */
export function achievementLabel(id) {
  if (id === "whole-show") return "A whole series";
  const [, ladder = "", n = ""] = RUNG.exec(id) ?? [];
  return NAMES.get(ladder)?.(n) ?? id;
}

/** How far along one still to come is: "7 of 10 films". */
export function progressLine({ id, have, need }) {
  const ladder = RUNG.exec(id)?.[1] ?? "";
  const unit = ladder === "films" && need === 1 ? "film" : UNITS.get(ladder);
  return unit ? `${have} of ${need} ${unit}` : `${have} of ${need}`;
}

/**
 * The day an achievement was earned, in the history's own words without
 * their clock time: a day-based achievement is dated at that day's midnight,
 * and "Sat 00:00" would read as a moment rather than a day.
 */
const earnedOn = (when, at) => when(at).replace(/ \d{1,2}:\d{2}$/, "");

/**
 * @param {{earned: {id: string, earnedAt: number}[], next: {id: string, have: number, need: number}[]}} achievements
 * @param {(at: number) => string} when the history's own time wording: "today 21:14", "Sat 21:14", "21 Sep"
 */
export function achievementsSection(achievements, when) {
  const section = el("section", "stats-achievements");
  section.append(el("h2", "shelf-sub", "Achievements"));
  if (achievements.earned.length > 0) {
    const list = el("ul", "achievements-earned");
    for (const { id, earnedAt } of achievements.earned) {
      const row = el("li");
      row.append(el("span", "achievement-name", achievementLabel(id)), el("span", "achievement-when", earnedOn(when, earnedAt)));
      list.append(row);
    }
    section.append(list);
  }
  if (achievements.next.length > 0) {
    section.append(el("h3", null, "Next"));
    const list = el("ul", "achievements-next");
    for (const step of achievements.next) {
      const row = el("li");
      // The words beside it already say it; the bar is for the eye alone.
      const bar = el("progress");
      bar.max = step.need;
      bar.value = step.have;
      bar.setAttribute("aria-hidden", "true");
      row.append(el("span", "achievement-name", achievementLabel(step.id)), el("span", "achievement-progress", progressLine(step)), bar);
      list.append(row);
    }
    section.append(list);
  }
  return section;
}
```

`web/public/lib/catalog/stats-page.js` (phase 02's):
- replace `import { historyLine, shortDate, watchTime, weekdayInitial } from "./stats-format.js";` with

```js
import { historyLine, shortDate, watchTime, weekdayInitial, whenLabel } from "./stats-format.js";
import { achievementsSection } from "./stats-achievements.js";
```

- replace `  main.append(totals(summary), lastThirty(summary.last30), history(summary.history, byId, Date.now()));` with

```js
  const now = Date.now();
  main.append(totals(summary), lastThirty(summary.last30));
  // Before the history, which has no end; nothing at all while there is nothing in it.
  const { earned, next } = summary.achievements;
  if (earned.length + next.length > 0) main.append(achievementsSection(summary.achievements, (at) => whenLabel(at, now)));
  main.append(history(summary.history, byId, now));
```

`web/public/styles/stats.css` — append:

```css

/* Achievements: what was earned and on which day, then the closest to come. */
.stats-achievements ul { margin: 0; padding: 0; list-style: none; }
.stats-achievements li { display: grid; grid-template-columns: 1fr auto; gap: 6px 16px; padding: 12px 0; border-bottom: 1px solid var(--rule-soft); }
.achievement-when, .achievement-progress { color: var(--ink-3); font-variant-numeric: tabular-nums; }
.stats-achievements h3 { margin: 20px 0 0; color: var(--ink-3); font-size: 0.6875rem; font-weight: 500; letter-spacing: 0.24em; text-transform: uppercase; }
.stats-achievements progress { grid-column: 1 / -1; width: 100%; height: 3px; border: 0; appearance: none; background: var(--rule-soft); }
.stats-achievements progress::-webkit-progress-bar { background: var(--rule-soft); }
.stats-achievements progress::-webkit-progress-value { background: var(--accent); }
.stats-achievements progress::-moz-progress-bar { background: var(--accent); }
```

(The section's `h2` carries the page's own section class, `shelf-sub`, like "Last 30 days" and
"History".)

`web/test/stats-page.test.ts` (phase 02's) — in its `SUMMARY` constant add the line

```ts
  achievements: { earned: [], next: [] },
```

(the page now reads the key the server always sends; an empty pair draws no section, so every
phase 02 assertion holds as written).

- [ ] **Step 5: Run, expect PASS**

Run: `cd web && bun test test/stats-achievements-section.test.ts test/stats-page-achievements.test.ts test/stats-page.test.ts test/shared-watch-state-fixtures.test.ts test/browser-module-assets.test.ts test/code-standards.test.ts && bun run typecheck && bun run lint`
Expected: 0 fail (11 `achievement-labels fixtures`); `tsc` and `eslint` silent.

- [ ] **Step 6: Commit**

```bash
git add web/public/lib/catalog/stats-achievements.js web/public/lib/catalog/stats-page.js web/public/styles/stats.css \
  web/test/fixtures/watch-state/achievement-labels.json web/test/shared-watch-state-fixtures.test.ts \
  web/test/stats-achievements-section.test.ts web/test/stats-page-achievements.test.ts web/test/stats-page.test.ts
git commit -m "feat(web): an Achievements section on the Stats page"
```

---

## Task 6.5: The new-achievement dot on the rail's Stats link, then the phase's checks and version

**Files:**
- Create: `web/public/lib/catalog/stats-dot.js`
- Create: `web/public/lib/playback/player-loader.js` (moved verbatim out of `app.js`)
- Create: `web/test/stats-dot.test.ts`
- Modify: `web/public/app.js` (−21 lines for the loader, +3: two imports, one call)
- Modify: `web/public/index.html` (the dot inside phase 02's Stats link)
- Modify: `web/public/lib/catalog/stats-page.js` (mark what it drew as seen)
- Modify: `web/public/styles/stats.css` (the dot)
- Modify: `web/test/stats-page-achievements.test.ts` (two cases)
- Modify: `web/test/code-standards.test.ts` (`public/app.js` 717 → 699)
- Modify: `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (minor bump)

**Interfaces:**
- Produces: `watchStatsDot(state)`, `markSeen(profileId, earned)`, `SETTLE_MS` (15 000).
- Storage: `localStorage["mediagram.stats-seen.<profileId>"]` = JSON array of ids (contract §7), replaced whole on each mark — an achievement lost (a title un-marked) and earned again is news again. Unreadable or missing → nothing seen.
- When it reads: once at start; at once on a profile switch (the old dot goes out before the answer); otherwise 15 s after the last shelf-affecting change — a local write, or another device's rows `refreshState` pulled in (`watch-state.js:211-232`). Known ceiling (Questions): a sync round that brings only day rows and moves no position, mark or list fires no change; the dot catches up at the next one.
- Marked seen by the page, after it has drawn — not by the dot's own reads, and not when the answer lands after the viewer left.
- Look: a 7 px circle in `--held` (Android's `tertiary`, the held dot) on the icon's top-right corner, so the rail's icons-only form (≤ 900 px) still shows it; its words, "New achievement", in `.sr-only` for a screen reader. Same corner on the phone and TV rails (phase 07).

- [ ] **Step 1: Failing tests** — `web/test/stats-dot.test.ts`

```ts
/** The new-achievement dot on the rail's Stats link: when it lights, and when it goes out. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { markSeen, SETTLE_MS, watchStatsDot } from "../public/lib/catalog/stats-dot.js";
import { browserEnvironment, settle } from "./support/player-environment";

const DOT = "stats-dot";

let env: ReturnType<typeof browserEnvironment>;
let stored: Map<string, string>;
let earnedBy: Record<string, string[]>;
let chosen: string | null;
let notify: () => void;

const state = {
  profileId: () => chosen,
  subscribeChanges: (listener: () => void) => { notify = listener; },
};
const dot = () => env.node(DOT);
const statsReads = () => env.requests.filter((request) => request.url.endsWith("/stats")).length;

beforeEach(() => {
  env = browserEnvironment();
  stored = new Map();
  Object.assign(env.window, {
    localStorage: {
      getItem: (key: string) => stored.get(key) ?? null,
      setItem: (key: string, value: string) => void stored.set(key, value),
      removeItem: (key: string) => void stored.delete(key),
    },
  });
  earnedBy = { anna: [], ben: [] };
  chosen = "anna";
  env.respondWith(async (url) => {
    const profile = decodeURIComponent(url.split("/")[3]!);
    const earned = (earnedBy[profile] ?? []).map((id) => ({ id, earnedAt: 1 }));
    return Response.json({ achievements: { earned, next: [] } });
  });
  dot().hidden = true;
});
afterEach(() => env.restore());

describe("the Stats dot", () => {
  test("lights for an achievement this browser has not shown", async () => {
    earnedBy.anna = ["films-1"];
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("stays out when everything earned has been shown", async () => {
    earnedBy.anna = ["films-1"];
    stored.set("mediagram.stats-seen.anna", JSON.stringify(["films-1"]));
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("lights once a sync brings an achievement earned on another device", async () => {
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);

    earnedBy.anna = ["streak-7"];
    notify(); // refreshState pulled another device's rows
    await settle();
    expect(dot().hidden).toBe(true); // not yet: changes settle first
    env.advance(SETTLE_MS);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("opening the Stats page marks what it shows and puts the dot out", async () => {
    earnedBy.anna = ["films-1", "genres-5"];
    watchStatsDot(state);
    await settle();
    markSeen("anna", [{ id: "films-1", earnedAt: 1 }, { id: "genres-5", earnedAt: 2 }]);
    expect(dot().hidden).toBe(true);
    expect(JSON.parse(stored.get("mediagram.stats-seen.anna")!)).toEqual(["films-1", "genres-5"]);

    notify();
    env.advance(SETTLE_MS);
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("seen is kept per profile, and a switch reads the new profile at once", async () => {
    earnedBy = { anna: ["films-1"], ben: ["films-1"] };
    stored.set("mediagram.stats-seen.anna", JSON.stringify(["films-1"]));
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(true);

    chosen = "ben";
    notify();
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("a switch puts the last profile's dot out before the new answer arrives", async () => {
    earnedBy.anna = ["films-1"];
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);

    env.respondWith(() => new Promise<Response>(() => {}));
    chosen = "ben";
    notify();
    expect(dot().hidden).toBe(true);
  });

  test("an answer for a profile no longer chosen is dropped", async () => {
    earnedBy.anna = ["films-1"];
    let release!: () => void;
    const held = new Promise<void>((resolve) => { release = resolve; });
    env.respondWith(async () => {
      await held;
      return Response.json({ achievements: { earned: [{ id: "films-1", earnedAt: 1 }], next: [] } });
    });
    watchStatsDot(state);
    chosen = null;
    release();
    await settle();
    expect(dot().hidden).toBe(true);
  });

  test("an unreadable seen entry counts as nothing shown", async () => {
    earnedBy.anna = ["films-1"];
    stored.set("mediagram.stats-seen.anna", "{not json");
    watchStatsDot(state);
    await settle();
    expect(dot().hidden).toBe(false);
  });

  test("positions saved while a title plays do not re-read the stats", async () => {
    watchStatsDot(state);
    await settle();
    const before = statsReads();
    for (let tick = 0; tick < 6; tick++) {
      notify();
      env.advance(10_000);
    }
    await settle();
    expect(statsReads()).toBe(before);
    env.advance(SETTLE_MS);
    await settle();
    expect(statsReads()).toBe(before + 1);
  });
});
```

and append to `web/test/stats-page-achievements.test.ts`:

```ts
test("drawing the page records what it showed as seen on this browser", async () => {
  env.respondWith(async () => Response.json(SUMMARY));
  await renderStats(env.node("main"), { byId: new Map() }, () => true);
  expect(JSON.parse(stored.get("mediagram.stats-seen.viewer")!)).toEqual(["films-1"]);
});

test("an answer that lands after the viewer left records nothing", async () => {
  env.respondWith(async () => Response.json(SUMMARY));
  await renderStats(env.node("main"), { byId: new Map() }, () => false);
  expect(stored.has("mediagram.stats-seen.viewer")).toBe(false);
});
```

- [ ] **Step 2: Run, expect failures**

Run: `cd web && bun test test/stats-dot.test.ts test/stats-page-achievements.test.ts`
Expected: FAIL — `stats-dot.js` does not exist; nothing is stored.

- [ ] **Step 3: Implement** — `web/public/lib/catalog/stats-dot.js`

```js
/**
 * The new-achievement dot on the rail's Stats link.
 *
 * Which achievements this browser has shown is kept here, per profile, in
 * `localStorage` — never on the server, never synced: having seen a badge is
 * a fact about a screen, not about the viewer, and one earned on the TV is
 * still news on the laptop. Opening the Stats page marks everything it shows
 * as seen ({@link markSeen}); anything earned since lights the dot. Never a
 * pop-up.
 */

const PREFIX = "mediagram.stats-seen.";
/** The dot inside the rail's Stats link (`index.html`). */
const DOT = "stats-dot";

/**
 * How long the page waits after a change before reading the stats again —
 * longer than the player's ten-second save tick, so a title playing does not
 * re-read them on every position it saves. The rail is behind the player
 * then anyway. A profile switch reads at once.
 */
export const SETTLE_MS = 15_000;

/** The ids this browser has shown `profileId`; none for a missing or unreadable entry. */
function seenIds(profileId) {
  try {
    const stored = JSON.parse(window.localStorage.getItem(PREFIX + profileId) ?? "[]");
    return new Set(Array.isArray(stored) ? stored.filter((id) => typeof id === "string") : []);
  } catch {
    return new Set();
  }
}

/** Whether anything in `earned` is missing from what this browser has shown `profileId`. */
function hasUnseen(profileId, earned) {
  const seen = seenIds(profileId);
  return earned.some((entry) => !seen.has(entry.id));
}

function showDot(on) {
  const dot = document.getElementById(DOT);
  if (dot) dot.hidden = !on;
}

/** What the Stats page is showing becomes what this browser has shown; the dot goes out. */
export function markSeen(profileId, earned) {
  try {
    window.localStorage.setItem(PREFIX + profileId, JSON.stringify(earned.map((entry) => entry.id)));
  } catch {
    // Storage refused: the dot comes back on the next read, nothing worse.
  }
  showDot(false);
}

/**
 * Keeps the dot in step with `state` — the page's own `watch-state.js` — for
 * as long as the page lives: a shelf-affecting change (a write here, another
 * device's rows pulled in, a profile switch) re-reads this profile's
 * achievements once things settle; a different profile reads at once, its
 * predecessor's dot gone before the answer arrives.
 *
 * @param {{ profileId: () => string | null, subscribeChanges: (listener: () => void) => unknown }} state
 */
export function watchStatsDot(state) {
  let readFor = null;
  let timer = null;
  const read = async () => {
    const asked = state.profileId();
    readFor = asked;
    if (asked === null) return showDot(false);
    try {
      const response = await fetch(`/api/profiles/${encodeURIComponent(asked)}/stats`);
      if (!response.ok) return;
      const { achievements } = await response.json();
      // Another profile chosen while this was in flight: its dot is not this.
      if (state.profileId() === asked) showDot(hasUnseen(asked, achievements?.earned ?? []));
    } catch {
      // Unreachable for now: the dot waits for the next change.
    }
  };
  state.subscribeChanges(() => {
    clearTimeout(timer);
    if (state.profileId() === readFor) {
      timer = setTimeout(read, SETTLE_MS);
      return;
    }
    showDot(false);
    void read();
  });
  void read();
}
```

`web/public/lib/catalog/stats-page.js`:
- after `import { achievementsSection } from "./stats-achievements.js";` add

```js
import { markSeen } from "./stats-dot.js";
```

- after `  main.append(history(summary.history, byId, now));` add

```js
  // Drawn, so shown: what this page holds is no longer news on this browser.
  markSeen(id, earned);
```

- [ ] **Step 4: Make room in `app.js`, then wire the dot**

`web/public/lib/playback/player-loader.js` — the block moved out of `app.js` unchanged but for its
import path (now beside `player.js`) and the `export`:

```js
/**
 * The player's own module graph — some 200 KB across three dozen files that
 * only playing something ever needs. Started once, kicked off in the
 * background right after the first shelf is drawn; a Play pressed before it
 * lands simply waits its turn on the promise already under way.
 */
let playerReady = null;

export function loadPlayer() {
  return (playerReady ??= import("./player.js")
    .then((mod) => {
      mod.initializePlayer();
      return mod;
    })
    .catch((error) => {
      // A later Play may as well try again — nothing about this profile or
      // catalog caused it, so nothing about them will fix it either.
      playerReady = null;
      throw error;
    }));
}
```

`web/public/app.js`:
- delete the block from `/**` + ` * The player's own module graph — some 200 KB across three dozen files that`
  through `loadPlayer`'s closing `}` and the blank line after it (21 lines; `app.js:60-80` before
  phase 02 moved it down by one). Its two callers (`await loadPlayer()` in the play path,
  `void loadPlayer().catch(() => {})` after the first draw) stay as they are.
- after `import { playsNext, requestPreload } from "./lib/playback/plays-next.js";` add
  `import { loadPlayer } from "./lib/playback/player-loader.js";`
- after phase 02's `import { renderStats } from "./lib/catalog/stats-page.js";` add
  `import { watchStatsDot } from "./lib/catalog/stats-dot.js";`
- after `state.subscribeChanges(onShelfAffectingChange);` add `watchStatsDot(state);`

`wc -l web/public/app.js` → 699 (717 − 21 + 3).

`web/public/index.html` — in phase 02's Stats link, after its `</svg>` and before
`<span class="label">Stats</span>`, insert:

```html
<span class="new-dot" id="stats-dot" hidden><span class="sr-only">New achievement</span></span>
```

so the link reads `…</svg><span class="new-dot" id="stats-dot" hidden><span class="sr-only">New achievement</span></span><span class="label">Stats</span></a>`.

`web/public/styles/stats.css` — append:

```css

/* The new-achievement dot, on the Stats icon's top-right corner so the rail's
   icons-only form still shows it; the held dot's sage, as on Android. Offsets
   follow the link's padding (12 px, then 10 px and 8 px) and the 21 px icon. */
.rail-nav a[data-section="stats"] { position: relative; }
.new-dot { position: absolute; top: 8px; left: 28px; width: 7px; height: 7px; border-radius: 50%; background: var(--held); }
@media (max-width: 900px) { .new-dot { left: 26px; } }
@media (max-width: 480px) { .new-dot { left: 24px; } }
```

`web/test/code-standards.test.ts` — set `"public/app.js": 699,` (the measured size; if `wc -l`
printed another number, that number) and extend the sentence Task 6.3 appended to the `CEILINGS`
doc comment so it reads:

```ts
 * Lowered again for `src/state/routes.ts`, once the stats route moved to
 * `src/routes.ts`, the one router that holds the catalog its achievements
 * are counted against, and for `app.js`, once the player's lazy loader moved
 * out to `lib/playback/player-loader.js`.
```

- [ ] **Step 5: Run everything**

Run: `cd web && bun test && bun run typecheck && bun run lint`
Expected: 0 fail (`browser-module-assets.test.ts` now walks `app.js` → `player-loader.js` → `player.js`, and into `stats-dot.js`); `tsc` and `eslint` silent.

- [ ] **Step 6: Look at it** — the preview (copies, no Telegram; never the real player):

```bash
cd web && bun run preview            # http://127.0.0.1:8795; leave it running
P=$(curl -s 127.0.0.1:8795/api/profiles | jq -r '.profiles[0].id')
curl -s "127.0.0.1:8795/api/profiles/$P/stats" | jq '.achievements'
```

Expected: `{ earned: [...], next: [three entries] }`, earned newest first; a kids profile's (if the
copy holds one: `jq '.profiles[] | select(.kids)'`) shows no `hours-`, `streak-` or `binge-` id.
Then with `/browse`, in a fresh browser context (empty `localStorage`):
1. open `http://127.0.0.1:8795/#/home` (choose the profile if asked) — the rail's Stats icon wears the
   sage dot when `earned` is non-empty; screenshot at 1440 px and at 390 px (icons-only rail);
2. open `#/stats` — "Achievements" between "Last 30 days" and "History", earned lines with a day and
   no clock time, "Next" with three progress lines and bars; the dot is gone;
3. reload `#/home` — the dot stays out (`localStorage["mediagram.stats-seen.<id>"]` holds the ids).
Stop the preview with `fuser -k -n tcp 8795`.

- [ ] **Step 7: Bump the minor version, by pattern**

```bash
current=$(grep -m1 -oP '^version = "\K[0-9]+\.[0-9]+\.[0-9]+' Cargo.toml)
next=$(echo "$current" | awk -F. '{print $1"."$2+1".0"}')
sed -i -E '0,/^version = "[0-9]+\.[0-9]+\.[0-9]+"$/s//version = "'"$next"'"/' Cargo.toml
sed -i -E 's/^(  "version": ")[0-9]+\.[0-9]+\.[0-9]+(",)$/\1'"$next"'\2/' web/package.json
sed -i -E 's/^(        versionName = ")[0-9]+\.[0-9]+\.[0-9]+(")$/\1'"$next"'\2/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep '"version"' web/package.json; grep 'versionName = ' android/app/build.gradle.kts
```

Expected: all three print `$next`. `versionCode` is derived from `versionName`; never edit it.

- [ ] **Step 8: Commit**

```bash
git add web/public/lib/catalog/stats-dot.js web/public/lib/playback/player-loader.js web/public/app.js \
  web/public/index.html web/public/lib/catalog/stats-page.js web/public/styles/stats.css \
  web/test/stats-dot.test.ts web/test/stats-page-achievements.test.ts web/test/code-standards.test.ts \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts
git commit -m "feat(web): a dot on the Stats rail link for an achievement not yet seen"
```

---

## Test matrix

| Layer | What | Where |
|---|---|---|
| Shared fixture (web + Rust core) | every rule, kids subset, earnedAt incl. day-based at local midnight, offsets and DST, library gone/shrunk, ties, next selection/sort/limit, streak's longest run | `achievements.json` via `shared-watch-state-fixtures.test.ts` (6.1); phase 07 runs the same file |
| Shared fixture (web + Kotlin) | labels and progress lines | `achievement-labels.json` (6.4); phase 07's `AchievementLabelsFixtureTest` |
| Catalog adapter, real SQLite | genres via the provider row, collections by kind+show, unplayable sets out, no `shows` table | `achievement-library.test.ts` (6.2) |
| HTTP (router) | kids flag from the store, library from the catalog, offset across DST | `stats-achievements-route.test.ts`, `state-stats-route.test.ts` (6.3) |
| DOM (stub browser) | section order and words, no section when empty, marking seen only when drawn | `stats-achievements-section.test.ts`, `stats-page-achievements.test.ts` (6.4, 6.5) |
| Dot (stub browser, fake timers) | lights/out, sync, switch, stale answer, corrupt storage, no re-read per save tick | `stats-dot.test.ts` (6.5) |
| Manual | preview + `/browse` at 1440 and 390 px | Task 6.5 Step 6 |

## Rollback

Each task is one commit; `git revert` them newest first. Nothing here is stored or synced, so a
revert loses no data: the `mediagram.stats-seen.*` keys left in a browser are ignored by an older
page. Reverting 6.3 alone needs 6.4–6.5 reverted first (the page reads `achievements`).

## Risk

| Risk | L × I | Mitigation |
|---|---|---|
| Phase 02's files drift from its plan (names, anchors) | M × M | Edits are anchored on text quoted from phase 02; Step 6 of 6.3 and Step 5 of 6.5 run the whole suite |
| `app.js` extraction collides with another session's edit | L × M | A verbatim move of a self-contained block; `browser-module-assets.test.ts` and the browser application tests cover the play path |
| A stats read per 15 s settle while browsing | L × L | One local SQLite read plus a pure pass over the library facts read once per catalog |
| Dot offsets off by a pixel or two at some widths | M × L | Checked by eye in Step 6 at both breakpoints; it is decoration over a labelled link |

## Open questions (also in the hand-off)

1. `today` is in the contract's input but no rule reads it; a day row dated after today counts (as in `allSeconds`) — pinned by "a day row dated after today counts like any other". Keep, or exclude future-dated rows?
2. "Most-finished collection" read as the highest have/need, a tie to the larger `have`.
3. Progress units beyond the contract's one example; a whole series names none.
4. Achievement dates drop the history's clock time ("Sat", not "Sat 00:00").
5. The stats dispatch moves from the state router to `createRouter`; phase 02's route test moves with it.
6. A rewatch moves `films-N`'s date later (the watched row keeps only the newest finish) — the contract's rule; decision 7 says "when the threshold was crossed".
7. One offset (now) for every historic finish: a finish within an hour of midnight from the other DST season can land on the neighbouring day.

## Success criteria

- `cd web && bun test && bun run typecheck && bun run lint` green; 19 `achievements fixtures` and 11 `achievement-labels fixtures` pass.
- `GET /api/profiles/{p}/stats` carries `achievements`; a kids profile's never holds `hours-*`, `streak-*` or `binge-*`.
- The Stats page shows the section between the chart and the history; the rail dot lights for an unseen earned id, goes out when the page has been drawn, and stays out across reloads in that browser.
- `app.js` ≤ 699 and `src/state/routes.ts` ≤ 243 with their ceilings lowered; all three manifests on the same new minor version.
