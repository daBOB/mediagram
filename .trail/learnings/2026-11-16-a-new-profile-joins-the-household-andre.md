---
title: "A new profile joins the household of whoever creates it, so the owner adds one elsewhere by creating it at home and moving it"
author: Andre
date: 2026-11-16
branch: main
cost: { minutes: 1, tokens: 116321 }
id: l-3265173b
scope: repo
status: active
src: agent
touches:
  - web/src/state/profiles-manage.ts
  - web/public/lib/household-api.js
---

## Decided
There is no "create in household X" field. A new grown-up or kid lands in its creator's household. To put a profile in another household:
- grown-up: that household's admin adds it from their own profile (needs that admin's PIN), or the owner adds it (lands in Home) and then uses Households -> <name> -> "Move a grown-up here".
- kid: a grown-up already in the target household adds it; the kid belongs to whoever adds it.
Admins are no longer only andre: on 2026-11-16 Madani's admin was Celine and Born's was Nadin. Read the live list before telling the user whose PIN is needed.

## Watch out
A device's picker shows only the household it joined, so the user must switch to a device joined to the target household to act as its admin.

In the code:
- web/src/state/profiles-manage.ts householdOf(actorId): a new profile gets its maker's household, Home stays undated
- createGrownUp checks "create-grown-up"; createKid sets parentId = actor
- web/public/lib/household-api.js move() posts /api/households/:id/move
- live state: curl -s localhost:8770/api/profiles (profiles + households with adminId)
