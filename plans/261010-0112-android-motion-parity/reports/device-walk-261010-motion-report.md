# The motion walk on the tablet

Tablet caad49da (Xiaomi 24091RPADG, 3200x2136, held landscape), debug build **0.121.0**
(versionCode 121000) from `feat/motion` at 50ce2f83, installed with
`ANDROID_SERIAL=caad49da ./gradlew :app:installDebug` over the 0.120.0 benchmark build. The
Telegram session survived. Walked 2026-10-10, 15:07–16:06, on profile **T** (test, PIN 1357).
The TV box was not touched.

**How it was judged.** Screen recordings (`screenrecord`, 1600x1068) cut into frames with
ffmpeg, held presses (`input motionevent DOWN … CANCEL`, so nothing was activated), pixel and
bounding-box measurements with ImageMagick, and node positions from `uiautomator dump`. At this
panel 1dp = 2.75 px. Evidence lives under
`/tmp/claude-1000/-home-andre-Workspace-mediagram/35591186-acb4-4a07-9b7f-c6e887a63688/scratchpad/walk-9.4/`
(written `walk-9.4/` below); it is scratch, not committed.

**Result.** Animations on: 39 pass, 0 fail, 4 not checked. "Remove animations" on: 13 pass,
0 fail. Five observations at the end, none a failed check. The profile could not be put back
to A (see "Settings left").

## Animations on

