# TV themes — box walk and frame times (2026-10-11)

Box `192.168.0.46:5555` (Hisense / Google TV, Android 14, UI 1920×1080 at 320 dpi), TV on, profile **andre** (nothing in the walk plays; no OK on a plate). The only setting changed was Settings › Appearance › Theme, and it ends on **mediagram**. Measured 19:45–22:15.

## Builds
| build | what | versionCode | install |
|---|---|---|---|
| base | sideloaded 0.120.5 already on the box | 120005 | `speed-profile` / `install-dm` |
| 0.122.0 | `feat/tv-themes` 487175c2 | 122000 | `install-multiple -r` apk + API31 `.dm`, `speed-profile` / `install-dm`, cert 5840181d…f576 |
| 0.122.0 fixed | 85a3a8b2 (box-walk fixes below; d7fbcda0 later is comment-only) | 122000 | same; box `base.apk` sha256 = local apk (36ba0b40…) |

The box keeps the fixed 0.122.0. **The next channel release must be ≥ 0.122.1.** The copy of 0.120.5 (apk + dm) pulled before the install is in the session scratchpad (`themes-frames-261011/base-0.120.5.*`).

## Protocol
The frost report's walks, with Home scroll added: W1 Home at rest (20 s on the Home pill, cover turning), W1b Home scroll (8 down / 8 up from the Home pill), W2 Movies (12 down / 12 up), W3 Series hero grid (6 down / 6 up). Each round starts with a force-stop and a cold launch, waits until the library has loaded, and checks focus with `uiautomator` before every OK. One warm round is discarded, then three runs; the medians are compared. Unit: ms. "janky" means `Janky frames` %, "legacy" means `Janky frames (legacy)` %.

Two things changed the script partway through, so the numbers come in **two sets, compared only within a set**:
- **Set A** (0.122.0, first build): rounds reached the Home pill with Up Up. base, mediagram and Apple.
- **Set B** (0.122.0 fixed): rounds reach the Home pill with Back, after the launcher once took the front just after a force-stop (no crash; a home intent hit the launcher). Back leaves Home's walk at about 210 frames instead of 163 in every style, so W1 does not compare across sets. mediagram, Apple and Google.
- A first Google run on the unfixed build is superseded. Up stuck under the bar made its W2 a different walk. The raw dumps are kept (`google-*`).

