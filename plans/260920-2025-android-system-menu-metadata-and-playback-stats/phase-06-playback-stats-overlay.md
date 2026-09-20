# Phase 6: Playback stats

**Deliverable:** an `ⓘ` in the transport bar that shows what the player is
actually doing — the format being decoded, the buffer, the cache, the reads.

## Context

- Spec §7 (the overlay), §9 (why it reports the decoder rather than the catalog)
- `android/ui-mobile/src/main/kotlin/PlayerControls.kt` — the bar, its glyph buttons, and `GlyphButton`
- `android/ui-mobile/src/main/kotlin/ControlsVisibility.kt` — the rules the overlay inherits
- `web/public/lib/preload-readout.js` — the reference for what a zero means

## Key insight

Every number here is one property access away on the `Player` the UI already
holds, and the cache and read counters were built in phase 3. Nothing is
threaded through `PlayerUiState` — it deliberately carries no position, and the
reason given there applies to every one of these numbers equally: a second copy
of something the player owns can only be the same number later, or a different
one wrongly.

This overlay is deliberately better than the web's. A browser will not say what
it is decoding, so the web builds its technical line from recorded catalog
values; ExoPlayer will say, so Android reports what is actually on screen. The
two surfaces can legitimately disagree, and when they do the catalog is the one
that is wrong.

---

### Task 1: What a stat row says

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/PlaybackStatRows.kt`
- Test: `android/ui-mobile/src/test/kotlin/PlaybackStatRowsTest.kt`

**Interfaces — Consumes:** `PlaybackTotals` (phase 3 task 1), `humanSize` (phase 3 task 3).
**Produces:** `internal fun videoStatLine(...)`, `internal fun audioStatLine(...)`, `internal fun bufferStatLine(...)`, `internal fun cacheStatLine(...)`, `internal fun readsStatLine(...)`, `internal fun droppedStatLine(dropped: Int): String?`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the overlay says, from what the player reports. These read the
 * decoder rather than the catalog, so a title whose index is wrong about its
 * codec shows the truth here.
 */
class PlaybackStatRowsTest {

    @Test
    fun videoIsSizeCodecAndRate() {
        assertEquals("1920×800 HEVC 9.4 Mbps", videoStatLine(width = 1920, height = 800, codec = "video/hevc", bitrate = 9_400_000))
    }

    /** A format the player has not resolved yet is said plainly. */
    @Test
    fun aFormatNotYetKnownSaysSo() {
        assertEquals("not yet known", videoStatLine(width = null, height = null, codec = null, bitrate = null))
    }

    /** media3 reports an unset bitrate as -1, which is not a rate. */
    @Test
    fun anUnsetBitrateIsOmittedRatherThanPrintedAsMinusOne() {
        assertEquals("1920×800 HEVC", videoStatLine(width = 1920, height = 800, codec = "video/hevc", bitrate = -1))
    }

    @Test
    fun audioNamesItsChannelsAndLanguage() {
        assertEquals("EAC3 5.1 German", audioStatLine(codec = "audio/eac3", channels = 6, language = "de"))
    }

    /** Stereo is worth saying; an unknown channel count is not worth guessing. */
    @Test
    fun audioWithoutChannelsOmitsThem() {
        assertEquals("AAC English", audioStatLine(codec = "audio/mp4a-latm", channels = 0, language = "en"))
    }

    @Test
    fun bufferIsTimeAheadAndBytesHeld() {
        assertEquals("1:23 ahead · 47.0 MB", bufferStatLine(aheadMs = 83_000, heldBytes = 49_283_072))
    }

    @Test
    fun cacheIsTheShareServedFromDisk() {
        assertEquals("81% from disk", cacheStatLine(PlaybackTotals(fromCacheBytes = 810, fromUpstreamBytes = 190, fetches = 4, failedReads = 0)))
    }

    /** Before anything has been read there is no share to take. */
    @Test
    fun anUntouchedCacheSaysNothingReadYet() {
        assertEquals("nothing read yet", cacheStatLine(PlaybackTotals(0, 0, 0, 0)))
    }

    @Test
    fun readsAreTheCountAndWhatTheyCarried() {
        assertEquals("34 fetches · 34.0 MB", readsStatLine(PlaybackTotals(0, 35_651_584, 34, 0)))
    }

    /** A failure is only mentioned when there has been one. */
    @Test
    fun failedReadsAreNamedOnlyWhenTheyHappened() {
        assertEquals("34 fetches · 34.0 MB · 2 failed", readsStatLine(PlaybackTotals(0, 35_651_584, 34, 2)))
    }

    /** Zero dropped frames is the ordinary case, and saying so is noise. */
    @Test
    fun noDroppedFramesIsNoRow() {
        assertNull(droppedStatLine(0))
        assertEquals("12 frames", droppedStatLine(12))
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android/android
./gradlew :ui-mobile:testDebugUnitTest --tests '*PlaybackStatRowsTest*'
```

Expected: FAIL to compile — `unresolved reference: videoStatLine`.

- [ ] **Step 3: Implement**

`PlaybackStatRows.kt` holds the six functions, all pure and `internal`. Codec
strings arrive as MIME types (`video/hevc`), so strip the prefix and uppercase
what remains. Channel counts map to the labels a viewer recognises — 6 is
`5.1`, 8 is `7.1`, 2 is `Stereo`, 1 is `Mono`, 0 is unknown and omitted.
Language codes render through `java.util.Locale.forLanguageTag(tag).displayLanguage`.

`clockTime` for the buffer position already exists in `PlayerClock.kt` — import
it. `humanSize` already exists from phase 3 — import that too. Two copies of
either would be the thing phase 1 of this plan exists to avoid, one module down.

- [ ] **Step 4: Run the test**

