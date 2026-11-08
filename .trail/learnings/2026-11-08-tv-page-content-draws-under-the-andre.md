---
title: "TV page content draws under the opaque top bar, so its first rows are hidden"
author: Andre
date: 2026-11-08
branch: main
id: l-836f8164
scope: repo
status: active
src: agent
---

## Decided
- On the TV, `TvLibraryChrome` draws the page full-screen and the opaque bar over its top `TvDepartmentsBarHeight + Overscan.vertical` (84 dp on a 960×540dp screen). A page's own first stop pads by `LocalTvPagePadding.top`; anything placed above that in the page's column sits behind the bar.
- The fetch line, its progress rule and the refresh notice in `TvCatalogScreen` were exactly that from 0.147.0 to 0.162.x: the TV never showed the fetch progress, though tests passed. Since 0.163.0 they are an overlay (`TvCatalogStatus`) starting at `gutter.top`.

## Tried and ruled out
- Padding the status rows by `gutter.top` inside the column: the body pads `gutter.top` again, so the shelves would jump down while a fetch runs.

## Watch out
- `assertExists()` passes for text hidden behind the bar. For anything that must be seen on TV, assert `getBoundsInRoot().top >= TvDepartmentsBarHeight + Overscan.vertical` (`TvCatalogScreenStateTest.theFetchLineAndItsBarSitBelowTheTopBar`).
- The user reported "the fetching progress bar is still missing" four times before the cause was measured; check placement on the device's real layout before assuming a fix shows.