| # | Check | Result | What I saw | Evidence |
|---|---|---|---|---|
| 1 | Cover: a new slide fades up from black, then slowly settles | pass | A dot tap and the 9 s turn both drop to black and ramp the new film up over about 1.4 s. With rotation paused, frames 4 s apart still differ slightly (RMSE 0.003–0.005): the settle is still running | `cover-fade/sheet.png`, `refresh/sheet.png` frames 27–31, `cover-paused/` |
| 2 | Cover: a tap mid-fade does nothing | pass | Tapped Details 0.3 s into a turn; Home stayed, no title opened | `cover-fade/` |
| 3 | Cover: the pause button holds it | pass | Paused for 13 s, longer than one 9 s hold: same film throughout, then "Play the cover stories" restarted it | `cover-paused/sheet.png` |
| 4 | Scroll down Home: each section rises in once | pass | Continue Watching rose and faded in as it came into view; the course list did the same further down | `home-rise2/z-sheet.png`, `home-scroll-down/sheet.png` |
| 5 | Scroll back up: none replays | pass | Every section came back at rest | `home-scroll-up/sheet.png` |
| 6 | A refresh landing replays nothing | pass | Home has no pull-to-refresh, so I used ⋮ › Update library ("Nothing new"). Neither the sections nor the cover replayed when it landed. Observation 1 covers the progress line | `refresh/sheet.png` |
| 7 | Continue strip: a fling ends on a card's start | pass | A fling left, a slow drag and a fling right: each came to rest with a card's left edge at x=641, the strip's start | dumps in the walk log |
| 8 | "See all →" nudges right while pressed | pass | Arrow moved 11 px right (4dp); the label stayed | `press/continue-seeall-pair.png` |
| 9 | Press feedback: a pill | pass | Sign out pill: tint 13→31, box 272→266 px wide (0.98), no ripple | `press/signout-pill2-pair.png` |
| 10 | Press feedback: a plate | pass | Continue plate: 572→562 px (0.98), quiet tint | `press/continue-plate-pair.png` |
| 11 | Press feedback: a dialog's button | pass | Sign out › Cancel: tint 27→44, label 119→117 px; Cancel then closed it, session intact | `press/dialog-cancel-pair.png` |
| 12 | Appearance options don't shrink (the ruling that replaced "a radio row") | pass | Light option held: card bounds unchanged to the pixel; it tints | `press/appearance-light-pair.png` |
| 13 | No ripple anywhere | pass | Every held control showed one flat tint; none showed a circle or wave | `press/*-pair.png` |
| 14 | Links tint but don't shrink: see-all, a genre tile, a person | pass | Genre tile (Drama): tint, text box identical. Cast portrait: tint, bounds identical. Row see-all: tint, no shrink | `press/genre-tile-pair.png`, `press/person-link-pair.png`, `press/movies-row-seeall-pair.png` |
| 15 | Title: hero art eases in, the pull-quote follows | pass | Art ramps over about 0.5 s; the quote's text starts about 0.4 s after the art and fades in over about 0.6 s | `title-open/walk.mp4` (frames 38 and 50 at 30 fps) |
| 16 | "▶ Play" nudges on press | pass | The ▶ moved about 2dp right; "Play" stayed | `press/title-play-pair.png` |
| 17 | The back arrow nudges left | pass | 8 px (3dp) left while held | `press/title-back-pair.png` |
| 18 | Tabs: the underline fades (no slide), the content rises 8dp | pass | The Overview underline went out, the Cast one came up in place; no frame showed it between tabs. The panel rose as it faded in: 6–8dp measured, since its first frames are too faint to place | `tab-switch/` (60 fps frames 52–62) |
| 19 | ⋯ opens with a fade and a 4dp drop, closes at once | pass | Opens over about 250 ms, moving down a few px (its first frames are too faint to place); closes between one frame and the next | `more-menu2/` |
| 20 | Department: the hero eases in, rows have "→", "All N →" nudges | pass | Series: words first, then the art eases in. Movies' first row ends in "All 1143 films →", whose arrow nudges 4dp (other rows not pressed). "All 1143 films →" at the foot moves 3dp as a whole | `dept-series/hero-strip.png`, `press/movies-row-seeall-pair.png`, `press/all-films-pill-pair.png` |
| 21 | Featured reel: slides cross-fade | pass | Poster region crossed from one film to the next over about 0.7 s visible, no cut | `featured-reel/walk.mp4` |
| 22 | Featured reel: the poster drifts once and holds when paused | pass | Moving before the tap; after the tap, five 1 s frames were pixel-identical | `reel-pause/strip.png` |
| 23 | Featured reel: the copy rises in a stagger | pass | Title line in first; facts, tagline and buttons about 0.25 s after it | `featured-reel/` (frames 23–43 from 8.0 s) |
| 24 | Player: controls fade in and out (260 ms) | pass | In and out each ramp over about 9–12 frames at 60 fps, front-loaded like CSS `ease` | `hud-fade/` |
| 25 | Player: a tap on fading-out controls brings them back and opens no menu | pass | At normal speed my taps landed before the fade began, so I stretched animations ×10 for one run and restored 1.0 straight after. A tap on Audio mid-fade brought the controls back, and no Audio menu opened | `fade-tap-slow/sheet.png` frames 23–30 |
| 26 | Player: the marks (Kids) menu opens and closes with no motion | pass | Appears in one frame, gone in one frame | `kids-menu/` (frames 101, 176) |
| 27 | Player: the press tint over video is white | pass | Audio tool held: the ground lightened 26→44 and the label turned stage coral | `press/hud-audio-pair.png` |
| 28 | Player: nothing else moves over the picture | pass | Through the opening and the first seconds of play, only the controls' fade moved | `player-open/sheet.png` |
| 29 | A dialog opens without the window animation | pass | The Sign out dialog box is fully drawn in its first frame. Observation 3 covers the scrim | `dialog-open/all/` (frames 26–36) |
| 30 | Cold start: the countdown leader (rings, crosshair, a sweep once a second, 3→2→1) above "Loading your library…" on the dark cover-shaped block | pass | All present: the sweep goes round once a second and the numeral steps 3→2→1. The cover-shaped block shows clearly in light theme. Observation 2 covers how the waits join | `cold-start/three.png`, `light-cold-search3/three-waits-light.png` |
| 31 | The player's opening shows the leader centred, no words | pass | Centred leader, sweep running, 3→2→1, no text | `player-open/lead-strip.png` |
| 32 | Light theme: the press tint shows on a pill | pass | Sign out: 243,240,233 → 226,223,217 | `press/light-pill-pair.png` |
| 33 | Hover: a poster lifts, a feature's art zooms | not checked | Not checked on device: no mouse or stylus at hand. `adb input mouse` sends moves, not hover, and changed nothing. Covered by unit tests | `press/hover-pair.png` |
| 34 | Menus: the season picker's menu is placed against its anchor, even mid-rise | pass | The Office has nine seasons, so landscape has no room below the picker. At rest the menu centres on the picker's top. Opened 0.12 s after a tab tap (mid-rise), it sits just above the picker, flush with its settled place, not 8dp off. Both are `DropdownMenu`'s own fallbacks | `small/18-season-menu-rest.png`, `season-midrise.png` |
| 35 | Tabs: one tint, not two stacked | pass | Similar tab held: one even 31,31,31 tint over the whole tab, no ripple | `press/tab-similar-pair.png` |
| 36 | Player top bar: My List, Kids and Notes turn the stage accent while pressed | pass | All three labels coral while held, over a white tint | `press/hud-mylist-pair.png`, `press/hud-kids-pair.png`, `press/hud-notes-pair.png` |
| 37 | Player: the up-next card doesn't drop while the controls fade out | pass | Seeked The Office S1E1 to its last minute. The card held its place above the controls through the whole fade, then went to the bottom once the controls were gone | `upnext/upnext-fade.png` |
| 38 | Player: subtitles don't drop while the controls fade out | not checked | Not checked on device: CC was dimmed on every title opened, so no subtitles were available | — |
| 39 | Blurred backdrop: scroll Home and judge by eye whether it stutters | pass | No stutter seen. The same four-swipe walk, measured from recorded frame gaps: Blurred p50 8.4 / p90 10.0 ms, 10 of 540 gaps over 20 ms; Default 8.4 / 9.9–16 ms, 7 of 667. Restored to Default | `blurred-scroll/`, `default-scroll/` |
| 40 | Light theme: the in-page "Loading your library…" waits use page inks | not caught | Three light-theme cold starts with Search opened during the load. The cold-start waits drew the dark cover block, which is by design; Search opened only after the library had loaded | `light-cold-search{,2,3}/sheet.png` |
| 41 | Light theme over video: the tint is white and the tool turns stage colour (8.2) | pass | Same as #27 with the page in light theme | `press/light-hud-audio-pair.png` |
| 42 | The focus ring over video is the stage accent (8.2) | pass | Keyboard focus on My List draws a coral ring, though the page accent is Green. Same in dark and light themes | `small/hud-focus2.png`, `small/light-hud-focus2.png` |
| 43 | Blurred backdrop over video (8.2) | not checked | The player draws no backdrop: nothing in `ui/player` reads `LocalBackdrop`. Blurred cannot reach the stage, so I did not run the player under it | — |

