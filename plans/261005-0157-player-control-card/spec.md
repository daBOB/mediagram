# Player control card — design spec

2026-10-05. Approved in conversation the same night; this file is the written form for review.

## Goal

The player's on-screen controls become one blurred card at the bottom of the picture. It has:

- play/pause, −15, +15, restart, previous and next;
- subtitles on/off, speed, audio track and framing;
- nerd stats;
- ☰, which opens a sidebar listing the show's episodes, with watched ones greyed out.

The card is built on all three surfaces: web (the reference), phone/tablet, and TV.

## User decisions (2026-10-05, do not reverse silently)

| # | Question | Answer |
|---|---|---|
| 1 | Controls not in the request (My List, Kids, Add to, Notes, volume, fullscreen, PiP, Up-next card, title/close) | **A slim top bar keeps them.** It holds the title, the marks, Notes and Close. Volume and fullscreen (web) and PiP (phone) ride in the card. The Up-next card is unchanged. |
| 2 | Blur on Android | **A frosted look with no blur.** The card has the same shape, filled with a dark translucent tint. This is written down as a deliberate difference. |
| 3 | Card placement | **A bottom-centre card**, laid out in three rows: seek, tools, transport. |
| 4 | How tools behave | **Toggles plus small menus.** CC toggles in one press and its ▾ opens the language list and Style. Speed, Audio and Framing each open a short list above the card. ⓘ toggles stats. The settings sheet and panel are retired. |
| 5 | Episode sidebar | **All seasons, on the right.** A season switcher sits on top, then the episode rows. Watched rows are greyed with a ✓, partly watched rows show a progress line, and the current row reads "Now playing". Picking a row plays it. Courses list their lessons the same way. ☰ is hidden for a film with no run. |
| — | Skip amount | **15 s everywhere.** This covers the buttons, the web keys, the phone's double-tap and the TV D-pad. (This was stated as an assumption and not objected to.) |

## Layout

```
┌──────────────────────────────────────────────────────┐
│ ← Title · S2E3            My List  Kids  Add to  Notes  ✕ │  slim top bar (top gradient kept)
│                                                      │
│                       (video)                        │
│                                                      │
│   ╭──────────────────────────────────────────────╮   │
│   │ 12:04 ━━━━━━━━━━━●──────────── 58:30 · ends 23:41 │   │  row 1: seek
│   │ CC▾  1×  Audio  Fit                 🔊 ▭ ⛶ │   │  row 2: tools (+ volume/fullscreen web, PiP phone)
│   │          ↺  ⏮  −15  ▶  +15  ⏭          ⓘ ☰ │   │  row 3: transport, stats, episodes
│   ╰──────────────────────────────────────────────╯   │
└──────────────────────────────────────────────────────┘
```

### Size and position

| Surface | Card width | Inset from the bottom |
|---|---|---|
| Web | Up to 880 px, centred | 24 px |
| Phone/tablet | Full width minus 12 dp each side; capped at 720 dp on a tablet | 12 dp |
| TV | 760 dp, centred | 32 dp |

On a narrow phone, row 2 wraps rather than shrinking its touch targets. Every target stays at least 48 dp.

### What moves where

- **Top bar:** the title, My List, Kids, Add to, Notes and Close. On the web these leave the bottom "rail" and join the bar.
- **Into the card:**
  - "Ends at" moves into row 1.
  - The web's "Play next" text button becomes ⏭.
  - The web's preload readout moves into nerd stats.
- **Gone:**
  - the bottom gradient (the top gradient stays, behind the top bar);
  - the web's bottom bar with its selects;
  - the phone's settings bottom sheet;
  - the TV's 340 dp settings panel.

  The sections inside the sheet and panel are reused inside the new menus. Their contents are not rewritten.
- **Unchanged:** the Up-next card, the Notes column, the subtitle style panel (now reached from CC ▾ → Style…), and A/B loop and frame-stepping keys on the web.

## Look

