# TV feedback, 2026-10-10 15:23 — posters too big, player controls not the tablet's

The user is looking at the TV box (0.120.3) and reported, with photos:
1. "These Posters are way to big." Series › Popular shows about 4.3 posters across the row.
2. "The controls are still not up to date. On tablet looks good." The TV player card shows a large serif "CC ○", "▾", "1×", "4:3", then big Fraunces "−15" / "+15" numerals, large transport icons, ⓘ and ☰. The tablet's player bar, which follows the web's, looks right to them.

Rule: TV UI work means **parity with the web/tablet design, not redesign**. The web player is the reference; the tablet already matches it. Never invent a new look.

## 1. Posters (poster strips/rows)
- **Web:** `web/public/styles/catalog.css:31-39`, `.grid.plates.strip`, puts 8 plates across: `grid-auto-columns: minmax(8.5rem, calc((100% - 7 * 14px) / 8)); gap: 14px`. The plate's text styles are in catalog.css too.
- **TV:**
  - Every plate is a fixed 160.dp and is not scaled: `DeptTileWidth` (ui/tv/catalog/TvDepartmentRows.kt:39), `TvPosterStrip.PosterWidth` (ui/tv/catalog/home/TvPosterStrip.kt:41), `TvRecentBand.RecentPosterWidth` (:42), and `TvSimilarRow.SimilarPlateWidth` (140.dp).
  - Leave `TvFilmOverview`'s single title-page poster alone.
  - Check the web's similar row and the recent band: if they are `.strip` too, they follow the same rule; if not, match whatever they use.
  - The TV scales the web by `TvScale = 960f/1280f` with `tvDp`/`tvSp` (`designsystem/TvTypeScale.kt`). That maps a 1280px web viewport onto the 960dp TV.
- **Do:**
  - One shared rule for strip plate width: the web's formula, measured on the row's own width (`BoxWithConstraints` or the row's measured width): `max(8.5rem→136px, (rowWidth − 7·gap)/8)`, with px values through tvDp, so 136px → 102dp and the 14px gap → 10.5dp.
  - Use it for every strip/row of plates. Apply the gap where the rows space their plates.
  - Check the plate's own text (title, "32 episodes") and the progress/badge overlays still fit at the smaller width, matching the web's plate text sizes through tvSp. Truncate as the web does.
  - Check that the focus scale and border (TvFocus) don't clip in the shorter row height, and that the rows' focus restorers and paging keys don't depend on the old width (`DeptCacheAhead`/`Behind` are px budgets; scale them if they assumed 160dp).
- **Tests:** a unit test pinning the width rule (row 800dp → about 102dp floor; a wide row → (w − 7g)/8). Update any test that asserted 160.dp.

## 2. Player controls (the card over the video)
- **Reference:**
  - Web: `web/public/styles/player-card.css` (tools, `.tool`, CC/speed/fit) and `web/public/styles/playback.css` (transport `.tsp`, seek bar, times, `.ghost` top-bar buttons).
  - Tablet: `android/ui-mobile/src/main/kotlin/ui/player/PlayerControlParts.kt`, `PlayerCardLayer.kt`, `PlayerTransport*.kt`, `PlayerTopBar.kt`. Read the tablet's icons, glyphs, fonts (`Interface`/Geist for labels and numbers, not Fraunces), sizes and spacing.
  - On this branch the tablet code is at main's 0.120.0, before the motion work. The tablet the user saw runs the motion branch, whose player files differ only in press/hover motion. Copy the **look**, not the motion.
- **TV today:** `android/ui-tv/src/main/kotlin/ui/tv/player/` holds TvPlayerControls.kt, TvTransport.kt, TvToolGroup.kt, TvOverlayButtons.kt, TvSeekBar.kt, TvPlayerTopBar.kt and TvPlayerType.kt (34.sp / 18.sp, not tvSp).
- **Do:**
  - Make the TV card read like the tablet's at the TV's scale. Use the same glyphs and fonts: the transport's −15/+15 as the tablet draws them, not big Fraunces numerals; CC/speed/fit as the tablet's tools. Size and space the controls as the web's CSS, through tvDp/tvSp.
  - Keep everything TV-specific that is about the remote: D-pad focus order, focus treatment (TvFocus), key handling, which control holds focus on open, and the seek bar's remote stepping. Change the look only.
  - Hit targets stay reachable by D-pad; there is no touch on TV.
  - If the TV has a control the tablet lacks, or the reverse, keep the TV's set and write the difference in a code comment, Surface Parity style.
  - The top bar (title, "My List", "For kids…", "Add to…"): match the tablet's top bar the same way.
- **Tests:** existing TV player tests must pass. Add or adjust a test that pins the label font family (Interface, not Display) for the transport skip labels, or whatever pins the change best.

## Process
- Work in /home/andre/Workspace/mediagram-tv on feat/tv-parity. Commit each part separately with conventional messages, no AI references and no attribution or trailer lines. Code comments say why and cite the web file:line; no plan references.
- Run `./gradlew :ui-tv:testDebugUnitTest :ui-tv:lintDebug` (plus `:core:designsystem` if touched) from android/. All must pass.
- Then bump the version by pattern to **0.120.4** in Cargo.toml, web/package.json and android/app/build.gradle.kts. Run `cargo metadata --format-version 1 >/dev/null` to refresh Cargo.lock, and commit `chore: 0.120.4 — TV posters and player controls at the web's size`.
- Don't build the release and don't touch any device; the controller does that. A tool hook blocks shell commands containing a `build` path segment.
- Write a short report beside this brief: `tv-feedback-261010-1530-posters-and-player-controls-report.md`. Include the before and after widths and sizes with web citations, deviations, and test counts.
