# Codebase survey: subtitles (verified 2026-09-30, read-only)

Every reference re-checked against the working tree at `a21b7df0` (+ another session's uncommitted
edits in `web/src/cache/*`, `web/src/index.ts`, `web/src/range.ts`, `web/src/telegram/source.ts`).

## Corrections to the brief

| Brief said | Code says |
|---|---|
| "library roots in the uploader config" | No such key. `Config` (`crates/mediagram/src/config.rs:16-62`) has no roots; `~/.config/mediagram/config.toml` neither. Backfill must be given folders. |
| Readers may refuse a v13 index | Every reader accepts a *newer* schema: web `assertSchema` is lower-bound only (`web/src/catalog.ts:58-69`); Android installs after `count_playable` only (`crates/mediagram-core/src/api/channel/install.rs:44-50`); package pointers lower-bound only (`crates/mlib-spec/src/package/mod.rs:157-163`). An *additive* v13 is readable by every deployed reader. |
| Android subtitles come over a byte path | Bodies are read from the installed index, no network (`crates/mediagram-core/src/api/set_text.rs:1-3`, `catalog_assets.rs:39-58`). Offline works today for free; bundles change that. |
| Web per-show memory is localStorage | Server `state.db` `preferences(profile_id, scope, name, value, updated_at)` (`web/src/state/store.ts:315-343`); Android core has the same table (`crates/mediagram-core/src/state/schema.rs:107-114`). Same names/values on both (`subtitle`, `cue-size`, `cue-backing`, `cue-offset`). **Not synced**: `writeWorthSyncing` returns false for `/preferences` (`web/src/routes.ts:79`); `ProfileState` has no preferences (`state/record.rs:69`, `web/src/state/sync-record.ts:110`). |
| Film page lists slang it cannot show | Worse: `languages()` expects an array, `slang`/`alang` arrive as JSON strings, so the film page shows neither "Audio languages" nor "Subtitles" (`web/public/lib/catalog/film-page.js:99-108`). |

## Data (this machine, `~/.local/share/mediagram/library.db`, v12)

- 5,005 sets (channel follower copy: 5,910 — pull before any backfill).
- Video sets with embedded slang: movie mkv 680, ep mkv 126, ep mp4 1,230, movie mp4 7 (= 2,043).
- mp4 video sets (remux candidates): ep 2,306 (1,685 multi-audio), movie 30, docu 27.
- `assets`: 1,235 `subtitle` rows (30.2 MB bodies; lang `und` 1,076, `en` 153, `de` 6) + 1,233 `summary` rows → `assets` table must stay.
- 3,760 movie/ep/docu sets, 3,759 distinct `total` byte sizes → exact-size matching of a source file to its set is near-unique.
- Samples: Arcane S01E03 mkv: `ger "German (Forced)"` forced=1 default=1; `ger "German"`; `eng "English (SDH)"` **hearing_impaired=0** (title is the only SDH signal); 30 more languages. Band of Brothers S01E07: `ger "Forced (SRT)"` subrip forced; four PGS (forced, full, 2x SDH) — PGS skipped.

## Reuse points (no second download path)

- Uploader small-document send: `TelegramRemote::send_index` (`crates/mediagram/src/channel_index/telegram_remote.rs:104-133`): `upload_stream` + `send_message` with flood-wait-only retry.
- Uploader ranged reads of uploaded files: `serve::router` (`crates/mediagram/src/serve/routes.rs:31-34`) over `TelegramSource` (`commands/serve.rs:9`).
- Web: `connectionFetcher(connection, messageId)` (`web/src/telegram/part-fetch.ts:42-45`) → `fetchPartOnce` returns the whole document for `length >= size` (`:97-121`); gate + stale-reference retry included.
- Web cache: chunk cache LRU walks and evicts *every* file under its root (`web/src/cache/store.ts:257-306`), root from `MEDIAGRAM_CACHE_DIR` (`web/src/config.ts:196`).
- Core: `transport::stream::part_document` (`crates/mediagram-core/src/transport/stream.rs:75-93`) + capped `iter_download` loop (`api/channel/install.rs:54-84`); session client from `core.state` as `api/read.rs:70-80` does.
- Android preload: one writer implements both preloaders (`android/core/playback/src/main/kotlin/CacheDataSourceWriter.kt:56`).

## Rollout facts

- Two uploaders; publish merges first (`channel_index/publish.rs:42-62`); merge copies only tables/columns both sides have (`index/merge_columns.rs:14-18`); push is a whole-db `VACUUM INTO` (`index/snapshot.rs:39-50`). An old build merging a newer channel drops tables it does not know from *its* push; nothing guards publish against a newer channel schema today.
- Profile-roles plan (`plans/260928-0047-...`, pending) edits the same files as phases 03/08 here: `web/public/lib/catalog/settings-page.js`, `web/src/routes.ts` `writeWorthSyncing`, `web/src/state/{sync-record,merge,store}.ts`, `crates/mediagram-core/src/state/**`.
- Ratchets: web `code-standards.test.ts` (player.js 996, transport.js 471, series-summary.js 201, src/index.ts 441, state/store.ts 800, state/sync-record.ts 328 — listed files may not grow); Rust 200 lines hard (`crates/mediagram/tests/code_standards.rs:9`), `state/merge.rs` at 200.
- Bun's Intl does not canonicalise bibliographic codes (`Intl.getCanonicalLocales("ger")` → `ger` in Bun, `de` in Node) → explicit map needed wherever a server compares raw ffprobe tags.

## Corrections after the red-team review (2026-09-30)

- The old faststart remux kept **no** subtitle stream (not one): remuxed mp4 uploads carry none (assumption-destroyer F1, scratch test).
- Of 2,834 channel sets whose `slang` lists de/en, **63** have a source reachable from this machine (0 of 2,028 mp4). Size uniqueness says nothing about whether the source still exists → phase 07a measures first.
- The channel holds **1,266** inline subtitle rows (the local index 1,235): 31 came from the other machine → `move-inline` runs after `pull-index`.
- The sidecars are not a full copy of the inline bodies (Wall Street Story: 0 `.vtt` beside 884 `.mp4`); the backup is the pre-move index snapshot (old snapshots are only unpinned, `channel_index/unpin.rs:17-30`).
- `web/src/catalog.ts:24` is held to `SCHEMA_VERSION` by `crates/mediagram/tests/shared_playable_sql.rs:36-45` → the `EXPECTED_SCHEMA` bump belongs to phase 02.
- `push-index --force` skips the pull (`channel_index/publish.rs:36-39`) → the schema guard reads the caption's `schema` (`mlib-spec/src/index_caption.rs:22-41`) on every path.
- The small-document send is `ChannelRemote::send_document` (generalised from `send_index` in phase 02); `doc_id` is not stored for bundles.
- Answers: `upload_slots` is 1 on this machine (no key in `config.toml`, default `config.rs:100-102`). No Android device installs a package catalog: no Kotlin caller of `refreshCatalog` outside the bindings and `FakeCore`; the app uses `refreshLibrary` (`CatalogRepository.kt:122`, `Libraries.kt:38`).
- The Android "Subtitles" fact reads inline-assets languages today while the web reads `slang`; both read `slang` after phase 04.
