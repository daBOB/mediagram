# mediagram

A Rust CLI that stores a personal video library in **one private Telegram
channel**: files are split into raw byte-range parts (3.5 GiB by default,
Telegram Premium's 4 GB per-message cap), each part is uploaded with a
structured caption, and a local SQLite index (`library.db`) is the
canonical record of what's in the channel. See
[`docs/mlib-spec.md`](docs/mlib-spec.md) for how it stores things.

## Requirements

- Rust 1.87+ (edition 2024). `rust-toolchain.toml` pins `stable`.
- `ffmpeg` and `ffprobe` on `PATH` (media inspection and faststart remux).
- A system `libsqlite3` (the `rusqlite` dependency links against it, not a
  bundled copy — grammers already statically links its own sqlite3, and
  two bundled copies collide at link time).
- A Telegram `api_id`/`api_hash` from <https://my.telegram.org>, and a
  private channel (or supergroup) where the logged-in account is an admin.
- A [TMDB](https://www.themoviedb.org/settings/api) API key (free tier) —
  optional if every `add` will use `--manual`.

## Install

```sh
cargo build --release
# binary at target/release/mediagram
```

## Config

Copy `config.example.toml` to `$XDG_CONFIG_HOME/mediagram/config.toml`
(usually `~/.config/mediagram/config.toml`) and fill in `api_id`,
`api_hash`, `channel` (a `-100…` channel id or the exact channel title),
and `tmdb_key`. Every key can be overridden with a `MEDIAGRAM_<KEY>`
environment variable (e.g. `MEDIAGRAM_API_HASH`), and a different config
path can be passed with `--config`. See `config.example.toml` for the full
list of optional keys (`part_size`, `throttle_ms`, `max_attempts`,
`tmp_dir`, `data_dir`).

## Commands

| Command | Description |
|---|---|
| `mediagram login` | Sign in with phone + code (+ 2FA password) and persist the session. **Must be run in a real, interactive terminal** — it prompts for input and there is no TTY when invoked from a non-interactive context. |
| `mediagram whoami` | Print the signed-in account and the resolved library channel. |
| `mediagram add <file>` | Split, upload, caption and index one media file. See flags below. |
| `mediagram resume [--no-push]` | Finish every set left `pending` by an interrupted `add` (adopts already-uploaded parts instead of re-uploading them). |
| `mediagram push-index` | Snapshot `library.db` and upload it to the channel as a pinned document. |
| `mediagram verify <set-id> \| --all [--full] [--since <unix>]` | Check a set (or every set): default mode compares each part's message/document against the index; `--full` re-downloads and hashes every part, and `--since` skips parts already verified at or after that timestamp so an interrupted sweep resumes. `--all` skips sets that are still uploading. |
| `mediagram add-course <dir> [--dry-run]` | Walk a course folder and upload every lesson: subdirectories are chapters, video files inside them are lessons. Re-running skips lessons already finished. |
| `mediagram add-show <dir> --tmdb <id> [--dry-run] [--yes]` | Walk a series folder and upload every episode, one set each. Season and episode come from the file name; the show comes from `--tmdb`, which is required because release prefixes defeat the title guess. Prints what it would file where, says which files the player would convert on every play and whether `prepare` can fix it, then asks. Re-running skips episodes already complete. |
| `mediagram metadata` | Record what TMDB says about each film and series — synopsis, genres, rating, network, status, and how many seasons and episodes exist — into the `shows` table. Reads the payloads `add` already cached, so a library that predates the table fills in with no API key and no network. |
| `mediagram posters` | Fetch cover art for the films and series in the index into `<data dir>/posters/`, where a player reading this machine's index finds it. Paths come from the TMDB payloads `add` already cached, so it usually needs no key and no network. Re-running skips what is already held; delete the directory to fetch it again. A course has no provider id and so has no poster. |
| `mediagram prepare <path> [--replace \| --out <dir>] [--mp4] [--audio a,b] [--subs a,b]` | Drop unwanted audio and subtitle tracks. `--mp4` also converts to a browser-playable mp4 — Matroska becomes mp4 and the audio becomes AAC, with the picture copied untouched — so the player stops converting it on every play. Warns when the video codec means that cannot help. `--out` writes a parallel tree; `--replace` rewrites in place, and only after the result passes every check. |
| `mediagram export-package [--publish] [--dry-run] [--out <dir>]` | Assemble the encrypted prebuilt package for a player and, with `--publish`, hand it and its pointer to `publish_cmd`. See [the package spec](docs/mlib-package-v1.md). |
| `mediagram rescan` | Rebuild `library.db` from channel captions. Additive only — never demotes or deletes a locally-recorded set; use `verify` to detect mismatches. |

`--full` re-downloads every requested byte, 512 KiB per request, so a
multi-terabyte library takes hours; the command prints the byte total and a
rough time estimate before it starts. Parts that fail keep their `FAIL` row
and lose any earlier `verified_at`, and one unreachable part no longer ends
the run: it is reported as a failed part and the sweep continues.

### Adding a course

Tutorials are a third kind of content alongside movies and episodes. A course
has chapters, a chapter has lessons, and each lesson is one video file, so it
becomes one ordinary set.

```
mediagram add-course ~/Courses/Rust\ Course --dry-run
mediagram add-course ~/Courses/Rust\ Course
```

The walk treats each subdirectory as a chapter and each video inside it as a
lesson, reading the leading number as the number and the rest as the title.
A flat folder is one chapter. Files that are not video are ignored. The
dry-run table shows every inferred number and title before anything uploads.

Courses never touch TMDB, which has no entry for them, so no API key is
needed on this path. Identity is the collection id plus the chapter and
lesson numbers, which means re-running after an interruption skips what
finished and survives renaming or moving the folder. Pass `--cid` to keep a
course's grouping stable across a retitle. A lesson that was started but
never finished is reported for `mediagram resume` rather than uploaded
again.

Single lessons can be added by hand with `--course`, `--chapter`, `--chap`
and `--lesson` on `mediagram add`.

### Publishing a package for a player

A player can read the pinned `library.db` from the channel, but it has to be
logged in first and it gets no artwork. The prebuilt package is one encrypted
file on a plain URL holding the index and its posters.

```
head -c 32 /dev/urandom | base64      # do this once, keep the output
```

Put that in `package_key`, set `publish_cmd` and `publish_base_url`, then:

```
mediagram export-package --dry-run     # what would be included
mediagram export-package --publish     # write, encrypt, upload
```

The archive is uploaded before `latest.json`, so a player never sees a
pointer to a file that is not there. A failing upload fails the run and no
pointer is published. Give the player the `latest.json` URL and the same key.

The package holds your private channel id and every message id. The key is
the only thing protecting it, so the URL is not a secret but the key is.
Format 1 does not sign the pointer: someone who controls the host can
withhold updates, though they cannot pass off stale content as fresh. The
[spec](docs/mlib-package-v1.md) states the model in full.

### `add` flags

```
mediagram add <file>
  --tmdb <id>        TMDB id (movie or show)
  --tvdb <id>        Stored verbatim; never fetched from TVDB
  --imdb <id>        With or without the `tt` prefix
  --season <n>
  --episode <n>
  --abs <n>          Absolute episode number (anime)
  --variant <label>  Distinguishes alternate cuts/qualities of the same title
  --manual           Enter metadata by hand instead of looking it up on TMDB
  --no-remux         Skip the MP4 faststart remux
  --alang a,b,c       Override detected audio languages
  --slang a,b,c       Override detected subtitle languages
  --hdr <label>       Override detected HDR format (SDR, HDR10, HLG, DV)
  --no-push          Do not push the index after this set completes
```

An explicit `--tmdb`/`--tvdb`/`--imdb` never prompts; without one, an
ambiguous filename match prompts interactively unless `--manual` is given.

## The web player

`web/` is a Bun server that reads the published index, serves any set as one
seekable HTTP file assembled from its parts, and converts on the fly what a
browser will not decode — Matroska, HEVC, AC-3 — into HLS. It runs anywhere
the account's Telegram session can be given to it, not only on the machine
that did the uploading.

```
cd web
bun install
bun run login             # once: issues a session and writes web/.env, mode 600
bun run dev               # listens on the network, and says so
bun run start             # loopback only, for running behind a proxy
```

Its catalog comes either from the `library.db` on this machine or, given
`MEDIAGRAM_PACKAGE_URL` and `MEDIAGRAM_PACKAGE_KEY`, from a package published
by `export-package` — which is what lets it run somewhere the uploader does
not.

It has no authentication of its own, so on anything but a network you trust
it belongs behind a reverse proxy that does.
[`docs/running-the-player.md`](docs/running-the-player.md) covers it end to
end: issuing a session, which titles convert and why, Caddy with TLS, what to
check when playback stalls, and how to revoke access.

## The 3.5 GiB part rule

Files are split into raw, contiguous byte-range parts — never re-encoded —
sized to a configurable multiple of 1 MiB, **3,758,096,384 bytes (3.5
GiB)** by default. That default stays comfortably under Telegram Premium's
4 GB per-message document cap regardless of exactly how that cap is
enforced server-side; a live smoke test uploading a full 3.5 GiB part to a
real Premium channel confirmed no throttling. `part_size` can be tuned in
`config.toml`, up to a hard maximum of `4 GiB − 1 MiB`.

## How it stores things

Every part carries a structured caption (`#mlib v=2` marker + minified
JSON) with the file's metadata, provider ids, and its own byte offset,
length and SHA-256 — full detail in
[`docs/mlib-spec.md`](docs/mlib-spec.md). The local `library.db`
SQLite database is canonical; a snapshot of it is pushed to the channel as
a pinned document after every completed set, so the channel alone is
enough to rebuild the index elsewhere (`mediagram rescan`). Architecture
and data flow are covered in
[`docs/system-architecture.md`](docs/system-architecture.md).

---

This product uses the TMDB API but is not endorsed or certified by TMDB.
