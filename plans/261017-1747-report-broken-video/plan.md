# Report a broken video — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A *Report* button in the player on web, phone, tablet and TV sends a broken-video report as a Telegram reminder to the account's Saved Messages.

**Architecture:** One text rule (web JS, the reference; Kotlin port) held to shared fixtures. Each surface sends through its own Telegram connection: the web server (`POST /api/report` → teleproto `sendMessage("me", {schedule})`), Android through a new Rust core call `report_send` (grammers `schedule_date`). No sync, no schema, no stored data.

**Tech Stack:** Bun + TypeScript server, plain-JS browser modules, teleproto; Rust core (grammers 0.10, uniffi); Kotlin/Compose (phone + TV).

**Spec:** `docs/superpowers/specs/2026-10-17-report-broken-video-design.md`

## Global Constraints

- Reminder scheduled **60 s** ahead, **not silent**, plain text (no Markdown parse), to the account itself.
- Reasons and words, exact: *Won't play*, *Picture or sound wrong*, *Subtitles wrong*, *Wrong title or poster*; button *Report* → *Reported*; *Couldn't send. Try again later.*
- Note: one line, whitespace collapsed, at most **200** characters.
- Everyone may report, kids included. No queue, no in-app list, no title-page entry.
- Screen labels: web *Browser*; Android *TV* / *Tablet* / *Phone* (`labelOf` in `PlayerViewModelTogether.kt`).
- Version **0.126.0** in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (bump by regex, all three).
- Commit messages: conventional, no AI references. No push, no release.
- Never start the real player for UI checks (preview harness). Device walks send real reports: ask first.
- After Rust changes: `scripts/generate-android-bindings.sh` and `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh` before any Android install.

## Review Focus

1. Telegram unreachable or signed out → sheet stays open, says *Couldn't send…*, nothing crashes (web 503 path, Android exception path).
2. A second press during *Sending* or after *Reported* sends nothing (button inactive until the player closes / another title opens).
3. Error from title A never rides in a report on title B (cleared on open, web and Android).
4. A hostile or huge body to `POST /api/report` (unknown set, unknown reason, 10 kB note, NaN position) → 400, nothing sent.
5. TV: the remote can reach *Report* from the top bar and from the failure screen, and Back closes the sheet without sending.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Shared report text rule + fixtures (web + Kotlin)](phase-01-shared-report-text-rule.md) | pending |
| 2 | [Web: route, sender, player sheet, error capture](phase-02-web-report.md) | pending |
| 3 | [Rust core `report_send` + Kotlin `ReportChannel`](phase-03-core-report-send.md) | pending |
| 4 | [Android player: phone and TV sheet, last error](phase-04-android-report-ui.md) | pending |
| 5 | [Docs, version, checks, review, device walk](phase-05-docs-version-review.md) | pending |

Phases 1 → (2 ∥ 3) → 4 → 5. Work on branch `feat/report-broken-video` in worktree `../mediagram-report`.
