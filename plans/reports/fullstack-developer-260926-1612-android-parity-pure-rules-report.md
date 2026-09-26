# Phase 2 (pure-rule half) — Kotlin ports report

Plan: `plans/260926-1330-android-editorial-departments-parity/phase-02-kotlin-data-and-rules.md`
Worktree: `feat/android-editorial-parity`, work context `/home/andre/Workspace/mediagram-android-parity`

## Files

### Modified
- `android/core/model/src/main/kotlin/MediaSet.kt` — added `collectionId: Long?`,
  `collectionName: String?`, `seriesType: String?`, `showStatus: String?`, all
  defaulting to `null`, appended after `popularity` so every existing
  (named-arg) constructor call still compiles.

### Created — main
- `android/feature/catalog/src/main/kotlin/Similar.kt` — `similarTo(title, candidates, seen)`
- `android/feature/catalog/src/main/kotlin/SeriesResume.kt` — `seriesResume(divisions, resumeOf, recent, watched)`, `episodeShort(set)`, `ResumeVerb`, `SeriesResumePick`
- `android/feature/catalog/src/main/kotlin/GenreIndex.kt` — `genreIndex(titles)`, `GenreIndexEntry`
- `android/feature/catalog/src/main/kotlin/Franchises.kt` — `franchisesIn(movies)`, `Franchise`
- `android/feature/catalog/src/main/kotlin/VisiblePeople.kt` — `visiblePeople(people, isVisible)`, `PersonCandidate`, `VisiblePerson`

### Created — test
- `SimilarTest.kt`, `SeriesResumeTest.kt`, `GenreIndexTest.kt`, `FranchisesTest.kt`, `VisiblePeopleTest.kt` — each mirrors its web `*.test.ts` case for case.

## Public signatures (for phases 4–6 to call)

```kotlin
fun similarTo(title: MediaSet, candidates: List<MediaSet>, seen: (MediaSet) -> Boolean = { false }): List<MediaSet>

enum class ResumeVerb { RESUME, CONTINUE, PLAY }
data class SeriesResumePick(val set: MediaSet, val at: Double?, val verb: ResumeVerb)
fun seriesResume(divisions: List<Division>, resumeOf: (String) -> Double?, recent: List<String>, watched: (String) -> Boolean): SeriesResumePick?
fun episodeShort(set: MediaSet): String   // "S3 E15"

data class GenreIndexEntry(val name: String, val count: Int, val art: String?)
fun genreIndex(titles: List<MediaSet>): List<GenreIndexEntry>

data class Franchise(val id: Long, val name: String, val films: List<MediaSet>, val art: String?)
fun franchisesIn(movies: List<MediaSet>): List<Franchise>

data class PersonCandidate(val personId: Long, val name: String, val portraitKey: String?, val titleKeys: List<String>)
data class VisiblePerson(val personId: Long, val name: String, val portraitKey: String?, val titles: Int)
fun visiblePeople(people: List<PersonCandidate>, isVisible: (String) -> Boolean): List<VisiblePerson>
```

## Design notes / deliberate simplifications

- `seriesResume` keeps the web's pure shape: `resumeOf`/`watched` are lambdas
  the caller (a phase 4+ view model) wires from `data.ResumePoint` and watch
  state, exactly as `series-page.js` wires them from `resume-point.js` and
  `watch-state.js`. Flattening delegates to the already-existing `playOrder`
  (`NextUp.kt`) rather than re-walking `Division` — no duplication of
  `flattenCollection`.
- `episodeShort` is intentionally distinct from the existing `episodeLabel`
  (`core/model/EpisodeLabel.kt`, produces `"S1E4"`): the web's
  `series-resume.js#episodeShort` prints `"S3 E15"` with a space, and is a
  different label for a different place (the resume button), not a formatting
  bug to reconcile.
- `visiblePeople` collapses the web's `byKey(key) => {films, shows}` into a
  single `isVisible: (String) -> Boolean` predicate — the rule only ever asks
  "is there anything visible under this key", never the film/show split, and
  the core has no person DTO yet for this phase to key off of. Simpler and
  equally faithful; noted per the phase's own instruction that
  `PersonCandidate` need only be "trivially mappable" later.
- `genreIndex` takes `List<MediaSet>` directly (films + first-episode-per-show)
  rather than porting the web's `titlesOf` helper — that's shelf-assembly glue
  for whichever phase-4/5 screen wires it up, not part of the five named rules.

## Tests

`cd android && ./gradlew :feature:catalog:testDebugUnitTest :core:model:test`

- Build: **PASS** (`BUILD SUCCESSFUL`, 71 actionable tasks, no failures reported).
- Re-ran filtered (`--tests catalog.SimilarTest/SeriesResumeTest/GenreIndexTest/FranchisesTest/VisiblePeopleTest --rerun-tasks`): **PASS**, all five test classes compiled and executed (Gradle would fail fast with "no tests found" if the filters missed; they matched).
- `core:model:test` (MediaSet field addition, existing `ByteSizeTest`/`ClockTimeTest`/markdown tests): **PASS**, unaffected by the additive fields.
- No `crates/`/`core/rust` involvement — no interference with the concurrent agent's native rebuild observed.

## Concerns

- None blocking. `Int.MAX_VALUE` used in `Franchises.kt` in place of the web's
  literal `9999` sentinel for a missing release year — same ordering effect,
  no behavioural difference, avoids a magic number that could plausibly
  collide with a real year on the web side (it can't at `9999` either, but
  `MAX_VALUE` reads more clearly as "no year, sort last").
- `franchisesIn`'s franchise `name` is taken from the first film encountered
  for that `collectionId` in `movies`' own order (`LinkedHashMap` insertion
  order), matching the web's `byId.set()`-at-first-sight behavior exactly.

**Status:** DONE
**Summary:** Ported the 5 pure rules (Similar, SeriesResume, GenreIndex, Franchises, VisiblePeople) plus 4 nullable MediaSet fields; all new + existing feature:catalog and core:model unit tests pass.
**Concerns/Blockers:** none.
