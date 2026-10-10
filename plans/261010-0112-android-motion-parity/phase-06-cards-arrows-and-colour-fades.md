# Phase 06 — Cards, arrows and colour fades

**Spec:** `../spec.md` §2 (Everywhere: colour; Title and department pages: arrows; Cards). **Status:** pending. **Depends on:** 02.
**Inventory rows:** #18–#23, under the touch rules of §3.

## Context

**Binding touch rules** (inventory §3, from the web's `(hover: hover)` gate, `catalog.css:51`):

| Input | What moves |
|---|---|
| A finger | Never lifts or zooms artwork. Arrows nudge while pressed; a plate's name turns accent while pressed. |
| A mouse, trackpad or hovering stylus | Lift, zoom and nudge, as on the web. |

`hoverMotion` (phase 01) already enforces this: lift and zoom follow `HoverInteraction` only, and a nudge also follows `PressInteraction`. Every call passes its numbers as named arguments.

**Sharing one source.** Each site hoists `remember { MutableInteractionSource() }` and passes it to both the `clickable` and `hoverMotion(source, …)`.
- The `clickable` takes `indication = LocalIndication.current` (the theme's `catalogueIndication()`, phase 02) or `catalogueIndication(pressScale = 1f)`.
- M3 buttons get the source through a new `interactionSource` parameter on two of phase 02's wrappers (Task 6.2).

**No web link shrinks when pressed** (`theme.css:167` is `button:active` only). Every Android site whose web element is an `<a>` passes `catalogueIndication(pressScale = 1f)`. Each is checked against the web markup, and the element is cited at its site.
- **Links that also move** take it in the step that gives them motion:

  | Link | Web element | Task |
  |---|---|---|
  | Feature card | `home-features.js:41` | 6.1 |
  | Genre tile | `utility-pages.js:76` | 6.1 |
  | Destination | `collections-page.js:47` | 6.1 |
  | Person (Cast, search) | `cast.js:49` | 6.1 |
  | See-all (Home, department rows) | `home-view.js:39`, `department-hero.js:51` | 6.2 |
  | "All N →" | `department-pages.js:183` | 6.2 |
  | Back | `title-spread.js:24` | 6.2 |

- **The other links** take it in Task 6.2 Step 9: rail rows, wordmark, department pills, the compact header's rail icons, genre links, the "Part of" franchise link, both quote credits, the cover's "Details", This month and the pager.
- **The search controls** take it too: the web's is an `<input>` (`index.html:89`), which `button:active` doesn't reach either.
- **Buttons keep 0.98**, each checked against its web element:
  - plates (`shelf-view.js:64`), which Home's Recently Added, Latest series and Latest courses also draw (`home-view.js:91-112`);
  - resume cards (`home-resume.js:34`);
  - the play pill (`title-spread.js:53`) and My List (`list-toggle.js:15`);
  - "＋ New list" (`collections-view.js:35`);
  - search filters (`search-view.js:144`) and episode and search rows (`course-view.js:48`, `search-view.js:165`);
  - the shelf mode toggle (`shelf-mode.js:88`), the cover's dots and pause, and the avatar (`index.html:93`).

**Colour fades read the selection, never a moving colour.** `DepartmentsBar` blends `ink` with the scroll on every frame (`DepartmentsBar.kt:79-80`), and it reaches `Pill` and the search `CircleIconButton` (`:115,119`). A 160 ms tween chasing that value would trail the bar behind the page, so a pill fades a 0→1 fraction and multiplies `ink` by it.
- Fills, borders and rings are read inside `drawBehind`/`drawWithContent` lambdas (draw phase).
- Text and icon colours go through M3 `Text`/`Icon`, which take a `Color`. They recompose their small scope for about 10 frames.

**Before-code is main's code with phase 02's renames applied** (`TextButton` → `CatalogueTextButton`, etc.). Other phases touch some of these files first or later:

| Phase | File | Its part | This phase's part |
|---|---|---|---|
| 03 | `HomeFeatures.kt` | reveals | `FeatureCard` |
| 04 | `ResumeCard.kt` | `ResumeRow` snap | `ResumeCard` |
| 04 | `home/CoverSlide.kt` | the cover's drift | the "Details" link |
| 05 | `DeptQuote.kt` | created; the quote's art-in | the credit's press |
| 03 | `DocumentariesDepartmentScreen.kt` | reveals around the group heading | its `seeAllLabel` |
| 07 | `TitlePills.kt` | the ⋯ menu | the play pill |

Anchor on the code shown, not on line numbers, wherever one of those phases ran first.

**Files over 200 lines this phase grows, split in the task that grows them.** Every move is verbatim, plus visibility where a private symbol now crosses files.

| File | Lines on main | What moves out | To |
|---|---|---|---|
| `home/HomeFeatures.kt` | 210 | `FeatureCard` | `home/FeatureCard.kt` |
| `home/ResumeCard.kt` | 191, +13 here | `Quote` | `home/PullQuote.kt` |
| `ArtTile.kt` | 206 | the tile-line helpers | `TileLines.kt` |
| `SearchGroupsView.kt` | 248 | `PeopleSection`, `SearchFilterChips` | `SearchPeople.kt`, `SearchFilterChips.kt` |
| `chrome/AppChrome.kt` | 193 after phase 02, +14 here | `railItemFor`, `railSelect` | `chrome/RailSelect.kt` |
| `chrome/LibraryRail.kt` | 211 | `RailData`, `dotFor`, `LocalRailData` (Task 6.2, which touches it first) | `chrome/RailData.kt` |
| `settings/SettingsControls.kt` | 204 | `SwatchCard` | `settings/SwatchCard.kt` |

**Inventory correction.** `DocumentariesDepartmentScreen.kt:136` is not a `PagePill`. It is a `DeptRowHeading` whose label bakes the arrow in (`"All N →"`). The only "All N →" `PagePill` is Movies' (`MoviesDepartmentScreen.kt:111-116`). The web's Documentaries link is a plain `see-all` labelled `All N` (`department-pages.js:123`), so Task 6.2 drops the baked arrow and lets `DeptRowHeading` draw it.

## Task 6.1: Artwork lifts and zooms under a hovering pointer, and a plate's name turns accent

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/PosterCard.kt` (whole file)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/home/FeatureCard.kt`; modify `home/HomeFeatures.kt` (lose `FeatureCard`)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/home/PullQuote.kt`; modify `home/ResumeCard.kt` (`ResumeCard` `:72-92`; lose `Quote`)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/TileLines.kt`; modify `ArtTile.kt` (KDoc `:59-61`, `:73-86`; lose `:136-206`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/CastPanel.kt` (`PersonCard` `:101-130`)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/SearchPeople.kt`; modify `SearchGroupsView.kt` (lose `PeopleSection` `:202-228`)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/CardHoverMotionTest.kt`

**Interfaces:**
- **Consumes:** `hoverMotion`, `catalogueIndication`, `Motion` (phase 01); `MediagramTheme`'s `LocalIndication` (phase 02).
- **Produces** (visibility only):
  - `FeatureCard` becomes `internal` (in `ui.catalog.home`);
  - `PeopleSection` becomes `internal` (in `ui.catalog`);
  - `Quote`, `tileLines`, `TileFlow` and `tileColumnsOf` keep their visibility and package.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/CardHoverMotionTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import data.PortraitRequestLog
import designsystem.MediagramTheme
import model.Credit
import model.TitleCredits
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Artwork moves for a hovering mouse or stylus and never for a finger, the
 * web's `(hover: hover)` gate (`catalog.css:51`). A layer's translation shows
 * in a node's bounds in root, so a plate's lift is read off the initials it
 * carries in place of a poster, and a face's off its initials. A zoom moves
 * only an image, which has no semantics node to measure; the module's own
 * tests and the device walk cover those. A name's colour is read from the
 * text layout it reports, which carries the colour it was composed with.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardHoverMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var accent = Color.Unspecified

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent {
                MediagramTheme {
                    accent = MaterialTheme.colorScheme.primary
                    Box { content() }
                }
            }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    private fun colourOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    private fun plate() = show { PosterCard(posterPath = null, title = "Dune", caption = null, modifier = Modifier.width(140.dp), onClick = {}) }

    @Test fun aPlateLiftsForAHoveringPointerButNeverForAFinger() {
        plate()
        val rest = top("D")
        compose.onNodeWithText("Dune").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(rest, top("D"), 0.3f, "a finger never lifts the plate")
        compose.onNodeWithText("Dune").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Dune").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(rest - 6f, top("D"), 0.3f, "a hovering pointer lifts it 6dp")
    }

    @Test fun thePlatesNameFadesToTheAccentWhilePressedAndBack() {
        plate()
        val rest = colourOf("Dune")
        assertNotEquals(accent, rest)
        compose.onNodeWithText("Dune").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        val mid = colourOf("Dune")
        assertNotEquals(rest, mid, "the name has started to fade")
        assertNotEquals(accent, mid, "rather than snapping")
        compose.mainClock.advanceTimeBy(250)
        assertEquals(accent, colourOf("Dune"))
        compose.onNodeWithText("Dune").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(rest, colourOf("Dune"))
    }

    @Test fun aCastFaceLiftsForAHoveringPointerButNeverForAFinger() {
        show {
            CastPanel(
                credits = TitleCredits(cast = listOf(Credit(personId = 1, name = "Ada Lovelace", role = "Herself", portraitPath = null)), crew = emptyList()),
                onOpenPerson = {},
                fetchPortrait = { null },
                portraits = PortraitRequestLog(),
            )
        }
        val rest = top("AL")
        compose.onNodeWithText("Ada Lovelace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest, top("AL"), 0.3f, "a finger never lifts the face")
        compose.onNodeWithText("Ada Lovelace").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Ada Lovelace").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(rest - 3f, top("AL"), 0.3f, "a hovering pointer lifts it 3dp")
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.CardHoverMotionTest'`
Expected: 3 failures. The hover lifts read rest, and the name's mid-fade colour equals rest. The finger halves pass already, as they must keep doing.

- **If after Step 4 the press halves pass but a hover half still reads rest**, Robolectric dropped the synthetic mouse event before `hoverable` saw it. Report BLOCKED with the output; don't delete the assertion. The module's own hover tests already cover `hoverMotion`.

- [ ] **Step 3: The plate — `PosterCard.kt`, whole file**

Replace the file with:

```kotlin
package ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import designsystem.Motion
import designsystem.Spacing
import designsystem.hoverMotion

/**
 * A plate, named underneath rather than across its face.
 *
 * Every episode of a show carries the same artwork, and the index pinned in
 * a channel carries no artwork at all, so the face is the least reliable
 * place to say what something is. Initials stand in for a missing poster —
 * enough to tell two plates apart at a glance, and the name is right below
 * them either way.
 *
 * The whole plate is the target, artwork and caption together. A name on
 * two lines is a third of this thing's height on a phone, and a name that
 * does not open what it names is a dead patch in the middle of a wall.
 * Merging the semantics is the other half of that: a screen reader should
 * meet one plate once, not an image and then its title again.
 *
 * Under a hovering mouse or stylus the art lifts and the name turns to the
 * accent, as the web's plate does; a finger only turns the name, while
 * pressed, since the web gives touch no lift either.
 *
 * [meta] is a fact about the title itself — an episode's show and number —
 * and sits above [caption], a fact about this viewer's own place in it
 * (`shelf-view.js`'s `card()` keeps the same two lines in the same order).
 * [progress] and [watched] draw the rule and the tick a collection card
 * never carries — only a film or a set on Continue or Next up does.
 * [held] draws the same "offline" badge `set-badge.js` draws — a title
 * this device holds in full.
 */
@Composable
internal fun PosterCard(
    posterPath: String?,
    title: String,
    caption: String?,
    modifier: Modifier,
    onClick: () -> Unit,
    meta: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    held: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    // `.card .name` turns accent over `--hover` while hovered (`catalog.css:44,48`);
    // a finger has no hover, so its press stands in.
    val nameInk by animateColorAsState(
        if (hovered || pressed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        animationSpec = Motion.hover(),
        label = "plate name",
    )
    Column(
        modifier = modifier
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
    ) {
        PosterArt(
            posterPath = posterPath,
            title = title,
            // `.card:hover .thumb` lifts 6px over 700ms (`catalog.css:49,51`). No
            // shadow: the plate carries none here, as PosterArt explains.
            modifier = Modifier.hoverMotion(source, durationMs = 700, lift = (-6).dp),
            progress = progress,
            watched = watched,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = nameInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small),
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (held) OfflineBadge(modifier = Modifier.padding(top = 2.dp))
    }
}
```

`LocalContentColor.current` is the name's colour today: `titleSmall` carries no colour (`core/designsystem/src/main/kotlin/Type.kt:133-143`).

- [ ] **Step 4: The feature card zooms — move it to `home/FeatureCard.kt` first**

Create `ui-mobile/src/main/kotlin/ui/catalog/home/FeatureCard.kt` with this header:

```kotlin
package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.Feature
import catalog.label
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import designsystem.Radius
import designsystem.catalogueIndication
import designsystem.hoverMotion
import model.Kind
import java.io.File
```

Move `FeatureCard` (`HomeFeatures.kt:128-210` on main, from `@Composable` to its closing brace) below it unchanged, except `private fun FeatureCard` becomes `internal fun FeatureCard`. If phase 03 has changed `FeatureCard`, move it as it stands.

From `HomeFeatures.kt`, remove the imports only `FeatureCard` used:
- `androidx.compose.foundation`: `background`, `clickable`, `layout.Box`, `layout.width`, `layout.widthIn`, `shape.RoundedCornerShape`
- `androidx.compose.material3`: `MaterialTheme`, `Text`
- `androidx.compose.ui`: `Alignment`, `draw.clip`, `graphics.Brush`, `graphics.Color`, `layout.ContentScale`, `semantics.Role`, `text.font.FontWeight`, `text.style.TextOverflow`, `unit.em`, `unit.sp`
- others: `catalog.label`, `coil3.compose.AsyncImage`, `designsystem.Eyebrow`, `designsystem.Radius`, `model.Kind`, `java.io.File`

`HomeFeatures.kt` drops to about 105 lines.

In `FeatureCard.kt`, after `val secondCard = index == 1`, add:

```kotlin
    val source = remember { MutableInteractionSource() }
```

then replace (moved from `HomeFeatures.kt:144-165`):

```kotlin
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.card))
                .background(Color(0xFF141416))
                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) }),
    ) {
```

with:

```kotlin
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.card))
                .background(Color(0xFF141416))
                // A feature is a link (`home-features.js:41`), and links don't shrink.
                .clickable(
                    interactionSource = source,
                    indication = catalogueIndication(pressScale = 1f),
                    role = Role.Button,
                    onClick = { onOpenTitle(set.setId) },
                ),
    ) {
```

and the image's modifier:

```kotlin
                modifier = Modifier.matchParentSize(),
            )
```

with:

```kotlin
                // `.feature:hover .feature-image` zooms to 1.045 over 1600ms (`home.css:157,160`),
                // inside the card's clip, for a hovering mouse or stylus only.
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1600, zoom = 1.045f),
            )
```

- [ ] **Step 5: The resume card zooms — move `Quote` to `home/PullQuote.kt` first**

This edit takes `ResumeCard.kt` from 191 lines to 204 (and phase 04 adds 3). Create `ui-mobile/src/main/kotlin/ui/catalog/home/PullQuote.kt` with this header:

```kotlin
package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.CoverTitle
import model.MediaSet
```

Move `Quote` (`ResumeCard.kt:156-191`, KDoc through closing brace) below it unchanged. From `ResumeCard.kt`, remove the imports only `Quote` used: `androidx.compose.ui.text.font.FontStyle`, `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.em`, `designsystem.CoverTitle` and `model.MediaSet`.

Add to `ResumeCard.kt` these imports: `androidx.compose.foundation.LocalIndication`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.remember` and `designsystem.hoverMotion`. Then, in `ResumeCard`, after `val art = set.backdropPath ?: set.posterPath`, add:

```kotlin
    val source = remember { MutableInteractionSource() }
```

and replace (`:89-92` on main):

```kotlin
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (art != null) {
            AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
```

with:

```kotlin
                .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
    ) {
        if (art != null) {
            // `.resume-card:hover .resume-art img` zooms to 1.04 over 1200ms (`home.css:245-246`), inside the clip.
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1200, zoom = 1.04f),
            )
```

`ResumeCard.kt` ends at about 165 lines.

- [ ] **Step 6: Genre tiles and destinations zoom — move the tile-line helpers to `TileLines.kt` first**

Create `ui-mobile/src/main/kotlin/ui/catalog/TileLines.kt` with this header:

```kotlin
package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
```

Move `ArtTile.kt:136-206` below it unchanged: `TileGap`, `tileColumnsOf`, `tileLines`, `TileFlow` and `TileLine`, with their KDoc. `aspectRatioAtLeast` (`:122-134`) stays, since only `ArtTile` uses it.

From `ArtTile.kt`, remove the imports only the moved code used:
- `androidx.compose.foundation.layout`: `Arrangement`, `BoxWithConstraints`, `Row`, `Spacer`, `fillMaxWidth`
- `androidx.compose.foundation.lazy.LazyListScope`, `androidx.compose.runtime.key`, `androidx.compose.ui.unit.Dp`

Add `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.remember`, `designsystem.catalogueIndication` and `designsystem.hoverMotion`.

In the KDoc, replace (`:59-61`):

```kotlin
 * [destination] sets the name as `.destination-name` does — larger and in
 * capitals — rather than as `.genre-tile-name`. The web's hover zoom has no
 * touch counterpart and is left out.
```

with:

```kotlin
 * [destination] sets the name as `.destination-name` does — larger and in
 * capitals — rather than as `.genre-tile-name`. The web's hover zoom
 * follows a hovering mouse or stylus; a finger never zooms the art, which
 * would still be moving after the tap had opened the tile.
```

Replace `:73-86`:

```kotlin
    val ink = if (art != null) OnImage else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .aspectRatioAtLeast(aspectRatio)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        if (art != null) {
            AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            ArtTileScrim(destination, Modifier.matchParentSize())
        }
```

with:

```kotlin
    val ink = if (art != null) OnImage else MaterialTheme.colorScheme.onSurface
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .aspectRatioAtLeast(aspectRatio)
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // Both are links (`utility-pages.js:76`, `collections-page.js:47`),
                // and links don't shrink.
                .clickable(
                    interactionSource = source,
                    indication = catalogueIndication(pressScale = 1f),
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {},
    ) {
        if (art != null) {
            // `.genre-tile:hover img` and `.destination:hover img` zoom to 1.05 over
            // 1100ms (`catalog.css:141,145`, `departments.css:99,103`), inside the clip.
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().hoverMotion(source, durationMs = 1100, zoom = 1.05f),
            )
            ArtTileScrim(destination, Modifier.matchParentSize())
        }
```

`ArtTile.kt` ends at about 145 lines.

- [ ] **Step 7: A cast face lifts — `CastPanel.kt`**

Add `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.remember`, `designsystem.catalogueIndication` and `designsystem.hoverMotion`. In `PersonCard`, replace `:108-115`:

```kotlin
    val portrait = rememberPortrait(person.personId, person.portraitPath, portraits, fetchPortrait)
    Column(
        modifier = Modifier.width(PERSON_WIDTH).clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.size(FACE_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
```

with:

```kotlin
    val portrait = rememberPortrait(person.personId, person.portraitPath, portraits, fetchPortrait)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier =
            Modifier
                .width(PERSON_WIDTH)
                // A person is a link (`cast.js:49`), and links don't shrink.
                .clickable(
                    interactionSource = source,
                    indication = catalogueIndication(pressScale = 1f),
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Box(
            // `.person:hover .person-face` rises 3px over 520ms (`title-page.css:178,180`).
            modifier =
                Modifier
                    .hoverMotion(source, durationMs = 520, lift = (-3).dp)
                    .size(FACE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
```

- [ ] **Step 8: A face in search lifts too — move `PeopleSection` to `SearchPeople.kt`**

The web's search draws the same `personCard` (`search-view.js:127-130`). Create `ui-mobile/src/main/kotlin/ui/catalog/SearchPeople.kt`, replacing `SearchGroupsView.kt:202-228`:

```kotlin
package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.BrowseViewModel
import catalog.VisiblePerson
import catalog.spelledCountOf
import designsystem.Spacing
import designsystem.catalogueIndication
import designsystem.hoverMotion
import ui.common.catalog.rememberPortrait

/**
 * People among the hits, as round portraits: the web's search draws the
 * same `personCard` the Cast tab does (`search-view.js:127-130`), so a face
 * lifts for a hovering pointer here as it does there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleSection(people: List<VisiblePerson>, onOpenPerson: (Long) -> Unit) {
    val browseViewModel: BrowseViewModel = hiltViewModel()
    Column(modifier = Modifier.padding(top = Spacing.medium)) {
        Text("People", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            for (person in people) {
                // Fetched lazily, at most once per session per person — the
                // same [rememberPortrait] a person's own page asks with.
                val portrait = rememberPortrait(person.personId, person.portraitPath, browseViewModel.portraits, browseViewModel::fetchPortrait)
                val source = remember { MutableInteractionSource() }
                Column(
                    modifier =
                        Modifier
                            // A person is a link (`cast.js:49`), and links don't shrink.
                            .clickable(
                                interactionSource = source,
                                indication = catalogueIndication(pressScale = 1f),
                                role = Role.Button,
                            ) { onOpenPerson(person.personId) }
                            .padding(Spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // `.person:hover .person-face` rises 3px over 520ms (`title-page.css:178,180`).
                    Box(Modifier.hoverMotion(source, durationMs = 520, lift = (-3).dp)) {
                        PersonFace(name = person.name, portraitPath = portrait, size = 64.dp)
                    }
                    Text(person.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.extraSmall))
                    Text(
                        spelledCountOf(person.titles, "title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

Delete `SearchGroupsView.kt:202-228`, from `@OptIn` through `PeopleSection`'s closing brace. Then remove the imports only it used:
- `androidx.compose.ui.Alignment`
- `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
- `catalog.BrowseViewModel`, `catalog.VisiblePerson`
- `ui.common.catalog.rememberPortrait`

`spelledCountOf` stays, because `CollectionsSection` uses it.

- [ ] **Step 9: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.CardHoverMotionTest'`
Expected: PASS, 3 tests.

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS. The suites cover every moved symbol: `HomeFeaturesTest`, `HomeScreenTest`, `SearchCollectionsTest`, `CollectionsScreenTest`, `GenresIndexScreenTest` and `PersonScreenTest`.

- [ ] **Step 10: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): artwork lifts and zooms under a hovering pointer, and a plate's name turns accent"
```

## Task 6.2: Arrows nudge toward where they lead, and no link shrinks

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/CatalogueButtons.kt` (phase 02's file: `CatalogueButton`, `CatalogueIconButton`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/TitlePills.kt` (the play pill, `:59-63` on main)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/ContinueBand.kt` (`BandHeading`'s link, `:101-109`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/DeptRowHeading.kt` (whole function)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/DocumentariesDepartmentScreen.kt` (`:136`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/PagePill.kt` (whole function)
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/AppChrome.kt` (`navigationIcon` `:114-126`; lose `:161-185`)
- Create: `ui-mobile/src/main/kotlin/ui/chrome/RailSelect.kt`
- Modify, the other web links (Step 9):
  - chrome: `chrome/LibraryRail.kt` (`:126`, `:185`; lose `:51-76`), `chrome/ChromeControls.kt` (`:58-63`, `:125`), `chrome/CompactLibraryHeader.kt` (`:87`, `:143`);
  - catalog: `catalog/GenreLinks.kt` (`:30`), `catalog/FactSheet.kt` (`:81`), `catalog/DeptQuote.kt` (phase 05's), `catalog/ShelfPager.kt` (`:45-71`);
  - home: `catalog/home/PullQuote.kt` (Task 6.1's), `catalog/home/RecentBand.kt` (`:146`), `catalog/home/CoverSlide.kt` (`:164`)
- Create: `ui-mobile/src/main/kotlin/ui/chrome/RailData.kt` (moved out of `LibraryRail.kt`, which is 211 lines and is first edited here)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/ArrowNudgeTest.kt`
- Modify test: `ui-mobile/src/test/kotlin/ui/catalog/DocumentariesDepartmentScreenTest.kt:149`

**Interfaces:**
- **Consumes:** `hoverMotion` (its `nudgeOnPress` defaults true for a nudge), `catalogueIndication`.
- **Produces:**
```kotlin
@Composable fun CatalogueButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit)
@Composable fun CatalogueIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit)
internal fun railItemFor(destination: Destination): RailItem?   // was private in AppChrome.kt
internal fun railSelect(item: RailItem, browse: BrowseActions, menu: MenuActions)
```
- `RailData`, `dotFor` and `LocalRailData` move to `RailData.kt`, keeping their names, visibility and package.

**Semantics rule:** a glyph is split into its own `Text` only so that it can move, and **TalkBack hears exactly what it heard before**.
- Where the arrow was part of the label ("▶ Play", "See all →"), the link sets that full string once with `clearAndSetSemantics { text = … }`. The existing tests that find "▶ Play" and "See all →" keep passing unchanged.
- Where the arrow is new (`DeptRowHeading`), the link reads its label alone. The arrow is decoration, as the web's `::after` is.
- `clearAndSetSemantics` replaces what the link's children say in the merged tree. It leaves the clickable's own role and action, which sit outside it on the same node, and it leaves the children in the unmerged tree, where the tests measure the glyph.

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/ArrowNudgeTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.catalog.home.BandHeading
import kotlin.test.assertEquals

/**
 * An arrow nudges toward where it leads while pressed — the touch form of
 * the web's hover — and nothing else in its control moves. The glyph's
 * layer translation shows in its bounds in root; the press's 0.98 snap is
 * drawn, not laid out, so it doesn't move them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArrowNudgeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MediagramTheme { content() } }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun left(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value

    private fun press(text: String) = compose.onNodeWithText(text).performTouchInput { down(center) }

    private fun release(text: String) = compose.onNodeWithText(text).performTouchInput { up() }

    @Test fun thePlayTriangleNudgesWhilePressedAndOnlyItMoves() {
        show {
            TitlePills(
                playLabel = "Play", onPlay = {}, watchlisted = false, onToggleWatchlist = {},
                editorsChoicePinned = false, onToggleEditorsChoice = null,
            )
        }
        compose.onNodeWithText("▶ Play").assertExists()
        val glyph = left("▶")
        val label = left("Play")
        press("▶ Play")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(glyph + 2f, left("▶"), 0.3f, "the triangle nudges 2dp toward the film")
        assertEquals(label, left("Play"), 0.3f, "and the label stays put")
        release("▶ Play")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(glyph, left("▶"), 0.3f)
    }

    @Test fun seeAllsArrowNudgesWhilePressedAndTheLinkStillReadsAsBefore() {
        var opened = 0
        show { BandHeading(title = "Continue Watching", count = null, onSeeAll = { opened++ }) }
        val arrow = left("→")
        press("See all →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow + 4f, left("→"), 0.3f, "the arrow nudges 4dp toward the wall")
        release("See all →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow, left("→"), 0.3f)
        assertEquals(1, opened)
    }

    @Test fun aDepartmentRowsLinkGainsTheWebsArrowAndStillReadsAsItsLabel() {
        show { DeptRowHeading(title = "Featured Movies", onSeeAll = {}, seeAllLabel = "All 3 films") }
        compose.onNodeWithText("All 3 films").assertExists()
        compose.onAllNodesWithText("All 3 films →").assertCountEquals(0)
        val arrow = left("→")
        press("All 3 films")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(arrow + 4f, left("→"), 0.3f)
    }

    @Test fun theAllNPillMovesAsAWholeAndANewListPillStaysPut() {
        show {
            Column {
                PagePill(text = "All 3 films →", onClick = {}, large = true)
                PagePill(text = "＋ New list", onClick = {})
            }
        }
        val all = left("All 3 films →")
        press("All 3 films →")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(all + 3f, left("All 3 films →"), 0.3f, "the whole pill moves toward the shelf")
        release("All 3 films →")
        compose.mainClock.advanceTimeBy(600)
        val make = left("＋ New list")
        press("＋ New list")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(make, left("＋ New list"), 0.3f, "a button-type pill has no motion of its own")
    }
}
```

In `DocumentariesDepartmentScreenTest.kt:149`, replace:

```kotlin
        val matcher = hasText("All 15 →")
```

with:

```kotlin
        val matcher = hasText("All 15")
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.ArrowNudgeTest' --tests 'ui.catalog.DocumentariesDepartmentScreenTest'`
Expected:
- `ArrowNudgeTest`: the first three fail fetching a node with the text "▶" or "→", which doesn't exist yet; the fourth fails its +3 assertion.
- `DocumentariesDepartmentScreenTest`: `theFoldersAllLinkOnItsHeadingOpensTheCollectionRatherThanPlaying` fails, because no node reads "All 15" yet.

- [ ] **Step 3: Two of the button wrappers take Material's `interactionSource`**

In `CatalogueButtons.kt`, add one line at the end of the file's opening comment:

```kotlin
 * [CatalogueButton] and [CatalogueIconButton] take Material's own
 * `interactionSource`, for content that moves with the button's hover or press.
```

Replace `CatalogueButton`'s head:

```kotlin
fun CatalogueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
```

with:

```kotlin
fun CatalogueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
```

and `CatalogueIconButton`'s head:

```kotlin
fun CatalogueIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
```

with:

```kotlin
fun CatalogueIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
```

- [ ] **Step 4: "▶ Play" — `TitlePills.kt`**

Add these imports:
- `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.foundation.layout.Row`, `androidx.compose.ui.Alignment`
- `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.text`, `androidx.compose.ui.text.AnnotatedString`
- `designsystem.hoverMotion`

Replace (main `:59-63`, after phase 02's rename):

```kotlin
        if (playLabel != null) {
            CatalogueButton(onClick = onPlay, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text("▶ $playLabel")
            }
        }
```

with:

```kotlin
        if (playLabel != null) {
            val play = remember { MutableInteractionSource() }
            CatalogueButton(onClick = onPlay, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET), interactionSource = play) {
                // Two nodes so the triangle can move on its own, still read as the one label it was.
                Row(
                    modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("▶ $playLabel") },
                    // `.pill { gap: 10px }` (`title-page.css:9`).
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // `.pill:hover .pill-play` moves 2px over 520ms (`title-page.css:26-27`); a press nudges it too.
                    Text("▶", modifier = Modifier.hoverMotion(play, durationMs = 520, nudge = 2.dp))
                    Text(playLabel)
                }
            }
        }
```

- [ ] **Step 5: "See all →" — `ContinueBand.kt` `BandHeading`**

Add these imports:
- `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.remember`
- `androidx.compose.ui.semantics.clearAndSetSemantics`, `androidx.compose.ui.semantics.text`, `androidx.compose.ui.text.AnnotatedString`
- `designsystem.catalogueIndication`, `designsystem.hoverMotion`

Replace `:101-109`:

```kotlin
        Text(
            text = "See all →",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(role = Role.Button, onClick = onSeeAll)
                    .padding(vertical = 12.dp),
        )
```

with:

```kotlin
        val seeAll = remember { MutableInteractionSource() }
        val linkStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
        val linkInk = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            modifier =
                Modifier
                    // `.see-all` is a link (`home-view.js:39`), and links don't shrink.
                    .clickable(
                        interactionSource = seeAll,
                        indication = catalogueIndication(pressScale = 1f),
                        role = Role.Button,
                        onClick = onSeeAll,
                    )
                    // Read as the one label it was before the arrow had a node of its own.
                    .clearAndSetSemantics { text = AnnotatedString("See all →") }
                    .padding(vertical = 12.dp),
            // `.see-all { gap: 8px }` (`home.css:200`).
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "See all", style = linkStyle, color = linkInk)
            // `.see-all::after` moves 4px toward the wall over 480ms (`home.css:208,210`); a press nudges it too.
            Text(text = "→", style = linkStyle, color = linkInk, modifier = Modifier.hoverMotion(seeAll, durationMs = 480, nudge = 4.dp))
        }
```

- [ ] **Step 6: A department row's link gains the web's arrow — `DeptRowHeading.kt` and Documentaries' label**

Replace `DeptRowHeading.kt` whole (40 lines) with:

```kotlin
package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import designsystem.catalogueIndication
import designsystem.hoverMotion

/**
 * A row's name, with an optional "See all" and an optional control beside it — the Compose port of `deptRow`'s own header.
 * The link carries the web's `.see-all` arrow (`home.css:208`), drawn here rather than written into [seeAllLabel].
 */
@Composable
internal fun DeptRowHeading(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See all",
    extra: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        extra?.invoke()
        if (onSeeAll != null) {
            val source = remember { MutableInteractionSource() }
            val linkStyle = MaterialTheme.typography.labelLarge
            val linkInk = MaterialTheme.colorScheme.primary
            Row(
                modifier =
                    Modifier
                        // `deptRow`'s way onward is a `.see-all` link (`department-hero.js:51`): no press shrink.
                        .clickable(
                            interactionSource = source,
                            indication = catalogueIndication(pressScale = 1f),
                            role = Role.Button,
                            onClick = onSeeAll,
                        )
                        // Read as its label alone, as before: the arrow is decoration, as `::after` is.
                        .clearAndSetSemantics { text = AnnotatedString(seeAllLabel) }
                        .padding(start = Spacing.medium),
                // `.see-all { gap: 8px }` (`home.css:200`).
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = seeAllLabel, style = linkStyle, color = linkInk)
                // `.see-all::after` moves 4px over 480ms on hover (`home.css:208,210`); a press nudges it too.
                Text(text = "→", style = linkStyle, color = linkInk, modifier = Modifier.hoverMotion(source, durationMs = 480, nudge = 4.dp))
            }
        }
    }
}
```

In `DocumentariesDepartmentScreen.kt:136` (on main; inside phase 03's `revealItem(reveals, "group-…")` once that has run), replace:

```kotlin
                    seeAllLabel = "All ${group.collection.count} →",
```

with:

```kotlin
                    seeAllLabel = "All ${group.collection.count}",
```

The web's label is `All ${n}`, with the arrow from `.see-all::after` (`department-pages.js:123`).

- [ ] **Step 7: "All N →" moves as a whole — `PagePill.kt`**

Add these imports: `androidx.compose.foundation.LocalIndication`, `androidx.compose.foundation.interaction.MutableInteractionSource`, `androidx.compose.runtime.remember`, `designsystem.catalogueIndication` and `designsystem.hoverMotion`. Replace the head of the function and its `Box` modifier (`:28-44`):

```kotlin
@Composable
internal fun PagePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = if (large) 52.dp else 44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = if (large) 28.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
```

with:

```kotlin
@Composable
internal fun PagePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                // `.dept-all` moves 3px toward the whole shelf on hover, as a whole
                // (`departments.css:81,83`); `.make` has no motion (`:107`).
                .then(if (large) Modifier.hoverMotion(source, durationMs = 520, nudge = 3.dp) else Modifier)
                .heightIn(min = if (large) 52.dp else 44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(
                    interactionSource = source,
                    // `.dept-all` is a link and doesn't shrink; `.make` is a button and does.
                    indication = if (large) catalogueIndication(pressScale = 1f) else LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = if (large) 28.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
```

`MoviesDepartmentScreen.kt:111-116` needs no change: `large = true` is the `.dept-all` form, as the KDoc says.

- [ ] **Step 8: The back arrow — `AppChrome.kt`, after moving the rail helpers out**

With the back arrow's edit, `AppChrome.kt` would reach about 207 lines. Create `ui-mobile/src/main/kotlin/ui/chrome/RailSelect.kt` with this header:

```kotlin
package ui.chrome

import ui.Destination
import ui.common.MenuActions
import ui.common.RailItem
```

Move `AppChrome.kt:161-185` below it unchanged (`railItemFor` and `railSelect`, with their KDoc), except `private fun railItemFor` becomes `internal fun railItemFor`. From `AppChrome.kt`, remove `import ui.common.RailItem`, which is now unused.

Add to `AppChrome.kt` these imports:
- `androidx.compose.foundation.LocalIndication`, `androidx.compose.foundation.interaction.MutableInteractionSource`
- `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.ui.unit.dp`
- `designsystem.catalogueIndication`, `designsystem.hoverMotion`

Replace `navigationIcon` (`:114-126`, after phase 02's rename):

```kotlin
                        navigationIcon = {
                            if (backLabel != null) {
                                CatalogueIconButton(
                                    onClick = onBack,
                                    modifier = Modifier.semantics { contentDescription = backLabel },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                    )
                                }
                            }
                        },
```

with:

```kotlin
                        navigationIcon = {
                            if (backLabel != null) {
                                val backSource = remember { MutableInteractionSource() }
                                // The web's way back is a link (`title-spread.js:24`), and links don't shrink.
                                CompositionLocalProvider(LocalIndication provides catalogueIndication(pressScale = 1f)) {
                                    CatalogueIconButton(
                                        onClick = onBack,
                                        modifier = Modifier.semantics { contentDescription = backLabel },
                                        interactionSource = backSource,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            // `.spread-back` moves 3px back over 520ms on hover (`title-page.css:84,86`).
                                            modifier = Modifier.hoverMotion(backSource, durationMs = 520, nudge = (-3).dp),
                                        )
                                    }
                                }
                            }
                        },
```

`AppChrome.kt` ends at about 181 lines. The back arrow isn't unit-tested: its `Icon` has no semantics node to measure. The device walk checks it.

- [ ] **Step 9: No other web link shrinks either**

**The rail's data moves out first.** `LibraryRail.kt` is 211 lines and is first edited here. Create `ui-mobile/src/main/kotlin/ui/chrome/RailData.kt` with this header:

```kotlin
package ui.chrome

import androidx.compose.runtime.compositionLocalOf
import catalog.ChromeCounts
import stats.NEW_ACHIEVEMENT
import ui.common.RailItem
```

Move `LibraryRail.kt:51-76` below it unchanged: `RailData` (KDoc, class, companion), `dotFor` and the comment with `LocalRailData`. From `LibraryRail.kt`, remove `import androidx.compose.runtime.compositionLocalOf` and `import stats.NEW_ACHIEVEMENT`. `ChromeCounts` stays, because `countFor` uses it.

**One expression per site.** Each site below is an `<a>` on the web.
- Add `import designsystem.catalogueIndication` to each file.
- Replace the expression as shown.
- Above the changed line, add one comment naming its web element, in this form, with the citation from the table:

```kotlin
// A link on the web (`film-page.js:134`), and links don't shrink when pressed.
```

| Site | Web element | File:line (main) | Before | After |
|---|---|---|---|---|
| Rail wordmark | `index.html:51`, `<a class="brand">` | `chrome/LibraryRail.kt:126` | `.clickable(onClick = onHome)` | `.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), onClick = onHome)` |
| Rail rows | `index.html:55-65`, `<a>` | `chrome/LibraryRail.kt:185` | `.selectable(selected = active, onClick = { onSelect(item) }, role = Role.Tab)` | `.selectable(selected = active, interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Tab, onClick = { onSelect(item) })` |
| Compact header wordmark | `index.html:51` | `chrome/CompactLibraryHeader.kt:87` | `.clickable(onClick = onHome)` | `.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), onClick = onHome)` |
| Search look-alike | `index.html:89`, an `<input>`: not a link, but `button:active` doesn't reach it either | `chrome/CompactLibraryHeader.kt:143` | `.clickable(onClick = onSearch)` | `.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), onClick = onSearch)` |
| Department pills | `index.html:76-82`, `<a>` | `chrome/ChromeControls.kt:125` | `.selectable(selected = active, onClick = onClick, role = Role.Tab)` | `.selectable(selected = active, interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Tab, onClick = onClick)` |
| Genre links | `film-page.js:134`, `<a class="genre">` | `catalog/GenreLinks.kt:30` | `modifier = Modifier.clickable(role = Role.Button) { onOpen(name) },` | `modifier = Modifier.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Button) { onOpen(name) },` |
| "Part of" franchise | `film-page.js:88`, `<a class="franchise-link">` | `catalog/FactSheet.kt:81` | `modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),` | `modifier = Modifier.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Button, onClick = onOpen),` |
| Department quote credit | `department-hero.js:30`, `<a>` | `catalog/DeptQuote.kt` (moved by phase 05 from `DeptHeroText.kt:198`) | `it.clickable(role = Role.Button, onClickLabel = "Open $leadName", onClick = onOpen)` | `it.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), onClickLabel = "Open $leadName", role = Role.Button, onClick = onOpen)` |
| Home pull-quote | `home-break.js:22`, the credit `<a>` | `catalog/home/PullQuote.kt` (moved in Task 6.1 from `ResumeCard.kt:164`) | `Column(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenTitle(set.setId) }) {` | `Column(modifier = Modifier.fillMaxWidth().clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Button) { onOpenTitle(set.setId) }) {` |
| This month | `home-break.js:40`, `<a>` | `catalog/home/RecentBand.kt:146` (the plate at `:105` is a `card` button and stays) | `.clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) })` | `.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Button, onClick = { onOpenTitle(set.setId) })` |
| Cover "Details" | `home-cover.js:140`, `<a class="cover-details">` | `catalog/home/CoverSlide.kt:164` | `.clickable(role = Role.Button, onClick = onDetails)` | `.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Button, onClick = onDetails)` |

**Circle icon buttons — `ChromeControls.kt`.** Every caller is a link or a field on the web:
- the compact header's rail icons are the rail's own links (`CompactLibraryHeader.kt:105`);
- the bar's search is the `<input>` (`DepartmentsBar.kt:119`).

Replace `:58-63`:

```kotlin
    val target =
        if (selected != null) {
            Modifier.selectable(selected = selected, onClick = onClick, role = Role.Tab)
        } else {
            Modifier.clickable(onClick = onClick)
        }
