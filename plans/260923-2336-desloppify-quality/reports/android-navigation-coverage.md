# Android navigation coverage

Status: DONE

## Result

Added six Compose/Robolectric scenarios that execute the production
`CatalogAndPlayer` / `LibraryFlow` routing, profile gate, screen branches,
navigation callbacks, and Activity saved-state registry. No production seam,
navigation source edit, dependency, or manifest change was needed.

`LibraryPositionsTest` retains its four small holder checks, with its comment
corrected to identify their limited scope and point to the real routing tests.

## Covered behavior

1. Click the Series shelf, show, season, episode, and Play; leave the player
   through its real back control, then use system and toolbar back to return
   through title, season, collection, and catalog. Assert playback really
   starts and stops once, visible destinations, and no root back affordance.
2. Open System over a title, move from there to TMDB key and Settings using
   their actual overflow menus, then use system back to uncover the original
   title directly. The next back returns to its season.
3. Save an Activity displaying a title, destroy it, create a new Activity and
   fresh ViewModels with that saved Bundle, and suspend its catalog read.
   Assert Loading first, then the restored title after the read completes;
   back still reaches its season and collection.
4. Repeat real Activity restoration with a season open. Loading does not
   discard the saved destination, and back from the restored season uncovers
   its collection.
5. Request Update library from an overlay over a deep title. Assert the actual
   repository refresh, return to the catalog, removal of its back affordance,
   and a subsequent collection visit showing the season wall.
6. Open a hand-built list from Collections, play its title, and use system back
   to return to that same list before leaving for the catalog.

The tests invoke visible UI actions and the real Activity back dispatcher.
They never assign `LibraryPositions` properties to simulate a navigation result.

## Fixture boundaries

The fixture uses actual Catalog, Profile, Fetch, and Player ViewModels, the
library-update coordinator, enrichment state, catalog grouping, and the existing
real player-handle lifecycle fixture. Repository IO and media decoding are
controlled. System, Settings, and cache menu facts are supplied through their
ViewModel boundaries; this suite verifies navigation through their real screens,
not their separate data-loading behavior.

ViewModels are seeded with the real `ViewModelProvider` into a test owner.
Hilt 1.4 still creates its generated-Activity factory even when those instances
already exist. The test therefore replaces only that external DI factory lookup
with its supplied delegate, and restores it in `finally`. No routing function
or screen is mocked. Each fixture clears its ViewModel store and cancels its
media scope on teardown. No native core, live Telegram, or real media decoder
is started.

The fixture uses the controller's new `catalog.profile` package and retained
Settings completions. It adds no `CoreClient` implementation or adapter override.

## Verification

From `android/`:

```sh
./gradlew :ui-mobile:testDebugUnitTest \
  --tests ui.LibraryFlowTest --tests ui.LibraryPositionsTest
./gradlew :feature:catalog:testDebugUnitTest :ui-mobile:testDebugUnitTest
```

- Focused navigation gate: **10 passed**, including six production-composition
  scenarios and four position-holder checks.
- Full module gate: **103 catalog/profile tests and 118 mobile UI tests**;
  zero failures, errors, or skips. The final invocation completed in 12 seconds;
  catalog/profile results were current from the earlier combined run, and
  Gradle correctly retained that task as up to date.
- Scoped ktlint formatting/check and `git diff --check` passed.

Logs: `/tmp/android-navigation-focused.log`,
`/tmp/android-navigation-verified.log`, and
`/tmp/android-navigation-format.log`.

Initial fixture runs exposed the Hilt factory requirement and the season row's
ordinal-prefixed title text; both fixture issues were corrected. These were
test-harness failures, not claimed production regressions.

Existing warnings about the absent Compose stability configuration and JVM
class sharing remain. All owned Gradle wrapper/test processes exited, and the
shared Gradle slot was released to the controller and cache worker. No scanner
state was changed and no commit was made.
