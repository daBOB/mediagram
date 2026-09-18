# Course screen: one level at a time

Ask: "show each nested level separate and doesn't show all", tutorials only.
Decisions confirmed with the user: drill-down (not an accordion), courses only
(series unchanged).

## What changed

| Before | After |
|---|---|
| `#/tutorials/Geldhochschule` renders the whole tree | renders one level |
| ~187 rows on the course page | 3 rows |
| back button to `#/tutorials` | breadcrumb of every ancestor |
| folder depth shown by indent, capped at 3 | depth is the URL |
| a numbered folder rendered after every lesson | sits at its number |

New: `divisionAt`, `lessonsUnder`, `levelEntries` in `library.js`;
`levelBlock`, `folderRow` in `course-view.js`; `crumbs`, `viewCourseLevel` in
`app.js`. `.back` deleted — nothing rendered it any more.

## Verified against the real index

162 real lessons exported from `library.db` into the preview harness.

| Level | Heading | Shown |
|---|---|---|
| `/Geldhochschule` | Geldhochschule · 162 lessons | 3 folders, 0 lessons |
| `/Ausbildung Trading` | 108 lessons | 2 folders |
| `/…/1. Grundlagen` | 70 lessons | 7 folders |
| `/…/3. Signal` | 29 lessons | 20 lessons + 1 folder |

Ordering inside `3. Signal`, which is the case that pays for `levelEntries`:

```
12 Ziel/Ausstieg · 13 Interpretation · FOLDER 14. Exkurs TWS · 15 Umsetzung TWS
```

Other checks: a show still renders 2 seasons and their episodes on one page
with no folder rows; a deep link opens directly; a crumb click walks up and
rewrites the hash; `#/tutorials/Geldhochschule/Nope/Deeper` gives
`"Geldhochschule" has no folder called "Nope › Deeper"` plus a crumb out. No
horizontal overflow at 390px, and folder names stop wrapping there now the
empty call-number column is dropped.

`./scripts/check.sh`: clippy clean, 26 Rust tests, 462 bun tests, 0 fail.

## Unresolved

- `cache-store.test.ts` "evicts what was read longest ago" failed once in a
  full run and passed 5/5 in isolation and on the next full run. Nothing in
  this change touches the cache. It is the known timing-dependent flake and it
  is a real one: it will fail a push at random until the test stops depending
  on eviction order under load.
- Lesson counts mix spelled and figure forms in one right-aligned column
  ("108 lessons" above "six lessons"), because `countOf` spells up to twenty.
  Correct per the rule set earlier and consistent with the rest of the page,
  but it does break the tabular alignment in a folder list. Left as the rule
  says; worth a look if it reads badly to you.
- `web/test/posters.test.ts` still has 4 TypeScript errors, unrelated and
  untouched. `tsc` is still not in `check.sh`.
