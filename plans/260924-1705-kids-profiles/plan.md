# Kids Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A profile can be created as a kids profile, which syncs between devices and sees only titles rated FSK 12 or under (plus unrated titles marked for Kids by hand), on the web player and the Android app.

**Architecture:** A `kids` flag on the profile row (web `state.db` v7, Rust core state v3), carried as an optional `"kids": true` per profile in the sync record (format stays 1) and merged as "any device says yes". Each surface filters its catalog once, where it takes it in, with the existing `kidsVerdict` rule; every view derives from the filtered list.

**Tech Stack:** Bun + TypeScript server (`web/src`), plain ES modules (`web/public`), Rust `mediagram-core` (rusqlite, serde, uniffi), Kotlin/Compose Android (`android/`).

**Spec:** `plans/260924-1705-kids-profiles/spec.md` — read it before any task.

## Global Constraints

- Threshold: `KIDS_AGE_LIMIT = 12` — FSK 0/6/12 allowed, 16/18 hidden (existing constant on both surfaces; reuse, never restate).
- Unrated titles are hidden for a kids profile unless in the device-wide hand-marked `kids` list.
- A rated title is judged by its rating alone; a hand mark never overrides it.
- `SYNC_FORMAT` stays `1`. `kids` is written on a profile **only when true**.
- Merge: kids if **any** record marks the profile; sync never turns it off.
- The flag is set at creation only. No endpoint or API changes it afterwards.
- Filter only: no PIN, no server-side enforcement, no cache changes.
- Web `STATE_SCHEMA` 6 → 7; Rust state `VERSION` 2 → 3; both by `ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0`.
- Never start the real web player for UI checks — use a stub harness (`web/public` served with stubbed `/api`).
- Code comments explain the why, never plan/phase references.
- Version 0.43.0 in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`) — bump by regex, confirm `Cargo.lock`.
- Conventional commits, no AI references.

## Review Focus

1. **A profile imported from another device before this device learned it is kids** — an existing ordinary local profile of the same name must become kids on import (web Task 3, Rust Task 4 each pin it).
2. **Switching from a kids profile back to an adult one in a running page** — the full library returns without a reload or catalog fetch (Task 6 pins it; the web `#who` button had no handler before this work).
3. **Search on a kids profile** — server search knows no profile; hits outside the filtered catalog must not appear (Task 6 pins it).
4. **A catalog refresh while a kids profile is active** — the new catalog is filtered too, not shown raw (Task 6 pins it on web; Task 9 on Android via the same combine path).
5. **An old record without `kids` merged with a new one that has it** — result is kids; export of an ordinary profile carries no `kids` key at all (Task 1 fixtures pin both, shared by web and Rust).

## Phases

| Phase | File | Tasks | Status |
|---|---|---|---|
| 1 | [phase-01-sync-record-and-merge.md](phase-01-sync-record-and-merge.md) | 1 web record+merge, 2 Rust record+merge | pending |
| 2 | [phase-02-web-state-and-api.md](phase-02-web-state-and-api.md) | 3 web schema, store, HTTP | pending |
| 3 | [phase-03-core-state-and-bindings.md](phase-03-core-state-and-bindings.md) | 4 Rust schema, profiles, exchange, API, bindings | pending |
| 4 | [phase-04-web-filter-and-ui.md](phase-04-web-filter-and-ui.md) | 5 filter rule, 6 app wiring + profile switch, 7 create form + label | pending |
| 5 | [phase-05-android-filter-and-ui.md](phase-05-android-filter-and-ui.md) | 8 model + repository + rule, 9 catalog filter + dialog + label, 9b player hides Kids mark | pending |
| 6 | [phase-06-docs-version-validation.md](phase-06-docs-version-validation.md) | 10 docs + version, 11 stub + device validation | pending |

Order matters: 1 → 2 and 1 → 3 (records before stores); 2 → 4; 3 → 5; 6 last.
