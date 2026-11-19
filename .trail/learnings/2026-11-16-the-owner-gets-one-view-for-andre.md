---
title: "The owner gets one view for every household on the Profiles page, not two halves behind two PINs"
author: Andre
date: 2026-11-16
branch: main
cost: { minutes: 15, tokens: 142987 }
id: l-385aa6a6
scope: repo
status: active
src: agent
touches:
  - plans/261116-2016-owner-profiles-view/phase-01-shared-rule-add-anywhere.md
  - plans/261116-2016-owner-profiles-view/phase-02-web-server-create-in-household.md
  - plans/261116-2016-owner-profiles-view/phase-03-rust-core-and-android-calls.md
  - plans/261116-2016-owner-profiles-view/phase-04-web-owner-view.md
  - plans/261116-2016-owner-profiles-view/phase-05-android-owner-view-state.md
  - plans/261116-2016-owner-profiles-view/phase-06-tablet-owner-view.md
  - plans/261116-2016-owner-profiles-view/phase-07-tv-owner-view.md
  - plans/261116-2016-owner-profiles-view/phase-08-verify-version-merge.md
  - plans/261116-2016-owner-profiles-view/plan.md
  - docs/superpowers/specs/2026-11-16-owner-profiles-view-design.md
---

## Decided
2026-11-16, user's pick after finding the page "crazy confusing" (it took four screenshots and an API call to add one person to household Madani):
- When the owner manages, the Profiles page is one view: each household with its actions, its people with their controls, Add a grown-up / Add a kid in every household, Create a household at the end. One owner PIN per visit, asked at the first change.
- The owner may add a grown-up straight into any household and a kid for any grown-up. A kid always lives where its grown-up is, so a kid add names a parent, never a household.
- Non-owner pages (household admins, plain grown-ups, kids) stay as they were.
- Web is built first; tablet and TV follow its structure.

## Tried and ruled out
- A separate owner page: brings back the multiple profile pages the user rejected on 2026-10-20.
- Patch only (share the PIN between the two halves, add a person from the household row): keeps the duplicate lists the user called confusing.

## Watch out
- On a device switched to another household the owner is not in its picker, so the Owner button stays there as the way into the owner view.
- Add a kid is hidden in a household with no grown-up.

In the code:
- spec docs/superpowers/specs/2026-11-16-owner-profiles-view-design.md, plan plans/261116-2016-owner-profiles-view/
- rule held to web/test/fixtures/watch-state/profile-rules.json in profiles-rules.ts, rules.rs, ProfileRoles.kt
