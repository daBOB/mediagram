# Web player as reference surface — map for the TV plan

Paths relative to repo root. Web = reference (CLAUDE.md § Surface Parity).

## Views
- **Routing** `web/public/app.js` `route()`: hash `#/section/name/...`. Masthead order: Home, Movies, Series, Tutorials · Continue, Watchlist, Collections, Kids (`kept`, from watch state) · System (`apart`), profile `#who`, search.
- **Profile picker** `lib/profile-picker.js` `chooseProfile`: "Who's watching?", initial-letter tiles, choice per device (a TV stays on its profile). Convenience, not login.
- **Kids/FSK** `lib/age-rating.js`: `KIDS_AGE_LIMIT = 12`; `kidsVerdict` safe/unsafe/unrated; rated > 12 never kids; unrated only if hand-marked; series judged by first episode; kids profile filters catalog once (`forKidsProfile`, app.js ~489) so every view agrees. `viewKids`: Movies / Series / "Marked by hand".
- **Home** `lib/catalog/home-shelves.js` `homeShelves`, `home-view.js`: Continue, Next up, Latest films, Latest series, Latest courses. `SHELF_LIMIT = 6`, heading "Title · total" + "See all", empty rows dropped, no genre rows, a title appears once. Courses row is text (no art). Spec `docs/superpowers/specs/2026-09-22-web-player-start-page-design.md` §3 **rejects side-scrolling rails**.
- **Grouping** `lib/library.js` `groupLibrary`: `ep` → series by "Season N"; `tut`/`doc` → tutorials by `path`/`chap`; **every other kind incl. unknown → movies**.
- **Cards** `shelf-view.js`, `plate.js`: 2:3 poster plate, caption beside, square corners, hairline rules; initials fallback (`initialsOf`). Shelf mode list|wall per device, **list default** (`shelf-mode.js` comment expects posters on a TV). Movies paged 48.
- **Featured reel** `featured-reel.js`: dark dialog, 12 unwatched films with posters, 7 s hold.
- **Film page** `film-page.js`: art beside facts, Play/resume; TMDB score/genres/description after; genre links `#/genre/X`.
- **Series/course** `course-view.js`, `season-wall.js`: >1 season → season plate wall; 1 season → episodes. Courses folder by folder.
- **Player** `index.html` `<dialog id="player">`, `lib/playback/player.js`: dark, full-screen, custom controls. Top: title, tech line, Close. Rail: ends-at, preload, Play next, Watchlist, Kids, Add to…, Notes. Seek bar. Bottom: time, audio/subs/speed, −10/play/+10, mute/volume/fullscreen. Controls fade 2.6 s, never while paused (`player-hud.js`). Resume ignores first 30 s/10 % and last 5 % (`resume-point.js`), shows "Carrying on from m:ss". No autoplay on open. Up-next 10 s countdown with Cancel/Play now (`up-next.js`); auto-start gated on buffer (`autoplay.js`); next two preloaded.
- **System** `lib/status/status-view.js`: label+figure rows in page type, live while open.
- **Search** `search-view.js`: flat ranked list with "why it matched". **Android has no search.**
- **Collections** `collections-view.js`: user-named lists.

## Keyboard / focus
- `lib/playback/player-keys.js` `keyAction` — pure key table: Space/k play-pause, ←/→ ±10 s, ↑/↓ volume, m, f, c, p, z, `,` `.` frame, `[` `]` speed, a/b loop, 0–9 seek tenths. Ignored in form controls; Space/Enter left to focused button; Escape to dialog. Wired `player.js:786`.
- `featured-reel.js:66` ←/→ slides.
- Else only `:focus-visible` (`style.css:129`, `:1245`). **No spatial nav on shelves** → D-pad focus order is a new decision for TV.

## Visual language (`web/public/style.css` :61-94)
"A printed catalogue of things already owned. Not a storefront." Paper `#f4f0e7`, ink `#1e1b16`, imprint red `#8c3b2e`; player stage `#0d0c0b`, accent `#d99a63`. Fraunces display, Newsreader text. Masthead not sidebar; hairline rules not cards. Android carries this inverted to a dark ground (`docs/project-changelog.md` ~495–525).

## TV in docs
- `docs/development-roadmap.md:164` "Later: the television surface" — needs its own round; "screens and a D-pad, not new machinery".
- `docs/system-architecture.md:553-561, 725` — `app` picks phone vs TV; `ui-tv` is a Gradle file only.
- `docs/superpowers/specs/2026-09-19-android-foundation-design.md:117-157, 240` — tv-material, same `feature:*` UiState, `UiModeManager` selection, leanback manifest.

## Unresolved
- No web precedent for D-pad focus order, plate focus styling, remote → key-table mapping (Back vs Escape, centre vs Space).
- Plates vs list default on TV.
