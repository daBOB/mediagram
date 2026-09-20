# Phase 5: The title detail screen

**Deliverable:** tapping a card opens a screen showing the artwork, the
synopsis, and what the file is — with Play on it.

## Context

- Spec §4 (the screen and its layout), §1 (the text has been on the device all along)
- `web/public/lib/format.js` — `technicalLine`, `hdrLabel`, `bitrateLabel`, the reference these mirror
- `android/ui-mobile/src/main/kotlin/CatalogScreen.kt` — `PosterCard` and `initialsOf`

## Key insight

This screen is the first place on Android where a title is described rather
than just listed, and every field it shows already exists after phases 2 and 4.
It is assembly, not new capability.

It also adds a tap before playback, which is a real cost. Play is the one
prominent control on the screen, for that reason.

---

### Task 1: What a file is, in one line

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/TechnicalLine.kt`
- Test: `android/ui-mobile/src/test/kotlin/TechnicalLineTest.kt`

**Interfaces — Consumes:** `MediaSet` carrying the five fields phase 2 task 1 added.
**Produces:** `internal fun technicalLine(set: MediaSet): String`, `internal fun hdrLabel(hdr: String?): String?`, `internal fun bitrateLabel(totalBytes: Long, durationSeconds: Int?): String?`.
Task 2 renders the first; phase 6 renders the third.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The same line the web player prints, in the same order. A viewer who reads
 * both surfaces should not have to learn two ways of describing one file.
 * `format.js` is the reference.
 */
class TechnicalLineTest {

    @Test
    fun aFilmIsDescribedInTheOrderTheWebPlayerUses() {
        assertEquals(
            "1080p · HDR10 · MKV · HEVC · EAC3 · 14.2 GB · 5 parts · 9.4 Mbps",
            technicalLine(
                setFixture(
                    quality = "1080p", hdr = "HDR10", container = "mkv",
                    vcodec = "hevc", acodec = "eac3",
                    total = 15_246_565_376, duration = 12_980, partCount = 5,
                ),
            ),
        )
    }

    /**
     * SDR is the absence of a fact rather than a fact, and a shelf where
     * every card says SDR says nothing at all.
     */
    @Test
    fun anSdrTitleSaysNothingAboutItsDynamicRange() {
        assertNull(hdrLabel("SDR"))
        assertNull(hdrLabel(null))
        assertEquals("HDR10", hdrLabel("HDR10"))
    }

    /** One part is the ordinary case; saying so is noise. */
    @Test
    fun aSinglePartSetDoesNotMentionItsParts() {
        val line = technicalLine(setFixture(container = "mp4", total = 1_000_000_000, duration = 3_600, partCount = 1))

        assert(!line.contains("part"))
    }

    /** Under ten the first decimal is the difference between links that carry it. */
    @Test
    fun aBitrateIsPreciseWhereItMatters() {
        assertEquals("9.4 Mbps", bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = 12_980))
        assertEquals("24 Mbps", bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = 5_082))
    }

    /** A percentage of an unknown length is a number with nothing behind it. */
    @Test
    fun aBitrateWithNoRuntimeIsNotGuessed() {
        assertNull(bitrateLabel(totalBytes = 15_246_565_376, durationSeconds = null))
        assertNull(bitrateLabel(totalBytes = 0, durationSeconds = 3_600))
    }

    /** Codecs are printed as a viewer would recognise them, not as stored. */
    @Test
    fun containerAndCodecsAreUpperCased() {
        val line = technicalLine(setFixture(container = "mkv", vcodec = "hevc", acodec = "eac3", total = 1, duration = 1))

        assert(line.contains("MKV · HEVC · EAC3"))
    }

    /** A field the index never recorded is left out, not printed empty. */
    @Test
    fun anUnknownFieldIsOmittedRatherThanBlank() {
        val line = technicalLine(setFixture(container = "mkv", vcodec = null, acodec = null, total = 1, duration = 1))

        assert(!line.contains(" ·  · "))
    }
}
```

Add a `setFixture(...)` helper in this file with defaults for every parameter,
rather than repeating a full `MediaSet` literal seven times.

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*TechnicalLineTest*'
```

Expected: FAIL to compile — `unresolved reference: technicalLine`.

- [ ] **Step 3: Implement**

`TechnicalLine.kt` mirrors `format.js`: join the non-empty parts with `" · "`,
in the order quality, HDR, container, vcodec, acodec, size, parts, bitrate.
`humanSize` is the one already written for the System screen in phase 3 — import
it rather than writing a second one. Uppercase the container and codecs, drop
`SDR`, drop a single part, and refuse a bitrate without both numbers positive.

Use `Locale.ROOT` on every `format` call, for the reason `PlayerClock.kt`
already gives.

- [ ] **Step 4: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*TechnicalLineTest*'
```