## "Remove animations" on (animator_duration_scale 0)

Turned on while Home was showing, then two cold starts.

| # | Check | Result | What I saw | Evidence |
|---|---|---|---|---|
| R1 | Turned on mid-run, nothing is left invisible or half-moved | pass | Home held its picture through the flip: every section at rest, nothing blank | `live-flip/sheet.png` |
| R2 | A page turn does not fade or rise | pass | Movies came up at rest in the next frame. A title from Movies drew one blank frame of chrome, then the page at rest | `rm-movies/sheet.png`, `rm-title/raw-sheet.png` |
| R3 | Sections don't rise as Home scrolls | pass | Edges move only with the finger | `rm-scroll/sheet.png`, `rm-rise/` |
| R4 | The cover doesn't rotate | pass | Same film for 13 s; no pause button, as rotation is off | `rm-cover/` |
| R5 | The masthead over the cover is solid | pass | A solid bar above the picture, where animations-on shows the picture through it | `small/24-rm-home.png` against `small/15-overflow-menu.png` |
| R6 | The reel's poster sits at its normal size and doesn't drift; slides switch at once | pass | Poster whole within its frame. Nothing changed for 7 s, then a step changed the slide in two frames | `rm-reel/end-s.png`, `rm-reel-rec/`, `rm-reel-next/` |
| R7 | ⋯ opens and closes with no motion | pass | Whole in its first frame | `rm-menu/raw-sheet.png` |
| R8 | Tabs: the underline jumps and the panel doesn't rise | pass | Old panel, one blank frame, new panel at rest | `rm-tab2/raw-sheet.png` |
| R9 | Player controls snap in and out | pass | 0→255 and 255→0 between single frames | `rm-hud/` |
| R10 | The library leader shows a still 3, no sweep | pass | Six screenshots: a still 3, no wedge | `rm-leader/sheet.png` |
| R11 | The player's leader shows a still 3, no sweep | pass | Same | `rm-player/leader-strip.png` |
| R12 | Nothing nudges | pass | See all's arrow sits 4dp over while pressed, but it jumps there with no movement. That matches the web, whose reduced motion removes the transition and keeps the `:hover` transform (`theme.css:180-182`, `home.css:208-210`) | `press/rm-seeall-pair.png` |
| R13 | The press tint still shows, without a fade | pass | See all: 13→31 | `press/rm-seeall-pair.png` |

