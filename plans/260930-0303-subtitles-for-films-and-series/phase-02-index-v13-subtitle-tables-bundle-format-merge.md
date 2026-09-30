# Phase 02 — Index v13: subtitle tables, bundle format, merge, publish guard, `send_document`

## Context links
- `crates/mlib-spec/src/schema.rs:8` (`SCHEMA_VERSION = 12`), `:29` (`READABLE_SCHEMAS`), `:43` (`GROUPS`), `:141-171` (V11/V12 pattern: new table, `set_at` newer-wins); `schema_tests.rs:14` (range test).
- `crates/mlib-spec/src/schema_versions.rs:52-66` — V4 `assets`; its rationale ("subtitles live here rather than as their own channel messages") is reversed by user decision 1.
- Lockstep: `crates/mediagram/tests/shared_playable_sql.rs:36-45` asserts `web/src/catalog.ts` declares `EXPECTED_SCHEMA = {SCHEMA_VERSION}`; `web/src/catalog.ts:24-39` (constant + comment).
- `crates/mediagram/src/index/migrations.rs:36-60`; `index/merge.rs:9-19` (rules table), `:140-171` (`copy_kept`, `fill_missing_assets` at `:158`); `merge_copy.rs:27-40`; `merge_categories.rs:16-30` (upsert-newer); `merge_columns.rs:14-18`.
- Publish: `channel_index/publish.rs:36-39` (`Mode::Force` sends without pulling), `:42-62` (pull loop), `:77-80` (`send_index` call); `channel_index/pull.rs:19-32` (`current`: newest own index post), `:36-74` (`pull_from`); `channel_index/remote.rs:53` (`send_index`), `telegram_remote.rs:104-133`; fake `crates/mediagram/tests/support/channel.rs:120,168`; `commands/push_index.rs:11-22`.
- `crates/mlib-spec/src/index_caption.rs:22-41` — every index caption carries the pushing build's `schema`.
- `crates/mlib-spec/src/package/charset.rs:15` (`is_lower_hex`, `pub(super)`).
- Readers accept newer schemas: `web/src/catalog.ts:58-69`, `crates/mediagram-core/src/api/channel/install.rs:44-50`, `crates/mlib-spec/src/package/mod.rs:157-163`.
- `crates/mlib-spec/src/caption_codec.rs:19` (`#mlib v=`), `crates/mediagram/src/index/rescan.rs:62-68` (other markers skipped).
- Red team: failure-mode F5/F6, security F5, scope-critic F3/F4.

## Overview
Priority P1. Effort 5h. Version: next **minor** (schema v13, automatic migration). Status: pending.
The spec half: where a set's subtitle file is recorded, what it contains, how two uploaders merge it, and the one way the uploader sends a small document. A v13 index pushed after this phase has empty new tables and the same content as v12.

## Key decisions
1. **Additive v13**: two tables, nothing dropped or deleted by the migration. Every deployed reader accepts a newer schema, so the push locks no client out. `assets` stays (1,233 summaries).
2. **One bundle per set**: gzip'd JSON, every track, self-describing — the durable copy.
3. **Index carries only what readers use**: per track `track, lang, forced, sdh, label`; per set the file reference `chat_id, message_id` plus `bytes`, `sha256`, `uploaded_at`. No `doc_id` (both readers resolve by message id: `web/src/telegram/part-fetch.ts:42-45`, `crates/mediagram-core/src/transport/stream.rs:75-93`), no `source`/`codec` columns (they live in the bundle).
4. **`sha256`** = lowercase 64-hex of the gzip bytes; readers refuse any other shape before it becomes a file name.
5. **Merge**: newer `uploaded_at` wins per set; tracks follow their file row; a bundled set keeps no inline subtitle rows.
6. **Guard from the caption**: no pull or publish over a channel index whose caption `schema` is newer than this build — `--force` included (it lists pins but downloads nothing). It cannot stop builds older than this one (rollout gate).
7. **Stale-uploader alarm**: when a pulled snapshot lacks `subtitle_files` but the local index has rows, say so loudly and re-publish.
8. **`send_document`**: `ChannelRemote::send_index(path, caption)` becomes `send_document(bytes, name, mime, caption) -> i32`; the index push calls it with `library.db`; phase 06 calls it for bundles. No second send path.

## Requirements
V13 group (`SCHEMA_VERSION = 13`, `READABLE_SCHEMAS` gains 13):
```sql
CREATE TABLE IF NOT EXISTS subtitle_files(
    set_id TEXT PRIMARY KEY REFERENCES sets(set_id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL,
    bytes INTEGER NOT NULL, sha256 TEXT NOT NULL, uploaded_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS subtitle_tracks(
    set_id TEXT NOT NULL REFERENCES subtitle_files(set_id) ON DELETE CASCADE,
    track INTEGER NOT NULL,                -- position in the bundle
    lang TEXT NOT NULL,                    -- 'de' | 'en' | 'und'
    forced INTEGER NOT NULL DEFAULT 0,
    sdh INTEGER NOT NULL DEFAULT 0,
    label TEXT NOT NULL,
    PRIMARY KEY(set_id, track)
);
```
`web/src/catalog.ts`: `EXPECTED_SCHEMA = 13`; comment adds "v13 only the wholly new `subtitle_files` and `subtitle_tracks` tables". Harmless for the running player (lower-bound check).