Expected: PASS, all seven.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/TechnicalLine.kt android/ui-mobile/src/test/kotlin/TechnicalLineTest.kt
git commit -m "feat(android): describe a file the way the web player describes it"
```

---

### Task 2: The screen

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/TitleDetailScreen.kt`
- Modify: `android/ui-mobile/src/main/kotlin/LibraryFlow.kt`, `android/ui-mobile/src/main/kotlin/CatalogScreen.kt`, `android/feature/catalog/src/main/kotlin/CatalogViewModel.kt`
- Modify: `android/core/data/src/main/kotlin/CatalogRepository.kt`, `android/core/model/src/main/kotlin/MediaSet.kt`

**Interfaces — Consumes:** `technicalLine` (Task 1), `CoreClient.showInfo` (phase 2 task 2), `CoreClient.posterPath` and the artwork phase 4 fetches, `LibraryScaffold` (phase 3 task 2).

- [ ] **Step 1: Carry the new fields through**

`MediaSet` gains `container`, `vcodec`, `acodec`, `quality`, `hdr` and
`posterKey`, and `DefaultCatalogRepository.toMediaSetOrNull` carries them from
`SetSummary`. `posterKey` is needed because the detail screen asks
`showInfo(posterKey)`, and deriving it a second time in the UI would be a
second copy of a rule that already exists in Rust.

- [ ] **Step 2: Build the screen**

`TitleDetailScreen(set: MediaSet, info: ShowInfo?, onPlay: () -> Unit)`, laid
out as spec §4 shows: poster beside year, runtime, genres and rating; the
tagline quoted; the overview as a paragraph; the technical line; then Play.

The tagline is quoted and the overview is not, because one is a line of
marketing and the other is a paragraph of description, and a viewer who cannot
tell them apart has been handed a wall of text.

`info` being null is ordinary — a course has no provider entry, and a library
assembled without a TMDB key has no rows at all. The screen renders without
those blocks rather than showing empty ones, the same rule the System screen
follows.

The poster reuses `PosterCard`'s `AsyncImage(model = File(posterPath))` and its
`initialsOf` fallback rather than a second image-loading path.

- [ ] **Step 3: Route to it**

`CatalogScreen`'s `onPlay` becomes `onOpenTitle`. `LibraryFlow.kt` gains an
`openedTitleId` position beside the other three, with its own `BackHandler`,
and the detail screen's Play is what sets `openedSetId`.

`CollectionScreen`'s rows route the same way, so a title reached through a show
behaves as one reached from the shelf.

`CollectionScreen` also gains the same header block above its tree, describing
the show or the course rather than an episode of it. It asks `showInfo` with
the collection's own poster key — which is the key every episode in it already
shares, since the `shows` table holds one row for a whole series. A course has
no provider entry, so it renders the tree alone, as it does today.

- [ ] **Step 4: Build and check the line budget**

```bash
./gradlew :ui-mobile:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/*.kt
```

Expected: PASS, every file under 200. `LibraryFlow.kt` now holds four
positions; if it has crossed, extract the positions and their `BackHandler`s
into `ui-mobile/src/main/kotlin/LibraryPositions.kt`.

- [ ] **Step 5: Prove it on the phone**

```bash
./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

1. Tap a film with artwork: detail opens, showing poster, synopsis and the technical line.
2. Play starts the film; back from the player returns to detail, not to the catalog.
3. Back from detail returns to the catalog.
4. Open a course lesson — no provider entry — and confirm the screen renders without synopsis blocks rather than with empty ones.
5. Rotate on detail: the screen survives.
6. Confirm the technical line matches what the web player shows for the same title.

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): say what a title is before playing it"
```

## Todo list

- [ ] `technicalLine` matches the web's field order and omissions
- [ ] `MediaSet` carries the five technical fields and the poster key
- [ ] Detail screen renders without synopsis blocks when there is no provider entry
- [ ] Play routes to the player; back from the player returns to detail
- [ ] Every `ui-mobile` file under 200 lines
- [ ] The six device confirmations made

## Success criteria

A card opens a screen that describes the title, and playing from it works.
`./scripts/check.sh` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The extra tap makes playback feel slower | Play is the one prominent control on the screen. Device step 2 is where it gets judged; if it reads badly, that is a finding for the user, not a silent redesign. |
| A second image-loading path diverges from the catalog's | The screen reuses `PosterCard`'s `AsyncImage` and `initialsOf`. |
| Poster key derived twice, in Rust and in Kotlin | `MediaSet` carries the key the DTO already computed. |
| `LibraryFlow.kt` grows past 200 with a fourth position | Step 4 measures and names the extraction. |
| Back from the player skips detail | Device step 2 checks exactly this. |

## Next steps

Phase 6 is independent of this and can run before or after it.
