# TV themes — mediagram, Apple TV style, Google TV style

Status: design approved in conversation 2026-10-10/11; this file awaits the user's review.
Branch: `feat/tv-themes` (worktree `../mediagram-themes`), cut from `feat/tv-parity` at `b640660d`.

## Goal

A viewer picks one of three TV themes in Settings › Appearance: **mediagram** (today's
look, the default), **Apple TV style**, **Google TV style**. A theme changes look *and*
layout. TV first; phone/tablet and web get their own design rounds later.

## Decisions (user-confirmed trail)

| # | Decision | Source |
|---|---|---|
| D1 | Themes change look and layout, not only colours | "Look and layout" answer |
| D2 | TV first, then the other surfaces | "TV first, then the rest" answer |
| D3 | The three mockups (`three-tv-homes.html`) read right | clicks on all three, then "go on" |
| D4 | Hero title is bold text in the theme's font; no TMDB logo art | "Bold text title" answer |
| D5 | Row and tab names stay mediagram's ("Continue", "Home"…) in every theme | "Keep mediagram's names" answer |
| D6 | Approach A: one app with themed seams, not per-theme screen copies | "A · Themed seams" answer |
| D7 | Scope table below (walls stay portrait; player untouched) | "Part 1 looks right" |
| D8 | Build as described below | "Part 2 looks right" |
| D9 | Tests, box measurement, delivery below; the walk may switch the theme setting on the box | "Part 3 … switch it on the box" |

Defaults taken without a question (user may override): the setting is per device, like the
rest of Appearance (`Appearance.kt:17-20`); Settings labels are "mediagram", "Apple TV style",
"Google TV style"; Apple's remote-tilt and Google's ambient screensaver are out (a D-pad has no
touch position to tilt with; a screensaver is its own feature).

## Scope

| Area | mediagram | Apple TV style | Google TV style |
|---|---|---|---|
| Chrome (catalogue tabs, My List, Continue walls) | rail + department pills, as today | no rail; centred translucent pill tab bar, search and avatar at its ends | top bar: avatar + search left, tabs, settings gear right; no rail |
| Home cover | serif cover story, as today | full-bleed backdrop, big bold sans title, white Play pill | inset card with large rounded corners, dots, a Watch chip |
| Rows (Home strips, department rows, Similar) | portrait posters | 16:9 cards, five across; focus lifts with a shadow | 16:9 cards, five across; focus is a white ring + slight grow |
| Walls (full grids) | portrait | portrait | portrait |
| Department heroes, title page, Settings, search | as today | look only: font, colours, corners, focus | look only |
| Player | as today | as today | as today |
| Ground | `Palette` ink, as today | black | dark grey |

Pushed frames (title, search, genre, Settings…) have no chrome today and still have none.

## Design

### Setting
- `enum class TvStyle(storageKey, label, note)` in `core/designsystem`, beside `Backdrop`:
  `MEDIAGRAM` (default), `APPLE`, `GOOGLE`.
- `Appearance` gains `style: TvStyle = TvStyle.Default`; `AppearanceSettings` gains
  `chooseStyle`; both implementations store it under key `style` in `appearance_settings`.
  An unknown or missing value reads as `MEDIAGRAM`. `AppearanceViewModel` gains `chooseStyle`.
- Phones and tablets read and ignore it.

### One look per theme
- `TvLook` (data class) holds every value a theme varies: ground colours, body family,
  display family, card corner radius, row card shape (portrait / landscape), focus treatment.
  `TvStyle.look` returns it; `TvTheme(accent, backdrop, style, …)` provides `LocalTvStyle`
  the way it provides `LocalBackdrop`, and builds its colour scheme and typography from the look.
- Accent stays the scheme's `primary` in every theme (progress, highlights). Apple/Google card
  focus does not use it.

| Value | mediagram | Apple TV style | Google TV style |
|---|---|---|---|
| Ground | today's `Palette` values | `#000000` | `#202124` |
| Body font | today (Newsreader / Geist) | Geist | Google Sans Flex |
| Display / cover title | Fraunces, as today | Geist 700 | Google Sans Flex 700 |
| Card radius | 0 (square) | 12 dp | 16 dp |
| Hero inset radius | — | — | 24 dp |
| Card focus | 3 dp accent border, scale 1.08 | no border, scale 1.10, soft shadow (`Glow`, black ~60 %, 16 dp) | 3 dp white ring, scale 1.05 |
| Focused tab / button | today's accent border | white pill, dark text | white pill, dark text |

Sizes are in the same units `TvFocus` uses today (its `FeatureCardShape` is 12 dp).

- Fonts: Google Sans Flex is OFL 1.1 (released 2025-11); bundle it with its licence text.
  Apple's SF is not licensed off Apple devices, so the Apple theme uses Geist (already bundled).
  Every weight a theme asks for that sits away from the font's default instance ships as a
  **static cut** made with fontTools' instancer (Geist 700; Google Sans Flex 400/500/700),
  per `Type.kt`'s top note: variable axes went unhonoured on-device for Fraunces.

