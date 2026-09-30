<div align="center">

# mediagram

**Your own film, series and course library, stored in a private Telegram channel and played back anywhere.**

[![Rust](https://img.shields.io/badge/uploader-Rust%202024-b7410e?logo=rust)](crates/mediagram)
[![Bun](https://img.shields.io/badge/web%20player-Bun-f9f1e1?logo=bun&logoColor=black)](web)
[![Android](https://img.shields.io/badge/app-Android%20%26%20Google%20TV-3ddc84?logo=android&logoColor=white)](android)
[![Version](https://img.shields.io/badge/version-0.66.2-informational)](Cargo.toml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](Cargo.toml)

[How it works](#how-it-works) ·
[Quick start](#quick-start) ·
[Uploading a folder](#uploading-a-folder-of-films-and-shows) ·
[Commands](#commands) ·
[Web player](#the-web-player) ·
[Android](#the-android-app) ·
[Docs](#documentation)

</div>

---

A Rust CLI splits each file into raw byte-range parts (3.5 GiB by default,
under Telegram Premium's 4 GB per-message cap) and uploads every part with a
structured caption. A SQLite index (`library.db`) is the canonical record of
what the channel holds, and a snapshot of it is pinned in the channel itself.
A web player and an Android app read that index and stream the bytes straight
from Telegram, with no media server in between.

Nothing is re-encoded to store it and nothing is recommended by an algorithm.
The library is a catalogue of things you already own, and the job is finding
one and watching it.

## Highlights

**Uploader (`mediagram`)**
- Films, series, documentaries and courses, each identified once, through
  TMDB or from the folder layout.
- Whole folders at a time. Re-running a folder skips what is already
  uploaded, and an interrupted upload resumes without sending a part twice.
- `prepare` makes files browser-playable before upload (mp4 container, AAC
  audio, faststart), so the player does not convert them on every play.
- Uploads run in the background and queue behind each other, one at a time
  or a few side by side (`upload_slots`); `status` shows each upload's
  progress from any terminal.
- Two machines can publish to the same channel: every publish merges the
  channel's index first.
- `verify --full` re-downloads every part and checks it against the SHA-256
  recorded at upload.

**Web player (`web/`)**
- A dark, magazine-style catalogue with TMDB backdrops and posters. Episodes
  are grouped into shows and lessons into courses.
- Profiles (including kids' profiles with age ratings), Continue watching,
  a watchlist and collections. Watch state is shared between devices through
  the channel.
- Seekable playback of any set. What a browser cannot decode (Matroska, HEVC,
  AC-3) is converted to HLS on the fly.

**Android app (`android/`)**
- Phone, tablet and Google TV (leanback) from one app, at parity with the
  web player.
- Uses the same Rust core as the uploader through UniFFI, so there is one
  Telegram byte path, not two.

**LAN cache (`mediagram_cache`)**
- A small chunk store for the home network. A chunk one device fetched from
  Telegram is not fetched again by the next.

## How it works

```mermaid
flowchart LR
    subgraph up["Uploading machine"]
        F["Media files"] --> P["mediagram prepare<br/>(mp4 + AAC, optional)"]
        P --> A["mediagram add / add-show<br/>(identify · split · caption)"]
        F --> A
        A --> DB[("library.db")]
    end

    A -- "parts, 3.5 GiB each" --> CH[("Private<br/>Telegram channel")]
    DB -- "push-index (pinned snapshot)" --> CH

    CH -- "index + byte ranges" --> W["Web player<br/>(Bun · HLS for the rest)"]
    CH -- "index + byte ranges" --> AN["Android app<br/>(phone · tablet · TV)"]
    AN -. "shared chunks" .- C["LAN cache"]
    W --> B["Browser"]
```

Every part carries a caption (`#mlib v=2` plus minified JSON) with the file's
metadata, provider ids, and the part's own offset, length and SHA-256. The
channel alone is therefore enough to rebuild the index (`mediagram rescan`).
The format is specified in [`docs/mlib-spec.md`](docs/mlib-spec.md).

## Quick start

### Requirements

| | |
|---|---|
| Rust | 1.87+ (edition 2024); `rust-toolchain.toml` pins `stable` |
| ffmpeg | `ffmpeg` and `ffprobe` on `PATH`, for inspection and remuxing |
| SQLite | a system `libsqlite3`. `rusqlite` links against it, because grammers already statically links its own copy and two bundled copies collide at link time |
| Telegram | an `api_id`/`api_hash` from <https://my.telegram.org>, and a private broadcast channel where your account is an admin. Premium is needed for the 3.5 GiB parts |
| TMDB | a free [API key](https://www.themoviedb.org/settings/api). Optional if every `add` uses `--manual` |

### Install and sign in

```sh
cargo build --release          # binary at target/release/mediagram
mediagram login                # asks for api_id, api_hash, channel, tmdb_key; then phone → code → 2FA
mediagram whoami               # proves the session works and the channel resolves
```

`login` writes `~/.config/mediagram/config.toml` (mode 600) and must be run in
a real, interactive terminal. If `whoami` cannot find the channel, it lists
every channel the account can see, which usually shows the typo.

### Upload something

```sh
mediagram add "~/Films/Arrival (2016).mkv" --tmdb 329865
mediagram add-show ~/Shows/Severance --tmdb 95396 --dry-run
mediagram add-course "~/Courses/Rust Course" --dry-run
mediagram status
```

`add` returns as soon as the set is planned (inspected, identified, split and
indexed) and leaves the bytes to a background process that outlives the
terminal; its output goes to `<data dir>/background.log`. Pass `--watch` to
stay in the foreground with a live progress line.

#### Subtitles beside a video

When a set is complete, the background process reads its German and English
text subtitles, from inside the file and from files beside it, and sends them
as one small bundle; the log shows `subtitles: German (Forced), German, …`
after `set … added`. A failure only costs the bundle, never the upload.

A file belongs to a video when it is named `<video name>` and then, optionally,
words separated by space, `.`, `_` or `-`, then `.vtt` or `.srt`. Each word is
a language (`de`, `deu`, `ger`, `german`, `deutsch`, `en`, `eng`, `english`,
`englisch`) or a flag (`forced`, `sdh`, `cc`, `hi`); any other word means the
file is another video's (so `Lesson 1` never takes `Lesson 10.srt`).

```text
Film.mkv   Film.srt   Film.de.srt   Film.en.forced.srt   Film.English.SDH.srt
```

With no language in the name, the video's first audio language is assumed.
A `.vtt` wins over an `.srt` of the same track, and an `.srt` that is not
UTF-8 is read as Windows-1252. Picture subtitles (PGS, VobSub) are not read.

#### Subtitles for what is already uploaded

Run these after `pull-index`, with no upload running (they hold the upload
lock, and refuse when `upload_slots` is above 1). Each sends its bundles one
set at a time, two seconds apart, publishes the index every 100 sets and at
the end (`--no-push` skips that), and stops cleanly after the set in hand on
Ctrl-C. Every mode takes `--dry-run`, which prints counts and writes nothing.

```sh
# lessons kept their .vtt in the index: give each a bundle, then drop the rows
mediagram subtitles move-inline
# local files: match by size (or name and duration), then send
mediagram subtitles backfill ~/Movies ~/Shows
mediagram subtitles backfill ~/Movies --accept-fallback ~/Movies/Film.mp4
mediagram subtitles backfill ~/Movies --redo <set-id>
# no local file: read the copy in the channel (MP4 first; --mkv adds the rest)
mediagram subtitles backfill --channel --limit 20
```

`move-inline` reads each bundle back from the channel and compares it with
what it sent before it removes the rows; after three mismatches it stops. A
name-and-duration match is listed but sent only when its file is named with
`--accept-fallback`. `--redo <set>` records a new bundle and deletes the
old message once the index naming the new one is published (with `--no-push`
the old message stays, and the run lists it). `--channel` reads through a loopback server that shares the one
Telegram session; a set whose copy has no German or English text track is
remembered (`meta` `subs-none:<set>`) and skipped next time until `--redo`.

### Watch it

```sh
cd web && bun install
bun run login      # once: issues the player its own session, into web/.env
bun run dev        # serves the library on your network, port 8770
```

## Uploading a folder of films and shows

The everyday job: a download folder holding some films and some series, part
of which may already be in the library. In order:

**1. Merge the channel's index first.** Another machine may have uploaded
titles this one does not know about, and "already held" can only be checked
against an index that includes them:

```sh
mediagram pull-index
```

**2. Prepare the files.** Matroska files and AC-3/E-AC-3 audio are converted
by the web player on every play. `prepare --mp4` fixes that once, before
upload, by changing only the container and the audio. It also drops audio
and subtitle tracks that are not German or English (`--audio`, `--subs`).

```sh
mediagram prepare "Mad Men (2007)"                          # report only
mediagram prepare "Mad Men (2007)" --mp4 --out ~/prepared   # copies, originals kept
mediagram prepare "Mad Men (2007)" --mp4 --replace          # rewrite in place
```

HEVC video cannot be helped this way, because the picture is never
re-encoded; `prepare` says so when it applies. `add-show` runs the same
survey and asks before uploading anything it would leave converting, and
`--yes` skips that question, so pass `--yes` only once you have seen the
report.

**3. Upload what is not there yet.**

- **Series:** `add-show` skips episodes that are already complete, so it
  can be pointed at a whole show every time. Look the id up on TMDB, and
  dry-run first:

  ```sh
  mediagram add-show "Mad Men (2007)" --tmdb 1104 --dry-run
  mediagram add-show "Mad Men (2007)" --tmdb 1104 --no-push
  ```

  It refuses a folder holding two files for one episode, for example an
  `.mkv` beside its converted `mp4/` copy. Point it at one of them.
- **Films:** `add` does **not** check whether a film is already held.
  Look the title up in `mediagram status` (or search `library.db`) before
  adding it again, and pass `--tmdb` so nothing prompts:

  ```sh
  mediagram add "Anaconda (2025).mkv" --tmdb 1234731 --no-push
  ```

- Skip anything still downloading (`*.tmp`, `*.part`).

Uploads queue behind each other, so it is fine to start them one after
another; `mediagram status` shows what is on the wire and the rest waiting.
One goes at a time unless `upload_slots` says more (see Configuration).

**4. Publish once at the end.** Every push re-pins the index, and Telegram
answers a burst of pins with a long `FLOOD_WAIT`. That is why step 3 passes
`--no-push`. When everything is up (or whenever the titles so far should
appear in the players), publish once:

```sh
mediagram push-index
```

Every publish pulls the channel in first, so titles another machine
uploaded in the meantime are kept rather than dropped. Two machines may
upload at the same time, but each must upload its own folders: nothing
de-duplicates across machines until the next merge.

## Commands

| Command | What it does |
|---|---|
| `login` | Sign in with phone + code (+ 2FA password) and persist the session. **Needs a real, interactive terminal.** |
| `whoami` | Print the signed-in account and the resolved library channel. |
| `add <file>` | Split, upload, caption and index one file. Returns once the set is planned; `--watch` stays and shows the upload. `--delete-source` removes the file once every part is in the channel. Does not de-duplicate. See [`add` flags](#add-flags). |
| `add-show <dir> --tmdb <id> [--dry-run] [--yes]` | Upload every episode of a series, one set each. Season and episode come from the file name, the show from `--tmdb` (required, because release prefixes defeat the title guess). Reports what the player would convert and asks first. Re-running skips complete episodes. |
| `add-course <dir> [--dry-run] [--category <name>]` | Upload a course: subfolders are chapters, videos are lessons, PDFs beside them are documents. Re-running skips what finished. `--category` files the course into a row on the Tutorials department page. |
| `add-docu <path> [--dry-run] [--category <name>]` | Upload a documentary, or a folder of them as one collection. Re-running skips what finished. `--category` files it into a row on the Documentaries department page. |
| `resume [--no-push]` | Finish every set an interrupted upload left `pending`, adopting parts already in the channel instead of sending them again. |
| `status` | What the library holds and what is going in: the set on the wire with its progress, the queue, each show against what TMDB says exists, and anything unfinished. Read-only. |
| `prepare <path> [--replace \| --out <dir>] [--mp4] [--audio a,b] [--subs a,b]` | Drop unwanted audio and subtitle tracks. `--mp4` also converts to a browser-playable mp4 (Matroska → mp4, audio → AAC, picture copied untouched). `--replace` rewrites in place, and only after the result passes every check. |
| `push-index [--force]` | Pull the channel index in, then snapshot `library.db` and pin it in the channel. A publish landing from another machine meanwhile is pulled in too. `--force` replaces the channel index as it is, pulling nothing. |
| `pull-index [--dry-run]` | Merge the channel index into this one, so titles uploaded from another machine are known here. Backs up the local index first; `--dry-run` only reports what would change (what `push-index --check` used to answer). |
| `sync-index [--refresh-older-than <days>]` | The whole round trip: `pull-index`, `metadata`, `posters`, then a push. A failed artwork fetch is reported and the push goes ahead. |
| `metadata` | Record what TMDB says about each film and series (synopsis, genres, rating, network, status, season and episode counts, original language). Reads the payloads `add` cached, so it usually needs no key and no network. |
| `posters` | Fetch cover art into `<data dir>/posters/` for a player reading this machine's index. Re-running skips what is held. |
| `artwork` / `edit` | Override a title's poster or backdrop; correct a set's metadata and rewrite its captions. `edit <set-id> --anime yes\|no\|auto` forces a title in or out of the Anime department, or drops back to the automatic rule — index-only, keyed to the TMDB title. `edit <set-id> --category <name>\|--clear-category` files a course or documentary into a row on its department page, or removes it — index-only. |
| `verify <set-id> \| --all [--full] [--since <unix>]` | Compare each part's message against the index; `--full` re-downloads and hashes every part, and `--since` lets an interrupted sweep resume. |
| `remove <set-id>` | Permanently delete a set: its channel messages and its index rows. |
| `rescan` | Rebuild `library.db` from channel captions. Additive only: it never demotes or deletes a set the index already has. |
| `export-package [--publish] [--dry-run]` | Build the encrypted prebuilt package a player can read from a plain URL. See [publishing a package](#publishing-a-package-for-a-player). |
| `serve` | Serve this machine's library over HTTP (Range requests over a set's parts). |

`--full` re-downloads every byte, 512 KiB per request, so a multi-terabyte
library takes hours; it prints the total and an estimate before it starts.
A failing part is recorded as `FAIL` and the sweep carries on.

<details>
<summary><b><code>add</code> flags</b></summary>

<a id="add-flags"></a>

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
  --course, --cid, --chapter, --chap, --lesson   add one course lesson by hand
  --no-remux         Skip the MP4 faststart remux
  --alang a,b,c      Override detected audio languages
  --slang a,b,c      Override detected subtitle languages
  --hdr <label>      Override detected HDR format (SDR, HDR10, HLG, DV)
  --no-push          Do not push the index after this set completes
  --delete-source    Delete the file once every part is in the channel
  --watch            Stay and show the upload instead of backgrounding it
```

An explicit `--tmdb`/`--tvdb`/`--imdb` never prompts. Without one, an
ambiguous filename match prompts interactively unless `--manual` is given.

</details>

<details>
<summary><b>Adding a course</b></summary>

A course has chapters, a chapter has lessons, and each lesson is one video
file, so it becomes one ordinary set.

```sh
mediagram add-course "~/Courses/Rust Course" --dry-run
mediagram add-course "~/Courses/Rust Course"
```

Each subfolder is a chapter and each video inside it a lesson; the leading
number is the number and the rest is the title. A flat folder is one chapter.
The dry-run table shows every inferred number and title before anything
uploads, with `L` marking a lesson and `D` a document.

PDFs are uploaded as documents: a handout beside its lesson, or a workbook in
a folder with no video at all. A document is numbered inside its chapter like
a lesson, so `03 Signal.pdf` sits beside `03 Signal.mp4` in the player.
Artwork and other files are ignored. A lesson's subtitles are the ones beside
its video (see [Subtitles](#subtitles-beside-a-video)). Adding PDFs to a course that
is already uploaded moves nothing: a re-run uploads the documents and skips
every finished lesson.

Courses never touch TMDB, so no API key is needed. Identity is the collection
id plus chapter and lesson numbers, so a re-run survives renaming or moving
the folder; pass `--cid` to keep the grouping stable across a retitle.

</details>

<details>
<summary><b>An existing library, or a new machine</b></summary>

**Same machine, index intact.** The data dir (`~/.local/share/mediagram`, or
`data_dir`) already holds `library.db`, the session and the TMDB cache:

```sh
mediagram status
mediagram resume       # completes every pending set
mediagram verify --all # metadata-only check; --full re-downloads and hashes
```

**New machine, library already in the channel.** Log in, then get an index.
Copying it across keeps what the channel does not record (`verified_at`
timestamps, the TMDB cache):

```sh
# with nothing uploading on the old host; the -wal sidecar holds recent writes
scp old-host:'~/.local/share/mediagram/library.db*' ~/.local/share/mediagram/
```

Otherwise rebuild it from the channel's own captions with `mediagram rescan`,
or merge the pinned index with `mediagram pull-index`. On a machine that has
never run `add` the TMDB cache is cold, so `metadata` and `posters` need a real
`tmdb_key`; copy `tmdb-cache/` to avoid that. Do not copy `session.sqlite`
around casually: it is the account.

</details>

<details>
<summary><b>Publishing a package for a player</b></summary>

<a id="publishing-a-package-for-a-player"></a>

A player can read the pinned `library.db` from the channel, but it has to be
logged in first and it gets no artwork. The prebuilt package is one encrypted
file on a plain URL holding the index and its posters.

```sh
head -c 32 /dev/urandom | base64      # once; keep the output as package_key
mediagram export-package --dry-run    # what would be included
mediagram export-package --publish    # write, encrypt, upload via publish_cmd
```

The archive is uploaded before `latest.json`, so a player never sees a pointer
to a missing file. Give the player the `latest.json` URL and the same key.

The package holds your private channel id and every message id, and the key is
the only thing protecting it: the URL is not a secret, the key is. Format 1
does not sign the pointer, so someone controlling the host can withhold
updates but cannot pass off stale content as fresh. See
[`docs/mlib-package-v1.md`](docs/mlib-package-v1.md).

</details>

## The web player

`web/` is a Bun server that holds its own Telegram session, reads the
published index, serves any set as one seekable HTTP file assembled from its
parts, and converts on the fly what a browser will not decode. It needs
nothing from the uploader's disk, so it can run on any machine.

```sh
cd web
bun install
bun run login      # once: issues a session and writes web/.env, mode 600
bun run dev        # listens on the network, and says so
bun run start      # loopback only, for running behind a proxy
```

Its catalogue comes from the channel's pinned index or, given
`MEDIAGRAM_PACKAGE_URL` and `MEDIAGRAM_PACKAGE_KEY`, from a package published by
`export-package`.

> [!WARNING]
> The player has **no authentication of its own**. On anything but a network
> you trust, put it behind a reverse proxy that does.
> [`docs/running-the-player.md`](docs/running-the-player.md) covers it end to
> end: sessions, which titles convert and why, Caddy with TLS, stalls, and
> revoking access.

## The Android app

`android/` is a Kotlin + Jetpack Compose app for phones, tablets and Google TV.
The Telegram transport and the index come from `crates/mediagram-core`, the
same Rust code the uploader uses, called through UniFFI.

```sh
# needs ANDROID_NDK_HOME and cargo-ndk
scripts/build-android-core.sh          # cross-compile the Rust core for every ABI
cd android && ./gradlew installDebug   # set ANDROID_SERIAL when several devices are attached
```

Rebuild the core after any Rust change. A stale native library still builds,
but the app crashes at launch.

## Configuration

`mediagram login` writes `$XDG_CONFIG_HOME/mediagram/config.toml` on first run.
To write it by hand, copy [`config.example.toml`](config.example.toml) and fill
in `api_id`, `api_hash`, `channel` (a `-100…` id or the exact title) and
`tmdb_key`. Optional keys: `part_size`, `throttle_ms`, `upload_slots`,
`max_attempts`, `tmp_dir`, `data_dir`, and the package settings. Every key can be overridden
with a `MEDIAGRAM_<KEY>` environment variable, and `--config` points at a
different file.

## Under the hood

**The 3.5 GiB part rule.** Files are split into raw, contiguous byte ranges,
never re-encoded, sized to a multiple of 1 MiB: **3,758,096,384 bytes
(3.5 GiB)** by default. That stays comfortably under Telegram Premium's 4 GB
document cap however it is enforced; a live test uploading a full 3.5 GiB part
to a Premium channel saw no throttling. `part_size` can go up to
`4 GiB − 1 MiB`.

**Parallel uploads.** Telegram limits upload speed per connection, not per
account, so `upload_slots = 2` lets two uploads run at once: measured, two
moved 26–31 MB/s where one moved 12–13 MB/s. The catch is rate limiting.
Within an hour of running two, Telegram answered with a transport-level
429 ("too many requests"). An upload now waits that out — 30 s, doubling to
ten minutes — and says so in its output ("Telegram asked to slow down"),
rather than ending the run; if those lines are frequent, the waiting eats
the gain and one slot is the better setting. The default is one.

**Repository layout.**

```
crates/
├── mlib-spec/        the contract: caption, part plan, filename grammar, index schema. No IO
├── mediagram-tmdb/   TMDB client with disk cache, provider details, poster download
├── mediagram-core/   portable client: UniFFI surface for Android, catalog store, byte path
├── mediagram/        the uploader CLI: inspection, upload pipeline, index, verify, serve
└── mediagram-cache/  the LAN chunk store; shares nothing with the uploader's index
web/                  the Bun web player (server in src/, browser app in public/)
android/              the Android app: phone, tablet and TV
```

## Documentation

| Document | Covers |
|---|---|
| [`docs/mlib-spec.md`](docs/mlib-spec.md) | The storage format: captions, parts, index schema |
| [`docs/mlib-package-v1.md`](docs/mlib-package-v1.md) | The encrypted prebuilt package and its threat model |
| [`docs/system-architecture.md`](docs/system-architecture.md) | Crates, data flow, the Android byte path, the LAN cache |
| [`docs/web-player.md`](docs/web-player.md) | The web player's modules, routes and codec policy |
| [`docs/running-the-player.md`](docs/running-the-player.md) | Deploying the player safely |
| [`docs/code-standards.md`](docs/code-standards.md) | Conventions for contributors |
| [`docs/project-changelog.md`](docs/project-changelog.md) | What changed, release by release |

---

<sub>MIT licensed. This product uses the TMDB API but is not endorsed or certified by TMDB.</sub>
