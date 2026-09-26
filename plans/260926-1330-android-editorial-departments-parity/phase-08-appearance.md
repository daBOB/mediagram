# Phase 8 — Settings › Appearance (theme + accent)

**Status:** pending. Web reference: `settings-page.js`, `appearance-boot.js`,
`styles/appearance.css` (accent values per theme, each ≥4.5:1 on its paper).

- Theme Dark / Light / Auto (Auto = follow the system), and the seven accents (coral,
  blue, violet, teal, green, amber, rose) with the web's per-theme values, in
  `core/designsystem` (`Palette.kt`/`Theme.kt`); stored per device (DataStore/prefs), not
  per profile — same as the web.
- Phone: an Appearance section in `SettingsScreen.kt`. TV: in the TV settings surface the
  TV branch adds (if it has none after merge, a minimal one).
- Contrast test in Kotlin over the same accent table (port of
  `web/test/appearance-contrast.test.ts`).
- **Deliberate difference:** the web's artwork mode (Default/Blurred/Artwork/Solid) is
  not ported (user decision 2026-09-26).

**TV theme (user decision, 2026-09-26, via the TV session):** the TV stays always dark and
takes the accent only. Recorded in docs/system-architecture.md "Television differs".
