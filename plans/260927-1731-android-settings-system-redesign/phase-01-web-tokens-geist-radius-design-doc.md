# Phase 01 — Web tokens, Geist, 6dp radius, DESIGN.md

## Context links

- Web tokens: `web/public/styles/theme.css:78-111` (dark), `:135-147` (light), `:3-70` (`@font-face`), `:157` (body = Geist), `:196-203` (`.eyebrow`)
- Web accents: `web/public/styles/appearance.css:6-17`
- Web type roles: `--display` Fraunces / `--serif` Newsreader / `--text` Geist (`theme.css:107-109`); Newsreader only for decks (`departments.css:37,48,110`, `title-page.css:102,114,174`, `home.css:60,179,289`)
- Page head: `web/public/styles/departments.css:31-36` (`.dept-title`), narrow `:91`
- Mockup tokens: `~/.gstack/projects/daBOB-mediagram/designs/android-settings-system-20260927/round2/tokens.css`
- Fonts: `web/public/font/geist-latin.woff2` (variable `wght` 400–700), `OFL-geist.txt`, `web/public/font/README.md`

## Overview

Priority P1 (blocks all). Status: complete. Swap the dark palette to the web's dark
neutrals in one place, add Geist as the interface face, add the page-head/eyebrow
styles and a 6dp control radius, and rewrite root `DESIGN.md` (the Android one) to the
new system. No screen code changes in this phase.

## Key insights (verified)

