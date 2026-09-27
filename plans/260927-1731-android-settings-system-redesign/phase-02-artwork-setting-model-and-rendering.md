# Phase 02 — Artwork setting: model, storage, hero rendering

## Context links

- Web options: `web/public/lib/catalog/settings-page.js:25-30` (`BACKDROPS`), read/write `:32-38`, built `:72` (`choice("Artwork", "backdrop", read("backdrop", "default"), …)`), swatches `web/public/styles/appearance.css:55-58`
- Web storage/boot: `web/public/lib/appearance-boot.js:7-8,14-21,25` (localStorage `mediagram.backdrop`, per browser, "a property of a screen, not of a profile"); no server/Rust involvement (`web/src` has no reference)
- Web rendering: `appearance.css:19-26`; hero art `title-page.css:62-71` (wide) / `:155-161` (≤900px), `departments.css:16-27` (wide) / `:88-92` (≤900px), home cover `home.css:29-37`
- Android appearance model: `core/designsystem/.../Appearance.kt:9-72`, `ThemeChoice.kt`, `Accent.kt`; VM `feature/setup/.../AppearanceViewModel.kt`; DI `AppearanceModule.kt:21-26`
- Theme roots: `ui-mobile/.../MobileApp.kt:43` (`MediagramTheme(appearance)`), `ui-tv/.../TvApp.kt:48` (`TvTheme(accent = appearance.accent)`)

## Overview

Priority P1 (03/04 draw its options). Status: pending. Add the web's fourth Appearance
question as a per-device preference, expose it to every hero through a CompositionLocal
set by both themes, and make the heroes honour it the way the web's CSS does.

## Key insights (verified)