```bash
./gradlew :ui-mobile:testDebugUnitTest --tests '*PlaybackStatRowsTest*'
```

Expected: PASS, all eleven.

- [ ] **Step 5: Commit**

```bash
git add android/ui-mobile/src/main/kotlin/PlaybackStatRows.kt android/ui-mobile/src/test/kotlin/PlaybackStatRowsTest.kt
git commit -m "feat(android): say what the player is decoding, in a line each"
```

---

### Task 2: The overlay, and the control that opens it

**Files:**
- Create: `android/ui-mobile/src/main/kotlin/PlaybackStatsOverlay.kt`
- Modify: `android/ui-mobile/src/main/kotlin/PlayerControls.kt`, `android/ui-mobile/src/main/kotlin/PlayerScreen.kt`

**Interfaces — Consumes:** the six functions from Task 1, `PlaybackCounters` (phase 3 task 1), `GlyphButton` (already in `PlayerControls.kt`).
**Produces:** `@Composable fun PlaybackStatsOverlay(player: Player, counters: PlaybackCounters, modifier: Modifier)`.

- [ ] **Step 1: Add the control**

`PlayerControls` gains an `onToggleStats: () -> Unit` parameter and a fourth
`GlyphButton` in its row:

```kotlin
            GlyphButton(
                glyph = "ⓘ",
                description = "Playback statistics",
                enabled = true,
                onClick = onToggleStats,
            )
```

Placed after skip-forward, so the three transport controls stay together and
the one that is not transport sits apart from them.

- [ ] **Step 2: Build the overlay**

`PlaybackStatsOverlay` reads live from the player on the same tick the bar
already uses:

```kotlin
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)
```

and reads `player.videoFormat`, `player.audioFormat`,
`player.totalBufferedDuration`, `player.videoDecoderCounters` (or
`getVideoPlaybackQuality()`-equivalent for dropped frames) plus
`counters.totals()` each recomposition. Rows whose function returns null are
not drawn.

It sits top-start under the back arrow, over the same scrim the bar uses, in a
monospace-ish tabular arrangement — this one *is* somebody's tool, unlike the
System screen, and reading columns of numbers is what it is for.

- [ ] **Step 3: Wire the toggle**

`PlayerScreen` holds `var statsShown by rememberSaveable { mutableStateOf(false) }`,
passes `onToggleStats = { statsShown = !statsShown }`, and draws the overlay
when `statsShown && controlsMayShow(state)`.

The overlay follows the bar: when the bar fades, the overlay goes with it, so a
viewer who leaves it on does not end up with numbers permanently over the
picture. Drawing it only while `controlsShown` is what achieves that, and it
needs no new visibility rule.

- [ ] **Step 4: Build and check the line budget**

```bash
./gradlew :ui-mobile:testDebugUnitTest
wc -l ui-mobile/src/main/kotlin/PlayerControls.kt ui-mobile/src/main/kotlin/PlayerScreen.kt ui-mobile/src/main/kotlin/PlaybackStatsOverlay.kt
```

Expected: PASS, all three under 200. `PlayerControls.kt` was 190 after the
inset fix and a fourth button will cross it — move `GlyphButton` and `TimeText`
into `ui-mobile/src/main/kotlin/PlayerControlParts.kt` when it does. They are
the two pieces with no tie to the bar's own state.

- [ ] **Step 5: Prove it on the phone**

```bash
adb shell run-as com.mediagram.android rm -rf cache/mlib
./gradlew :app:installDebug
adb shell monkey -p com.mediagram.android -c android.intent.category.LAUNCHER 1
```

Against a cleared cache, so the counters start from nothing:

1. Play a film, show the bar, tap `ⓘ`: the overlay appears.
2. Video and audio rows name a real format, not "not yet known".
3. The buffer row advances while playing.
4. Reads climb during playback; the cache share is low on first play.
5. Stop, replay the same film: the cache share is now high — proof the counters distinguish disk from network.
6. Tap `ⓘ` again: the overlay goes. Let the bar fade: the overlay goes with it.
7. Compare the video row against the same title's technical line on the detail screen, and note any disagreement — that is the catalog being wrong, and it is worth recording rather than fixing here.

- [ ] **Step 6: Commit**

```bash
git add android/
git commit -m "feat(android): show what the player is really doing"
```

## Todo list

- [ ] Six row functions, all pure, all proved on the JVM
- [ ] An unset bitrate is omitted rather than printed as -1
- [ ] Zero dropped frames draws no row
- [ ] `ⓘ` in the bar, apart from the three transport controls
- [ ] The overlay fades with the bar
- [ ] `clockTime` and `humanSize` imported, not rewritten
- [ ] The seven device confirmations made, against a cleared cache

## Success criteria

A viewer can see the decoded format, the buffer, and how much of playback came
from disk rather than from Telegram — and the cache share visibly differs
between a first and a second play of the same film. `./scripts/check.sh` passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| `videoFormat` is null early and every row reads "not yet known" | Tested explicitly; the row says so rather than showing blanks, and device step 2 checks it resolves. |
| Reading the player every tick is expensive | It is the tick the bar already runs, and the overlay is only composed while shown. |
| `PlayerControls.kt` crosses 200 lines with a fourth button | Step 4 measures and names the extraction. |
| A second `humanSize`/`clockTime` gets written | Step 3 of Task 1 says to import them and why. |
| The overlay covers the picture permanently | It is drawn only while the bar is shown, so the existing fade carries it. Device step 6 checks it. |
| Dropped-frame API differs across media3 versions | media3 is pinned at `1.10.1`; if the accessor named here is absent, report it rather than substituting a different statistic. |

## Next steps

This is the last phase. The plan's Review section gets filled in, and spec §11's
open questions — the technical line on cards, and `versionCode` — go to the user.
