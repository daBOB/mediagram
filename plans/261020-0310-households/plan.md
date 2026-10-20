---
title: "Households"
description: "One install shared by several households: each device joins one and sees only its profiles; the owner sets households up."
status: pending
priority: P2
effort: 6d
branch: feat/households
tags: [profiles, households, sync, web, android, rust]
created: 2026-10-20
---

# Households — Implementation Plan

> **For agentic workers:** superpowers:subagent-driven-development. Each `### Task N.M` is one brief: phase header + Global Constraints + the task.

**Goal:** Several households share one install. A device joins one household, and its picker, Everyone here and Manage show only that household. The owner creates households, gives each an admin, and moves grown-ups (with their kids) between them.
**Spec (binding):** `docs/superpowers/specs/2026-10-20-households-design.md` (user decisions 1–4, controller calls 1–15).

**Architecture:**
- **One rule, written once:** `web/public/lib/household-rules.js`, browser JS shared with the server as `daily-limit.js` is. It covers who is in which household and who runs it (`householdView`), the owner's actions and joining (`householdAllowed`), and a device's household (`deviceHousehold`), all held to `household-rules.json`. Rust ports all three; Kotlin ports `householdAllowed` and the words.
- **Stored:** a `households` table + `profiles.household_id` (web v16, core v12). Home is id `home`, named "Home", nothing dated. **Owner = today's install-wide admin claim** (earliest wins, already synced).
- **Sync:** top-level `households` (name and admin each stamped, removal sticky) + per-profile `household` (stamped). Newest wins, ties by device. Nothing dated 0 is written, so a one-household install writes what it did before. Format stays 1.
- **Filtered in one place per surface:** the browser's `watch-state.js` `loadProfiles` (household in `localStorage`), and the core's `profiles()`/`chosen_profile()` (household in `state_meta`). Everything downstream follows; only the owner's part reads everyone.
- **Data flow:** owner action → route or core call (owner PIN + rule) → stamped rows → sync export → merge and import on other devices → resolved on read → filtered to each device's household.

## Global Constraints

- **Branch:** `feat/households`, worktree `../mediagram-households`, cut from `main` after `feat/one-profiles-page` and `feat/profiles-page` (phases 03–04) merge. Re-measure every quoted line count there. Never `git stash`.
- **The web is the reference** (`CLAUDE.md` § Surface Parity). Words come only from `household-words.js` / `HouseholdWords.kt`.
- **Line limits:** new files ≤200; ratcheted web files grow only with a dated sentence (`web/test/code-standards.test.ts`); Rust sources ≤200; Kotlin files over 200 must not grow.
- **Commits:** conventional, no AI references, no plan labels in code, tests or messages. No push or release without the user. One minor version bump at merge, by regex in all three manifests.
- **UI checks:** web in the preview harness on a scratch copy of `state.db`, via gstack `/browse`.
- **Devices:** pin the serial (tablet `caad49da`/`d54bc94f`, box `192.168.0.56:5555`); rebuild the `.so` first; test profiles only; navigate only; the user types the owner's PIN.
- **Bindings:** `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`.

## Review Focus

1. **The kids' TV box on first launch** (call 15): a device that chose a profile before joins that profile's household, even with two households and before a sync round lands. Tests: `device` fixture cases 5–6; `a_device_that_chose_before_joins_its_profiles_household`; `household.test.ts`.
2. **Older builds and one-household installs:** an untouched Home writes no new key, and owner = the old claim, so older builds still agree about Home. Tests: `a household nobody changed is not said`; merge case 9; the v16 migration test.
3. **Resolution:** a kid follows its parent; moving a grown-up re-dates their kids; a gone household reads as Home; the owner runs their own household. Tests: `view` fixture cases 2–7; `the owner moves a grown-up and their kids, each dated`.
4. **Joining:** takes the household admin's or the owner's PIN; wrong PINs wait per household, never per profile; nothing is synced. Tests: `wrong join PINs wait per household…`; `join answers 204 and starts no sync round`.
5. **One filter place per surface:** switching away from the chosen profile shows the picker (Android `reopen()`); the owner's part reads everyone. Tests: `switchingAwayFromTheChosenProfileShowsThePicker`; `profiles_lists_only_the_joined_household`.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Household rule, words, shared fixtures](phase-01-shared-rule-words-and-fixtures.md) | — | pending |
| 02 | [Web server: store, sync, management, routes](phase-02-web-server-store-sync-routes.md) | 01 | pending |
| 03 | [Rust core: rule, store, device household, sync, API, bindings](phase-03-rust-core-store-sync-api.md) | 01 | pending |
| 04 | [Web player: join, switch, owner's part](phase-04-web-player-join-switch-owner.md) | 02 | pending |
| 05 | [Android data: model, calls, repository, view models](phase-05-android-data-model-and-view-models.md) | 03 | pending |
| 06 | [Android UI: phone/tablet and TV](phase-06-android-ui-phone-tablet-and-tv.md) | 05, 04 | pending |
| 07 | [Docs, version, checks, review, device walk](phase-07-docs-version-checks-review-walk.md) | all | pending |

**Order:** 02 ∥ 03 (disjoint files), then 04 ∥ 05. In 06, run 6.1→6.2 and 6.3→6.4 in sequence (shared section files); the phone pair can run beside the TV pair.

## Decisions this plan makes where the spec is silent (for the user to confirm)

1. **Its own plan and branch,** from `main` after both in-flight branches merge. Households touch both branches' files, so phases added to either branch would hold back finished work.
2. **The owner role is today's install-wide admin claim,** unchanged on the wire.
3. **Home:** fixed id `home` on every device (each migrates alone), named "Home", nothing dated. A gone or unknown household reads as Home, and Home is never deleted.
4. **The owner always runs the household they are in.** So Change admin isn't offered there (this narrows call 5). It stops a household admin taking the owner role by resetting the owner's PIN, and keeps older builds agreeing about Home.
5. **A kid whose parent is here** is in the parent's household, whatever its own field says.
6. **Deletion is final** (sticky `removed`); a household made again gets a new id.
7. **A household's admin can't be moved** while other grown-ups remain there; change the admin first. The last grown-up may leave, which lets an emptied household be deleted.
8. **Household names are unique** among live households: "A household with that name already exists."
9. **Joining:** one PIN field takes the household admin's PIN or the owner's. It is checked by `POST /api/households/{id}/join` or `join_household`, with one wrong-PIN wait per household.
10. **No separate "leave":** Switch household takes one PIN, from the new household's admin or the owner. It shows to anyone but a kid, only when there are two or more households.
11. **Which household a device is in,** in order: the stored one, then the chosen profile's, then nothing until a sync round is heard, then the only one, else ask. A device whose household was deleted follows its chosen profile.
12. **Android keeps the device's household in the core's `state_meta`,** beside the chosen profile, not in Android preferences: the rule needs both.
13. **The Owner button** shows to anyone but a kid while an owner exists. With the owner chosen, the part shows directly. The PIN is held for the visit.
14. **"Who runs this household?" asks per household:** a grown-up may claim a household that has grown-ups but no admin (only a race or an older build leaves one). With no owner yet, the claim makes the owner, as today.
15. **Nothing else lists profiles:** stats belong to the chosen profile, watch together lists screens, and cast is unchanged (call 10).
16. **Households are listed by normalised name,** in code-point order, the same on web and core (avoids the collator mismatch).

## Review

(Filled in at close-out: checks summary, review findings and fixes, walk report link.)
