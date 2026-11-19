---
title: "The Profiles tab's You section is drawn on the same row columns as the people under Manage profiles"
author: Andre
date: 2026-11-17
branch: main
cost: { minutes: 24, tokens: 492104 }
id: l-988a690c
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - docs/project-changelog.md
  - web/package.json
  - web/public/lib/profile-manage.js
  - web/public/lib/catalog/settings-profiles.js
  - web/public/styles/manage-profiles.css
  - web/public/styles/shell.css
---

## Decided
2026-11-17, after the user called Settings → Profiles "the ugly setting page": the top of the tab (who is watching, Switch profile, Subtitles) had stayed loose stacks of heading, text and pill while the households under it were ruled rows, and that mismatch is what read as ugly. You is now one ruled list on the people rows' columns: initial, name, who they are, and the control where a person's › would be; Subtitles is a row on the same columns. A household's name sits over its Admin · profiles line, ⋯ at the end. "As X" shows only when the one managing is not the one watching.

## Tried and ruled out
- A System and playback status link inside the Profiles tab: it floated mid-page with no heading. The rail's System link already goes there under the same condition (/api/status answers).

## Watch out
- New settings rows go on these columns (36px initial, 10rem name, 1fr line, auto control), not as a heading over a loose control, or the page splits into two looks again.
- "The misaligned indicator" on the loading screen meant the film-leader countdown sitting low-left of an empty page; it is centred on the screen now.

In the code: web/public/lib/catalog/settings-profiles.js (you, subtitlePreference), web/public/styles/manage-profiles.css (.you, .setting-row, .household-row), web/public/lib/profile-manage.js (asNote), web/public/styles/shell.css (.loading-state).
