# Profiles page and access — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Request (2026-10-20):** "i need in the web player a user managment. create profiles, access management." The user skipped the questions ("just start"), so the scope below is the controller's reading, marked as such.

**What exists:** households already have profiles with roles (`web/src/state/profiles-rules.ts`): one admin grown-up, other grown-ups, and kids owned by the grown-up who made them; PINs; a kid's age limit (6/12) and daily limit. All of it is managed in **Manage profiles**, an overlay opened only from the "Who's watching?" picker (`web/public/lib/profile-manage.js`; Android `ManageProfilesScreen.kt`, `TvManageProfiles.kt`). Settings has no place for it, so it reads as missing.

**Goal:**
1. A **Profiles** page in Settings on the web player: everyone in the household, and the same create/remove/PIN/limit actions as Manage profiles, inline. Android gets the same section (Surface Parity).
2. **Access per profile** (phase 03): which library sections a profile may open. Controller's reading of "access management"; built only after the user confirms it (see phase 03).

**Architecture:** no new server rule or API for phases 01–02. The page reuses the household rule, the profile API (`web/public/lib/profile-api.js`) and the manage panel's drawing, lifted out of its overlay so the overlay and the page share one body. Android reuses `ManageProfilesViewModel` and its screens.

**Spec:** none written (the user skipped the questions). This plan's Decisions section is the binding text; rulings made against it are provisional.

## Global Constraints

- **The web is the reference** for wording, layout and rules (`CLAUDE.md` § Surface Parity).
- **The household rule stays as it is:** the admin adds, removes and resets grown-ups; every grown-up manages their own kids and their own PIN; the server refuses anything else (`profiles-rules.ts`, `profile-rules.json`).
- **Managing always needs a grown-up's PIN**, given once per visit and dropped when the visit ends (leaving the tab, the page or the panel).
- **Line limits:** each new file under 200 lines; a ratcheted file that grows gets its ceiling raised with a dated sentence (`web/test/code-standards.test.ts`).
- **Commits:** conventional, with no AI references and no plan labels in code, test names or messages. **No push, no release** without the user's word.
- **UI checks:** use the preview harness (`cd web && bun run preview`), never the real player; drive it with gstack `/browse`.
- **Devices:** always pin a serial (tablet `caad49da`, Pad 5 Pro `d54bc94f`, box `192.168.0.56:5555`); test profiles only.
- **Version:** one bump at the end (phase 04), to the next minor above `main` at that time, in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts` at once, by regex. The cast branch is also aiming at the next minor; whichever merges second takes the one after.
- **Work** on branch `feat/profiles-page`, worktree `../mediagram-profiles`. Run `scripts/check.sh` before the merge.

## Decisions (the controller's, for the user to confirm)

1. **A Profiles tab in Settings**, beside Appearance and Profile, shown only while the chosen profile is a grown-up. A kid manages nothing, so a kid never sees it; nobody chosen sees it neither.
2. **Manage profiles stays on the picker as well.** Its doc says it is "opened from the picker only" so a device left on a grown-up's profile does not hand a child the controls. The tab keeps that promise another way: the tab is hidden for a kid, and every visit still asks "Who are you?" and that grown-up's PIN.
3. **The page lists everyone** before anyone signs in (name, Admin / Grown-up / Kid of *parent* · FSK *n* · daily limit, and whether a PIN is set), so a grown-up can see the household at a glance. Changes still need the PIN.
4. **No rename.** Sync knows a viewer by its normalised name (`profiles-rules.ts` `nameTaken`, `sync-record.ts`), so renaming would split a profile in two across devices. Out of scope.
5. **Android:** a Profiles section in Settings on the phone, tablet and TV, opening the existing Manage profiles screens inline, with the same visibility rule.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Web: Profiles tab in Settings](phase-01-web-profiles-tab.md) | none | pending |
| 02 | [Android: Profiles section in Settings](phase-02-android-profiles-section.md) | 01 | pending |
| 03 | [Access per profile (to confirm)](phase-03-section-access.md) | user's confirmation | blocked |
| 04 | [Docs, version, checks](phase-04-docs-version-checks.md) | 01, 02 (03 if built) | pending |

## Review

(Filled in at close-out.)
