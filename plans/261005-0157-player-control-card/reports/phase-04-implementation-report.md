# Phase 04 implementation report: phone/tablet control card

Branch: worktree-agent-aa3dca47c4dff0d31 (fast-forwarded to main a0f5e5c5 first).

## Commits
One commit per task:
   - T1 afb823c1 feat(player): card state for the phone player's menus and episode sidebar
   - T2 2ff8f396 feat(player): phone control card with seek, tools and transport rows
   - T3 0b1f9463 feat(player): card menus for subtitles, speed, audio and framing
   - T4 5929eaed feat(player): episode sidebar for the phone player
   - T5 d55e8910 feat(player): phone player wears the control card; retire the settings sheet
   - T6 19e6748e test(player): double tap seeks by the player's own fifteen seconds
   - T7 bcb9c5e9 docs: record the player control card in the Android design system

## RED / GREEN
- T1, T2, T3, T4: RED = main files moved aside, test compile failed with unresolved references to the new types; GREEN after restoring.
- T5: RED not run separately (screen, bridge, fixture changes landed together). GREEN: full suite.
- T6: passes on first run, as the plan expects (pins existing behaviour).
- T7: grep check: `### Player card` count 1; "The player, the title detail screen" and "palette is unresolved on Android" gone.

## Tests
- `:ui-mobile:testDebugUnitTest lint :ui-tv:compileDebugKotlin` all green (365 ui-mobile tests at T5; +2 gesture tests, no failures at the end).
- `:app:checkDebugDuplicateClasses` green.
- `grep PlayerSettingsSheet|PlayerControls(` in ui-mobile/src: empty.

## Deviations
- `PlayerScrubber` row 1: plan snippet replaced the whole clock row with only `58:30 · ends 23:41`, which would drop the position. Implemented the spec's row 1 literally: position, slider (weight 1), `duration · ends`, in one Row.
- Lead ruling applied: `heightIn(min = MIN_TARGET)` on the rows in SpeedSection, AudioSection, FramingSection, SubtitleSection, SubtitleStyleSection (ChoiceRow), plus the style panel's Reset button. Test `everyMenuRowMeetsTheTouchTargetFloor` covers one row per menu (Speed, Audio, Framing, Subtitles, Style…, style panel Reset). RadioButton already measured about 48 dp, so this test did not fail before the modifier; it guards the floor.
- `CardMenuGatesTest.theStylePanelStaysOpenWhileItIsAdjusted`: added `performScrollTo()` before the click; with 48 dp rows the Sync row sits below the 360 dp menu cap and is scrolled out of view (a real scroll container, so users scroll).
- `PlayerCardScreenTest.aRowPickedInTheSidebar...`: added `advanceTimeByFrame()` + `waitForIdle()` after the click; the pending switch reaches `onSwitch` via a lifecycle-aware flow a frame later.
- Phase 03 API drift: none hit. `episodes` is nullable and used as planned (`hasEpisodes = episodes != null`, sidebar only when non-null); `OTHER_SECTION` not referenced.
- PlayerScreen.kt is 206 lines (plan text, over the ~200 guideline by a few lines).
- No version bump, changelog, push or device work, per instructions.

## Concerns
- Menu-height 48 dp lift to confirm on the tablet walk (phase 06).

## Fix round 1

Commits: 66ef1863 (H1, M1, lead 8, L1, L2), c5bc5fcd (M2), 6a7d3888 (lead 7, M3, M4, stale comments, DESIGN.md).

- H1: menu height capped to the room between the top bar's bottom (`PlayerCardState.topLimit`, reported by the top bar) and its button; it scrolls. All in ui-mobile, `cardMenuOffset` untouched. Test at 800x360: menu bottom above button top, menu top below the marks.
- M1: a full-width sidebar (<600 dp) leaves the card, menus and top bar out of composition, so nothing sits under it or is reachable by TalkBack. Test at 360 dp: no "Add to list" while open, back after close.
- M2: row 1 is a FlowRow; bar has weight(1f) and min 160 dp, the length/end time wrap under it. Test at 360 dp, font scale 1.5: bar >= 160 dp.
- M3: test taps the picture (performTouchInput, past the double-tap timeout). Teeth shown: removing `dismissMenu()` fails it.
- M4: CC and ⓘ report On/Off; openers (▾, Speed, Audio, Framing, ☰) report Expanded/Collapsed. Focus moves into a menu or the sidebar on open and back to the opener on close (`PlayerCardFocus.kt`). Tests assert states and focus (assertIsFocused).
- L1 comment corrected. L2 menu cleared when the card is not shown (test: error then play, menu does not return). Stale "sheet" comments gone from ui-mobile; the feature/player ones are outside this ownership.
- Lead 7: row 2 is a Row: tools FlowRow (weight 1) plus PiP at the right end. Test: CC within 24 dp of the card's left edge, PiP within 24 dp of the right.
- Lead 8: with the sidebar open and narrower than the screen, the card sits in a Box padded by the sidebar width and centres in what is left; its own 12 dp margin is the gap. Test at 1280x800: card.right <= sidebar.left - 11.5, back to normal after close.
- DESIGN.md: row 2 alignment, bar wrap, sidebar clearance, ⓘ ☰ not pinned (L5).
- `PlayerCardScreenBase` (abstract) now shared by the 1280, 800x360, 360 and clearance test classes.
- Verified: `:ui-mobile:testDebugUnitTest lint :app:checkDebugDuplicateClasses :ui-tv:compileDebugKotlin` green.
- RED seen first for H1, M1, M2, M4, row 2, clearance. M3 and L2 passed on first run (the logic already existed; M3 teeth shown by breaking it). L2 fix had landed with the H1 commit.
- PlayerScreen.kt is 208 lines.