```

with:

```kotlin
    // Never a `button` on the web, so neither kind shrinks (`theme.css:167`): the
    // rail's links drawn as icons (`index.html:55-65`) and the search field (`:89`).
    val noShrink = catalogueIndication(pressScale = 1f)
    val target =
        if (selected != null) {
            Modifier.selectable(selected = selected, interactionSource = null, indication = noShrink, role = Role.Tab, onClick = onClick)
        } else {
            Modifier.clickable(interactionSource = null, indication = noShrink, onClick = onClick)
        }
```

**The pager — `ShelfPager.kt`.** Its steps and pages are links on the web (`pager.js:85,100`). Its buttons are phase 02's `CatalogueTextButton`, which read `LocalIndication`, so the row provides the link's indication.

Add these imports: `androidx.compose.foundation.LocalIndication`, `androidx.compose.runtime.CompositionLocalProvider` and `designsystem.catalogueIndication`. Replace `:45-71` (after phase 02's rename):

```kotlin
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.medium),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogueTextButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text("‹ Prev") }
        for (link in links) {
            when (link) {
                PageLink.Gap -> Text("…", modifier = Modifier.padding(horizontal = Spacing.small))
                is PageLink.To ->
                    if (link.page == page) {
                        // The page on screen, marked rather than offered.
                        Text(
                            text = link.page.toString(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.medium).semantics { selected = true },
                        )
                    } else {
                        CatalogueTextButton(onClick = { onPage(link.page) }, modifier = Modifier.semantics { role = Role.Button }) {
                            Text(link.page.toString())
                        }
                    }
            }
        }
        CatalogueTextButton(onClick = { onPage(page + 1) }, enabled = page < pages) { Text("Next ›") }
    }
