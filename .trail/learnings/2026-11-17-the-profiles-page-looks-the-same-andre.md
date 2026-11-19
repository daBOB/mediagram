---
title: "The Profiles page looks the same on web, tablet and TV: Who's managing? tiles, then calm rows that open on a tap"
author: Andre
date: 2026-11-17
branch: feat/owner-profiles-view
id: l-dbf379cb
scope: repo
status: active
src: agent
touches:
  - web/public/lib/profile-manage-who.js
  - docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md
---

## Decided
2026-11-17, the user's pick after calling picker → Manage profiles "STILL crazy ugly !!" and the dense owner view (every control spelled out at once) "chaotic":
- Picker → Manage profiles is one question, *Who's managing?*, drawn with the picker's own avatar tiles (owner first, labelled Owner; an Owner tile at the end on a device showing another household). No Everyone here list, no Who are you? pills, no separate Owner button.
- The owner's view: each household is a heading with *Admin X · N profiles* and its actions behind ⋯ (TV: the household line's OK); one row per person (name, role line, ›) that opens that person's controls on a tap; one *Add someone* per household (Grown-up or Kid, Belongs to only with more than one grown-up, default the admin); *Create a household*; *This device* as one line at the end.
- Anyone else managing gets the same rows for what they may manage; what each person may do is unchanged.

## Tried and ruled out
- The first redesign (2026-11-16): one owner view with every control visible. It fixed the two-halves confusion but looked chaotic; the picker route was left as it was, which is the screen the user actually opened.
- Merging that dense view to main on its own to "show progress": it would have put the page the user had just rejected onto their live player.

## Watch out
- Judge every route by its look, not only the one being built: Settings as the owner, Settings as another grown-up, picker → Manage profiles, a device joined to another household. Screenshot each, dark and light, desktop and phone width, before calling it done.
- Which rows a manager sees and what each opens is decided once in the shared Android state (`ManagePart` rows); the tablet and TV only draw it. Keep it there.

In the code: web `web/public/lib/profile-manage-who.js`, `profile-tiles.js`, `profile-manage-owner.js`, `profile-add-someone.js`, `catalog/household-rows.js`, `catalog/household-menu.js`; Android `feature/catalog/.../profile/ManageHouseholdParts.kt`, tablet `ui-mobile/.../profile/ManageHouseholdPart.kt`, `AddSomeone.kt`, TV `ui-tv/.../profile/TvManagePart.kt`, `TvManageAdds.kt`, `TvProfileTiles.kt`. Spec `docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md`.