Bundle v1 (`crates/mlib-spec/src/subtitle_bundle.rs`; `flate2` + `serde_json`, workspace deps):
- gzip(JSON) of `{"v":1,"set":"<set_id>","tracks":[{"lang","forced","sdh","label","source","codec","vtt"}]}`; `tracks[i]` ↔ `subtitle_tracks.track = i`; `source` `embedded|sidecar`, `codec` the original (`subrip`, `ass`, `mov_text`, `webvtt`, `srt`, `vtt`).
- `encode`, `decode`; caps: compressed ≤ 16 MiB, decompressed ≤ 64 MiB (read through `take`), one VTT ≤ 4 MiB, `v != 1` → error; `valid_sha256(&str)` reusing `is_lower_hex` (made `pub(crate)`).
- Channel message: name `<set_id>.subs.json.gz`, mime `application/gzip`, caption `#mlib-subs v=1` + newline + `{"set":"<set_id>"}` (`SUBS_CAPTION_PREFIX`); never pinned; ignored by rescan and index discovery.

Shared fixtures (so 03/04/06 can run in parallel):
- `crates/mlib-spec/tests/fixtures/subtitle-bundle-v1.json` — three tracks: German (Forced), German, English (SDH), two cues each.
- `web/test/fixtures/subtitles/choice-cases.json` — the playback rule in `plan.md`, as data. Input `{tracks, remembered, preferred, audioTag, alang, last}`; output `{audio, regular, forced, toggleOn, pickerRows, ccVisible, styleVisible}`. At least: nothing set + German audio → forced German only, style visible, picker Off/German/English (SDH); remembered `off` → no regular, forced still shows; toggle on with `last` unset → preferred → audio language → first regular; toggle on with preferred `off` → skips it (audio language next); toggle on with `last = en:sdh`; preferred `en` with only SDH English → SDH; remembered language missing → preferred; forced-only title (Boardwalk shape) → no picker, no CC, style visible, forced shows for German audio; `audioTag` null + `alang ["de"]` → audio `de`; `audioTag "ger"` → `de`; lesson `und` never matches a preference but matches remembered `und`; audio unknown → no forced.

Uploader:
- `index/merge_subtitles.rs` (new): `shared_columns(conn, "subtitle_files")` empty → 0 (a v12 snapshot). Else, in `copy_kept` before `fill_missing_assets`:
  1. `INSERT INTO main.subtitle_files … SELECT … FROM channel.subtitle_files ch WHERE EXISTS (main.sets for ch.set_id) ON CONFLICT(set_id) DO UPDATE SET … WHERE excluded.uploaded_at > subtitle_files.uploaded_at` (`WHERE true` before `ON CONFLICT`, as `merge_categories.rs:23`).
  2. Sets whose main row now carries the channel's `message_id`: delete their main tracks, insert the channel's.
  3. `DELETE FROM main.assets WHERE kind = 'subtitle' AND set_id IN (SELECT set_id FROM main.subtitle_files)`.
- `merge_copy.rs` `fill_missing_assets`: skip `kind = 'subtitle'` rows of sets that have a `main.subtitle_files` row.
- `merge.rs`: call it; rules table row; `MergeReport.subtitles_taken` and `channel_lacks_subtitles` (channel has no `subtitle_files` table while local has rows); `channel_index/report.rs` prints both, the second as a warning naming the likely cause (a pre-v13 uploader pushed).
- `pull.rs`: `current()` refuses when the chosen caption's `schema` (new `index_caption::schema(caption)`, like `pushed_at`) exceeds `SCHEMA_VERSION`, naming the reinstall command. `pull_from`: when `channel_lacks_subtitles`, record a publish owed (`pins::owe_publish`); `pull-index` then publishes (after-pull mode) and says so.
- `publish.rs`: the `Mode::Force` branch calls `pull::current(remote)` first, so the guard covers every push; `send` calls `send_document(read(snapshot), "library.db", INDEX_MIME_TYPE, caption)`.
- `remote.rs` / `telegram_remote.rs` / `tests/support/channel.rs`: `send_index` → `send_document` (`upload_stream` over the byte slice; flood-wait-only retry kept).

Docs: `docs/mlib-spec.md` §6 "Schema v13 additions" + a section for the `#mlib-subs` document and bundle v1; `CONTEXT.md`: *subtitle bundle*, *forced track*, *SDH track*.