- **Palette is one place.** Dark values: `core/designsystem/.../Palette.kt:29-73`; light: `:82-106`. Both theme adapters read it: phone `catalogueColorScheme` (`Theme.kt:29-124`), TV `tvColorScheme` (`ui-tv/.../TvTheme.kt:31-60`). No `Color(0x…)` exists outside `core/designsystem` (git grep, 0 hits). One duplicate: `app/src/main/res/values/colors.xml` `ground #FF16130F` (window before Compose).
- **Light palette and accents already equal the web** (verified value by value): `LightGround F4F0E8`=paper, `LightPage FBF8F2`=surface, `LightSunk E5DFD3`=paper-sunk, `LightText 1B1916`=ink, `LightFigures 48433B`=ink-2, `LightRule 0x331B1916`=rule (.2), `LightRuleStrong 676157`=ink-3, `LightOchre 7A5510`=warn, `LightSage 2F6A35`=held; `Accent.kt:18-24` dark/light pairs = `theme.css:88,144` + `appearance.css:6-17`. Keep all of them.
- **Role mapping rule already in use (light):** Ground=paper, Page=surface, Sunk=paper-sunk, Text=ink, Figures=ink-2, Rule=rule, RuleStrong=ink-3, Ochre=warn, Sage=held. Apply the same rule to dark, so both themes read one mapping.
- **Two web tones have no Palette value:** `--sidebar` (#09090a / #ebe5d9) and `--rule-soft` (rgba(243,239,231,.08) / rgba(27,25,22,.09)). Settings needs both, in both themes. M3 has no fitting role, so add them as Palette pairs plus one tiny `CatalogueTones` CompositionLocal (sidebar, ruleSoft, quiet=ink-3) provided by `MediagramTheme` per resolved theme; the default is dark, so `TvTheme` and bare tests need nothing.
- **Type today:** Fraunces (`display`, opsz 28) for headline/title; Newsreader (`read`, opsz 16) for every body and label role (`Type.kt:36-122`); TV: `TvTypeScale.title` Fraunces 34sp, `.body` Newsreader 18sp (`Type.kt:136-149`). The web sets **all interface text in Geist** and keeps Newsreader for 17px reading copy (decks, overviews, quotes).
- **Radius:** web `--radius: 6px` (`theme.css:111`) is the control radius (inputs, buttons, panels, dialogs). Android has no shape token; M3 defaults apply. Plates stay square here (web thumbs are 3px; `TvFocus.kt:70` `RectangleShape`) — follow-up.
- **Fonts pipeline exists:** `core/designsystem/licenses/README.md` documents `woff2_decompress` from `web/public/font/`; `/usr/bin/woff2_decompress` is installed.

## Requirements

Functional
- Dark Palette = web dark tokens (exact): Ground #0D0D0E, Page #151517, Sunk #1B1B1D, Text #F3EFE7, Figures #CBC5BA, Rule 0x2EF3EFE7 (18%), RuleStrong #9C968B (ink-3), Ochre #E6C47F, Sage #A3D3A4; new Sidebar #09090A / LightSidebar #EBE5D9; RuleSoft 0x14F3EFE7 (8%) / LightRuleSoft 0x171B1916 (9%). `Imprint` default stays coral dark #E57A61.
- `colors.xml` ground = #FF0D0D0E.
- Geist variable TTF bundled; `Interface` family (400/500/600 via `wght`).
- Typography: `bodyMedium`, `bodySmall`, `labelLarge/Medium/Small` → Geist (tnum kept on labelMedium/Small); `bodyLarge` stays Newsreader 17sp (the web's reading copy size); headlines/titles stay Fraunces. `TvTypeScale.body` → Geist 18sp (floor kept).
- New styles: `PageTitle` (Fraunces 500, opsz 112, letterSpacing −0.03em, lineHeight 0.86em, `LineHeightStyle(Center, Trim.None)` so caps are not clipped) + a compact/TV variant at opsz 72; `Eyebrow` (Geist 500 11sp, letterSpacing 0.32em, lineHeight 1.4em); `SectionHead` (Geist 600 16sp); `LedgerLabel` (Geist 14sp) / `LedgerValue` (Geist 15sp, tnum).
- `Radius.control = 6.dp`; `MaterialTheme(shapes = Shapes(extraSmall..large = RoundedCornerShape(6.dp)))`; `extraLarge` (sheets/dialogs) left at M3 default — follow-up.
- `PageHead(title, eyebrow, titleColor, eyebrowColor, maxTitleSize)` composable in designsystem: `BasicText(title.uppercase(), autoSize = TextAutoSize.StepBased(max = maxTitleSize), maxLines = 1)` + eyebrow; `semantics { heading() }`. Foundation only, so ui-tv can call it.

Non-functional
- Every text colour ≥ 4.5:1 on Ground, Page, Sunk and Sidebar (The Measured Colour Rule) — pinned by a test.
- APK growth ≤ ~80 KB (Geist latin TTF).

## Architecture

```
Palette (values, both themes) ──► catalogueColorScheme(dark, accent) ─► MaterialTheme (phone)
          │                       └► CatalogueTones via LocalCatalogueTones (sidebar, ruleSoft, quiet)
          └──────────────────────► tvColorScheme(accent) ─► tv MaterialTheme (TV, dark only)
Type.kt: Display(Fraunces) · Read(Newsreader) · Interface(Geist) ─► CatalogueTypography / TvTypeScale / PageTitle·Eyebrow
Radius.control ─► M3 Shapes (phone) ; used explicitly by settings pills/chips (phases 03/04)
```
Data flow: static values only; `Imprint` still written by the two `SideEffect`s (`Theme.kt:142`, `TvTheme.kt:94`).

## Related code files

Modify
- `android/core/designsystem/src/main/kotlin/Palette.kt` (values + Sidebar/RuleSoft pairs, docs)
- `android/core/designsystem/src/main/kotlin/Theme.kt` (shapes, `LocalCatalogueTones` provide)
- `android/core/designsystem/src/main/kotlin/Type.kt` (Geist family, role swap, PageTitle/Eyebrow/SectionHead/Ledger styles, TvTypeScale.body)
- `android/core/designsystem/src/main/kotlin/Spacing.kt` (`Radius` object)
- `android/core/designsystem/licenses/README.md` (Geist line + regenerate command)
- `android/core/designsystem/src/test/kotlin/AccentContrastTest.kt` (doc comment: Ground is no longer "warmer near-black")
- `android/app/src/main/res/values/colors.xml`
- `DESIGN.md` (repo root — the Android design system)

Create
- `android/core/designsystem/src/main/res/font/geist.ttf` (`woff2_decompress web/public/font/geist-latin.woff2`)
- `android/core/designsystem/licenses/OFL-geist.txt` (copy of `web/public/font/OFL-geist.txt`)
- `android/core/designsystem/src/main/kotlin/PageHead.kt`
- `android/core/designsystem/src/main/kotlin/CatalogueTones.kt` (data class + CompositionLocal, ~20 lines)
- `android/core/designsystem/src/test/kotlin/PaletteContrastTest.kt`

Delete: none.

## Implementation steps

1. Decompress Geist latin woff2 → `res/font/geist.ttf`; copy OFL; extend licenses README.
2. `Palette.kt`: replace the nine dark values, add `Sidebar`/`LightSidebar`, `RuleSoft`/`LightRuleSoft`; rewrite the object's KDoc (it currently argues for the warm "inverted stock" — replace with "the web player's dark theme, `theme.css` `:root`, verbatim").
3. `colors.xml` → #FF0D0D0E (comment already says it mirrors `Palette.Ground`).
4. `CatalogueTones.kt`: `data class CatalogueTones(sidebar, ruleSoft, quiet)`, `DarkTones`, `LightTones`, `LocalCatalogueTones = staticCompositionLocalOf { DarkTones }`. `MediagramTheme` wraps content in `CompositionLocalProvider(LocalCatalogueTones provides if (dark) DarkTones else LightTones)`.
5. `Theme.kt`: pass `shapes = CatalogueShapes` (6dp extraSmall..large). No role remapping (CatalogueColorsTest keeps passing: every role still comes from Palette).
6. `Type.kt`: `Interface` family (Geist, `FontVariation.weight`); swap body/label roles as listed; add PageTitle (two opsz variants), Eyebrow, SectionHead, LedgerLabel/LedgerValue; `TvTypeScale.body` → Geist 18sp; add `TvTypeScale.pageTitleMax = 72.sp`, `eyebrow = 16sp` (ten-foot).
7. `PageHead.kt` using `BasicText` + `TextAutoSize.StepBased` (Compose BOM 2026.06.01, `libs.versions.toml:12`).
8. `PaletteContrastTest`: Text, Figures, RuleStrong(quiet), Ochre, Sage vs Ground/Page/Sunk/Sidebar, both themes, ≥ 4.5:1 (reuse `AccentContrastTest`'s luminance helper — move it to a shared test file if needed).
9. Rewrite `DESIGN.md` front matter + Colors/Typography/Shapes sections to the new tokens (web role names beside Android names), add Components: settings index row, page head, ledger, line/quiet pill, chip, toggle, swatch card; replace "The One Accent Rule" with the web's accent use (focus, selected, outline pills, toggles — `web/DESIGN.md:271`); record the deliberate differences (TV dark-only; plates square for now).
10. Compile all modules; run `:core:designsystem:testDebugUnitTest`.

## Todo

- [x] Geist TTF + OFL + README
- [x] Dark palette values + Sidebar/RuleSoft pairs
- [x] colors.xml
- [x] CatalogueTones local + provide in MediagramTheme
- [x] 6dp Shapes
- [x] Type roles to Geist; PageTitle/Eyebrow/SectionHead/Ledger styles; TvTypeScale.body
- [x] PageHead composable
- [x] PaletteContrastTest; AccentContrastTest comment
- [x] DESIGN.md rewrite
- [x] Compile + designsystem tests green

## Success criteria

- `git grep -n 'Color(0x' android | grep -v designsystem` = 0; `colors.xml` = Palette.Ground.
- designsystem unit tests (CatalogueColorsTest, AccentContrastTest, TvTypeScaleTest, OverscanTest, SharedPreferencesAppearanceSettingsTest, PaletteContrastTest) pass.
- A throwaway preview/screenshot of any screen shows #0D0D0E bar and Geist labels.

## Risks

| Risk | L×I | Mitigation |
|------|-----|------------|
| Every screen changes colour at once; something reads wrong (e.g. `AppChrome.kt:117` pages on `surface` #151517 while the web's page is paper) | H×M | Phase 05 screenshot sweep; list, don't fix (decision 2) |
| Body roles to Geist make overviews/decks sans where the web uses Newsreader (`TitleSpread.kt` overview uses `bodyMedium`) | H×L | Follow-up list in phase 05; `bodyLarge` kept Newsreader so fixing is a one-word change per site |
| `lineHeight 0.86em` clips Fraunces caps | M×M | `LineHeightStyle(Center, Trim.None)`; verify on tablet |
| API 24–25 ignore variation settings (`Type.kt:32-34`) | L×L | Default instance legible; same as today |

## Security

None (static assets; OFL licence shipped beside the font as the existing two are).

## Next steps

Phase 02 (Artwork model) builds on `Appearance`; phase 03/04 consume PageHead, tones, styles.