- **Storage decision is the web's, and it is unambiguous:** per device/browser, not per profile, not synced (`appearance-boot.js:7-8`, `settings-page.js:8-9`). Android already keeps theme/accent that way in plain prefs file `appearance_settings` (`Appearance.kt:14-17,46-71`). Add key `backdrop` beside `theme`/`accent`; unknown/missing → `default` (web `read("backdrop","default")`). No open question.
- **Four ids/labels/captions** (web verbatim): `default` Default "Artwork fades into the page"; `blurred` Blurred "Colour and light, softened"; `artwork` Artwork "The picture behind the words"; `solid` Solid "Plain pages, no artwork".
- **What each mode does on the web:** Blurred → `blur(28px) saturate(1.25)`, `scale(1.12)` on `.spread-art`, `.dept-art` **and** `.cover-stage` images; Artwork → `.spread-art`/`.dept-art` `inset:0`; Solid → `.spread-art`/`.dept-art` hidden, `.spread`/`.dept-hero` `min-height:0`, `.spread-quote`/`.dept-quote` hidden; home cover keeps its art in Artwork and Solid.
- **On the web's narrow layout Artwork changes nothing visible:** the ≤900px rules keep an explicit `height` (`title-page.css:157` 72vw, `departments.css:89` 64vw), so `inset:0` only re-sets edges already at 0 and `bottom` is ignored. Android's TitleSpread is the narrow layout at every width by design (`TitleSpread.kt` KDoc), so **Artwork = Default on Android** is web parity, not a gap. The wide two-column spread is a follow-up.
- **Android heroes (the web's three):**
  - title spread = `ui-mobile/.../catalog/TitleSpread.kt:45-115` (art 260dp at `:57`, foot fade, tagline quote) — used by `TitleDetailScreen.kt:95`, `CollectionScreen.kt:193`
  - department hero = `DepartmentHero.kt:44-100` (16:9 art at `:60`, words over a black gradient) — `MoviesDepartmentScreen.kt:60`, `ShowsDepartmentScreen.kt:75`, `CollectionsScreen.kt:40`, `FranchiseScreen.kt:48`; its quote is a separate `PullQuote` at `MoviesDepartmentScreen.kt:69`, `ShowsDepartmentScreen.kt:85`
  - cover = `CoverStory.kt:124-137`, art at `:132` (`HomeScreen.kt:70`); `HomeScreen.kt:93` PullQuote is the home's own quote — not affected on the web
  - TV: `ui-tv/.../catalog/TvCoverStory.kt:68-140` is both the home cover (`TvHome.kt:123`) and the department hero (`TvMoviesDepartmentPage.kt:68`, `TvShowsDepartmentPage.kt:72`, passing a `kicker`). TV title pages have no art.
- Cards that use backdrops (`FeatureStrip.kt:79`, `ResumeStrip.kt:71`, `TvFeatureStrip.kt:76`) are not heroes on the web either — untouched.
- `Modifier.blur` is a no-op below API 31; `minSdk = 24` (`libs.versions.toml:49`). The TV box's API level is unknown → need a fallback: decode the art tiny (Coil `size(48)`) and let `ContentScale.Crop` upscale it, which softens to colour and light on any API.
- `ui-common` has neither Coil nor designsystem today (`ui-common/build.gradle.kts`); both are already deps of ui-mobile and ui-tv.

## Requirements

Functional
- `Backdrop` enum (DEFAULT/BLURRED/ARTWORK/SOLID) with `storageKey`, `label`, `note`, `fromStorageKey` (unknown → DEFAULT).
- `Appearance.backdrop`; `AppearanceSettings.chooseBackdrop`; both impls persist/hold it; `AppearanceViewModel.chooseBackdrop`.
- Move display labels next to their enums (DRY for phone + TV + index status): `ThemeChoice.label/note`, `Accent.label`, `Backdrop.label/note` (today duplicated in `AppearanceSection.kt:50-57,114` and `TvAppearanceBlock.kt`'s swatch label).
- `LocalBackdrop` provided by `MediagramTheme(appearance)` and `TvTheme(accent, backdrop)`; default DEFAULT (tests without a theme unchanged).
- `HeroArtwork(path, modifier, contentScale = Crop)` in ui-common: reads `LocalBackdrop`; BLURRED → API≥31 `Modifier.blur(28.dp, BlurredEdgeTreatment.Rectangle)` + `graphicsLayer { scale 1.12 }` + saturation 1.25 colour filter; API<31 → tiny-decode request + same scale/filter. Other modes draw the image plainly.
- Per hero:
  - TitleSpread: SOLID → no art box, no tagline quote, no 260dp placeholder; else art via `HeroArtwork`.
  - DepartmentHero: SOLID → text-only branch (existing `art == null` path); else `HeroArtwork`.
  - Dept PullQuotes (`MoviesDepartmentScreen.kt:69`, `ShowsDepartmentScreen.kt:85`): hidden when SOLID.
  - CoverStory and TvCoverStory-as-cover: BLURRED only.
  - TvCoverStory-as-department-hero (new `departmentHero: Boolean = false`, set by the two TV department pages): SOLID → no art (text on page); BLURRED → blur.

Non-functional
- No extra decode for non-blurred modes; blurred API<31 decodes less than today.

## Architecture

```
prefs "appearance_settings".backdrop ─► SharedPreferencesAppearanceSettings ─► StateFlow<Appearance>
  ─► AppearanceViewModel.state ─► MobileApp: MediagramTheme(appearance) ┐
                                 TvApp: TvTheme(accent, backdrop)        ├─ provide LocalBackdrop
  heroes: HeroArtwork / TitleSpread / DepartmentHero / CoverStory / TvCoverStory read LocalBackdrop
Settings (phases 03/04) ─► AppearanceViewModel.chooseBackdrop ─► prefs write + StateFlow emit ─► recompose
```
`staticCompositionLocalOf`: the value changes only from Settings; a full recomposition then is correct and cheap.

## Related code files

Modify
- `android/core/designsystem/src/main/kotlin/Appearance.kt` (field, interface, both impls, key `backdrop`)
- `android/core/designsystem/src/main/kotlin/ThemeChoice.kt`, `Accent.kt` (labels/notes)
- `android/core/designsystem/src/main/kotlin/Theme.kt` (provide `LocalBackdrop`)
- `android/feature/setup/src/main/kotlin/AppearanceViewModel.kt` (`chooseBackdrop`)
- `android/ui-tv/src/main/kotlin/ui/tv/TvTheme.kt` (`backdrop` param, provide local), `ui-tv/.../TvApp.kt:48`
- `android/ui-common/build.gradle.kts` (+ `coil.compose`, + `:core:designsystem`)
- `android/ui-mobile/src/main/kotlin/ui/catalog/TitleSpread.kt`, `DepartmentHero.kt`, `CoverStory.kt`, `MoviesDepartmentScreen.kt`, `ShowsDepartmentScreen.kt`
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCoverStory.kt`, `TvMoviesDepartmentPage.kt`, `TvShowsDepartmentPage.kt`
- Tests: `core/designsystem/src/test/kotlin/SharedPreferencesAppearanceSettingsTest.kt`, `feature/setup/src/test/kotlin/AppearanceViewModelTest.kt`

Create
- `android/core/designsystem/src/main/kotlin/Backdrop.kt` (enum + `LocalBackdrop`)
- `android/ui-common/src/main/kotlin/ui/catalog/HeroArtwork.kt`
- `android/ui-mobile/src/test/kotlin/ui/catalog/HeroBackdropModesTest.kt` (Robolectric: SOLID drops the art/quote nodes on TitleSpread + a department screen; DEFAULT keeps them)

Delete: none.

## Implementation steps

1. `Backdrop.kt` enum + `LocalBackdrop`; labels on `ThemeChoice`/`Accent`.
2. `Appearance` + settings impls + key; extend prefs test (round trip, unknown → default, missing → default).
3. `AppearanceViewModel.chooseBackdrop`; extend VM test.
4. Provide `LocalBackdrop` in `MediagramTheme` and `TvTheme`; pass `appearance.backdrop` at `TvApp.kt:48`.
5. ui-common deps + `HeroArtwork` (API branch inside; its comment says why the tiny-decode path exists: `Modifier.blur` needs API 31).
6. Switch the four hero images to `HeroArtwork`; add SOLID branches and the two PullQuote guards; `TvCoverStory.departmentHero`.
7. Remove the "artwork mode is not ported" sentence at `AppearanceSection.kt:33-34` (the section itself is rebuilt in 03).
8. Tests: prefs, VM, `HeroBackdropModesTest`; compile ui-mobile, ui-tv.

## Todo

- [ ] Backdrop enum + LocalBackdrop + enum labels
- [ ] Appearance/settings/VM + tests
- [ ] Theme providers (phone, TV) + TvApp wiring
- [ ] HeroArtwork in ui-common (+ deps)
- [ ] TitleSpread / DepartmentHero / CoverStory / TvCoverStory + dept PullQuotes
- [ ] HeroBackdropModesTest
- [ ] Compile + unit tests green

## Success criteria

- Choosing a mode (via VM in a test) persists across a new `SharedPreferencesAppearanceSettings` instance.
- SOLID: no hero image or dept quote in title and department screens; home cover unchanged. BLURRED: hero/cover images carry the blur path (API 35 Robolectric) — verified visually on the tablet in phase 05.
- Existing department/title/home tests pass unchanged (default = today's rendering).

## Risks

| Risk | L×I | Mitigation |
|------|-----|------------|
| Artwork visibly = Default on Android; a viewer may think it is broken | M×L | Same as the web's own narrow layout; say so in DESIGN.md; wide spread is a follow-up |
| Blur cost on low-end TV box | L×M | Blur is GPU RenderEffect on 31+; tiny decode below is cheaper than today |
| `scale(1.12)` shows past hero bounds | M×L | `clipToBounds()` on the hero box |
| Default dept hero already reads like the web's Artwork (words over art) | pre-existing | Follow-up, not fixed here (decision 2) |

## Security

Backdrop is not a secret; plain prefs like theme/accent (`Appearance.kt:41-45` rationale: a keystore failure must not cost a viewer the choice).

## Next steps

03 draws the four swatch cards on phone/tablet; 04 adds them to the TV Appearance section.
