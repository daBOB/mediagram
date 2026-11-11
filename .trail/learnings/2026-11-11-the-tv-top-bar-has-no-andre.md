---
title: "The TV top bar has no overscan band, because the household's TVs show the whole picture"
author: Andre
date: 2026-11-11
branch: main
cost: { minutes: 6, tokens: 69228 }
id: l-24184c8c
scope: repo
status: active
src: agent
touches:
  - android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt
---

## Decided
- 2026-11-12, after the user said the mediagram top bar was too tall: the bar lost its 27dp top overscan inset. It is now the web's masthead height alone (76 web px = 57dp), where before it was 84dp, about a sixth of the screen with an empty band above the pills.
- The evidence: the rail's icons have always sat about 30dp from the screen edge, well inside the 5% TV-safe margin, and they show in full on the Hisense TV that both the box and funstation use. So the TV does not crop edges.

## Watch out
- Overscan.vertical/horizontal still pads many other TV screens (player controls, profile picker, setup, settings). They are untouched. If one of those looks too boxed in, the same reasoning applies, but ask first: this was a bar-only change.

In the code:
- android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt TvDepartmentsBarHeight, now used alone wherever the bar's height mattered (three bar styles, page padding, TvBarClearance, TvCatalogBlend)
