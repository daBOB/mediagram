---
title: 2026-10-22 user chose web parity for the TV frame
author: Andre
date: 2026-11-08
id: l-db22f836
scope: repo
status: active
touches:
  - CLAUDE.md
---

## Decided
2026-10-22: the user reported "sometimes we have a sidepanel, the gaps of the rows in home, or series page is different". Asked rail + bar everywhere / rail only (tablet) / no rail anywhere; the user picked **rail + bar everywhere** (the web's shell: `.library-rail` + `.masthead` frame every page).

Before: only TvCatalogScreen drew TvLibraryChrome; every pushed frame (title, series, person, genre, Latest, search…) drew neither, so content jumped from 91.5dp to 48dp in and 84dp to 27dp down.

**Why:** web is the reference (CLAUDE.md Surface Parity); do not drop the rail from pushed pages again or offer "no rail" as a simplification.

## Watch out
pages opened from the catalogue sit in the same chrome and read LocalTvPagePadding, never Overscan directly; Settings/System menu screens and the player stay outside it (the tablet's documented exception). Related: tv-ui-work-means-parity-not-redesign.

_From Claude Code's memory of this repo, last updated Oct 22._
