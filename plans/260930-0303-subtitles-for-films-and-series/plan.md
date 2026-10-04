---
title: "Subtitles for films and series"
description: "German/English text subtitles (embedded + sidecars) become one Telegram file per set that the index points at; off by default, forced auto-on, per-profile language, synced, quick toggles on web, phone and TV."
status: in-progress
priority: P2
effort: 9d
branch: main
tags: [subtitles, uploader, index, web, android, tv, sync, parity]
created: 2026-09-30
---

# Subtitles for films and series

**User decisions 2026-09-30 (do not reverse):** 1 separate files; index lists tracks + file ref; fetched on demand, cached; lesson subtitles move out; v12→v13 · 2 embedded de/en text incl. forced/SDH; PGS/VobSub skipped · 3 off by default; forced auto-on for the audio language; per-profile language · 4 remux fix, `.srt` + language-named sidecars, quick toggles + `KEYCODE_CAPTIONS`, synced choices · **after review:** existing titles are measured first (07a), film/series backfill scope decided from the counts, lesson move-inline stays · forced still shows when subtitles are off. Survey: [reports/codebase-survey-subtitles.md](reports/codebase-survey-subtitles.md) · operator results: `reports/rollout-log.md` (created by the first run).

## Key decisions
- **One bundle per set** (gzip JSON of every track, self-describing): half the uploads of one-per-track, one request and one offline unit per title. Per show rejected: re-upload per new episode, two uploaders, a season fetched for one episode.
- **v13 additive**: `subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)` + `subtitle_tracks(set_id, track, lang, forced, sdh, label)`; `assets` keeps summaries; every deployed reader accepts newer schemas. Readers read bundle, else inline rows (`ORDER BY lang`) until phase 09.
- **Merge**: newer `uploaded_at` wins per set, tracks follow, a bundled set keeps no inline rows. No pull or push over a newer channel schema (caption `schema`, `--force` included); a channel missing the tables triggers a loud re-publish.
- **Writer**: at set completion in `finish`, from the original file, one ffmpeg pass into a temp dir; forced = flag, title or sparse cues. One send path: `ChannelRemote::send_document`.
- **Caches**: web = memory map + disk store for held titles beside (not inside) the chunk cache; Android = disk, 64 MiB LRU, preload + course holds. Both: sha shape + sha256 checks, corrupt file refetched.
- **Facts**: "Subtitles" keeps describing the file (`slang`) on both surfaces. **Sync**: optional `ProfileState.preferences` rows, no format bump.

## Playback rule (web, phone, TV — `web/test/fixtures/subtitles/choice-cases.json`)
```
audio      = playing stream's language tag ?? first of the set's alang ?? unknown          (ger/deu→de, eng→en)
wanted     = remembered for this show ?? profile preference ?? off
regular    = wanted off ? none : same key (lang[:sdh]) → same lang plain → same lang SDH → profile preference → none
forced     = no regular showing && audio known ? forced track in the audio language : none  — also when "Off"
toggle on  = last regular this session ?? profile preference (unless off) ?? regular in the audio language ?? first regular
toggle off = regular off (remembered "off"); forced follows its own line.  Picker = Off + regular tracks.
CC / 'c' shown when a regular track exists (state = regular showing); style/offset when any track can show. `und` matches only a remembered `und`.
```