### Focus
`TvFocus`'s card and surface functions (`TvFocus.kt:93-177`) read `LocalTvStyle` and return
the theme's shape, scale, border and glow. Its private `RectangleShape` becomes the
mediagram value of the look. Every card and pill already routes through these functions.

### Chrome
- `TvLibraryChrome` keeps `TvChromeFocus`, its three Back handlers and its `onExit` routing;
  it switches only what it draws: rail + `TvDepartmentsBar` (mediagram), `TvCentredTabBar`
  (Apple), `TvTopBar` (Google). The new bars reuse `TvPill`, `TvRoundIconButton`, `TvAvatar`
  and the same requesters (`selectedPillFocus`, `searchFocus`, `avatarFocus`, `contentFocus`).
- Without a rail: content starts at the plain gutter; Left at the content's start goes nowhere
  (no rail to open); Back from content goes to the selected tab; Back from the bar exits.
- Google's gear opens Settings directly. In both rail-less themes the avatar's menu page
  (`TvMenuPage`) lists the rail's seven items — My List, Continue, Latest, Genres, Stats,
  Settings, System — which it leaves out today only because the rail carries them
  (`TvMenuPage.kt:30-40`).
- Apple's bar is a translucent dark pill with **no blur**: TV blur measured too slow on the box
  (2026-10-10, +4–12 ms p90).
- The bar-over-cover blend (`TvCatalogBlend.kt`) applies to all three bars.

### Home cover
`TvHomeCover` keeps its rotation (9 s hold, 1.4 s crossfade), dots, focus pause and
`TvCoverActions`; only the slide inside the crossfade is picked by theme:
`TvCoverSlide` (mediagram), `TvFullBleedSlide` (Apple), `TvInsetCardSlide` (Google).
The actions take the theme's pill look (Apple: white Play pill). Both new slides use
`set.backdropPath` and honour `Backdrop.SOLID` like today's slide. Copy is unchanged
(D5): the "Featured today" kicker, genre, tagline and facts line keep their wording.

### Rows
- In Apple/Google, row cards are 16:9: art is `backdropPath ?: posterPath` (poster cropped,
  as `TvResumeCard` already does); name and meta stay under the card.
- Strip width for landscape: `max(204 dp, (rowWidth − 4·StripGap) / 5)` beside the portrait
  rule in `TvPageSpacing.kt`.
- `TvPosterStrip` and `TvRecentBand` draw their own poster `Card`s today; they move onto
  `TvPlate` so every row switches shape in one place.

### Settings › Appearance
`TvAppearanceSection` gains a third row, "Theme", of three swatches shaped like
`TvArtworkSwatch` (label + note), reached by Down from Artwork; `TvSettingsScreen` passes
`style` and `onSelectStyle` through. Choosing applies at once, no restart.

## Non-goals
Phone/tablet and web themes; TMDB logo art; tilt/parallax; ambient screensaver; light
variants of the new themes; per-profile themes; renamed rows or tabs; any blur on TV;
player changes.

## Testing
- Unchanged and passing: `TvPageSpacingTest`, `TvLibraryRailSizesTest`, `TvDepartmentsBarTest`,
  `TvCoverSlideStateTest`, `TvThemeTest`, `TvFocusShapesTest`, `TvHomeReturnTest` — they pin
  mediagram's geometry, so mediagram must not move.
- New (Robolectric/Compose, `ui-tv/src/test`):
  - style round-trips through `SharedPreferencesAppearanceSettings`; unknown value → mediagram;
  - each `TvStyle.look` carries the values in the table above;
  - Apple/Google chrome: no rail node; Back from content → selected tab; Up from content → bar;
    Left at content start stays in content; avatar menu lists the seven rail items; gear opens
    Settings (Google);
  - the cover draws the theme's slide and still rotates/pauses on focus;
  - row cards are 16:9 in Apple/Google, with poster fallback when `backdropPath` is null;
  - the Theme row selects, persists and re-themes without a restart.
- `designsystem` and `ui-tv` unit tests green before each commit; `scripts/check.sh` green before merge.

## Device verification (box 192.168.0.46:5555)
- Release-signed build with `.dm`, installed with `install-multiple -r`; check `install-dm`.
- Walk each theme: Home, a department, My List, the avatar menu, Settings. The walk may change
  **only** Settings › Appearance › Theme and sets it back to mediagram at the end; everything
  else is navigate-only. Check the profile avatar first.
- Frame times: Home at rest and Home scroll, each theme against mediagram, same build. Apple's
  shadow is the only new drawing cost. Three runs per theme per scene; if Apple's median p90 is
  more than 2 ms above mediagram's, drop the shadow and keep the lift.

## Delivery
- `feat/tv-themes` sits on `feat/tv-parity`; tv-parity merges first.
- Minor version bump in all three manifests, above whatever main and the box carry then
  (the box runs a sideloaded 0.120.5; motion holds 0.121.0).
- Docs: `docs/project-changelog.md`, and the surface-parity note that phone/tablet/web have no
  themes yet (a deliberate, written difference until their rounds).
