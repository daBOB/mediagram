# B1g — phone course page, TV Cast person cards

2026-10-04, worktree branch `worktree-agent-a8c9d85d50bcff90c`, reset to `main` at `d68e1ecb`
(0.106.0). No version bump (lead bumps on merge). Untouched: profile/PIN code, Settings ›
Profile, `docs/project-changelog.md`, shared test fixtures.

## Commits

| Commit | What |
|---|---|
| `53e8ff76` | feat: phone course page = shelf head over its lessons |
| `854c0b8c` | feat: TV Cast draws round person cards instead of poster plates |
| `8315d4bd` | docs(design): Shelf head paragraph names both |

## Done

1. **Phone/tablet course page** (`ui-mobile/.../CollectionScreen.kt`). The course branch is now a
   private `CoursePage`: `ShelfHead(collection.name, courseExtentOf(divisions))` (the same
   `courseExtentOf` the TV uses, so "two lessons · one document" and the documentary wording
   match the web's `extentOf`) as the first `LazyColumn` item, then the same indented rows.
   The headline name + `TitleHeader` (poster, age, rating, genres, tagline, overview) is gone,
   as on the web's `course-view.js` and the TV. `TitleHeader` had no other caller and was
   deleted from `TitleDetailScreen.kt` with its now-unused imports. `SeriesPage` untouched;
   `CollectionScreen`'s signature unchanged (show page still takes `info`/`onOpenGenre`).
   No resume line added: the web's course page has none (the TV's is a written-down
   remote-only difference).
2. **TV Cast** (`ui-tv/.../TvCastRow.kt`). `TvCastPlate` (2:3 `TvPlate`) replaced by the
   existing `TvPersonCard`: round portrait spanning the 130dp card, name, character under it
   (`credit.role`). `TvPersonCard` now takes `personId/name/portraitPath/sub` instead of a
   `VisiblePerson`; search passes `spelledCountOf(titles, "title")` as `sub`
   (`TvSearchCell.kt`), cast passes the role. Focus: unchanged rules — `key` per item via
   `itemsIndexed(key = personId)`, the requester always attached (index match swaps which
   requester, never adds/removes the modifier), ring + scale values follow focus, no new
   `onExit`/`focusRestorer`. Crew line, restore (cast → card, crew-only → name) and Up → Cast
   tab untouched.

Docs: DESIGN.md › Shelf head (phone course page; TV Cast uses the person card).

## Tests

`./gradlew -q --continue :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-common:testDebugUnitTest :feature:catalog:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint`
— exit 0: **ui-tv 484, ui-mobile 284, ui-common 73, feature:catalog 341, 0 failures**;
androidTest compiles; lint: no finding in a touched file; app baseline's "2 entries not found"
pre-existing.

New:
- `CoursePageTest` (phone): heading + "TWO LESSONS · ONE DOCUMENT", ≥34dp title; lessons start
  under the head; handed info + poster + genres, no overview/tagline/score/genre and the name
  only once; a lesson row still plays; documentary collection says "ONE DOCUMENTARY".
- `TvCastPersonCardsStateTest`: cards side by side, < 200dp, initials centred half a card-width
  down (a circle, not a 2:3 plate), name under the face, character under the name; OK opens the
  person; D-pad (touch mode off) Right walks cards, Up (no crew) lands on the Cast tab, Down
  returns into the row.
- `TvCastCrewLinksStateTest`: wording only (plate → card); its walks pass unchanged on the cards.

## Box/device only — not verifiable here

- TV: ring + 1.08 scale on a 130dp circle in a `LazyRow` — the first card's left edge is clipped
  by the row the same way the old plates were; long character names wrap under the card.
- Phone/tablet: course head on real fonts; Redmi Pad walk of a multi-level course.

## Concerns

- None blocking. `CollectionFrame` (phone) still looks up `TitleInfo` for a course; it is now
  unused there (harmless, mirrors the TV's unused credits/similar lookup on a course).

## Unresolved questions

- None.
