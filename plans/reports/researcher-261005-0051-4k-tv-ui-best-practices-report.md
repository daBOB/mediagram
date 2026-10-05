# 4K TV UI Best Practices for Android TV / Jetpack Compose for TV

**Report Date:** 2026-10-05  
**Project Context:** mediagram Android TV app (Compose for TV 1.1.0, Media3 1.10.1, Coil 3.5.0)

---

## 1. Resolution & Density: UI Rendering on 4K Panels

### Design Canvas & Asset Targets

- **Design Resolution:** 960px × 540px @ MDPI (1px = 1dp) — universal baseline for all TV resolutions [developer.android.com/design/ui/tv/guides/styles/layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- **Asset Target:** 1080p (Full HD). Android system downscales to 720p on 720p devices [developer.android.com/design/ui/tv/guides/styles/layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- **Display Densities:**
  - 1080p UI: xhdpi (320dpi)
  - 4K/2160p UI: xxxhdpi (640dpi)
  - [developer.android.com/training/multiscreen/screendensities](https://developer.android.com/training/multiscreen/screendensities)

### 1080p UI on 4K Panel: Normal & Recommended

**Yes, 1080p UI on a 4K panel is the normal Android TV setup.** Google's layout guide makes 1080p the asset target for every TV ([layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)). Video surfaces are decoded and composited separately from the UI, so a 1080p UI does not cap video at 1080p. *(That last point is platform behaviour, not a quote from the guide.)*

**Implication:** Do not make 4K-native UI assets. On a box that reports a 1080p UI, the app renders at 1920×1080 (xhdpi). The box or panel scaler then upscales the whole frame. xxxhdpi only applies on a device whose UI surface is actually 2160p.

### Overscan Safe Area Margins

- **Horizontal:** 48dp (5% of 960px width)
- **Vertical:** 27dp (5% of 540px height)

Position critical UI elements within these margins. Modern TVs rarely have true overscan, but margins ensure visibility across all TV models [developer.android.com/design/ui/tv/guides/styles/layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts).

### Grid System

- **12-column layout** with 52dp column width, 20dp gutter spacing, 58dp side margins [developer.android.com/design/ui/tv/guides/styles/layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- **Common Card Widths:**
  - 1 card: 844dp | 2 cards: 412dp each | 3 cards: 268dp each | 4 cards: 196dp each | 5 cards: 124dp each

---

## 2. Typography at 10-Foot Distance

### Material 3 Type Scale (TV-Optimized)

| Role | Size (sp) | Line Height (sp) | Usage |
|------|-----------|-----------------|-------|
| **Display Large** | 57 | 64 | Short, prominent text; screen headings |
| **Display Medium** | 45 | 52 | Featured, immersive content |
| **Display Small** | 36 | 44 | Display text |
| **Headline Large** | 32 | 40 | High-emphasis text; featured carousels |
| **Headline Medium** | 28 | 36 | Featured content headers |
| **Headline Small** | 24 | 32 | Card headers, list titles |
| **Body Large** | 16 | 24 | Longer text passages; main content |
| **Body Medium** | 14 | 20 | Standard body text |
| **Body Small** | 12 | 16 | Small utility text |
| **Label** | 11-14 | varies | Captions, button text, component labels |

[developer.android.com/design/ui/tv/guides/styles/typography](https://developer.android.com/design/ui/tv/guides/styles/typography)

### Legibility Requirements

- **Font:** Roboto (system default). Use sans-serif for body and labels.
- **Avoid:** Thin fonts, decorative fonts, fonts with very narrow and broad strokes.
- **Prefer:** Large counters, adequate optical sizing, distinguishable letterforms, sufficient font width.
- **Best Practice:** Use Roboto for native elements; reserve expressive fonts for display/headline roles only. [developer.android.com/design/ui/tv/guides/styles/typography](https://developer.android.com/design/ui/tv/guides/styles/typography)

---

## 3. Focus & D-Pad Navigation

### Focus Indicators & Modifiers (androidx.tv 1.1.0)

**Surface and Card components include:**
- **Border** — focus border indication
- **Glow** — glow effect for focused elements (most prominent, high contrast)
- **Scale** — scale indication for focus changes (subtle size bump)

[developer.android.com/jetpack/androidx/releases/tv](https://developer.android.com/jetpack/androidx/releases/tv)

### D-Pad Navigation & Focus Management

- **FocusRequester:** Explicitly move focus via `Modifier.focusRequester()` + `FocusRequester().requestFocus()` [developer.android.com/reference/kotlin/androidx/compose/ui/focus/package-summary](https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/package-summary)
- **focusRestorer Modifier:** Save/restore focus for dynamic content (lists, grids) [developer.android.com/reference/kotlin/androidx/compose/ui/focus/focusRestorer.modifier](https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/focusRestorer.modifier)
- **Two-dimensional traversal:** All UI elements reachable via D-pad (up, down, left, right) with clear focus patterns aligned to user reading habits. [developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv](https://developer.android.com/develop/adaptive-apps/guides/tv/build-adaptive-apps-for-tv)

### Lazy Lists: Migration to Standard Compose Foundation

**Compose Foundation 1.7.0+ recommended approach:**
- Use standard `LazyRow`, `LazyColumn`, `LazyHorizontalGrid`, `LazyVerticalGrid` instead of deprecated TV-specific variants.
- Built-in focus-positioning features keep focused items visible and positioned intuitively.
- Custom `BringIntoViewSpec` allows pivot-point tuning (e.g., keep focused item 30% from left edge) [developer.android.com/training/tv/playback/compose/lists](https://developer.android.com/training/tv/playback/compose/lists)

**Deprecated → Replace:**
- `TvLazyRow` → `LazyRow`
- `TvLazyColumn` → `LazyColumn`
- `TvLazyHorizontalGrid` → `LazyHorizontalGrid`
- `pivotOffsets` → `BringIntoViewSpec`

---

## 4. Image Assets: Resolution, Bitmap Memory, Downsampling

### Asset Sizes

| Asset | Resolution | Density | Purpose |
|-------|------------|---------|---------|
| App Icon (Launcher) | 160×160 px | xhdpi | Home screen launcher |
| App Banner | 320×180 px | xhdpi | Featured app display |
| Poster / Card | 1080p (target) | — | Movie/show posters |
| Backdrop / Hero | 1080p (target) | — | Background images |

[developer.android.com/training/tv/publishing/checklist](https://developer.android.com/training/tv/publishing/checklist)

### Critical Rule: Match Device UI Resolution

**Do NOT load images with resolution higher than device UI resolution.**
- 1080p UI device: load 1080p images, not 4K.
- 720p UI device: downscale 1080p images to 720p.
- Loading 4K images on 1080p UI wastes memory and provides no visual benefit [developer.android.com/training/tv/playback/memory](https://developer.android.com/training/tv/playback/memory)

### Low-RAM TV Device Memory Budgets

TV devices with 1–1.5 GB RAM are "low-RAM" per `ActivityManager.isLowRamDevice()`.

**Total Memory Budget: 280 MB max** (Anon+Swap + Graphics + File) [developer.android.com/training/tv/playback/memory](https://developer.android.com/training/tv/playback/memory)

| Component | Target | Notes |
|-----------|--------|-------|
| **Anon + Swap** | < 160 MB | Java, native, stack, media buffers |
| **Graphics** | 30–40 MB | GPU textures, display buffers |
| **File** | 60–80 MB | Code pages, files |
| **Peak Recommended** | 200 MB | (Anon+Swap + Graphics) |

### Image Downsampling

**Coil 3.5.0 (in use) handles downsampling automatically:**
- Coil analyzes target view size and decodes to approximate dimensions (avoids decoding oversized images).
- Memory and disk caching reduce repeated loads.
- Configuration: Set `ImageLoader.Builder` with memory cache policy and disk cache directory [coil-kt.github.io](https://coil-kt.github.io/coil/)

**Hardware bitmaps:** "Use hardware-backed bitmaps when possible". This "avoids duplicating bitmaps which otherwise would be in both graphics memory and anonymous memory" ([memory](https://developer.android.com/training/tv/playback/memory), checked against the page). Coil's `allowHardware` defaults to true.

**Best Practice:** Load images at the size they'll be displayed. For a 268dp poster card on a 1080p UI, Coil will downsample accordingly.

---

## 5. Color, Contrast, HDR

### TV Color Guidelines (Material 3 for TV)

Source for all of the following: [color-on-tv](https://developer.android.com/design/ui/tv/guides/foundations/color-on-tv). Quotes were checked against the page.
- **Dark themes:** "Using darker colors saves power. Avoid using white background unless necessary."
- **Contrast varies by panel:** "Same color on different TVs may look washed out on TV with a low contrast ratio." Test on the real TV, not only the emulator.
- **Picture modes:** Standard is the default. Vivid raises saturation and Dynamic raises contrast, so the UI must survive both.
- **Color space:** "When designing basic UI elements, use the standard sRGB color space". DCI-P3 content "may only be compatible with advanced TV displays."
- *Correction:* an earlier draft said "avoid saturated reds (burn-in risk)". The page says nothing about reds or burn-in, so the claim was removed.

### Material 3 Color System for TV

Generated from five key colors (Primary, Secondary, Tertiary, Surface, Outline) with 13+ tonal palettes. Use **Material Theme Builder** to generate schemes or extract seed color from content (movie posters) [developer.android.com/design/ui/tv/guides/styles/color-system](https://developer.android.com/design/ui/tv/guides/styles/color-system)

### HDR Playback & UI Behavior

- **HDR playback:** ExoPlayer plays HDR (HDR10, HDR10+, Dolby Vision) with no special setup ([hdr-playback](https://developer.android.com/media/grow/hdr-playback)). That page's only tone-mapping note is about screenshots: "Android takes screenshots in SDR". *Correction:* an earlier draft cited it for "UI is composited in SDR" and for display-mode switching. The page says neither, so treat any claim about how UI looks over HDR video as unverified.
- **Frame-rate matching** ([frame-rate](https://developer.android.com/media/optimize/performance/frame-rate)):
  - For long videos, Google "strongly recommend[s]" calling `setFrameRate(fps, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, CHANGE_FRAME_RATE_ALWAYS)`.
  - A non-seamless switch, such as 24p on a 60 Hz output, needs both a user opt-in (the "Match content frame rate" setting) and an app opt-in (`CHANGE_FRAME_RATE_ALWAYS`).
  - The platform only switches mode on `setFrameRate()` "if the mode switch is lightweight". For a heavy switch "(for example, on an Android TV device), use `preferredDisplayModeId`".
- **Media3 default:** `ExoPlayer.Builder` sets `videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS` ([ExoPlayer.java:513](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java)). Apps that want `CHANGE_FRAME_RATE_ALWAYS` "should set the mode to `VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF` … and should then call `Surface#setFrameRate` directly" (same file, javadoc of `setVideoChangeFrameRateStrategy`).

---

## 6. Motion & Performance on Low-End TV SoCs

### Baseline Profiles

**Include Baseline Profiles.** They ship profile-guided AOT compilation for startup, scrolling and animation paths ([overview](https://developer.android.com/topic/performance/baselineprofiles/overview)). The earlier "~30–40%" figure was not re-checked against the page.

**Quality-guideline status** ([tv-app-quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), checked against the page):
- **TV-BP, Baseline Profiles:** Tier 2 "TV Optimized". Recommended, not mandatory. *Correction:* an earlier draft called it "Required".
- **TV-ME, memory:** Tier 3, which is mandatory. On low-RAM devices the foreground app must stay within the [memory limits](https://developer.android.com/training/tv/playback/memory).
- **TV-OV, overscan:** Tier 3. No text or controls may be cut off at the screen edges.

### Compose Performance Best Practices

| Practice | Impact | Details |
|----------|--------|---------|
| **Use `remember` for expensive calculations** | Reduces recomposition cost | Cache results, move logic to ViewModel [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| **Provide stable keys in lazy layouts** | Avoids unnecessary recompositions | `items(notes, key = { note.id })` [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| **Use `derivedStateOf`** | Limits recomposition scope | Only recompose when derived state changes, not on every scroll [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| **Defer state reads** | Skips composition phase | Use lambda-based modifiers: `.offset { scrollProvider() }` [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |
| **Animations in draw phase** | More performant than layout phase | Avoid layout phase animations [developer.android.com/develop/ui/compose/animation/quick-guide](https://developer.android.com/develop/ui/compose/animation/quick-guide) |
| **No backwards writes** | Prevents infinite recomposition | Never write state after reading in same composition [developer.android.com/develop/ui/compose/performance/bestpractices](https://developer.android.com/develop/ui/compose/performance/bestpractices) |

### Lazy Layout Performance

**Compose Foundation 1.7.0+:** Built-in focus-positioning features in `LazyRow`/`LazyColumn` improved scrolling performance by reducing focus search space. No explicit frame-budget docs; optimization is automatic. [developer.android.com/training/tv/playback/compose/lists](https://developer.android.com/training/tv/playback/compose/lists)

---

## Applies to mediagram Android TV

Checked against the code in `android/` on 2026-10-05:

- **Resolution:** OK, no change needed. The box reports a 1080p UI, which matches the 1080p asset target.
- **Lazy layouts:** OK. `ui-tv` already uses `androidx.compose.foundation.lazy` (25 files) and has no `TvLazy*` or `androidx.tv.foundation.lazy` imports. The Compose BOM is 2026.06.01, well past Foundation 1.7.
- **Images:** TV backdrops raised from w780 to w1280 in 0.117.0 (the TV reported sw540, so it was treated as a phone). Otherwise OK. There is no custom `ImageLoader`, so Coil 3.5.0 runs on defaults: it sizes decodes from layout constraints and `allowHardware` is on. Backdrops should still be requested at ≤1920 px wide, never at original TMDB size.
- **Baseline Profiles: done in 0.117.0** (`:baselineprofile`, plan 261005-1655). *Was missing:* `androidx-profileinstaller` sits in `libs.versions.toml` but no module uses it, and no `baseline-prof.txt` exists. A `benchmark` build type exists in `app/build.gradle.kts`. This is the TV-BP Tier 2 gap and the main lever for scroll jank on the box.
- **Frame-rate matching: done in 0.117.0** (TV only, `preferredDisplayModeId`). *Was not done:* No `setVideoChangeFrameRateStrategy`, `setFrameRate` or `preferredDisplayModeId` appears anywhere. Media3's default `ONLY_IF_SEAMLESS` means 23.976/24p films most likely play at the box's output rate with 3:2 judder. Fixing it means setting the strategy to OFF and choosing the display mode, best via `preferredDisplayModeId` on TV.
- **Colour:** keep the dark theme. Test with the TV in Standard and in Vivid, and on the real panel.

---

## Conflicts & Unverified

1. **Animation durations:** none of the primary sources gives TV-specific timings. Test on the box.
2. **UI over HDR video:** no primary source found for how the UI is composited or tone-mapped while HDR plays.
3. **Overscan margins (48/27dp):** these are the current guide's values. The box shows no overscan, so they are conservative but still required by TV-OV.
4. **Baseline Profile gain figure:** "~30–40%" was not re-checked.

---

## Unresolved Questions

1. *(Answered 2026-10-05: the box now drives a Hisense TV with 23.976/24/25 Hz modes, so matching does switch.)* Does the box offer "Match content frame rate", and does it switch refresh rate at all? It should be checked before any frame-rate work, because a 24p test file would show it.
2. Does the box report `isLowRamDevice()`? That decides whether the TV-ME memory limits apply to it.
