---
title: "Profile roles: admin, parents with PINs, kids with FSK 6 or 12"
description: "An undeletable admin, PIN-locked grown-ups who own kids profiles, and a per-kid age limit, on the web player and Android, synced."
status: pending
priority: P2
effort: 5d
branch: main
tags: [profiles, kids, pin, sync, web, android, core, parity]
created: 2026-09-28
---

# Profile roles, PINs and per-kid age limits

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans. Steps use `- [ ]` checkboxes.

**Goal:** andre is an undeletable admin; grown-ups are PIN-locked and own the kids
profiles they make; each kid sees FSK 6 or FSK 12, set by its parent — the same on
the web player and Android, synced through the channel.

**Architecture:** Roles, parent, limit and PIN hash ride on each profile's entry in
the existing per-device sync record (new optional keys, no format bump). One pure
rule function, one PIN hash, one wrong-PIN wait — written in TS for the web and
ported to Rust for the Android core, held together by shared JSON fixtures.

**Tech stack:** Bun + `bun:sqlite` + `node:crypto` (web); Rust, rusqlite, `sha2`,
uniffi 0.32 (core); Kotlin, Compose, Hilt (Android).

**Spec:** `docs/superpowers/specs/2026-09-28-profile-roles-design.md`
**Contract (names, reasons, wire, API — read first):** [shared-contract.md](shared-contract.md)


## Amendments (2026-10-04)

- **Pre-flight against 0.102.1:** `../261004-1508-open-tasks-sweep/reports/b3-profile-roles-preflight-report.md`.
  Its rulings override this plan's text where they differ: web state schema **v12**
  (`roles-schema.ts`), core **v8**, the named file splits, role stamps bounded by the
  shipped `isStamp`/`MAX_STAMP`, own writes clamped, Back on a reopened chooser stays
  "Stay as I am", subtitle preferences keep syncing promptly.
- **User, 2026-10-04:** start web + core (01–05a) now, ahead of the Android test cleanup;
  a new kids profile starts at **FSK 6** (Open #1); the household-admin PIN stays
  **separate** from the web's Settings admin token. Android Settings › Profile reads
  "Name · Kids · FSK N" like the web (Open #3 closed — the screen exists since 0.90.0).

## Decisions (user, 2026-09-27 — do not reverse silently)

1. Grown-ups get a PIN (4 digits, required); kids profiles open freely. Honest
   ceiling: not a login — devtools/adb get past it (spec §1).
2. Admin-only: create grown-ups, remove grown-ups, reset another's PIN. A parent
   alone manages its own kids. The admin cannot be removed.
3. Hand marks carry an age: "from 6" / "from 12"; existing marks become "from 12".
4. Rules live on each synced profile (approach A); earliest admin claim wins.

## Phases

| # | Phase | Owns | Status |
|---|---|---|---|
| 01 | [Web: schema, sync keys, merge, export/import](phase-01-web-schema-sync-merge.md) | `web/src/state/{schema,sync-record,merge,store,lists-exchange}.ts`, new `profiles.ts`, `roles-{record,merge,exchange}.ts`, fixture, `code-standards.test.ts` | done (pending merge) |
| 02 | [Web: rules, PIN, wait, routes](phase-02-web-rules-pin-routes.md) | new `profiles-manage.ts`, `profiles-routes.ts`, `route-json.ts`; `profiles.ts`, `routes.ts`, `store.ts`, `server.ts`, `http/browser-write.ts`, `src/routes.ts`, fixtures | pending |
| 03 | [Web: picker, PIN prompt, manage panel, filter, marks](phase-03-web-browser-picker-manage-filter.md) | `web/public/**` | pending |
| 04 | [Core: schema, record, merge, exchange](phase-04-core-schema-sync-merge.md) | `crates/mediagram-core/src/state/**` (data half) | done (pending merge) |
| 05 | [Core: rules, PIN, wait, uniffi API](phase-05-core-rules-pin-api.md) | `crates/mediagram-core/src/{state/profiles*,api/state*}` | pending |
| 06 | [Android: model, repository, view models, marks](phase-06-android-data-viewmodels.md) | `android/core/**`, `android/feature/**` | pending |
| 07 | [Android: phone and TV screens](phase-07-android-phone-tv-screens.md) | `android/ui-mobile/**`, `android/ui-tv/**` | pending |
| 08 | [Verify, docs, version](phase-08-verify-docs-version.md) | `docs/**`, manifests | pending |

## Dependencies

01 → 02 → 03 (web). 04 needs 01's fixtures; 05 needs 02's fixtures and 04; 06 needs
05 (regenerated bindings + rebuilt `.so`); 07 needs 06; 08 last. 03 and 04 may run in
parallel once 01–02 land. **02 is pushed together with 03** (02 changes the routes the
current picker calls), and **05 together with 06** (05 changes the Kotlin-facing API). Another session commits to `main`: rebase before each
phase and bump versions by pattern (see phase 08 / each commit step).

## Global constraints

- Line limits: web `web/test/code-standards.test.ts` (200, ratchet — listed files may
  not grow; `store.ts` 800, `routes.ts` 267, `sync-record.ts` 328, `schema.ts` 245,
  `watch-state.js` 502, `app.js` 742, `shelf-view.js` 285); Rust hard 200 per file
  (`crates/mediagram/tests/code_standards.rs`; `state/merge.rs` is at 200).
- No plan/phase references in code, comments, test names or commit messages.
- Every commit bumps the three manifests by pattern (first: minor → next `0.X.0`).
- UI verification: web via `cd web && bun run preview` only; Android on
  `ANDROID_SERIAL=caad49da`, a test profile; rebuild the native `.so` after Rust changes.

## Review focus (inputs no task's happy path covers)

1. A pre-upgrade grown-up with no PIN, entered from the picker → must set a PIN first.
2. A kid whose parent was removed on this device but still exists in another's doc.
3. Two devices claiming admin before syncing → exactly one admin everywhere.
4. An older build's document (`kids: true`, no `kidsAge`) after a parent set FSK 6.
5. Five wrong PINs, then the right one during the wait → still `wait`.

## Open for the user (defaults the plan takes until told otherwise)

1. A new kid starts at **FSK 6** (the stricter) on both surfaces; its parent raises it.
2. Android differs from the web on purpose: "Android" in the picker note, a "Look again"
   button next to "Create the first profile" (a first sync can outlast the picker's
   5 s wait), a new PIN typed in two steps, the TV's Kids choice as a dialog.
3. Android Settings has no "who is watching" line (the web has one) — a pre-existing
   gap, not built here.
4. A limit changed on another device reaches an open web tab on reload or the next
   picker open — follow-up.
5. Until andre claims admin on the web right after release, anyone can claim it.
