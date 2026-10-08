# Phase 01 — hero returns when the remote leaves for the bar

**Status:** done. Verified on the box at 0.118.2 (adb release install, not published).

## Change

- `ui-tv/.../catalog/TvToTopWhenLeftForTheBar.kt`: when Up or Back takes focus out of the page, the page scrolls back to item 0. Left onto the rail and OK (opening a title) leave the scroll alone.
- Applied on Movies, Documentaries and Collections (LazyColumn), on `TvWall` (Series, Tutorials, Anime and every wall), and on Home (the cover returns on Back).
- Test `TvToTopWhenLeftForTheBarTest`: red with the scroll disabled, green with it. Full `:ui-tv:testDebugUnitTest` green.

## Anime entry (added the same day)

With nothing underway, Anime's wall header is the hero alone, taller than the screen. Down from the bar asks the page container to take focus, and Compose gives it to the first stop already laid out. No plate was laid out yet, so the remote stayed on the bar. A group's `onEnter` does not run on that path: the requester names the container itself, not a descendant. So `TvWall(headerHoldsNoStop)` turns the header into a forwarding stop instead (`TvPassesEntryOn.kt`), which scrolls the restore plate in and focuses it. It is focusable only while the wall does not hold focus, so Up from the plates still reaches the bar. It also stays focusable while it holds the remote, because a focused stop switched off clears focus to the root and the bar takes it. Test: `TvWallEntryStateTest`, red before the fix.

## Verified on the box

- Movies: D D D then U U U → bar, hero back. D D D then Back → hero back.
- Series: the same. Home: D ×4 then Back → cover back.
- Anime: Down from the pill → first plate (Beyblade Burst); Up → bar, hero back. Series Down still lands on "Continue your series".

## Seen, not changed

- Up into a row can land on a different card than the one left (geometric focus search).
- Up into Movies' first row leaves its heading under the bar (`StaysPutWhenOnScreen` measures against the overscan inset, not the bar).
