# Web Ambient (0.184.0)

Ambient is the fifth option in Settings > Appearance > Artwork (`data-backdrop="ambient"`), matching the tablet's.

## What was built
- `web/public/lib/ambient-colour.js`: port of Android's `strongestColourOf` and `glowOf`, with androidx `colorToHSL` / `HSLToColor`. It runs in single precision (`Math.fround`), so family edges fall where the Kotlin's do. `glowOfImage` samples a poster `<img>` on one reused small canvas. The decode is halved by powers of two while width/(2s) >= 32, as Android does. Results are cached per URL (512 entries, like Android's LruCache).
- `web/public/lib/ambient.js`: loaded as its own module from index.html (app.js is at its line cap). It mounts and unmounts on the `mediagram:appearance` and `storage` events. While mounted it adds:
  - a fixed `.ambient-glow` layer of 4 `<i>` blobs, and
  - `.ambient-veils`, the keep-clear layer described below.
- `web/public/styles/ambient.css`: blobs are radial gradients animated only by `translate` and `transform` (compositor). `@property --ambient` makes the colour ease over 900 ms (Android: 900 ms tween). Cards are rounded to `--radius-card` (12px, Android's `Radius.card`).
- Settings list entry has Android's label and note: "Ambient" / "The poster's colour, drifting behind the page". The swatch is an accent radial gradient, as on Android.

## Where the colour comes from
- Title, film and show pages (anything with `.spread`): the page's own backdrop `.spread-art img`. Android's `TitleSpread` calls `AmbientFromArt(backdropPath)`, so this is the backdrop, not the poster. The page holds that colour against the rows on it (Similar). With no art it keeps the previous colour, as Android does.
- Home, department and shelf pages: the reading line at 1/3 of the window height. Among on-screen `main .thumb img` posters not scrolled off the left, take the row whose centre is nearest the line (a row is centres within half a card height), then its left-most. This is `cardNearestLine` ported, with a 120 ms settle after scroll, change or poster load.
- No poster known yet: the glow stays transparent, then eases to the first colour. A page with no poster keeps the last colour (Android: target kept).
- Light theme: Android has no special case (same glow, 0.34 alpha, over the theme's ground), so none here. Checked in light: it reads as a soft tint.

## Keeps off backdrops
Android cuts the glow around each backdrop's bounds. Here a paper-coloured veil (`box-shadow: 0 0 48px 48px var(--paper)`) sits under each `.spread-art` / `.dept-art` / `.cover`, in document coordinates so it scrolls with the page. Solid at the art, gone by about 96px out. Rects are re-measured on DOM change, resize and ResizeObserver, never per frame.

## Deliberate differences from Android
- **Veil, not cut-out.** Page ground is flat paper, so a paper veil is equivalent to a cut-out, and it avoids per-frame masks. The ease is Gaussian rather than Android's 4-stop curve.
- **CSS blobs, not a shader.** Blobs overlap with ordinary alpha rather than metaball field sums. Each blob follows a one-way sweep (ease-in-out, alternate) between its orbit extremes, instead of a sine orbit with a varying radius. Positions at t=0, speeds and the falloff curve (core to 0.67r, 38% at r, about 0 at 2.9r, peak alpha 0.34) are taken from the shader. Under reduced motion theme.css stops the animation and each blob stays at its t=0 position: a still, complete glow.
- **Downscale**: the browser's canvas downscale vs Android's `inSampleSize` decode, so a poster can differ by a shade; the colour family is the same.
- **Grey posters**: Kotlin's `glowOf` adds saturation 0.08 at hue 0, so grey glows faintly warm ([108,108,108] -> [117,99,99]). Ported as is, since "same inputs, same colour".
- **Card scope**: only `.thumb` posters (shelf walls and strips) feed the reading line. The Continue Watching cards are landscape backdrops, not posters, and do not.
- Settings list wraps to a second row (5 options at the 52rem panel width); Android's list is the same five.

## Screenshots
Dir: `/tmp/claude-1000/-home-andre-Workspace-mediagram/930dbe37-ea74-4134-ac75-bbd8cd255651/scratchpad/ambient/`
- Home: `home-dark.png`, `home-light.png`, `home-phone-dark.png` (390px)
- Department: `dept-dark.png`
- Film: `film-dark.png`
- Show: `show-dark.png`, `show-light.png`, `show-phone-dark.png` (390px)
- Settings: `settings.png`, `settings-artwork.png`

The preview was stopped. Reduced motion was not screenshotted; it follows from theme.css.

## Tests
- `web/test/ambient-colour.test.ts`: 4 posters (vivid, dark, grey, teal beside red) match a single-precision port of the Kotlin run on the same pixels. Pure red is worked by hand ([236,19,19]). An empty poster gives grey. The boot script applies `ambient` to `<html>`.
- `web/test/settings-artwork-ambient.test.ts`: the Ambient radio exists, and choosing it stores `mediagram.backdrop` and fires `mediagram:appearance`.
- `browser-html-player.test.ts`: the pinned script list now includes `/lib/ambient.js`.
- `bun run lint`, `bun run typecheck` and `bun test` (4141 pass) are clean.
- Kotlin itself could not be run (no kotlinc here); expected values come from the independent float32 port.

## Versions
Branch rebased onto main (0.183.2). Bumped to 0.184.0 in `Cargo.toml`, `Cargo.lock` (5 crates), `web/package.json` and `android/app/build.gradle.kts` (versionName), plus a changelog entry.

## Unresolved
- Real TV mini-PC frame rate not measured. The layer is 4 compositor-animated 100vh squares, plus a gradient repaint only during the 900 ms colour ease.
