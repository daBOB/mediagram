# Phase 01 — Faststart remux keeps every stream; one-off audio audit

## Context links
- `crates/mediagram/src/media/remux.rs:36-43` — `ffmpeg -i src -c copy -movflags +faststart` with no `-map`; `:45-56` bail on non-zero exit and on a still-trailing `moov`.
- `crates/mediagram/src/upload/prepare_set.rs:26-28` (inspect runs on the original), `:74-76` (remux after it; a failure aborts planning with "preparing file for splitting"), `:101-102` (caption `alang`/`slang` from the original).
- `crates/mediagram/src/media/streams.rs:22-32,48-83` (`Stream { index, kind, language, codec }`, `probe`) — enough to build the map; no `probe.rs` change here (phase 06 owns it).
- `crates/mediagram/src/index/lifecycle.rs:55-69` — a remux's `tmp:` marker is forgotten at completion, so the index cannot say which sets were remuxed.
- `mediagram serve --addr` (`crates/mediagram/src/cli.rs:109-114`, `commands/serve.rs:19-51`) serves `/sets/{id}/stream` with Range reads through the uploader's session (`serve/routes.rs:31-34`).
- Red team: failure-mode F7, scope-critic F5, assumption-destroyer F1 (`reports/from-code-reviewer-to-planner-red-team-*`).

## Overview
Priority P1, independent; ships first. Effort 2h. Version: next **patch** (bug fix, no new command). Status: pending.

Without `-map`, ffmpeg's default selection kept one video and one audio stream and **no subtitle stream at all** (reviewer's scratch test: 2 audio + 2 `mov_text` in → `video, audio(ger)` out; with `-map 0:V? -map 0:a? -map 0:s?` all five streams and the forced flag survived). Every MP4 source whose `moov` trailed `mdat` was uploaded without its other audio languages and without any subtitle stream, while the caption's `alang`/`slang` still describe the original.

## Key insights
- Only sources failing `needs_faststart` are remuxed (`remux.rs:22`).
- An explicit `-map` bypasses ffmpeg's "can this container hold it" filter: an `eia_608` caption track or PCM audio in a QuickTime file named `.mp4` would turn a remux that works today into a failed upload. So the map is built from the probe, and a refused remux falls back once to today's arguments.
- Subtitles lost this way come back only from a source that still exists; phase 07a measures how many do. The lasting damage worth a decision here is **audio** → the audit is audio-only.

## Requirements
- `faststart_args(streams) -> Vec<String>` (pure): `-map 0:V?` (video, not cover art), `-map 0:a?`, one `-map 0:<index>` per subtitle stream whose codec is `mov_text` or `dvd_subtitle` (the subtitle codecs the mp4 muxer writes); data/attachment streams never mapped; then `-c copy -movflags +faststart`.
- `ensure_faststart`: `streams::probe(src)` only when a remux is needed; run with the mapped args; on failure remove the partial output, `tracing::warn!` that the mapped remux was refused and some streams may be dropped, retry once with today's arguments. The post-check `needs_faststart(&dest)` stays.
- One-off audit script (not repo code): `reports/uploaded-stream-headers.sh`, mode `audio` (mode `subs` is added by phase 07a). Needs: installed uploader, `mediagram pull-index` done, no upload running on this machine (the Telegram session is shared).
  1. `mediagram serve --addr 127.0.0.1:8799 &` (loopback; stopped by a `trap`).
  2. Candidates: `sqlite3 -readonly ~/.local/share/mediagram/library.db` → complete `mp4` sets of kind `movie|ep|docu` with `json_array_length(alang) > 1`.
  3. Per set: `ffprobe -v error -probesize 65536 -analyzeduration 0 -of json -show_entries stream=codec_type,codec_name:stream_tags=language http://127.0.0.1:8799/sets/<id>/stream` — reads the `moov` at the head only (uploads are faststart), a few MB per title.
  4. `jq` maps tags like `classify::lang_code` (`ger|deu→de`, `eng→en`, `und`/none dropped) and prints TSV `set_id  kind  title  alang  uploaded_audio  missing_audio`, then totals.
  Never sends, never pins.

