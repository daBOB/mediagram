# TV Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A TV viewer picks mediagram, Apple TV style or Google TV style in Settings › Appearance, and the whole TV redraws in that look and layout.

**Architecture:** A `TvStyle` setting joins `Appearance`; `TvTheme` provides it as `LocalTvStyle` and builds colours and type from it. Every look value flows through seams that already exist (`TvFocus`, `TvType`, the colour scheme); the layout splits only in the chrome, the Home cover and the row cards, each picking by style.

**Tech Stack:** Kotlin, Compose (BOM 2026.06.01), tv-material 1.1.0, Robolectric Compose tests, fontTools for the font cuts.

**Spec:** `plans/261011-0052-tv-themes/spec.md`.

## Global Constraints

- **Worktree:** `../mediagram-themes`, branch `feat/tv-themes` off `feat/tv-parity`. Paths are relative to its `android/` unless they start with `plans/`, `docs/`, `web/` or `scripts/`.
- **mediagram must not move a pixel.** Every existing test passes unchanged except where a task names the edit (reading `TvType.Base` outside composition).
- **No blur on TV.** No new dependencies. Files stay under 200 lines; split rather than grow.
- **Copy is unchanged** in every style: row, tab and button names stay mediagram's.
- **Comments say why, never which plan, phase or task.** Commits: conventional, no AI references, no attribution lines.
- **Tests per task:** the task's own Gradle test tasks, then `./gradlew spotlessApply detekt` before each commit; `scripts/check.sh` before merge.
- **Gradle:** a hook blocks shell commands containing the path segment `build`; Gradle task names are fine, but read reports with tracked paths only.
- **Devices:** box `192.168.0.46:5555` only in phase 08, always with `-s`; check the avatar (T = test, PIN 1357) first; navigate only, except Settings › Appearance › Theme, which is set back to mediagram at the end; never "Start over", never uninstall or `pm clear`.

## Review Focus

1. **Theme switched in Settings, then Back** to a rail-less catalogue whose restore key names a rail row: focus lands on the avatar (Google: the gear for Settings), never a crash. Tests in 5.3.
2. **An empty My List in a rail-less style** asks its tab for focus: the avatar takes it. Test in 5.1.
3. **The Apple tab bar on a narrow screen** (640dp): the pills scroll inside the pill, the end fades, every pill and both end controls stay reachable. Test in 5.2.
4. **A row title with neither backdrop nor poster** in a landscape style: initials on a 16:9 plate. Test in 7.1.
5. **A rail item chosen from the avatar menu before the library is ready:** it waits, then opens once. Test in 5.1.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Setting and look](phase-01-setting-and-look.md) | none | done |
| 02 | [Colours and fonts reach every TV screen](phase-02-colours-and-fonts.md) | 01 | done |
| 03 | [Focus per style](phase-03-focus-per-style.md) | 01 | done |
| 04 | [Settings › Appearance › Theme](phase-04-settings-theme-row.md) | 01, 03 | done |
| 05 | [Chrome without a rail](phase-05-chrome-without-a-rail.md) | 01, 03 | done |
| 06 | [Home cover per style](phase-06-home-cover-per-style.md) | 02, 03 | done |
| 07 | [Landscape rows](phase-07-landscape-rows.md) | 01, 03 | done |
| 08 | [Docs, version, box walk and frame times](phase-08-docs-version-and-box.md) | all | done |

## Rulings made while planning

- **Fonts through `TvType` getters.** `FontFamily.Resolver` is sealed (a probe implementing it failed to compile: "Extending sealed classes or interfaces from a different module is prohibited"), so no root-level font swap exists. `TvType`'s roles become composable getters over `TvType.Base`; 16 top-level styles in ui-tv become getters.
- **Colours through the scheme.** 65 direct `Palette.*` reads in ui-tv would keep mediagram's ink under every style; each maps one-to-one onto a scheme slot `tvColorScheme` already fills, so mediagram's values are unchanged.
- **Spec corrections** (marked *(planning)* in the spec): Apple's cover is today's slide with its own title rule; Home strips keep their own cards and share a row helper; landscape floor is 1.5× the portrait floor; shows gain a backdrop; neutral greys per style; Google Sans Flex gets a 600 cut because `TvType` asks for 600.
- **Rail-less focus:** one `TvChromeFocus.railTarget(item)` replaces every direct rail-row request; rail-less pages inset by `Overscan.horizontal` on both sides.

## Review

- **Shipped:** Settings › Appearance › Theme (mediagram, Apple TV style, Google TV style) on TV. Each style was walked on the box, and the box ends on mediagram with 0.122.0 installed, so the next channel release must be ≥ 0.122.1.
- **Measured** ([report](reports/box-walk-and-frame-times-261011-report.md)): mediagram matches 0.120.5. Apple tripped the shadow rule, so the shadow was dropped. Apple without it is +2 or less on Movies and Series but still +5–6 ms p90 on Home. Google is level with or below mediagram everywhere.
- **The walk found four branch defects, all fixed:** Up lost under the floating bar, swatches hiding their preview, the shadow, and Settings' tones. It found three issues mediagram already had (the cover scroll on Right, Series' double inset, a row's first card clipped), which are left for a separate round.
- **Open for the user:** Apple's Home cost, R16 (cream-at-rest Watch now / Play beside a white focused pill), and the lighter sidebar under Apple and Google.
