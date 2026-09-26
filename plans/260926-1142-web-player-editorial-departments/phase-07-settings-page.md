# Phase 7 — Settings: Appearance, Playback, Profile

**Priority:** P2. **Status:** pending.

## Context
There is no settings page today. `260922-2105-settings-menu-telegram-and-cache` phase 06
will add Telegram and cache tabs, and this phase builds the shell they slot into.
Existing prefs: `lib/playback/subtitle-style.js`, `volume-store.js`, `autoplay.js`,
`preference-scope.js`, and `profile-picker.js`.

## Requirements (panel 09)
- `#/settings/{tab}`, with the serif-caps "SETTINGS" and the eyebrow "Make it yours".
- **Appearance:** Theme (Dark / Light / Auto) as three preview swatches, an **accent**
  (6–7 hues, which set `--accent` on `:root`), and **background style** (Default / Blurred /
  Artwork / Solid; this is how the department and detail heroes treat backdrops). The
  settings are stored per profile through `preference-scope.js`, which avoids a new store.
- **Playback:** the existing subtitle style, autoplay and audio-language preferences,
  in one place.
- **Profile:** name and switch profile, reusing the picker.
- **The tab list shows only tabs that have content.** Library, Devices and Advanced
  appear when the settings plan lands them. System stays at `#/system`, linked from here.
- Each accent must keep text at 4.5:1 or better in both themes. Check this in the test.

## Todo
- [ ] settings view + tabs  - [ ] appearance tokens  - [ ] playback/profile tabs  - [ ] contrast test