- **Web:**
  - fill: `backdrop-filter: blur(24px) saturate(1.2)` over the stage colour at about 45% opacity;
  - a 1 px hairline border at about 8% white;
  - radius `--radius-card` (12px), and no shadow.
  - This is the second blur exception in the stylesheet. The masthead comment in `web/public/styles/shell.css` changes from "here and nowhere else" to name both places, and `web/DESIGN.md` gets a Player section saying so.
- **Android:** the same radius, border and padding, filled with black at 78% opacity (darker than today's `SCRIM_ALPHA` 0.55 gradient, since a card has no fade to lean on) and no blur, radius 12 dp. `DESIGN.md` (repo root) records this as a deliberate difference under the player. The reason is that the video draws on its own surface, which is what keeps HDR and Dolby Vision passthrough working. Blurring it would need a TextureView, and that breaks both.
- **Shared rules:**
  - Controls never lift or grow on hover or focus.
  - Focus on TV uses the existing TV focus treatment.
  - The menus and the sidebar use the same fill as the card.

## Behaviour

### Transport (row 3)

- **↺ Restart** seeks to 0:00 of the current title and keeps the play/pause state.
- **⏮ Previous** opens the title before this one in the run. It is disabled on the first.
  - It never means "restart"; ↺ is that.
  - The web key `0` (restart) and the TV MediaPrevious key (previous in run) keep their current meanings.
- **−15 / +15** seek by 15 s, clamped to the title's start and end.
- **▶/❚❚** toggles play. Its keys are unchanged.
- **⏭ Next** opens the next title in the run, the same path the Up-next card takes. It is disabled on the last.
  - With no run (a film), ⏮ and ⏭ are hidden rather than disabled.

### Tools (row 2)

- **CC** toggles subtitles on or off in one press.
  - "On" picks the language last chosen for this title. If there is none, it picks the subtitle language the player would choose today.
  - With no subtitle track, CC is disabled.
- **▾** opens a menu: the languages, Off, then "Style…", which opens the existing style panel.
- **Speed (`1×`)** opens a menu of 0.75×, 1×, 1.25×, 1.5×, 1.75× and 2×. These are today's `SPEEDS` / `PLAYBACK_SPEEDS`, and the button always shows the current speed.
- **Audio** opens the track list. It is hidden when the title has only one track, as today.
- **Framing (`Fit`)** opens a menu of Fit, Fill, 16:9 and 4:3. These are today's `FRAMINGS` and `FramingController`, and the button shows the current framing.
  - The web gains a visible control, and the `z` key now updates its label.
- **Web only:** mute/volume and fullscreen. **Phone only:** PiP.

### Menus

- One menu is open at a time. It opens directly above its button, inside the card's width.
- Choosing a value closes the menu. Esc on the web and Back on Android close it without choosing.
- On TV, focus enters the menu on the current value. Back returns focus to the button that opened it.

### Stats (ⓘ)

- ⓘ toggles an overlay at the top left, under the top bar.
- **Android** keeps its rows (`PlaybackStatRows.kt`): video, audio, buffer, cache, reads and dropped frames.
- **Web** gets an overlay with the same row names, from what the browser can tell:
  - **Video:** resolution, codec, and bitrate where known (from the file facts already behind `#tech`).
  - **Audio:** codec, channels and language.
  - **Buffer:** seconds ahead (from `video.buffered`).
  - **Cache:** the preload readout, moved here.
  - **Dropped frames:** from `getVideoPlaybackQuality()`, shown only when there are some.
- A row the web cannot know is left out, never shown as a guess.

### Episode sidebar (☰)

- ☰ is shown when the title belongs to a run: a series, or a course. It is hidden otherwise.
- **Where it sits:** the panel slides in from the right over the video. The video keeps playing.
  - Width: web 360 px; phone 320 dp, or full width under 600 dp; TV 360 dp.
- **Season switcher:** the header is `‹ Season N ›`, opening on the current season. With one season it shows the title "Season 1" without arrows.
  - Courses use the same grouping the web's course page uses: sections, or one list.
- **Rows** show the episode number, title and runtime.
  - Watched (`isWatched` / `snapshot.watched`): drawn at about 45% opacity with a ✓. Still playable.
  - Partly watched (has a resume position): a thin progress line under the row.
  - Current: "Now playing" in place of the runtime, and the row is not actionable.
- **Picking a row** plays it through the same open path ⏭ uses. The sidebar closes.
- **Closing:** ✕, Esc/Back, or choosing a row. On TV, focus enters on the current row. Back closes the sidebar before it closes the card.
- **Data:**
  - **Web:** the show's collection (what `plays-next.js` already finds) and `watch-state.js`. The season grouping reuses `catalog/course-view.js` (`seasonBlock`, `watchedTick`).
  - **Android:** one new shared model in `feature/player`, built from `CatalogRepository` and the `WatchStateRepository` snapshot. It lists the run's seasons, and each episode with its id, number, title, runtime, and watched/progress/current state. Phone and TV both draw from it. Today the player receives only `run: List<String>`.

### Hiding

- The card and the top bar hide together on today's timer: the web's 2600 ms and Android's `ControlsVisibility`.
- They stay up while paused, while a menu is open, and while the sidebar is open.

## Keys and gestures (15 s)

| Where | What changes |
|---|---|
| Web | `SKIP_SECONDS` (`transport.js`) and `SKIP` (`player-keys.js`) go to 15. The aria labels on the skip buttons follow. |
| Android | `SKIP_MS` (`PlayerFactory.kt`) goes to 15_000. Phone double-tap on the outer thirds follows, and so does the TV D-pad's `SKIP_SECONDS` (`TvPlayerKeys.kt`). |
| TV | Back closes, in order: an open menu, then the sidebar, then the stats overlay, then the card. When the card is shown, focus starts on ▶/❚❚. |

## Testing

- **Rewritten to the new card, not deleted.** These tests pin today's layout:
  - web `browser-html-player.test.ts` (ids), `player-keys.test.ts` (±10) and the HUD timer test;
  - phone `PlayerControlsWidthTest`, `PlayerSettingsSheetGatesTest` and `PlayerGesturesTest`;
  - TV `TvPlayerKeysTest`, `TvPlayerFixture` (the skip) and `TvPlayerSettingsTest`;
  - `PlayerFactoryTest` (10_000).
- **New tests:**
  - The episode-list model on web and Android: grouping, watched/progress/current, season switching, and a film having no list.
  - Restart and previous, including first and last in the run, and a film.
  - CC toggle: on restores the last language; no track means disabled.
  - Menus: open, choose, Esc/Back, and one open at a time.
  - Web stats rows: a row with nothing known is absent.
  - TV focus: the card opens on play/pause, a menu opens on its current value, and the Back order above.
- **Checks:**
  - Web through the stub preview (`cd web && bun run preview`), never the live player for UI work.
  - Tablet (serial `caad49da`) and TV box (`192.168.0.35:5555`): look and navigate only, never select anything that changes a setting.

## Rollout

1. **Web card:** the card, the slim top bar, the menus, 15 s, restart, previous and the stats overlay.
2. **Web sidebar.**
3. **Android shared model:** the episode list, previous and restart in `PlayerViewModel`, and 15 s.
4. **Phone/tablet:** the card, menus and sidebar. Runs in parallel with 5, in a separate worktree.
5. **TV:** the card, menus, sidebar and focus.
6. **Close-out:** DESIGN docs, the changelog, device walks, `check.sh`, and a release. Publishing to the channel needs the user's word.

## Out of scope

- Chapters and skip intro (`plans/260920-2128-powerful-video-player` phase 05).
- Episode stills or thumbnails in the sidebar. The channel index carries none for Android, and the web keeps parity.
- Any change to the Up-next card or Notes.