## Observations (not failed checks)

1. **The refresh line pushes Home down.** While Update library runs, the indeterminate
   progress line in `CatalogShelves.kt:76-77` takes the top bar's height plus its own. So the
   whole of Home, which draws under the bar, drops by about a bar's height, then jumps back
   when the refresh lands (`refresh/sheet.png` frames 4–34). main does the same at
   `CatalogScreen.kt:142-143`. The docs already list this "fetching" line as a difference to
   check against the web.
2. **The cold-start waits don't quite read as one wait.** The setup check, the profile read
   and the library each draw the leader block in a different place. The leader steps down the
   screen once, then over by the rail's width when the chrome arrives, and its count restarts
   at 3 each time (`cold-start/three.png`; bounding boxes at y 509 → 680 → in-frame). Ruling 8
   wanted the three to read as one wait.
3. **The dialog scrim deepens over about 150 ms.** The dialog box is whole in its first frame,
   but the window's dim layer behind it darkens across about nine frames (8→2 on the sidebar).
   That is the system's dim, not the window animation `NoDialogAnimation` turns off.
4. **The up-next card has no move of its own.** After the controls fade, it drops from above
   the controls to the bottom between one frame and the next. The walk only asked that it hold
   during the fade.
5. **Two of my own taps opened things.** Before the ×10 run, two attempts at #25 landed before
   the fade began and opened the Audio menu. I closed it with Back and changed nothing.

## Playback on T

All on profile T, avatar checked first: The Office S1E1 (seeked into its last minute for #37,
then Back), Geldhochschule lesson 5 (for the top bar's Notes and Kids, backed out), Der Gesang
der Flusskrebse and The End of Oak Street, each opened a few seconds for the player checks.
These now show on T's Continue. Nothing played on A.

## Settings left

| Setting | Read before | Left at |
|---|---|---|
| animator_duration_scale | 1.0 | **1.0**. Changed only to 10 for #25 and to 0 for "Remove animations", restored after each |
| transition_animation_scale, window_animation_scale | 1.0 | 1.0 (never touched) |
| Appearance › Artwork (backdrop) | Default | **Default** (Blurred for #39) |
| Appearance › Theme | Dark (accent Green) | **Dark · Green** (Light for #32, #40, #41) |
| Profile | A (andre) | **T (test). Not restored**: switching back asks for andre's PIN, which I don't have. The user has to switch back |
| Build | 0.120.0 benchmark | 0.121.0 debug (this walk's build) |

Storage budget and the cache server were not opened. "Start over" was never tapped.