## Phases (parallel: 01 ∥ 02; 07a after 01, ∥ 02–05, before 06; 03 ∥ 04; 05 ∥ 06; 08 ∥ 05–07 · every commit bumps the three manifests by pattern)
| # | Phase | Owns | Bump | Status |
|---|---|---|---|---|
| 01 | [Remux keeps every stream; audio audit](phase-01-remux-keeps-every-stream-and-audit.md) | `media/{remux,test_fixtures}.rs`, `reports/uploaded-stream-headers.sh` | patch | completed (0.84.8) |
| 02 | [v13, bundle, merge, guard, send_document](phase-02-index-v13-subtitle-tables-bundle-format-merge.md) | `crates/mlib-spec/**`, `index/merge*.rs`, `channel_index/**`, `tests/support/channel.rs`, `web/src/catalog.ts`, shared fixtures | minor | completed (0.85.0) |
| 03 | [Web: files, rule, picker, setting, holds](phase-03-web-subtitle-files-default-rule-and-settings.md) | `web/**` except `web/src/state/**`, `web/src/catalog.ts`, the other session's files (`index.ts` only after it lands) | minor | completed (0.86.0–0.88.4; live player reloads itself — `/api/sets` lists 2,662 sets with subtitles, 2026-10-04) |
| 04 | [Android core + player](phase-04-android-core-subtitle-files-and-default-rule.md) | `crates/mediagram-core/src/{catalog*,api/{subtitles,set_text,channel,store},dto}`, `android/core/**`, `android/feature/{player,catalog}/**`, `TitleDetailScreen.kt` | minor | completed (TV box 0.92.0 2026-09-30; tablet 0.102.1 2026-10-04: lesson opens Off, CC on shows cues, Play next names the next lesson; contract run 34/34 2026-10-03) |
| 05 | [Android: CC, captions key, style gate, setting](phase-05-android-quick-toggles-captions-key-profile-setting.md) | `android/ui-mobile/**` (not `TitleDetailScreen.kt`), `android/ui-tv/**`, `android/ui-common/**/settings`, `android/feature/setup/**` | minor | completed (TV box 2026-09-30; tablet 2026-10-04: CC toggles, hidden on a forced-only film; Settings › Profile shows the Subtitles row) |
| 06 | [Uploader: attach at completion](phase-06-uploader-extracts-sidecars-and-uploads-bundles.md) | `src/subtitles/**`, `media/{probe,streams}.rs`, `upload/{session/item,plan,prepare_set}.rs`, `index/{lifecycle,subtitles}.rs`, `course/sidecars.rs`, `remove/**` | minor | completed — real end to end: the other machine's The Deuce S3E1–8 upload (2026-10-01) and every bundle attached since (user, 2026-10-04); tablet: Alien: Covenant's German forced lines show with subtitles Off, no CC for a forced-only film |
| 07a | [Measure before backfill](phase-07a-measure-existing-titles-before-backfill.md) | `commands/subtitles/**`, `commands/mod.rs`, `cli.rs`, `PICTURE_SUBTITLES` visibility, script `subs` mode | minor | completed (0.88.0; this machine measured) |
| 07 | [Move lesson subtitles; scoped backfill](phase-07-move-lesson-subtitles-then-scoped-backfill.md) | `commands/subtitles/**`, `cli.rs`, `index/assets.rs` | minor | code complete; move-inline done 2026-10-01; MP4 channel backfill resumed 2026-10-04 20:00 (1,828 sets); `--mkv` + local folders approved to follow |
| 08 | [Sync subtitle preferences](phase-08-sync-subtitle-preferences-across-devices.md) | `web/src/state/**`, `web/src/routes.ts` (after 03), `crates/mediagram-core/src/state/**` | minor | completed for tablet ↔ web (2026-10-04: test profile English → web `en` in ~20 s, Off → web `off` in ~15 s); TV leg waits for adb on the box |
| 09 | [Remove inline read path (gated)](phase-09-remove-inline-subtitle-read-path.md) | inline branches in `subtitle-tracks.ts`, `catalog_subtitles.rs`, preview helper | patch | gated — approved to run on/after 2026-10-08 if the follower copy still shows 0 inline rows and the other machine reads ≥ 0.91.0 (user, 2026-10-04) |

## State 2026-09-30 23:00 (code complete through 08; 09 gated)
Merged on `main`: 03 wiring 0.88.3 + polish 0.88.4 · 08 0.89.0 · 05 0.90.0 · 06 0.91.0 · 07 0.92.0 — each reviewed (code-reviewer), findings fixed before merge, `scripts/check.sh` green at 0.91.0. The dev web player (`bun --watch`) reloaded onto each merge and runs current code (healthy, 0 failed reads). Not yet: this machine's uploader is 0.83.0 (no guard); the channel index is still v12 (1,266 inline rows) and the other machine pushed a v12 index at 22:26 (so it is < 0.85). Next gates below, in order, each needing the user.

