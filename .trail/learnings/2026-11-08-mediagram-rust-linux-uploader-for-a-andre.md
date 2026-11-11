---
title: Mediagram = Rust Linux uploader for a Telegram-backed personal video library
author: Andre
date: 2026-11-08
id: l-98e3b41f
scope: repo
status: active
touches:
  - plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md
---

## Decided
mediagram (started 2026-09-14): Rust CLI that raw-splits videos into 3.5 GiB parts, uploads them to ONE private Telegram channel via grammers-client 0.10.0, writes a `#mlib v=2` JSON caption on every part, and keeps a local SQLite `library.db` pushed to the channel as a pinned document. A future Android TV app (Kotlin, TDLib, Media3 virtual-concat DataSource) will consume the same `mlib-spec` crate over UniFFI.

User facts: Telegram Premium account (4 GB cap). Runs Proxmox at home, so Jellyfin was discussed as the more durable primary; Telegram is treated as a fun project / off-site mirror. Fluent in Rust and Kotlin; chose Rust + grammers over Python/Telethon and over tdlib-rs.

**Why:** these choices came out of a long brainstorm + validated plan and should not be re-litigated in later sessions.

## Watch out
authoritative plan is `plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md` ("Locked decisions" section). Phase 2 must run a real 3.5 GiB smoke upload before phases 5-7. `ck` CLI is not installed, so plan files were hand-scaffolded. Repo had no git init as of phase 1.

_From Claude Code's memory of this repo, last updated Sep 14._
