---
title: "Report a broken video merged as 0.126.0, fixed in 0.126.1 (2026-10-18, unpushed,…"
author: Andre
date: 2026-11-08
id: l-17c6b24a
scope: repo
status: active
touches:
  - docs/superpowers/specs/2026-10-17-report-broken-video-design.md
  - tasks/lessons.md
---

## Decided
Report a broken video (plan `plans/261017-1747-report-broken-video/`, spec `docs/superpowers/specs/2026-10-17-report-broken-video-design.md`) was merged to `main` as 0.126.0 on 2026-10-17 (merge a8e9227f). It sits on 0.124.0, 0.124.1 and 0.125.0, all unpushed and unreleased.

**User's decisions (do not reverse silently):** reason + optional note; error detail attached automatically; everyone can report, kids too; destination is a message *scheduled* ~60 s into the account's own Saved Messages. Every screen shares one Telegram account, so a plain message never notifies; a scheduled one arrives as a reminder and does (user confirmed on the phone).

**Why:** asked for "a report broken video feature" on 2026-10-17 and answered each design question.

## Watch out
- **Walked 2026-10-18** on tablet (debug) and box (release, apk + .dm, install-dm) as 0.126.1, after 0.126.0 crashed on Send (`(?U)` regex, see `tasks/lessons.md`). Test reports sent: tablet 00:34 (Bad Boys II, "Picture or sound wrong"), box 00:40 (Der große Frust, "Subtitles wrong"), both noted "please ignore".
- **Both devices were left on profile "test"** (PIN 1357): the tablet was on "andre"/A, the box on "andre". Going back needs the user's own PIN. "TV test" has no PIN; picking it asks to *create* one, so don't.
…

_From Claude Code's memory of this repo, last updated Oct 17._