```

with:

```kotlin
    // The web's pager steps and pages are links (`pager.js:85,100`), and links
    // don't shrink when pressed; the buttons below read this indication.
    CompositionLocalProvider(LocalIndication provides catalogueIndication(pressScale = 1f)) {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(vertical = Spacing.medium),
            horizontalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogueTextButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text("‹ Prev") }
            for (link in links) {
                when (link) {
                    PageLink.Gap -> Text("…", modifier = Modifier.padding(horizontal = Spacing.small))
                    is PageLink.To ->
                        if (link.page == page) {
                            // The page on screen, marked rather than offered.
                            Text(
                                text = link.page.toString(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = Spacing.medium).semantics { selected = true },
                            )
                        } else {
                            CatalogueTextButton(onClick = { onPage(link.page) }, modifier = Modifier.semantics { role = Role.Button }) {
                                Text(link.page.toString())
                            }
                        }
                }
            }
            CatalogueTextButton(onClick = { onPage(page + 1) }, enabled = page < pages) { Text("Next ›") }
        }
    }
```

**Coverage.** None of this adds a test: the 0.98 snap is drawn, and no unit test here can see it (plan ruling 4).
- **Suites that click these links still pass**, proving each still opens:
  - `LibraryRailTest` (rail rows);
  - `FilmPageTest` (genre and "Part of" links);
  - `DepartmentHeroTest` (`theQuotesCreditOpensTheLeadWhenGivenACallback`);
  - `HomeScreenTest`.
- **No test clicks these:** the pager, the department pills and the cover's "Details". The device walk covers them, along with no link shrinking anywhere.

**Files after this step:** `LibraryRail.kt` is about 186 lines; `RecentBand.kt` 181; `CoverSlide.kt` about 178 after phase 04.

- [ ] **Step 10: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.ArrowNudgeTest' --tests 'ui.catalog.DocumentariesDepartmentScreenTest'`
Expected: PASS.

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS, with no edit to the tests that find "▶ Play", "▶ Resume …", "See all →", "All 3 films" or "All 3 films →":
- `LibraryFlowTest`, `FilmPageTest`, `SeriesPageTest`, `WideTitleSpreadTest`
- `OptionalMetadataTest`, `TitlePreloadTest`, `FilmPreloadFlowTest`
- `HomeScreenTest`, `MoviesDepartmentScreenTest`
- `CatalogueButtonsTest`'s bare-button guard

