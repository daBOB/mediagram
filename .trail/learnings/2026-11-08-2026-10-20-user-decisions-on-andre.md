---
title: "2026-10-20 user decisions on profile management: one Profiles page, households (owner…"
author: Andre
date: 2026-11-08
id: l-5a3aad0c
scope: repo
status: active
touches:
  - docs/superpowers/specs/2026-10-20-households-design.md
  - ".superpowers/sdd/plan/progress.md"
  - docs/superpowers/specs/2026-10-20-profile-rename-design.md
---

## Decided
User decisions 2026-10-20 (do not reverse silently):
- **Profile management lives on ONE Settings → Profiles page** (You / Everyone here / Manage profiles); the picker only chooses, its "Manage profiles" opens that same page. The user rejected four profile places ("why do i have 4 different profile setting pages") and a separate "Add a profile" button ("no"). The interim "My profile + Household" rename was dropped unmerged.
- **Hidden sections per profile** ("yes, section access per profile"): movies/series/anime/documentaries/tutorials; set by the admin for grown-ups, a kid's owner for kids; synced; soft rule.
- **Households** ("before we can create a profile we must create household and then can assign a profile. only the profile in a household can see other profiles from this household"): the owner (andre) alone creates households, gives each its admin, assigns profiles; each device joins one household with that household's admin PIN and its picker shows only it; soft rule (not real separation); the 4 existing profiles become household "Home", andre admin. Spec `docs/superpowers/specs/2026-10-20-households-design.md` (main 055d616e), approved "Build it".
…

## Watch out
keep everything profile-related on the one Profiles page on every surface (web is the reference); creating a grown-up needs the household admin (andre) and PIN — tell the user where, it was missed once. Related: player-aesthetic-is-a-user-decision, pin-android-installs-to-a-serial.

Households plan written and committed on main 3830575c (`plans/261020-0310-households/`, 7 phases); runs on its own branch `feat/households` cut from main after feat/one-profiles-page and feat/profiles-page phase 03 merge. Plan calls: owner = today's install-wide admin claim; Home has fixed id `home`; the owner alw
…

_From Claude Code's memory of this repo, last updated Oct 20._