## Architecture
```
add → prepare_set → ensure_faststart(src)
  needs_faststart? ─no→ src
  └yes→ streams::probe(src) → faststart_args → ffmpeg ─ok→ dest ─needs_faststart(dest)? bail
                                                 └fail→ warn + ffmpeg (today's args) → dest
audit: library.db (read-only) → ids → ffprobe http://127.0.0.1:8799/sets/<id>/stream (head only) → jq → TSV
```

## Related code files
- Modify: `crates/mediagram/src/media/remux.rs` (args builder, fallback, tests), `crates/mediagram/src/media/test_fixtures.rs` (two fixtures), `docs/project-changelog.md`.
- Create (plan dir, operator tool): `plans/260930-0303-subtitles-for-films-and-series/reports/uploaded-stream-headers.sh`.

## Implementation steps
1. `faststart_args` + unit test over probe JSON: video + 2 audio + `mov_text` + `dvd_subtitle` + `eia_608` + a data stream → `eia_608` and data not mapped.
2. Fixture `make_trailing_moov_mp4_multi`: lavfi video, two `sine` audios (`ger`, `eng`), two tiny SRT inputs → `mov_text` (`ger` with `-disposition:s:0 forced`, `eng`), `moov` trailing. Test: remux keeps 1 video, 2 audio, 2 subtitles, both languages and the forced flag.
3. Fixture with a stream the mp4 muxer refuses: probe locally which of an `eia_608` track or PCM audio in a mov-muxed `.mp4` ffmpeg n9.0.2 refuses under an explicit map, build the fixture from that one. Test: remux succeeds through the fallback and the output is faststart.
4. Fallback path in `ensure_faststart`. All tests guarded by `ffmpeg_required`.
5. Bump the three manifests by pattern (patch); changelog.
6. Write the audit script; operator run (lead): reinstall (`cargo install --path crates/mediagram --locked`), `mediagram pull-index`, `reports/uploaded-stream-headers.sh audio > reports/remux-audio-audit-<date>.tsv`; add the `missing_audio` count to `reports/rollout-log.md`.

## Todo
- [ ] `faststart_args` + unit test
- [ ] multi-stream fixture + test
- [ ] refused-stream fixture + fallback test
- [ ] manifests (patch), changelog
- [ ] audit script (audio mode) written and run; TSV saved; count in `reports/rollout-log.md`

## Success criteria
- `scripts/check.sh` green, ffmpeg-gated tests executed on this box.
- Audit TSV saved under `reports/`; `reports/rollout-log.md` states how many sets miss an audio language.

## Tests
| Level | What |
|---|---|
| Unit | `faststart_args` keeps mp4-legal subtitles, drops `eia_608`/data |
| ffmpeg-gated | multi-stream remux keeps all streams + forced flag; refused stream → fallback succeeds; existing faststart tests unchanged |
| Manual | audit over the channel (read-only) |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Mapped remux refused by the muxer | Low × Med | Probe-built map (mp4-legal subtitle codecs only) + one fallback to today's args |
| Cover art copied as a second video track | Low × Med | `0:V` excludes attached pictures |
| ~2,000 head reads trip Telegram limits | Med × Low | Sequential; serve's retry honours FLOOD_WAIT; small probesize |
| Audit run beside an upload | Low × Med | Script refuses when an upload process is running (`pgrep` of `mediagram` upload commands) |

## Security
Loopback-only serve; read-only index; output holds set ids and titles only.

## Rollback
Revert the commit; nothing persisted changes shape.

## Next
Audio-damaged sets → re-upload decision (user, follow-up). Subtitle-stripped uploads → measured in phase 07a.