## Architecture
```
publish (any mode) ─current(remote): caption schema > 13? refuse─┐
  AfterPull: download snapshot → merge (subtitle_files newer-wins, tracks follow, inline rows of bundled sets dropped)
             └ channel lacks tables but local has rows → warn + publish owed
  snapshot → send_document(bytes, "library.db", …) → pin
```

## Related code files
- Modify: `crates/mlib-spec/src/{schema.rs,schema_tests.rs,lib.rs,index_caption.rs,package/charset.rs}`, `crates/mlib-spec/Cargo.toml` (`flate2.workspace = true`), `crates/mediagram/src/index/{mod.rs,merge.rs,merge_copy.rs}`, `crates/mediagram/src/channel_index/{pull.rs,publish.rs,remote.rs,telegram_remote.rs,report.rs}`, `crates/mediagram/tests/support/channel.rs`, `web/src/catalog.ts`, `docs/mlib-spec.md`, `CONTEXT.md`, `docs/project-changelog.md`.
- Create: `crates/mlib-spec/src/subtitle_bundle.rs`, `subtitle_bundle_tests.rs`, `crates/mlib-spec/tests/fixtures/subtitle-bundle-v1.json`, `web/test/fixtures/subtitles/choice-cases.json`, `crates/mediagram/src/index/merge_subtitles.rs`, `merge_subtitles_tests.rs`.

## Implementation steps
1. V13 DDL + constants + `EXPECTED_SCHEMA = 13`; schema test 6..=13.
2. Bundle module + tests (round trip, fixture, each cap, wrong `v`, not gzip, `valid_sha256`).
3. Fixtures, the rule cases written from `plan.md` line by line.
4. `merge_subtitles.rs` + `fill_missing_assets` exclusion + report fields.
5. `index_caption::schema`; guard in `current()`; Force branch calls it; stale-uploader alarm + owed publish.
6. `send_document` rename/generalisation (+ fake).
7. `crates/mediagram-core` builds unchanged; its tests run.
8. Docs, bump by pattern, changelog.
9. Operator (lead): reinstall here; `mediagram push-index` → channel v13. Ask the other uploader machine to upgrade and push once; its printed message id must be the channel's newest with `"schema":13` in the caption.

## Todo
- [ ] V13 group, constants, `EXPECTED_SCHEMA = 13`, schema tests
- [ ] bundle codec, caps, `valid_sha256`, tests
- [ ] shared fixtures (bundle, rule cases incl. toggle, forced-only, audio fallback)
- [ ] merge + exclusion + report + stale-uploader alarm
- [ ] caption-schema guard on every pull and push, `--force` included
- [ ] `send_document` replaces `send_index`
- [ ] docs, manifests, changelog; v13 pushed; other uploader verified at v13

## Success criteria
- `scripts/check.sh` green (incl. `shared_playable_sql.rs`).
- Merge tests: channel-only bundle for a local set copied with tracks; newer replaces older incl. tracks; older ignored; set absent locally → skipped; v12 snapshot → 0 and `channel_lacks_subtitles` set when local has rows; no inline subtitle row left for a bundled set; summaries still filled.
- Guard tests with the fake channel: newest caption `schema: 99` → `pull-index`, after-pull publish and `--force` all refuse, nothing sent, local index untouched.
- After the push: web `/api/sets` count unchanged; tablet (`caad49da`) and TV box (`192.168.0.35:5555`) open the catalog as before.

## Tests
| Level | What |
|---|---|
| Unit (mlib-spec) | schema range; bundle codec + caps + fixture; caption `schema` |
| Unit (uploader) | merge rules; guard in all three paths; alarm; `send_document` with the fake |
| Integration | v12 fixture db → v13 keeps every row |
| Manual | v13 push; three clients still read it |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| A pre-v13 uploader pushes after a v13 push and drops the tables from the channel | Med × High after phase 07 (subtitles vanish on every client until the next v13 push) | Rollout gate: other uploader verified at v13 by its own push's caption; alarm + automatic re-publish on this machine's next pull; readers keep the inline path until phase 09 |
| Merge re-adds legacy rows from an old snapshot or a restored `library.before-channel-merge-*.db` | Med × Low | exclusion + cleanup delete, tested |
| Hostile or corrupt bundle | Low × Med | decode caps; readers check `bytes`, sha shape, sha256 |
| `--force` after a `rescan` into an empty data dir pushes an index with no bundles (same schema, not refused) | Low × Med | Known limit: restoring rows from `#mlib-subs` captions is a follow-up; runbook says never `--force` after a rescan |

## Security
Nothing reaches a browser. Decode caps before allocation; sha shape checked before any file name is built.

## Rollback
Revert the build: an older uploader opens a v13 db (`migrations.rs:36-39` no-ops, read-only opens are lower-bound) and its `VACUUM INTO` still copies unknown tables; older readers already read v13.

## Next
03 and 04 (readers) in parallel; 06 (writer) after the rollout gate.