**If a "▶ Play" lookup now fails**, the merged button lost the label set on its inner `Row`. Check that `clearAndSetSemantics` sits on that `Row`, not on the button's `modifier`. Don't rewrite the tests.

- [ ] **Step 11: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): arrows nudge toward where they lead, and links no longer shrink when pressed"
```

## Task 6.3: Selection colours fade instead of snapping

**Files:**
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/ChromeControls.kt` (`Pill`, `:109-128`)
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/LibraryRail.kt` (`RailRow`, `:174` and `:184` on main; its data moved out in Task 6.2)
- Modify: `ui-mobile/src/main/kotlin/ui/chrome/CompactLibraryHeader.kt` (`:104`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/ShelfModeToggle.kt` (`:22-31`)
- Create: `ui-mobile/src/main/kotlin/ui/catalog/SearchFilterChips.kt`; modify `SearchGroupsView.kt` (lose `SearchFilterChips` and `filterLabel`)
- Create: `ui-mobile/src/main/kotlin/ui/settings/SwatchCard.kt`; modify `settings/SettingsControls.kt` (lose `SwatchCard`, `:134-160`)
- Modify: `ui-mobile/src/main/kotlin/ui/settings/AppearanceSection.kt` (the accent dot, `:112-118`)
- Modify: `ui-mobile/src/main/kotlin/ui/catalog/home/CoverControls.kt` (the pager's bars, `:107-133`)
- Test: `ui-mobile/src/test/kotlin/ui/catalog/SelectionColourFadeTest.kt`

**Interfaces:**
- **Consumes:** `Motion.hover()`.
- **Produces** (internal):
```kotlin
@Composable internal fun SearchFilterChips(filters: List<Pair<SearchFilter, Int>>, chosen: SearchFilter, onChoose: (SearchFilter) -> Unit)  // was private
internal fun Modifier.selectionRing(shown: State<Float>, ring: Color, ground: Color, radius: Dp, hairline: Color = Color.Transparent): Modifier  // ui.settings
```

**Checked, nothing to fade** (named in the brief; each looked at, and left alone for the reason given):

| Site | Why |
|---|---|
| `CircleIconButton` (`ChromeControls.kt:48-77`) | Its tint is the caller's. `DepartmentsBar`'s search tint is the scroll-blended `ink` (`DepartmentsBar.kt:80,119`), which must not lag. `CompactLibraryHeader` fades its own (below). |
| `GenreLinks.kt:24-31` | Always accent text with no border: nothing changes with state. Its press stopped shrinking in Task 6.2. Its look differs from the web's `.genre` pill (`catalog.css:96-97`); see Gaps. |
| `PagePill.kt` | One constant border. The web's hover-only border change is the indication's tint here. Its nudge is Task 6.2's. |
| Cover pause, `SolidPill`, `LinePill` (`CoverControls.kt:29-106`) | No state colour. Their web changes are hover-only (`home.css:74,76,340`), and the indication's tint stands in. |
| Featured reel dots (`FeaturedReel.kt`) | The web's reel dots snap (`playback.css:575-582`). |

- [ ] **Step 1: Write the failing tests**

Create `ui-mobile/src/test/kotlin/ui/catalog/SelectionColourFadeTest.kt`:

```kotlin
package ui.catalog

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import catalog.SearchFilter
import data.settings.ShelfView
import designsystem.MediagramTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.chrome.LibraryRail
import ui.common.RailItem
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A chosen control's ink fades over `--hover` (160ms) rather than snapping.
 * Text colour is readable through the text layout a node reports, which
 * carries the colour it was composed with. Fills, borders and rings are
 * drawn and can't be read here (see PageGroundColorTest); the device walk
 * checks those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectionColourFadeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>

    @After fun close() {
        compose.runOnUiThread { if (::controller.isInitialized) controller.close() }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            controller.get().setContent { MediagramTheme { content() } }
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun colourOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    /** Just after a choice: neither colour yet; once the fade is done, [to]. */
    private fun assertFades(text: String, from: Color, to: Color) {
        assertNotEquals(from, to, "the two states must differ for this check to mean anything")
        compose.mainClock.advanceTimeBy(64)
        val mid = colourOf(text)
        assertNotEquals(from, mid, "$text has started to fade")
        assertNotEquals(to, mid, "$text has not snapped")
        compose.mainClock.advanceTimeBy(250)
        assertEquals(to, colourOf(text))
    }

    @Test fun theShelfModeTogglesChosenInkFadesIn() {
        var current by mutableStateOf(ShelfView.LIST)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onSurface
            off = MaterialTheme.colorScheme.onSurfaceVariant
            ShelfModeToggle(current = current, onChoose = { current = it })
        }
        assertEquals(off, colourOf("Grid"))
        compose.onNodeWithText("Grid").performClick()
        assertFades("Grid", from = off, to = on)
    }

    @Test fun aRailRowsInkFadesWhenItBecomesTheActiveOne() {
        var active by mutableStateOf<RailItem?>(RailItem.LATEST)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onBackground
            off = MaterialTheme.colorScheme.onSurfaceVariant
            LibraryRail(active = active, onHome = {}, onSelect = { active = it })
        }
        assertEquals(off, colourOf("Genres"))
        compose.onNodeWithText("Genres").performClick()
        assertFades("Genres", from = off, to = on)
    }

    @Test fun aSearchFilterFadesItsInkWhenChosen() {
        var chosen by mutableStateOf(SearchFilter.ALL)
        var on = Color.Unspecified
        var off = Color.Unspecified
        show {
            on = MaterialTheme.colorScheme.onPrimaryContainer
            off = MaterialTheme.colorScheme.onSurfaceVariant
            SearchFilterChips(listOf(SearchFilter.ALL to 3, SearchFilter.MOVIES to 2), chosen) { chosen = it }
        }
        assertEquals(off, colourOf("Movies 2"))
        compose.onNodeWithText("Movies 2").performClick()
        assertFades("Movies 2", from = off, to = on)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.SelectionColourFadeTest'`
Expected: compilation fails. `SearchFilterChips` is private in `SearchGroupsView.kt`. Once Step 7 makes it internal and before the fades exist, the other two fail at "has not snapped".

- [ ] **Step 3: The department pills — `ChromeControls.kt` `Pill`**

Add these imports: `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.draw.drawBehind` and `designsystem.Motion`. Replace `:119-125`, as Task 6.2 left it:

```kotlin
    Box(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .then(if (active) Modifier.background(ink.copy(alpha = 0.12f)) else Modifier)
                // A link on the web (`index.html:76-82`), and links don't shrink when pressed.
                .selectable(selected = active, interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Tab, onClick = onClick)
```

with:

```kotlin
    // `.departments a { transition: background-color var(--hover) }` (`shell.css:50,55`).
    // Only the choice fades: [ink] follows the bar's scroll blend frame by frame,
    // and a tween chasing it would leave the bar trailing the page.
    val fill by animateFloatAsState(if (active) 1f else 0f, animationSpec = Motion.hover(), label = "pill fill")
    Box(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(ink.copy(alpha = 0.12f * fill)) }
                // A link on the web (`index.html:76-82`), and links don't shrink when pressed.
                .selectable(selected = active, interactionSource = null, indication = catalogueIndication(pressScale = 1f), role = Role.Tab, onClick = onClick)
```

- [ ] **Step 4: The rail — `LibraryRail.kt`**

Its data moved to `RailData.kt` in Task 6.2. Add to `LibraryRail.kt` these imports: `androidx.compose.animation.animateColorAsState`, `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.draw.drawBehind` and `designsystem.Motion`. In `RailRow`, replace `:174`:

```kotlin
    val ink = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
```

with:

```kotlin
    // `.rail-nav a { transition: color, background-color var(--hover) }` (`shell.css:50,55`).
    val ink by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.hover(),
        label = "rail ink",
    )
    val fill by animateFloatAsState(if (active) 1f else 0f, animationSpec = Motion.hover(), label = "rail fill")
    val activeFill = MaterialTheme.colorScheme.onBackground
```

and `:184`:

```kotlin
                .then(if (active) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)) else Modifier)
```

with:

```kotlin
                .drawBehind { drawRect(activeFill.copy(alpha = 0.10f * fill)) }
```

`background` stays imported for the rail's own `.background(tones.sidebar)`. `:185`, the `selectable`, is as Task 6.2 left it. `LibraryRail.kt` ends at about 193 lines.

- [ ] **Step 5: The compact header's rail icons — `CompactLibraryHeader.kt`**

Add these imports: `androidx.compose.animation.animateColorAsState`, `androidx.compose.runtime.getValue` and `designsystem.Motion`. Replace `:104`:

```kotlin
                    val itemInk = if (selected) ink else MaterialTheme.colorScheme.onSurfaceVariant
```

with:

```kotlin
                    // The rail rows' colour fade (`shell.css:50`), here on their icons. [ink] is
                    // the theme's own here, not a scroll blend, so it is safe to animate.
                    val itemInk by animateColorAsState(
                        if (selected) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = Motion.hover(),
                        label = "rail icon",
                    )
```

- [ ] **Step 6: The shelf mode toggle — `ShelfModeToggle.kt`**

Add these imports: `androidx.compose.animation.animateColorAsState`, `androidx.compose.runtime.getValue` and `designsystem.Motion`. Replace `:22-31` (after phase 02's rename):

```kotlin
        for ((view, label) in listOf(ShelfView.LIST to "List", ShelfView.GRID to "Grid")) {
            val on = view == current
            CatalogueTextButton(onClick = { if (!on) onChoose(view) }, modifier = Modifier.semantics { selected = on }) {
                Text(
                    text = label,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
```

with:

```kotlin
        for ((view, label) in listOf(ShelfView.LIST to "List", ShelfView.GRID to "Grid")) {
            val on = view == current
            // `.mode { transition: color var(--hover) }` (`catalog.css:15,17`).
            val ink by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = Motion.hover(),
                label = "shelf mode",
            )
            CatalogueTextButton(onClick = { if (!on) onChoose(view) }, modifier = Modifier.semantics { selected = on }) {
                Text(text = label, color = ink, style = MaterialTheme.typography.labelLarge)
            }
        }
```

- [ ] **Step 7: The search filters — move them to `SearchFilterChips.kt`**

Create `ui-mobile/src/main/kotlin/ui/catalog/SearchFilterChips.kt`:

```kotlin
package ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.SearchFilter
import designsystem.Motion
import designsystem.Spacing

/**
 * One pill per kind of hit, with its count, the chosen one filled — the
 * web's `.search-filter` row (`catalog.css:150-153`). Choosing one fades its
 * ink and fill over `--hover` (`:151`), keyed by kind so a list that gains or
 * loses a kind while typing never fades one pill into another's colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchFilterChips(filters: List<Pair<SearchFilter, Int>>, chosen: SearchFilter, onChoose: (SearchFilter) -> Unit) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        for ((kind, count) in filters) {
            key(kind) {
                val selected = kind == chosen
                val ink by animateColorAsState(
                    if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    animationSpec = Motion.hover(),
                    label = "filter ink",
                )
                val fill by animateColorAsState(
                    if (selected) colors.primaryContainer else colors.surfaceVariant,
                    animationSpec = Motion.hover(),
                    label = "filter fill",
                )
                Text(
                    text = "${filterLabel(kind)} $count",
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    modifier = Modifier
                        // Clipped, so the fill and the press tint both keep the pill's shape.
                        .clip(RoundedCornerShape(16.dp))
                        .drawBehind { drawRect(fill) }
                        .clickable(role = Role.Button) { onChoose(kind) }
                        .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
                )
            }
        }
    }
}
```

Move `filterLabel` (`SearchGroupsView.kt:144-153`) below it unchanged, still `private`. Delete the old `SearchFilterChips` (`SearchGroupsView.kt:119-142`) and `filterLabel`. The call at `:83` stays as it is.

From `SearchGroupsView.kt`, remove the imports only these used: `androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.shape.RoundedCornerShape` and `androidx.compose.ui.semantics.Role`. `SearchGroupsView.kt` ends at about 175 lines.

- [ ] **Step 8: Swatches and accent dots — `SwatchCard.kt` and `AppearanceSection.kt`**

Today a chosen swatch or dot snaps its border and insets its picture by 4dp, which is a layout change that can't fade. The ring is now drawn over the picture's edge instead:
- the accent band at the edge, then a band of the page's ground, then the picture;
- both bands fade in, so nothing is laid out again.

The end looks as it does today: the ring, the ground-coloured gap and the picture inside it.

Create `ui-mobile/src/main/kotlin/ui/settings/SwatchCard.kt`:

```kotlin
package ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Motion
import designsystem.Radius

/** Each band of the ring, accent then ground — `0 0 0 2px paper, 0 0 0 4px accent` (`appearance.css:47`). */
private val RING_BAND = 2.dp

/** The unchosen edge, `inset 0 0 0 1px var(--rule)` (`appearance.css:45`). */
private val HAIRLINE = 1.dp

/**
 * A picture of a theme or an artwork mode: 16:9, at [Radius.card], ringed
 * in the accent, with the page's own ground as the gap between the ring and
 * the picture, when selected. Purely presentational: the caller owns
 * `selectable` and the merge boundary, the same way an accent dot's own
 * wrapping column does —
 * putting `selectable` on this nested box instead left it out of the
 * caller's merged semantics node entirely.
 */
@Composable
internal fun SwatchCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(Radius.card)
    val colors = MaterialTheme.colorScheme
    // `.swatch { transition: box-shadow var(--hover) }` (`appearance.css:45,47`).
    val shown = animateFloatAsState(if (selected) 1f else 0f, animationSpec = Motion.hover(), label = "swatch ring")
    Box(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .selectionRing(shown, ring = colors.primary, ground = colors.background, radius = Radius.card, hairline = colors.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(shape), content = content)
    }
}

/**
 * The ring Settings draws on a chosen swatch or accent dot: [ring] at the
 * edge, then a band of the page's [ground], then the picture. [shown] runs
 * 0 to 1 as it fades in, and it is read here, in the draw phase. Drawn over
 * the picture's edge rather than by insetting it, so choosing one never lays
 * anything out again. [hairline] is the unchosen edge, fading out as the
 * ring fades in.
 */
internal fun Modifier.selectionRing(
    shown: State<Float>,
    ring: Color,
    ground: Color,
    radius: Dp,
    hairline: Color = Color.Transparent,
): Modifier =
    drawWithContent {
        drawContent()
        val p = shown.value
        val band = RING_BAND.toPx()
        val hair = HAIRLINE.toPx()
        val corner = radius.toPx()
        roundBand(hairline.copy(alpha = hairline.alpha * (1f - p)), hair / 2, hair, corner)
        if (p > 0f) {
            roundBand(ground.copy(alpha = ground.alpha * p), band + band / 2, band, corner)
            roundBand(ring.copy(alpha = ring.alpha * p), band / 2, band, corner)
        }
    }

/** A rounded stroke whose middle runs [inset] in from the edge; a stroke straddles its path. */
private fun DrawScope.roundBand(color: Color, inset: Float, width: Float, corner: Float) =
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(width),
    )
```

From `SettingsControls.kt`, delete `SwatchCard` (`:134-160`, KDoc through closing brace). Then remove the imports only it used: `androidx.compose.foundation.layout.BoxScope`, `androidx.compose.foundation.layout.aspectRatio`, `androidx.compose.foundation.layout.fillMaxSize` and `androidx.compose.ui.draw.clip`. `border`, `RoundedCornerShape` and `Radius` stay. `SettingsControls.kt` ends at about 172 lines.

In `AppearanceSection.kt`, add `androidx.compose.animation.core.animateFloatAsState` and `designsystem.Motion`, and remove `androidx.compose.foundation.border` and `androidx.compose.foundation.layout.padding`; this edit uses up their only call. Replace `:112-118`:

```kotlin
                    val ring =
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape).padding(4.dp)
                        } else {
                            Modifier
                        }
                    Box(modifier = Modifier.size(40.dp).then(ring).clip(CircleShape).background(accent.dark))
```

with:

```kotlin
                    // `.dot { transition: box-shadow var(--hover) }` (`appearance.css:57-58`): the
                    // ring is in the ink and fades in, and the dot itself never shrinks.
                    val shown = animateFloatAsState(if (isSelected) 1f else 0f, animationSpec = Motion.hover(), label = "accent ring")
                    Box(
                        modifier =
                            Modifier
                                .size(ACCENT_DOT)
                                .selectionRing(
                                    shown,
                                    ring = MaterialTheme.colorScheme.onBackground,
                                    ground = MaterialTheme.colorScheme.background,
                                    radius = ACCENT_DOT / 2,
                                )
                                .clip(CircleShape)
                                .background(accent.dark),
                    )
```

and add at the end of the file:

```kotlin

/** An accent dot's size; its ring is drawn inside it. */
private val ACCENT_DOT = 40.dp
```

The ground is `colorScheme.background` because that is what the settings panes are painted with (`SettingsPanes.kt:75,100`). It is the colour today's 4dp gap showed through.

- [ ] **Step 9: The cover's bars — `CoverControls.kt` `CoverPager`**

Add these imports: `androidx.compose.animation.animateColorAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.draw.drawBehind` and `designsystem.Motion`. Replace `:107-133`:

```kotlin
        for (index in 0 until count) {
            Box(
```

with:

```kotlin
        for (index in 0 until count) {
            // `.cover-dot { transition: background-color var(--hover) }` (`home.css:123,125`): the
            // current story's bar fades over as the cover turns. The Featured reel's dots snap, as the web's do.
            val bar by animateColorAsState(
                if (index == current) OnImage else Color(0x52F6F2EA),
                animationSpec = Motion.hover(),
                label = "cover bar",
            )
            Box(
```

and the bar itself:

```kotlin
                            .size(width = 20.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index == current) OnImage else Color(0x52F6F2EA)),
```

with:

```kotlin
                            .size(width = 20.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .drawBehind { drawRect(bar) },
```

- [ ] **Step 10: Run the tests to see them pass**

Run: `./gradlew :ui-mobile:testDebugUnitTest --tests 'ui.catalog.SelectionColourFadeTest'`
Expected: PASS, 3 tests.

Run: `./gradlew :ui-mobile:testDebugUnitTest`
Expected: PASS: `LibraryRailTest`, `DepartmentsBarColorTest`, `AppearanceSectionTest`, `SearchCollectionsTest`, `HomeCoverTest`, `WidthClassStateTest` and every other suite.

- **If `DepartmentsBarColorTest` reads a stale colour mid-scroll**, an animation is chasing the scroll-blended `ink`. Check that `Pill` animates only `fill`, and that `CircleIconButton` is untouched.

- [ ] **Step 11: Lint and commit**

```bash
./gradlew :ui-mobile:spotlessApply :ui-mobile:detekt
git add ui-mobile
git commit -m "feat(motion): selection colours fade instead of snapping"
```

## Success criteria

- Tests:
  - `CardHoverMotionTest` (3), `ArrowNudgeTest` (4) and `SelectionColourFadeTest` (3) pass;
  - `:ui-mobile:testDebugUnitTest` stays green;
  - no existing test is edited, apart from `DocumentariesDepartmentScreenTest:149`.
- **A finger never lifts or zooms artwork.** Under a finger only the plate's name turns accent, and only arrows nudge.
- **The numbers match the web:**
  - lift −6dp/700ms (plate) and −3dp/520ms (face);
  - zoom 1.045/1600ms (feature), 1.04/1200ms (resume) and 1.05/1100ms (tile, destination);
  - nudge +4dp/480ms (see-all), +2dp/520ms (play), −3dp/520ms (back) and +3dp/520ms ("All N →", as a whole).
- **What TalkBack hears is unchanged**, except Documentaries' folder link, which now reads "All 15" like Movies' rows.
- **No web link shrinks when pressed.** Every site whose web element is an `<a>` passes `catalogueIndication(pressScale = 1f)` (the Context table and Task 6.2 Step 9), and so do the two search controls. Buttons keep 0.98.
- Every file this phase creates or edits is under 200 lines.
- **Device walk** (phase 09, tablet `caad49da`), for what can't be read here:
  - the rail, pill, chip, swatch and dot fills and rings fade;
  - the cover's bar fades over as the cover turns;
  - the zooms run inside their clips;
  - the back arrow nudges while pressed;
  - pressing any link (rail, pills, wordmark, genre links, quote credits, "Details", This month, the pager) tints it without shrinking, while a button still snaps to 0.98;
  - the bar's colours follow the scroll blend over the cover with no lag;
  - with "Remove animations" on, everything snaps to its end.
- **Hover on the device.** Check the hover forms with a mouse or a hovering stylus if one is at hand; otherwise they rest on the tests above. Note which way the walk was done.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| A plate lifting at the top of a `LazyRow` loses 6dp to the row's clip. | M×L | The web's `.strip` clips the same way (`overflow-x: auto` makes `overflow-y` clip too, `catalog.css:36-37`). Parity, not a bug. |
| Robolectric doesn't deliver synthetic hover. | L×M | Step 2's note: BLOCKED, not a weaker test. The module's tests already cover `hoverMotion`. |
| The play triangle now sits 10dp from its label (the web's gap), not a space's width away. | L×L | It is the web's number. `TitlePillsWidthTest` checks the row still fits. |
| Eight moves add review load. | M×L | Each is verbatim and named in its step. `git diff -M` shows them as moves. |

**Rollback:** each task is one commit. Reverting 6.2 restores the baked "All N →" label and its test line. Reverting 6.3 restores the inset swatch ring.

## Gaps (owed by Surface Parity, not in this phase)

1. **Rows turn their title accent on hover** (`catalog.css:76-78`; `.card .name` in list form, `:44,48`). Spec §2 names rows, but the inventory maps no Android site to them.
   - The counterparts are `CollectionRows.kt:89`, `SearchRow.kt:44` and `ShelfList.kt:81`. `ShelfList`'s thumb also lifts on the web (`catalog.css:51`).
2. **Settings' Appearance options still shrink 0.98.** On the web each is a `<label>` around a radio input (`appearance.css:38-58`). That is neither a link nor a button, so `button:active` doesn't reach it. They are the `selectable` columns at `AppearanceSection.kt:67-72,104-108,136-141`. They're not links, so this phase leaves them; they're owed by the same CSS rule.
3. **`GenreLinks` looks different from the web's `.genre` pill** (`catalog.css:96-97`): underlined accent text against a bordered, uppercase pill whose border and text turn accent on hover. That is a look decision, not motion.
4. **`SettingsIndex.kt:141`** snaps its chosen row's fill, the same pattern as the rail. The inventory has no row for it, and the web's settings page has no index to compare its press against.