## Rollout order (gates)
0. **Shared guard first** (decided 2026-09-30, shared with the Quest VR plan): the newer-schema pull/publish guard from phase 02 (incl. `--force`) ships alone as a patch release to BOTH uploaders before any v13 (or the VR plan's v14) index is pushed.
1. 01 (+ audio audit), then 07a; 07a runs on both uploaders after `pull-index` → user picks 07's film/series scope.
2. 02 → reinstall here, `push-index` (v13, same content); other uploader upgrades, verified by its own push's caption `"schema":13`.
3. 03 → restart the web player; 04 + 05 together → tablet `caad49da`, TV box `192.168.0.35:5555` (benchmark build).
4. Gate (03/04/05 live, both uploaders ≥ 02) → 06 on both uploaders → one real upload end to end.
5. 07 here after `pull-index`: pre-move snapshot id recorded, move-inline with read-back, backfill in 07a's scope; other machine backfills its own folders.
6. 08 after 03/04, never beside profile-roles phases 01–05. 09 once the channel shows 0 inline rows and both uploaders are ≥ 06.

## Red Team Review
Session 2026-09-30: four reviewers (reports under `reports/from-code-reviewer-to-planner-red-team-*`), **28 raw findings: 1 Critical, 5 High, 22 Medium**. All accepted; none rejected; 4 applied with a fix other than the one suggested (marked *). Answers: `upload_slots` = 1 here (default; no key in `config.toml`), the other machine is checked in 07's runbook; no Android device installs a package catalog (no Kotlin caller of `refreshCatalog`), and the prune is gone anyway.

| Finding (AD assumption, FM failure-mode, SC scope, SA security) | Sev | Applied in |
|---|---|---|
| Sources unreachable for most subtitled titles; old remux kept no subtitle stream (AD1) | C | 07a new, 07 re-scoped, 01 facts |
| Explicit `-map` breaks unmuxable streams; permanent audit command (FM7, SC5) | M, M | 01: probe-built map + fallback; script under `reports/` |
| EXPECTED_SCHEMA lockstep; `--force` skips the guard (FM6, SA5) | M, M | 02 |
| Unused columns, `doc_id`, third send path (SC3, SC4) | M, M | 02, 06 |
| Forced tracks unflagged in this library (AD2) | H | 06 density rule + fixture |
| Toggle rule unwritten; forced-only title has no controls (SC2, AD3) | H, M | plan.md rule, 02 fixture, 03, 04, 05 |
| Plan-time staging: foreground read, sweep race, rebuilds `finish`; `.vtt`/`.srt` tie (SC1, FM2*, AD7, AD6) | H, H, M, M | 06: attach in `finish` (*staging dropped instead of fixed) |
| Fallback matching: shrinking pool, no name check, drive roots (FM1, AD5) | H, M | 07a matcher, 07 opt-in |
| move-inline: no read-back, wrong rollback claim (SA1) | M | 07 read-back, pre-move snapshot, `--redo` |
| Stale uploader after the move; inline path never removed (FM5, SC6) | M, M | 02 alarm, 07 gate (Med × High), 09 |
| Android prune wipes held bundles; cache integrity; extra hold param (FM3*, SA2*, SA3, SC8) | M ×4 | 04: LRU cap instead of prune; `currentCore()` |
| Offline parity for lessons and web held titles; second web disk cache (FM4, SA4, SC7*) | M ×3 | 03 held store (*disk kept for held titles), 04 course hold |
| Bun `gunzipSync` has no cap (SA note) · Android audio fallback (FM8) · fact reversal (AD4) | M, M | 03 `node:zlib`; 04 `alang`; 03/04 facts stay `slang` |

## Decided after review (2026-09-30)
- Android lesson hold: the opened lesson + the next 10 lessons (phase 04), not the whole course.
- Phase 07's send / `--redo` mode (crash repair for new uploads) stays in scope whatever 07a decides for films and series.
- **07a result → backfill scope:** 2,856 of 3,058 de/en-subtitled sets already carry de/en text in their *uploaded* copies (local sources here: 62). The film/series backfill extracts from the uploaded copies through the channel — MP4 first (~2,164, ≈1 min each), then MKV (~692, full reads) — paced, resumable; phase 07 Part B re-planned: `backfill --channel` through an in-process loopback `serve` on the sending session. Numbers: [reports/rollout-log.md](reports/rollout-log.md).

## Unresolved questions
1. ~~Phase 07 film/series scope~~ — decided: channel extraction (see above).
2. Course hold on Android fetches up to 864 bundles (~20 MB) on the first lesson opened in Wall Street Story. Keep it whole-course as accepted, or bound it to the chapter or the next N lessons?
3. Keep: lessons' 1,076 `und` tracks never match a profile language; sidecars in other languages are skipped. Open: re-upload of 01's audio-damaged sets (follow-up); the other machine's source folders and `upload_slots`.
