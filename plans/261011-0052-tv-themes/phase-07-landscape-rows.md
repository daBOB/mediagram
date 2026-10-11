# Phase 07 — Landscape rows

## Context
- Spec: § Rows (with the planning corrections: shared helper instead of folding the Home strips onto `TvPlate`; floor 1.5× the poster floor; shows carry a backdrop).
- Rows of titles: `catalog/TvDepartmentRows.kt` (`DeptRow` → `TvPlate`, `DeptEntryRow` → `TvEntryPlate`), `catalog/TvSimilarRow.kt` (films and shows → `TvPlate`), `catalog/home/TvPosterStrip.kt` (shows, own `Card`), `catalog/home/TvRecentBand.kt` (`TvRecentPosterRow`, films, own `Card`, no caption). Each measures `stripPlateWidth(maxWidth)`.
- Walls (`TvWall`, kept walls, search, franchise) also draw `TvPlate`/`TvEntryPlate` and stay portrait in every style, so a plate goes landscape only when its caller says it sits in a row.
- `MediaSet.backdropPath` exists (`core/model/src/main/kotlin/MediaSet.kt:84`); `Entry.Collection` (`feature/catalog/src/main/kotlin/Collection.kt:32-41`) has a poster but no backdrop; `collectionOf` (`Shelves.kt:~100-120`) takes the first member's poster.

## Task 7.1: Rows switch to 16:9 stills in Apple and Google

**Files:**
- Modify: `feature/catalog/src/main/kotlin/Collection.kt:32-41`, `feature/catalog/src/main/kotlin/Shelves.kt` (`collectionOf`)
- Create: `ui-tv/src/main/kotlin/ui/tv/catalog/TvRowCards.kt`
- Modify: `ui-tv/src/main/kotlin/ui/tv/catalog/TvPageSpacing.kt`, `TvPlate.kt`, `TvEntryPlate.kt`, `TvDepartmentRows.kt`, `TvSimilarRow.kt`, `home/TvPosterStrip.kt`, `home/TvRecentBand.kt`
- Test: `feature/catalog/src/test/kotlin/ShowBackdropTest.kt` (create), `ui-tv/src/test/kotlin/ui/tv/catalog/TvRowCardsTest.kt` (create), `ui-tv/src/test/kotlin/ui/tv/catalog/TvPlateRowShapeTest.kt` (create)

**Interfaces:**
- Consumes: `currentTvLook.landscapeRows` (1.2).
- Produces: `Entry.Collection.backdropPath: String?`; `internal val LandscapeStripPlateFloor: Dp`; `internal fun landscapeStripPlateWidth(rowWidth: Dp): Dp`; `internal val rowCardsAreLandscape: Boolean` and `internal val rowArtRatio: Float` (`@Composable @ReadOnlyComposable` getters); `@Composable @ReadOnlyComposable internal fun rowPlateWidth(rowWidth: Dp): Dp`; `internal fun <T> rowArtOf(landscape: Boolean, poster: T?, backdrop: T?): T?`; `internal const val StillAspectRatio`, `internal const val PosterAspectRatio`, `internal const val PlateArtTag`; `TvPlate(…, backdropPath: File? = null, inRow: Boolean = false)`; `TvPlateArt(…, ratio: Float = PosterAspectRatio)`; `TvEntryPlate(…, inRow: Boolean = false)`.

- [ ] **Step 1: Write the failing tests**

`feature/catalog/src/test/kotlin/ShowBackdropTest.kt`:

```kotlin
package catalog

import model.Kind
import model.MediaSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A show's backdrop is its first episode's that has one — every episode carries the show's art, as with the poster. */
class ShowBackdropTest {
    @Test
    fun aShowTakesTheFirstBackdropAnyOfItsEpisodesCarries() {
        val shows = collections(listOf(episode("e1", backdrop = null), episode("e2", backdrop = "/art/e2.jpg")), CollectionKind.SHOW, "Unknown")
        assertEquals("/art/e2.jpg", shows.single().backdropPath)
    }

    @Test
    fun aShowWhoseEpisodesCarryNoneHasNone() {
        assertNull(collections(listOf(episode("e1", backdrop = null)), CollectionKind.SHOW, "Unknown").single().backdropPath)
    }

    private fun episode(
        id: String,
        backdrop: String?,
    ) = MediaSet(
        setId = id,
        kind = Kind.EPISODE,
        title = id,
        show = "Reacher",
        chapter = null,
        path = null,
        season = null,
        episodeFirst = null,
        episodeLast = null,
        year = null,
        durationSecs = null,
        posterPath = null,
        totalBytes = 0,
        addedAt = 0,
        rawTitle = id,
        backdropPath = backdrop,
    )
}
```

`ui-tv/src/test/kotlin/ui/tv/catalog/TvRowCardsTest.kt`:

```kotlin
package ui.tv.catalog

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A landscape row's arithmetic and its choice of picture. */
class TvRowCardsTest {
    @Test
    fun fiveStillsShareTheRowLessFourGaps() {
        assertEquals((864.dp - StripGap * 4) / 5, landscapeStripPlateWidth(864.dp))
    }

    @Test
    fun aNarrowOrUnboundedRowTakesTheFloor() {
        assertEquals(LandscapeStripPlateFloor, landscapeStripPlateWidth(500.dp))
        assertEquals(LandscapeStripPlateFloor, landscapeStripPlateWidth(Dp.Infinity))
        assertEquals(StripPlateFloor * 1.5f, LandscapeStripPlateFloor)
    }

    @Test
    fun aLandscapeRowShowsTheBackdropThenThePosterAPortraitRowThePoster() {
        assertEquals("b", rowArtOf(landscape = true, poster = "p", backdrop = "b"))
        assertEquals("p", rowArtOf(landscape = true, poster = "p", backdrop = null))
        assertEquals("p", rowArtOf(landscape = false, poster = "p", backdrop = "b"))
    }
}
```

`ui-tv/src/test/kotlin/ui/tv/catalog/TvPlateRowShapeTest.kt`:

```kotlin
package ui.tv.catalog

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import catalog.initialsOf
import designsystem.TvStyle
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A plate in a row is a 16:9 still under Apple and Google and a poster under mediagram; a plate on a wall is a poster in every style. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlateRowShapeTest : TvScreenStateTest() {
    private fun ratio(): Float {
        val art = compose.onNodeWithTag(PlateArtTag).getUnclippedBoundsInRoot()
        return (art.right - art.left) / (art.bottom - art.top)
    }

    @androidx.compose.runtime.Composable
    private fun Plate(inRow: Boolean) = TvPlate(title = "Die Hard", posterPath = null, onOpen = {}, modifier = Modifier.width(160.dp), inRow = inRow)

    @Test
    fun aRowPlateIsAStillUnderAppleAndGoogle() {
        for (style in listOf(TvStyle.APPLE, TvStyle.GOOGLE)) {
            show(style) { Plate(inRow = true) }
            assertTrue(abs(ratio() - 16f / 9f) < 0.02f, "$style: ${ratio()}")
            close()
        }
    }

    @Test
    fun aRowPlateStaysAPosterUnderMediagram() {
        show(TvStyle.MEDIAGRAM) { Plate(inRow = true) }
        assertTrue(abs(ratio() - 2f / 3f) < 0.02f, "${ratio()}")
    }

    @Test
    fun aWallPlateStaysAPosterInEveryStyle() {
        show(TvStyle.APPLE) { Plate(inRow = false) }
        assertTrue(abs(ratio() - 2f / 3f) < 0.02f, "${ratio()}")
    }

    /** No backdrop and no poster: the plate's initials, on a still's proportions. */
    @Test
    fun aStillWithNoArtAtAllShowsTheInitials() {
        show(TvStyle.APPLE) { Plate(inRow = true) }
        compose.onNodeWithText(initialsOf("Die Hard")).assertExists()
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd android && ./gradlew :feature:catalog:testDebugUnitTest --tests 'catalog.ShowBackdropTest' :ui-tv:testDebugUnitTest --tests 'ui.tv.catalog.TvRowCardsTest' --tests 'ui.tv.catalog.TvPlateRowShapeTest'`
Expected: compilation fails — `backdropPath` on `Entry.Collection`, the row helpers, `PlateArtTag` and `inRow` are unresolved.

- [ ] **Step 3: Shows carry a backdrop**

In `Collection.kt`, `Entry.Collection` gains `val backdropPath: String? = null,` after `posterKey`. In `Shelves.kt`'s `collectionOf`, after `posterKey = …`:

```kotlin
        // The show's backdrop, taken the way its poster is: every episode carries the same art.
        backdropPath =
            divisions.firstNotNullOfOrNull { level ->
                level.items.firstNotNullOfOrNull(MediaSet::backdropPath)
            },
```

- [ ] **Step 4: The row helpers**

In `TvPageSpacing.kt`, after `stripPlateWidth`:

```kotlin
/** A row of stills' floor: half again the poster floor, 204 web px, so five still fit the box's row. */
internal val LandscapeStripPlateFloor = StripPlateFloor * 1.5f

/** A still's width in a row [rowWidth] wide: a fifth of the row less its gaps, never under [LandscapeStripPlateFloor]; an unbounded row takes the floor. */
internal fun landscapeStripPlateWidth(rowWidth: Dp): Dp =
    if (rowWidth.isFinite) maxOf(LandscapeStripPlateFloor, (rowWidth - StripGap * 4) / 5) else LandscapeStripPlateFloor
```

Create `TvRowCards.kt`:

```kotlin
package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import ui.tv.currentTvLook

/**
 * What a row of titles — a Home strip, a department row, Similar — cuts its
 * cards to: posters under mediagram, 16:9 stills five across under Apple TV
 * and Google TV styles, as those home screens' rows are. Every row reads its
 * shape, picture and width here, so the rows switch together; a wall of
 * plates never reads it and stays posters in every style.
 */
internal val rowCardsAreLandscape: Boolean
    @Composable
    @ReadOnlyComposable
    get() = currentTvLook.landscapeRows

/** A row card's art proportions in the current style. */
internal val rowArtRatio: Float
    @Composable
    @ReadOnlyComposable
    get() = if (rowCardsAreLandscape) StillAspectRatio else PosterAspectRatio

/** A row card's width in a row [rowWidth] wide, in the current style. */
@Composable
@ReadOnlyComposable
internal fun rowPlateWidth(rowWidth: Dp): Dp = if (rowCardsAreLandscape) landscapeStripPlateWidth(rowWidth) else stripPlateWidth(rowWidth)

/** The picture a row card shows: the backdrop in a landscape row where there is one — else the poster, cropped — and the poster in a portrait row. */
internal fun <T> rowArtOf(
    landscape: Boolean,
    poster: T?,
    backdrop: T?,
): T? = if (landscape) backdrop ?: poster else poster

/** A still's own proportions. */
internal const val StillAspectRatio = 16f / 9f
```

In `TvPlate.kt`:
- `private const val PosterAspectRatio` becomes `internal const val PosterAspectRatio`;
- add `internal const val PlateArtTag = "plate-art"` (KDoc: "The art box of a plate, for a test to measure; a resource id on the box too.");
- `TvPlate` gains `backdropPath: File? = null` and `inRow: Boolean = false` (last); inside, before `Card`:
  ```kotlin
      val landscape = inRow && rowCardsAreLandscape
  ```
  and the art call becomes `TvPlateArt(posterPath = rowArtOf(landscape, posterPath, backdropPath), title = title, progress = progress, watched = watched, ratio = if (landscape) StillAspectRatio else PosterAspectRatio)`;
- `TvPlateArt` gains `ratio: Float = PosterAspectRatio` (after `watched`), its box uses `.aspectRatio(ratio)` and `.testTag(PlateArtTag)` (import `androidx.compose.ui.platform.testTag`);
- KDoc of `TvPlate` gains: "[inRow] says the plate sits in a row of titles rather than on a wall: in a style whose rows are stills ([rowCardsAreLandscape]) it then shows [backdropPath] — or the poster, cropped — at 16:9."

In `TvEntryPlate.kt`, `TvEntryPlate` gains `inRow: Boolean = false` and passes `inRow = inRow` to both `TvPlate` calls, with `backdropPath = entry.set.backdropPath?.let(::File)` for a film and `backdropPath = entry.backdropPath?.let(::File)` for a collection.

- [ ] **Step 5: The rows**

- `TvDepartmentRows.kt` `DeptRow`: `val plateWidth = rowPlateWidth(maxWidth)`; the `TvPlate` call gains `backdropPath = set.backdropPath?.let(::File), inRow = true`. `DeptEntryRow`: `rowPlateWidth(maxWidth)`; `TvEntryPlate(…, inRow = true)`.
- `TvSimilarRow.kt`: both `stripPlateWidth(maxWidth)` → `rowPlateWidth(maxWidth)`; the films' `TvPlate` gains `backdropPath = set.backdropPath?.let(::File), inRow = true`, the shows' `backdropPath = entry.backdropPath?.let(::File), inRow = true`.
- `home/TvPosterStrip.kt`: `val landscape = rowCardsAreLandscape` and `val plateWidth = rowPlateWidth(maxWidth)` inside `BoxWithConstraints`; the art `Box` uses `.aspectRatio(rowArtRatio)`; `val poster = show.posterPath` becomes `val art = rowArtOf(landscape, show.posterPath, show.backdropPath)`, and the `if (poster != null)`/`File(poster)` read `art`. Its KDoc's "cut to [ui.tv.TvPlate]'s own poster shape" gains "— or, in a style whose rows are stills, to a still ([rowCardsAreLandscape])".
- `home/TvRecentBand.kt` `TvRecentPosterRow`: the same three changes, with `set.posterPath`/`set.backdropPath`.

Imports: `ui.tv.catalog.rowPlateWidth`, `rowArtRatio`, `rowCardsAreLandscape`, `rowArtOf` in the `home` files; drop `stripPlateWidth` imports left unused.

- [ ] **Step 6: Run the suites**

Run: `cd android && ./gradlew :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest`
Expected: PASS — the new tests, and `TvPageSpacingTest`, `TvPlateStateTest`, `TvWallStateTest`, `TvHomeStateTest` unchanged under mediagram.

- [ ] **Step 7: Commit**

```bash
cd android && ./gradlew spotlessApply detekt
git add feature/catalog/src ui-tv/src
git commit -m "feat(tv): rows of titles become wide stills in the Apple and Google styles"
```

## Success criteria
- Under Apple/Google every row of titles shows 16:9 stills five across (floor 153 dp), backdrop first, poster cropped otherwise, initials with neither; walls stay posters.
- Shows have a backdrop when any episode carries one.
- mediagram's rows and walls are unchanged.