## Verdict
- **mediagram does not regress.** On 0.122.0 it matches 0.120.5 on every walk (Set A, p90 medians 42 / 48 / 34 / 32 against the base's 42 / 48 / 40 / 34).
- **The shadow rule tripped.** In Set A, Apple was more than 2 ms above mediagram on W1b (+9), W2 (+4) and W3 (+8). The shadow was dropped (`perf(tv): Apple TV style's lift without its shadow`), the build was rebuilt, reinstalled and measured again.
- **Apple without the shadow (Set B, against mediagram):** W1 +6, W1b +5, W2 +2, W3 −2. W3 is fixed, and W2 is within the rule. Home is still 5–6 ms over. The rule's one remedy is used up, so this is reported, not acted on (see Unresolved questions).
- **Google is level with or below mediagram on every walk** (Set B): W1 28 against 42, W1b 42 against 48, W2 32 against 36, W3 34 against 34.

## Set A — 0.122.0, rounds start with Up

**W1 Home at rest**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| base | 1 | 163 | 5.52 | 98.16 | 36 | 40 | 57 | 10 | 14 |
| base | 2 | 165 | 6.06 | 98.79 | 34 | 44 | 65 | 10 | 14 |
| base | 3 | 163 | 5.52 | 98.77 | 34 | 42 | 65 | 10 | 14 |
| base | **median** | 163 | 5.52 | 98.77 | 34 | 42 | 65 | 10 | 14 |
| mediagram | 1 | 163 | 5.52 | 98.77 | 34 | 42 | 57 | 10 | 14 |
| mediagram | 2 | 162 | 5.56 | 99.38 | 34 | 42 | 61 | 10 | 13 |
| mediagram | 3 | 163 | 5.52 | 98.77 | 36 | 42 | 61 | 10 | 14 |
| mediagram | **median** | 163 | 5.52 | 98.77 | 34 | 42 | 61 | 10 | 14 |
| apple | 1 | 162 | 5.56 | 99.38 | 34 | 42 | 69 | 10 | 15 |
| apple | 2 | 162 | 5.56 | 100 | 34 | 42 | 69 | 10 | 14 |
| apple | 3 | 162 | 5.56 | 99.38 | 34 | 42 | 61 | 10 | 12 |
| apple | **median** | 162 | 5.56 | 99.38 | 34 | 42 | 69 | 10 | 14 |

**W1b Home scroll**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| base | 1 | 490 | 5.31 | 96.94 | 34 | 48 | 73 | 10 | 23 |
| base | 2 | 478 | 5.65 | 51.26 | 24 | 48 | 81 | 10 | 23 |
| base | 3 | 484 | 4.96 | 92.56 | 27 | 48 | 81 | 10 | 23 |
| base | **median** | 484 | 5.31 | 92.56 | 27 | 48 | 81 | 10 | 23 |
| mediagram | 1 | 486 | 4.73 | 92.39 | 32 | 48 | 69 | 10 | 22 |
| mediagram | 2 | 492 | 4.88 | 90.85 | 31 | 48 | 73 | 10 | 22 |
| mediagram | 3 | 484 | 4.75 | 91.53 | 26 | 48 | 77 | 10 | 23 |
| mediagram | **median** | 486 | 4.75 | 91.53 | 31 | 48 | 73 | 10 | 22 |
| apple | 1 | 471 | 7.22 | 97.24 | 32 | 53 | 97 | 10 | 25 |
| apple | 2 | 471 | 7.01 | 96.82 | 32 | 57 | 85 | 10 | 25 |
| apple | 3 | 470 | 7.87 | 91.28 | 26 | 57 | 85 | 10 | 25 |
| apple | **median** | 471 | 7.22 | 96.82 | 32 | 57 | 85 | 10 | 25 |

**W2 Movies rows**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| base | 1 | 296 | 3.38 | 74.32 | 24 | 40 | 57 | 7 | 14 |
| base | 2 | 273 | 4.76 | 70.33 | 24 | 40 | 77 | 7 | 13 |
| base | 3 | 296 | 4.39 | 67.23 | 24 | 40 | 85 | 7 | 13 |
| base | **median** | 296 | 4.39 | 70.33 | 24 | 40 | 77 | 7 | 13 |
| mediagram | 1 | 293 | 3.75 | 63.82 | 23 | 34 | 53 | 7 | 12 |
| mediagram | 2 | 300 | 3.67 | 51 | 23 | 40 | 65 | 7 | 13 |
| mediagram | 3 | 291 | 4.12 | 54.98 | 23 | 32 | 65 | 7 | 13 |
| mediagram | **median** | 293 | 3.75 | 54.98 | 23 | 34 | 65 | 7 | 13 |
| apple | 1 | 297 | 3.03 | 28.96 | 23 | 36 | 57 | 10 | 13 |
| apple | 2 | 298 | 4.03 | 66.44 | 25 | 38 | 57 | 10 | 14 |
| apple | 3 | 297 | 3.7 | 51.52 | 24 | 38 | 77 | 9 | 13 |
| apple | **median** | 297 | 3.7 | 51.52 | 24 | 38 | 57 | 10 | 13 |

**W3 Series hero grid**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| base | 1 | 352 | 2.27 | 83.24 | 24 | 38 | 53 | 7 | 12 |
| base | 2 | 352 | 1.99 | 87.78 | 24 | 34 | 65 | 7 | 13 |
| base | 3 | 354 | 1.69 | 82.77 | 24 | 32 | 53 | 7 | 11 |
| base | **median** | 352 | 1.99 | 83.24 | 24 | 34 | 53 | 7 | 12 |
| mediagram | 1 | 351 | 1.71 | 88.32 | 24 | 32 | 53 | 7 | 12 |
| mediagram | 2 | 353 | 1.7 | 88.39 | 24 | 32 | 57 | 7 | 12 |
| mediagram | 3 | 357 | 2.24 | 79.83 | 24 | 32 | 48 | 7 | 11 |
| mediagram | **median** | 353 | 1.71 | 88.32 | 24 | 32 | 53 | 7 | 12 |
| apple | 1 | 352 | 1.7 | 86.93 | 27 | 40 | 53 | 10 | 13 |
| apple | 2 | 354 | 1.41 | 94.35 | 28 | 40 | 53 | 11 | 14 |
| apple | 3 | 351 | 1.99 | 82.05 | 26 | 34 | 57 | 10 | 14 |
| apple | **median** | 352 | 1.7 | 86.93 | 27 | 40 | 53 | 10 | 14 |

## Set B — 0.122.0 fixed, rounds start with Back (Apple without the shadow)

**W1 Home at rest**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| mediagram | 1 | 216 | 5.56 | 100 | 34 | 44 | 69 | 10 | 14 |
| mediagram | 2 | 231 | 5.19 | 99.57 | 34 | 42 | 61 | 10 | 14 |
| mediagram | 3 | 209 | 6.22 | 98.56 | 36 | 42 | 61 | 10 | 22 |
| mediagram | **median** | 216 | 5.56 | 99.57 | 34 | 42 | 61 | 10 | 14 |
| apple | 1 | 218 | 5.96 | 99.08 | 34 | 48 | 73 | 10 | 14 |
| apple | 2 | 204 | 6.37 | 100 | 34 | 46 | 69 | 10 | 23 |
| apple | 3 | 202 | 5.94 | 99.5 | 34 | 48 | 65 | 10 | 23 |
| apple | **median** | 204 | 5.96 | 99.5 | 34 | 48 | 69 | 10 | 23 |
| google | 1 | 207 | 3.86 | 67.15 | 25 | 27 | 38 | 10 | 13 |
| google | 2 | 205 | 4.39 | 80 | 25 | 28 | 40 | 10 | 14 |
| google | 3 | 206 | 3.88 | 75.73 | 25 | 28 | 38 | 10 | 13 |
| google | **median** | 206 | 3.88 | 75.73 | 25 | 28 | 38 | 10 | 13 |

**W1b Home scroll**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| mediagram | 1 | 476 | 3.99 | 86.76 | 28 | 48 | 73 | 10 | 24 |
| mediagram | 2 | 466 | 4.08 | 90.13 | 28 | 48 | 69 | 12 | 23 |
| mediagram | 3 | 483 | 3.73 | 98.14 | 32 | 48 | 73 | 10 | 21 |
| mediagram | **median** | 476 | 3.99 | 90.13 | 28 | 48 | 73 | 10 | 23 |
| apple | 1 | 472 | 6.14 | 85.81 | 25 | 53 | 73 | 10 | 24 |
| apple | 2 | 462 | 6.28 | 90.91 | 26 | 53 | 89 | 10 | 24 |
| apple | 3 | 473 | 5.71 | 97.25 | 29 | 53 | 73 | 10 | 24 |
| apple | **median** | 472 | 6.14 | 90.91 | 26 | 53 | 73 | 10 | 24 |
| google | 1 | 474 | 1.48 | 84.81 | 25 | 42 | 65 | 10 | 17 |
| google | 2 | 477 | 1.47 | 96.23 | 26 | 42 | 65 | 10 | 17 |
| google | 3 | 489 | 1.23 | 98.77 | 32 | 42 | 61 | 10 | 17 |
| google | **median** | 477 | 1.47 | 96.23 | 26 | 42 | 65 | 10 | 17 |

**W2 Movies rows**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| mediagram | 1 | 305 | 4.59 | 65.9 | 23 | 34 | 65 | 7 | 14 |
| mediagram | 2 | 298 | 4.03 | 68.79 | 24 | 40 | 69 | 7 | 13 |
| mediagram | 3 | 291 | 5.15 | 67.35 | 24 | 36 | 53 | 7 | 12 |
| mediagram | **median** | 298 | 4.59 | 67.35 | 24 | 36 | 65 | 7 | 13 |
| apple | 1 | 294 | 4.08 | 57.14 | 24 | 40 | 65 | 9 | 15 |
| apple | 2 | 296 | 3.04 | 58.45 | 24 | 38 | 57 | 9 | 13 |
| apple | 3 | 298 | 3.69 | 21.14 | 20 | 36 | 61 | 9 | 13 |
| apple | **median** | 296 | 3.69 | 57.14 | 24 | 38 | 61 | 9 | 13 |
| google | 1 | 295 | 3.73 | 63.73 | 25 | 32 | 57 | 8 | 13 |
| google | 2 | 295 | 3.05 | 53.9 | 24 | 32 | 48 | 9 | 14 |
| google | 3 | 296 | 2.7 | 72.64 | 25 | 34 | 48 | 8 | 13 |
| google | **median** | 295 | 3.05 | 63.73 | 25 | 32 | 48 | 8 | 13 |

**W3 Series hero grid**

| build | run | frames | janky | legacy | p50 | p90 | p99 | g50 | g90 |
|---|---|---|---|---|---|---|---|---|---|
| mediagram | 1 | 353 | 1.7 | 88.39 | 24 | 34 | 48 | 7 | 11 |
| mediagram | 2 | 355 | 2.25 | 87.89 | 24 | 38 | 57 | 7 | 11 |
| mediagram | 3 | 351 | 1.71 | 88.89 | 24 | 34 | 48 | 7 | 11 |
| mediagram | **median** | 353 | 1.71 | 88.39 | 24 | 34 | 48 | 7 | 11 |
| apple | 1 | 354 | 1.98 | 89.83 | 26 | 34 | 48 | 9 | 13 |
| apple | 2 | 353 | 1.42 | 90.37 | 26 | 32 | 53 | 9 | 13 |
| apple | 3 | 354 | 1.98 | 75.42 | 25 | 32 | 53 | 9 | 13 |
| apple | **median** | 354 | 1.98 | 89.83 | 26 | 32 | 53 | 9 | 13 |
| google | 1 | 356 | 1.97 | 80.34 | 26 | 34 | 53 | 9 | 13 |
| google | 2 | 358 | 1.96 | 90.5 | 26 | 34 | 57 | 9 | 13 |
| google | 3 | 354 | 1.69 | 89.27 | 26 | 32 | 53 | 9 | 13 |
| google | **median** | 356 | 1.96 | 89.27 | 26 | 34 | 53 | 9 | 13 |

## Walk: what read wrong, per style
Every screenshot is in the session scratchpad (`themes-frames-261011/theme-*.png`). They are not committed, because Settings shows the Telegram account and its sessions.

**Fixed on this branch** (one fix dispatch, reviewed and approved; commits 09e36073, 6523ab90, 868756a8, 85a3a8b2; each checked again on the box):
1. **Up was lost under the floating bar** (Apple and Google). Movies › Featured scrolled up under the bar: the card's top was at y 54, above the pills' top at 78. 2-D search then found no target, so the chrome's `onExit` never ran, and only Back escaped. Now an Up with nothing above the card goes to the selected pill. On the box: Up from Featured reaches Movies.
2. **A focused swatch hid what it previewed** (all three styles, mediagram included). tv-material's default focused fill (`inverseSurface`, the text colour) blanked a theme's "Aa" and an Artwork mode's colour. Swatches now keep their own colour; on the box the focused swatch shows "Aa" inside its ring.
3. **The shadow was dropped** (the rule above).
4. **Settings' sidebar stayed mediagram's #09090A** under every style, because `LocalCatalogueTones` was never provided on TV. Each style's tones now apply. Under Apple the sidebar is #1C1C1E and under Google #303134, both a step *lighter* than the page, where mediagram's sits a step darker. It looks like a tvOS/Google TV side panel; judge it on the box.

**Checked and fine:**
- **Live re-theme.** Choosing a swatch redraws Settings in place.
- **Down from Artwork lands on Theme**, on its first swatch.
- **Back after a theme switch** lands on the avatar (Apple), Google's gear (Google) and the rail's Settings row (mediagram). Never lost, never a crash.
- **The avatar menu** lists the rail's seven items, and My List opens from it.
- **The Apple tab bar and Google top bar** reach every pill and the end controls.
- **A long-titled film on Google's inset cover** ("Event Horizon - Am Rande des Universums") wraps to two lines cleanly.
- **Walls stay portrait. The player was not opened.**

**Already the same on mediagram (0.120.5's behaviour, not this branch's), not fixed here:**
- **Right from Watch now to "+ My List" scrolls Home** about 300–345 px up under the bar, in every style.
- **The Series page insets its header twice.** `TvWall`'s `contentPadding` plus the header's own padding put it at 192/290 px rail-less, and the hero art stops short of the right edge in a hard box.
- **A row's first focused card is clipped at the row's left edge.** It grows about its centre past an unpadded `LazyRow`. Google's white ring shows it most, but mediagram's accent border is cut too, by more (9.6 dp against 6 dp). A fix would touch every row and change mediagram.

**Open (design, the user's call):**
- **R16 (Apple and Google).** Watch now and Play rest as a cream pill. When focus sits on the white "+ My List" beside it, two light pills sit side by side. Focus shows only as white-against-cream plus the grow. Screenshots: `theme-apple-home-watchnow-rest.png`, `theme-google-home-watchnow-rest.png`.
- **Minor.** On landscape cards a two-line title makes that card taller than its neighbours, so a row's bottom edge is ragged (`theme-apple-movies.png`).

## Unresolved questions
- **Apple's Home is still 5–6 ms over mediagram's p90** without the shadow (W1 48 against 42, GPU p90 23 against 14 in two of three runs; W1b 53 against 48). Accept it, or look further (the cover's title cut, the rounded clips)?
- **R16:** keep the cream-at-rest Watch now / Play, or give focus more (off-white rest plus a ring, or a darker rest fill)?
- **The pre-existing mediagram issues** (cover scroll on Right, Series' double inset, a row's first card clipped): fix them in a separate round?
- **Apple/Google's sidebar a step lighter than the page:** keep it?
