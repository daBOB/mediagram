# Phase 03 — Film pages: Preload button, progress bar, server line

## Context links

- Phone/tablet: `android/ui-mobile/src/main/kotlin/ui/catalog/TitlePills.kt:39-80`
  (Play / Resume, My List, ⋯), `TitleDetailScreen.kt:59-122` (calls `TitlePills`
  at 102-110), wiring `ui/LibraryFlowBranches.kt:138-163`.
- TV: `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvTitlePage.kt:72-76, 185-190`.
- Engine (phase 02) and `LanChunkClient.setStatus` (phase 01).
- Settings' Storage section (the "raise the cache budget" link target).

## Overview

P2 · pending. Films only (`Kind.MOVIE`, and `DOCUMENTARY` if the home plan has added
it by then). A Preload control beside Play on both film pages, driven by a small
Hilt ViewModel keyed by set id.

## Requirements

- States → control (phone/tablet: pill beside Play in `TitlePills`' style; TV: a
  focusable plate in the Play row, TvFocus look):
  - Idle, nothing held: "Preload · 5.8 GB"; partly held: "Preload · 36% held".
  - Queued: "Queued" (tap cancels). Running: "Preloading" + a thin full-width bar
    under the button row with "2.1 of 5.8 GB · 36%" (tap/OK cancels, with the label
    saying so for accessibility). Paused (playing / metered): bar stays, label
    "Paused while playing" / "Waiting for Wi-Fi".
  - Done: "Preloaded ✓"; the ⋯ menu (phone) / a second plate (TV) offers "Remove
    preload".
  - NeedsSpace: "Needs 5.8 GB" + "Raise the cache budget" → opens Settings › Storage.
  - Failed: short reason + retry.
- Home server line (quiet, under the bar or under the buttons when idle): "Home server:
  5.8 of 5.8 GB" — only when the LAN cache is on, a server is paired, and the new route
  answers with `bytesHeld > 0`. The "of y GB" is the film's own `totalBytes` from the
  catalogue, never the server's `total` (which can be null while chunks are held). Poll every 5 s while the page is visible and the film
  is Running; once on open otherwise. Hidden on 404/null.
- Resume/Play keep working unchanged; pressing Play while this film preloads pauses the
  preload (engine rule).
- Kids profiles: same as Play — no extra gate.
- Comment beside the control: Android-only by decision; the web player has no film
  preload.

## Related code files

- Create: `feature/player/.../TitlePreloadViewModel.kt` (or feature/catalog — wherever
  the engine is reachable without a ui-module cycle), `ui-mobile/.../TitlePreload.kt`,
  `ui-tv/.../TvTitlePreload.kt`, tests.
- Modify: `TitlePills.kt`, `TitleDetailScreen.kt`, `TvTitlePage.kt`, wiring in
  `LibraryFlowBranches.kt` / TV equivalent. Keep files under ~200 lines.

## Steps

1. ViewModel (state from engine + server line) + unit test with fakes.
2. Phone/tablet control + Robolectric tests (each state's label; cancel; remove; needs
   space link) at default width and `w1164dp-h777dp`.
3. TV control + Robolectric test (focus lands, OK toggles).
4. All module tests green. Minor bump, changelog. No commit.

## Todo

- [ ] ViewModel + test
- [ ] phone/tablet control + tests
- [ ] TV control + test
- [ ] server line (hidden on old server)
- [ ] tests green, version, changelog

## Success criteria

On the tablet and the TV box: tap Preload on a film → bar climbs; leave the app and
come back → still climbing; play something → "Paused while playing"; finish →
"Preloaded ✓", the film plays from the device with no Telegram reads; the server line
reaches the film's size.

## Risks

- Film page on phone is already dense: the bar must not push Play below the fold.
- Server polling must stop when the page leaves composition.
