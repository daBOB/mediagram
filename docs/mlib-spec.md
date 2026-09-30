# mlib caption and index spec (v2 and v3)

Normative description of the wire format a video library stored in a
private Telegram channel uses. **Two caption versions are current**: `v3` is
written on every new part, `v2` is what earlier uploads carry, and a reader
must accept both. They differ by two fields, described in §2.

The format covers: what every uploaded part's caption contains,
how files are split and named, how the local index is structured, and the
pinned document that snapshots it to the channel. Everything here is
implemented in the `mlib-spec` Rust crate (`crates/mlib-spec/src`), but this
document is self-contained: a parser can be written from it without reading
Rust.

## 1. Part caption

Every uploaded part (not just part 0) carries a caption with this exact
shape:

```text
#mlib v=2
{"t":"movie","ids":{...},...}
optional free-form human text (truncated first if the caption is too long)
```

- Line 1 is the marker, byte-for-byte `#mlib v=4` on new captions and
  `#mlib v=2` on older ones. A parser identifies an
  mlib caption by checking the text starts with the prefix `#mlib v=`
  (after trimming leading whitespace); the version that follows determines
  how to parse line 2.
- Line 2 is one JSON object, minified (no extra whitespace), encoded as
  UTF-8 with non-ASCII characters written raw (`Amelie` stays `Amélie`, not
  `Am\u00e9lie`), no HTML entities or custom emoji. The caption budget is
  counted in UTF-16 code units rather than bytes, so a raw non-ASCII title
  costs more budget than its byte length suggests. It is the [`Caption`](#2-caption-json)
  record below.
- Any further lines are free-form human text (e.g. an emoji + title line).
  They are not part of the record and must never be parsed for data — they
  exist purely so the channel is pleasant to scroll in the Telegram app.
- CRLF line endings and leading blank lines/whitespace before the marker
  are tolerated by the reference parser.

### Caption budget

Telegram counts caption length in **UTF-16 code units**, not bytes or Rust
`char`s (this is Premium-independent: the free-tier caption limit is used
so parts stay postable regardless of account tier). The whole caption text
— marker line + JSON line + human line — must fit in **1,024 UTF-16
units**. When rendering a caption:

1. The marker + JSON is built first. If that alone already exceeds the
   budget, it is an error — the JSON is never truncated or altered to fit.
2. The human line is then truncated to whatever room is left, cutting on a
   full Unicode scalar boundary (never splitting a surrogate pair).

An emoji outside the Basic Multilingual Plane costs 2 UTF-16 units even
though it is 1 `char`; a parser/writer that counts Rust `chars` or UTF-8
bytes instead of UTF-16 units will compute a different (wrong) budget.

## 2. Caption JSON

### What changed in v3

| Field | Added | Meaning |
|---|---|---|
| `cid` | v3 | Collection id: a stable grouping anchor for sets with no provider id. Courses are its first user; `null` for movies and episodes |
| `chap` | v3 | Chapter title, for course lessons; `null` otherwise |
| `path` | v4 | Folders this set came from within its collection, `/`-separated (`"Ausbildung Trading/1. Grundlagen/1. Trading"`); `null` otherwise |

A v2 caption simply lacks both. A reader decoding v2 treats them as absent,
which is why the uploader can keep writing v3 without rewriting anything
already published.

### The third kind

`t` is `movie`, `ep`, or `tut`. A **tutorial** is one lesson of a course, and
it reuses the fields an episode already has rather than introducing a second
vocabulary:

| Concept | Field |
|---|---|
| Course title | `show` |
| Collection id | `cid` |
| Chapter number | `s` |
| Chapter title | `chap` |
| Lesson number | `e`, always a single number |
| Lesson title | `title` |

Because chapter and lesson live in `s` and `e`, ordering, resume, the
playable invariant and the index schema need no special cases for courses.

A course with no chapters uses chapter 1. A lesson file is named
`Course (Year) - c02l02 - Lesson Title`, with `c`/`l` rather than `s`/`e` so
a course never reads as a television series in a file list.


Field order below is the **wire order**: a byte-for-byte reference
implementation serializes fields in exactly this order, and tooling that
compares captions verbatim (e.g. detecting an unmodified re-upload) depends
on it. Round-trip example (a movie, part 0 of 17, 3.5 GiB parts):

```json
{"t":"movie","ids":{"tmdb":693134,"tvdb":null,"imdb":"tt15239678"},"show":null,"title":"Dune: Part Two","year":2024,"s":null,"e":null,"abs":null,"q":"2160p","hdr":"DV","container":"mkv","vcodec":"hevc","acodec":"truehd","alang":["en","de"],"slang":["en"],"dur":9960,"variant":null,"set":"01JQ8F2K9M4XZ","part":{"i":0,"n":17,"off":0,"len":3758096384,"sha256":"aaaa…(64 lowercase hex chars)"},"total":62914560000}
```

An episode of a show additionally sets `show`, `s` (season) and `e`
(episode), or `abs` for anime-style absolute numbering; a two-episode file
serializes `e` as `[1,2]` rather than `1`:

```json
{"t":"ep","ids":{"tmdb":693134,"tvdb":null,"imdb":"tt15239678"},"show":"Severance","title":"Hello, Ms. Cobel","year":2022,"s":2,"e":[1,2],"abs":11,"q":"2160p", … ,"set":"01JQ8F2K9M4XZ","part":{…},"total":62914560000}
```

| Field | Type | Notes |
|---|---|---|
| `t` | `"movie"` \| `"ep"` | Lowercase string enum. |
| `ids` | object | `{"tmdb": number\|null, "tvdb": number\|null, "imdb": string\|null}`. Every provider id is optional; a manually tagged title may have all three `null`. `imdb` includes the `tt` prefix, e.g. `"tt0816692"`. |
| `show` | string \| null | Show name; `null` for movies. |
| `title` | string \| null | Movie title, or the episode's own title for `t:"ep"`. |
| `year` | integer \| null | Release year. |
| `s` | integer \| null | Season number; `null` for movies and absolute-only anime. |
| `e` | integer \| `[integer,integer]` \| null | Episode number, or an inclusive `[first,last]` range for multi-episode files. `null` for movies. |
| `abs` | integer \| null | Absolute episode number (anime). May coexist with `s`/`e`, or be the only numbering present. |
| `q` | string \| null | Free-form quality label, e.g. `"2160p"`, `"1080p"`. |
| `hdr` | string \| null | `"SDR"`, `"HDR10"`, `"HDR10+"`, `"HLG"`, `"DV"`, or another free-form tag. |
| `container` | string | File extension without the dot, e.g. `"mkv"`, `"mp4"`. Never `null`. |
| `vcodec` | string \| null | e.g. `"hevc"`, `"av1"`. |
| `acodec` | string \| null | e.g. `"aac"`, `"truehd"`. |
| `alang` | array of string | Audio track language codes, in track order. `[]` if unknown. |
| `slang` | array of string | Subtitle language codes. `[]` if none/unknown. |
| `dur` | integer \| null | Duration in whole seconds. |
| `variant` | string \| null | Free-form label distinguishing alternate cuts/qualities of the same title (e.g. `"Director's Cut"`). |
| `set` | string | The set id: a [ULID](https://github.com/ulid/spec) minted once when the file is first split, shared by every part. Never derived from content, so re-uploading the same file produces a different `set`. |
| `part` | object | See [§3](#3-part-block) below. |
| `total` | integer | Logical file size in bytes; equals the sum of every part's `len`. Identical on every part of the set. |

Absent optional values serialize as JSON `null`, never as an omitted key —
a parser can assume every key above is always present.

## 3. Part block

```json
{"i":0,"n":17,"off":0,"len":3758096384,"sha256":"…64 lowercase hex chars…"}
```

| Field | Type | Notes |
|---|---|---|
| `i` | integer | 0-based part index. |
| `n` | integer | Total part count for the set. |
| `off` | integer | Byte offset of this part's first byte within the logical file. **`off` is authoritative for part order and placement** — a reader reconstructing the file seeks to `off` for each part rather than trusting `i` to be contiguous or gapless, though in practice `i` is always `off / part_size`. |
| `len` | integer | Byte length of this part. `sum(len for all parts) == total`. |
| `sha256` | string | Lowercase hex SHA-256 of exactly these `len` bytes (not of the whole file). |

Parts are planned as a raw, contiguous split: part sizes are a configurable
multiple of 1 MiB, default **3,758,096,384 bytes (3.5 GiB)**, comfortably
under Telegram Premium's 4 GB per-message cap regardless of exactly how
that cap is enforced. The maximum accepted part size is `4 GiB − 1 MiB`.
Every part except possibly the last is exactly `part_size` bytes; the last
part holds the remainder.

## 4. Part file naming

Telegram truncates document file names to **60 characters**. Names are a
display convenience only — captions and the index are authoritative, so a
parser must never derive metadata from the file name when a caption is
available. The grammar:

- Base name: `Title (Year)` for a movie; `Show (Year) - s02e01` or
  `Show (Year) - s01e01-e02` for episodes with season/episode numbers;
  `Show (Year) - 011` for absolute-only numbering (no season). The year
  suffix is omitted if unknown.
- Illegal filesystem characters (`/ \ : * ? " < >  |`) are replaced with a
  space and runs of whitespace collapsed.
- Full name: `<base>.<ext>` when the set has exactly one part; otherwise
  `<base>.<ext>.pNNN` with a zero-padded 3-digit part index (e.g.
  `.mkv.p003`). The extension is capped to 8 characters before the suffix
  is appended.
- If the base name plus suffix would exceed 60 characters, the base is
  truncated — preferring to cut at a word boundary if one exists past the
  halfway point of the available room, otherwise cutting hard — so the
  `.pNNN` suffix is never lost.

## 5. Set identity: `set_hash`

Once every part of a set has uploaded successfully, the set's identity
hash is computed as:

```text
set_hash = sha256( concat( lowercase_hex(part[0].sha256), lowercase_hex(part[1].sha256), …, lowercase_hex(part[n-1].sha256) ) )
```

i.e. SHA-256 over the concatenation of every part's hex SHA-256 string,
lowercased and trimmed, in part-index order. This is equivalent in
collision strength to hashing the whole file, but is computable
incrementally as parts finish uploading and independently verifiable part
by part (see `mediagram verify --full`). There is no separate whole-file
hash anywhere in the spec.

## 6. Local index: `library.db`

The local SQLite database is the **canonical** record; the channel only
ever holds a periodically pushed, read-only snapshot of it (§8). Schema
(`crates/mlib-spec/src/schema.rs`, applied as idempotent
`CREATE ... IF NOT EXISTS` migrations):

```sql
CREATE TABLE IF NOT EXISTS sets(
    set_id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    tmdb INTEGER, tvdb INTEGER, imdb TEXT,
    show TEXT, chap TEXT, path TEXT, title TEXT, year INTEGER,
    season INTEGER, episode TEXT, abs INTEGER,
    quality TEXT, hdr TEXT, container TEXT NOT NULL,
    vcodec TEXT, acodec TEXT,
    alang TEXT NOT NULL DEFAULT '[]', slang TEXT NOT NULL DEFAULT '[]',
    duration INTEGER, variant TEXT, group_key TEXT,
    total INTEGER NOT NULL, part_count INTEGER NOT NULL,
    set_hash TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    spec_version INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS parts(
    set_id TEXT NOT NULL REFERENCES sets(set_id) ON DELETE CASCADE,
    idx INTEGER NOT NULL,
    byte_offset INTEGER NOT NULL,
    byte_length INTEGER NOT NULL,
    chat_id INTEGER, message_id INTEGER,
    doc_id INTEGER,
    sha256 TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    verified_at INTEGER,
    PRIMARY KEY(set_id, idx)
);
CREATE INDEX IF NOT EXISTS parts_message ON parts(chat_id, message_id);
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS assets(
    set_id TEXT NOT NULL REFERENCES sets(set_id) ON DELETE CASCADE,
    kind TEXT NOT NULL,          -- 'subtitle' | 'summary'
    lang TEXT NOT NULL DEFAULT '', -- '' when not language-specific
    body TEXT NOT NULL,
    PRIMARY KEY(set_id, kind, lang)
);
```

### Schema v9 additions

Version 9 adds three tables for editorial metadata (credits, franchises) and
series classification:

```sql
-- New columns on existing 'shows' table (both nullable):
ALTER TABLE shows ADD COLUMN collection_id INTEGER;      -- TMDB franchise id
ALTER TABLE shows ADD COLUMN collection_name TEXT;       -- TMDB franchise name
ALTER TABLE shows ADD COLUMN series_type TEXT;           -- TV series type: Scripted, Miniseries, Documentary, Reality, News, Talk Show, Video (null for movies)

-- Cast, crew, creators from TMDB. Part of the primary key to allow
-- multiple credits per title; 'ord' is display order within the title.
CREATE TABLE IF NOT EXISTS credits(
    source TEXT NOT NULL,                 -- 'tmdb' or other provider
    kind TEXT NOT NULL,                   -- 'movie' or 'tv'
    id INTEGER NOT NULL,                  -- TMDB title id
    ord INTEGER NOT NULL,                 -- Display order (cast by billing, then crew, then creators)
    person_id INTEGER NOT NULL,           -- TMDB person id
    name TEXT NOT NULL,                   -- Person's name
    role TEXT,                            -- Character name (for cast) or job (for crew), e.g. 'Director', 'Creator'
    dept TEXT NOT NULL,                   -- 'cast' or 'crew'
    profile TEXT,                         -- Bare TMDB portrait path (e.g. '/abc.jpg'), or null
    PRIMARY KEY(source, kind, id, ord)
);

-- TMDB collections (franchises). Keyed by TMDB collection id.
CREATE TABLE IF NOT EXISTS franchises(
    source TEXT NOT NULL,                 -- 'tmdb'
    id INTEGER NOT NULL,                  -- TMDB collection id (same as shows.collection_id)
    name TEXT NOT NULL,                   -- Collection name
    overview TEXT,                        -- Description, may be null or empty
    PRIMARY KEY(source, id)
);
```

Readers of schema v6–v8 tolerate missing columns/tables (treat them as absent).
A reader probes for the `credits` and `franchises` tables; if absent, it
renders without cast/crew/franchises. A reader that understands v8 but not v9
(schema version comparison or table probe) will never see v9 data; once it
upgrades, its next `library.db` snapshot refreshes from the channel.

The uploader's v9 metadata comes from two backfills after `mediagram metadata`
runs: `backfill_credits` (top 12 cast, directors, series creators) and
`backfill_franchises` (from `belongs_to_collection`). On the web player's end,
portrait keys follow the pattern `tmdb-person-<id>.jpg` (185px wide) and are
downloaded by `mediagram posters --index <snapshot>`.

**`episode` and `role` field formats**

`episode` stores the JSON encoding of the caption's `e` field (a bare
integer or a `[a,b]` array), not a separate season/episode pair, so it
round-trips single- and multi-episode files identically. `role` in credits
carries the character name for cast or the job title for crew; `doc_id` is the
Telegram document id (`Document::id()` — a signed 64-bit integer distinct
from the message id); it is recorded for reference but message lookup
always keys on `(chat_id, message_id)`, since a document can be re-sent
under a different id (e.g. after a forward) while the message stays put.

`verified_at` is the result of the **last** verification, not a high-water
mark: `verify --full` sets it to the check time when the downloaded bytes
hash to `sha256`, and clears it as soon as that part fails a later check. A
non-null value means "this part matched its recorded hash then and has not
failed since"; null means unverified, never verified, or last seen failing.

### Schema v11 additions

Version 11 adds a title's original language and a hand-set anime decision:

```sql
-- New column on existing 'shows' table (nullable):
ALTER TABLE shows ADD COLUMN original_language TEXT;  -- TMDB ISO 639-1 code, e.g. 'ja'

-- A person's decision that a title is, or is not, anime — kept apart from
-- 'shows' because that table's one writer replaces a row whole on every
-- 'metadata' run, which would clobber the decision.
CREATE TABLE IF NOT EXISTS anime_overrides(
    source TEXT NOT NULL,                 -- 'tmdb'
    kind TEXT NOT NULL,                   -- 'movie' or 'tv'
    id INTEGER NOT NULL,                  -- TMDB title id
    anime INTEGER,                        -- 1 = force in, 0 = force out, NULL = automatic
    set_at INTEGER NOT NULL,              -- Unix seconds; decides a merge
    PRIMARY KEY(source, kind, id)
);
```

`original_language` rides the same backfill every other `shows` column does:
`mediagram metadata` reads it back out of the TMDB details payload `add`
already cached, so filling it for an existing library costs no request and
no API key.

`anime_overrides.anime` NULL is a kept row, not a deleted one: clearing an
override has to reach the other machine on the next merge, and a deleted row
carries nothing to merge. Set by `mediagram edit <set-id> --anime
yes|no|auto`, keyed to the TMDB title so one override covers every episode of
a series, including ones uploaded later. `set_at` is what a merge compares —
the newer value wins outright, `anime` included, unlike `shows`'s NULL-only
fill.

### Schema v12 additions

Version 12 adds a hand-set category per course, documentary collection or
standalone documentary — the same unit its custom artwork already keys:

```sql
-- A category on one unit — a course, a documentary collection or a
-- standalone documentary. Kept apart from 'shows' and from a set's own row
-- for the same reason 'anime_overrides' is: it must survive both a
-- 'metadata' run and a 'rescan', and neither writes it.
CREATE TABLE IF NOT EXISTS categories(
    department TEXT NOT NULL,             -- 'tutorials' or 'documentaries'
    item_key TEXT NOT NULL,                -- title_art_key(show ?? title)
    category TEXT,                         -- NULL = cleared, kept as a row
    set_at INTEGER NOT NULL,               -- Unix seconds; decides a merge
    PRIMARY KEY(department, item_key)
);
```

`item_key` is `mlib_spec::package::title_art_key(show ?? title)` — the same
key a unit's custom poster or backdrop already lives under, so a category
survives a re-upload or a resume and follows a course uploaded from two
folders under one title, the way its artwork does. `category` NULL is a kept
row, not a deleted one, for the reason `anime_overrides.anime` NULL is: a
clear has to reach the other machine on the next merge. Set by `mediagram
edit <set-id> --category "<name>"`/`--clear-category`, or by `add-course`/
`add-docu --category "<name>"` before the first upload. A name is trimmed,
collapsed, refused empty or "Other" in any case, and — writer-side only — a
case-insensitive match of a spelling already used by another unit in the
same department adopts that spelling instead. `set_at` is what a merge
compares — the newer value wins outright, `category` included.

A reader on v10 or older simply never sees this column or table; its next
snapshot, once it upgrades, refreshes both from the channel.

### Schema v13 additions

Version 13 adds a set's subtitle bundle — one small gzip'd file per set — and
the tracks it holds:

```sql
-- Where a set's subtitle bundle lives, and how a merge decides whose is
-- newer.
CREATE TABLE IF NOT EXISTS subtitle_files(
    set_id TEXT PRIMARY KEY REFERENCES sets(set_id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL,  -- where to fetch it
    bytes INTEGER NOT NULL, sha256 TEXT NOT NULL,           -- to verify a download
    uploaded_at INTEGER NOT NULL                            -- Unix seconds; decides a merge
);

-- One row per track in the bundle, so a reader can show a label and pick a
-- track before ever fetching the file.
CREATE TABLE IF NOT EXISTS subtitle_tracks(
    set_id TEXT NOT NULL REFERENCES subtitle_files(set_id) ON DELETE CASCADE,
    track INTEGER NOT NULL,               -- position inside the bundle's own track list
    lang TEXT NOT NULL,                   -- 'de' | 'en' | 'und'
    forced INTEGER NOT NULL DEFAULT 0,
    sdh INTEGER NOT NULL DEFAULT 0,
    label TEXT NOT NULL,
    PRIMARY KEY(set_id, track)
);
```

Kept apart from `assets`: a bundle is one small message per set, fetched once
and cached, not text that rides every index push whether or not anyone ever
plays that title. `assets` keeps its inline `kind = 'subtitle'` rows only
until every reader has moved to the bundle (a later schema removes the inline
read path); a set with a `subtitle_files` row keeps no inline rows of its own.

`subtitle_files.sha256` is the lowercase 64-hex digest of the bundle's gzip
bytes — the same shape `mlib_spec::subtitle_bundle::valid_sha256` checks
before it becomes a file name. `uploaded_at` is what a merge compares to
decide whose bundle is newer, `subtitle_tracks` included: unlike `shows`, a
bundled set is not filled in column by column — the newer upload for that set
is the whole truth, and its tracks are replaced whole rather than merged
row by row, because the two uploaders never split one set's bundle between
them.

A reader on v12 or older simply never sees these tables; its next snapshot,
once it upgrades, refreshes them from the channel. A push over a channel that
already has these tables but whose own build predates them (an old uploader
publishing after a v13 push) drops them from the channel — the uploader
guards against this: a push or pull refuses a channel index whose caption
names a newer schema than the build understands, and a pull that finds the
tables missing while the local index still holds rows republishes to restore
them.

### The `#mlib-subs` bundle document

A set's subtitle bundle travels as its own small channel document, never
pinned and never mistaken for a part or an index snapshot: its caption starts
`#mlib-subs`, distinct from a part's `#mlib v=` and an index's `#mlib-index`,
so `rescan` and the index-snapshot search skip it without special-casing it.

```text
#mlib-subs v=1
{"set":"01JQ8F2K9M4XZ00000000042"}
```

The document itself (name `<set_id>.subs.json.gz`, MIME type
`application/gzip`) is gzip'd JSON:

```json
{
  "v": 1,
  "set": "01JQ8F2K9M4XZ00000000042",
  "tracks": [
    { "lang": "de", "forced": true, "sdh": false, "label": "German (Forced)",
      "source": "embedded", "codec": "subrip", "vtt": "WEBVTT\n\n…" }
  ]
}
```

`tracks[i]` corresponds to `subtitle_tracks.track = i` for that set. `source`
is `embedded` or `sidecar` — where the uploader read the track from; `codec`
is the original codec (`subrip`, `ass`, `mov_text`, `webvtt`, `srt`, `vtt`),
kept for reference, though every reader plays the `vtt` field regardless.
A reader decodes the bundle through capped reads at every stage — compressed
size, decompressed size, and each track's cue text — before trusting any of
it, and refuses a `v` newer than it understands. `mlib_spec::subtitle_bundle`
is the one place this shape is written down; the uploader and every reader
share it from there.

#### What the uploader writes

Once a set is complete the uploader reads its German and English text
subtitles (from the file the person named, not from a remux) and sends one
bundle per set, in one ffmpeg pass into a private temp folder:

- **Tracks:** embedded `subrip`, `ass`/`ssa`, `mov_text`, `webvtt` and `text`
  streams, and sidecar files `<stem>[ ._-]<words>.vtt|.srt` beside the video,
  where every word is a language (`de|deu|ger|german|deutsch`,
  `en|eng|english|englisch`) or a flag (`forced`, `sdh`, `cc`, `hi`). A
  sidecar with no language is in the video's first audio language, else `und`;
  other languages are skipped. Picture subtitles (PGS, VobSub) are skipped.
  Tracks tagged in neither language, or titled as commentary, are skipped.
- **Forced:** the `forced` disposition; else a title naming `forced` or
  `erzwungen`; else, for an embedded track claiming neither that nor SDH, few
  cues: at most 120 an hour, or at most a quarter of the cues of the densest
  track in its language. Sidecars are forced by their name only.
- **SDH:** the `hearing_impaired` disposition, or a title naming `SDH`, `CC`,
  `hearing` or `hörgeschädigt`. Forced wins when both are claimed.
- **One track per (`lang`, `forced`, `sdh`):** a sidecar beats an embedded
  track, a `default` embedded track beats a later one, a `.vtt` beats an
  `.srt`.
- **Labels and order:** `German`, `German (Forced)`, `English (SDH)`,
  `Subtitles` for `und`; by language, forced before regular.
- **Recording:** in one transaction, the tracks replace the set's
  `subtitle_tracks`, the `subtitle_files` row is upserted with
  `uploaded_at = now`, the set's inline `assets` subtitle rows are deleted and
  a publish is owed. Removing a set deletes its bundle message too.

A failure anywhere in this leaves the set complete without a bundle.

### Playable invariant

A set is considered complete/playable exactly when:

```sql
s.status = 'complete'
AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)
```

`parts.status` is `'pending'` until a part's message has been sent (or
adopted from a prior interrupted run) and `'done'` once its `chat_id`,
`message_id`, `doc_id` and `sha256` are all recorded.

## 7. `#mlib-index` document

After every set completes, the uploader snapshots `library.db` (a WAL
checkpoint followed by `VACUUM INTO` a temp file, so a concurrent write can
never be captured mid-write) and sends it to the channel as a pinned
document named `library.db`, MIME type `application/vnd.sqlite3`, with the
caption:

```text
#mlib-index v=2
{"pushed_at":1700000000,"schema":9,"sets":42}
```

`pushed_at` is a Unix timestamp, `sets` is the row count of the `sets`
table at snapshot time, `schema` is `mlib_spec::schema::SCHEMA_VERSION`
(the `library.db` table layout version — distinct from the caption spec
version `v=2`, which carries `v=2` or `v=3` captions on parts). The index
caption's `schema` field reads current on every push; READABLE_SCHEMAS
(in the crate) defines which index versions this reader accepts. This marker
(`#mlib-index v=`) never collides with a part caption's marker (`#mlib v=`),
so a reader can tell the two apart by prefix alone. Each push pins the new
index message and unpins whatever index message it replaces. A reader
looking for the current index reads the channel's pinned messages and also
searches the channel for the marker — a publish interrupted between sending
and pinning, or a second machine publishing, leaves a snapshot unpinned —
and picks the newest `#mlib-index` one among both. "Newest" is by `pushed_at`, and a reader does
not believe one more than a day ahead of its own clock — such a caption is
treated like one whose time cannot be read, so it never outranks a real
snapshot. Only the channel's own posts are candidates: a library is a broadcast
channel, where only its admins can post, and a message anyone else managed to
put there is not a snapshot anyone published. The marker, the JSON
line and the choice (`index_caption::newest`) live in
`mlib_spec::index_caption`; the uploader, the Android core and the web player
all choose with it.

### `meta` keys

| Key | Meaning |
|---|---|
| `schema_version` | Current `library.db` schema version, refreshed on every open. |
| `last_push_at` | Unix timestamp recorded into the snapshot just before it is vacuumed out, so the pushed copy carries its own push time. |
| `index_message_id` | Message id of the currently pinned `#mlib-index` document. |
| `stale_index_message_id` | Set when unpinning a previous index message failed; retried on the next push, cleared once it succeeds (or the message turns out to already be gone). |
| `pulled_index_message_id` | Message id of the channel index this index last took in, pulled or published from here. A publish that finds it still current pulls nothing. Uploader bookkeeping; readers ignore it. |
| `publish_owed` | Set when an upload session completed sets it did not publish (`--no-push`, another upload about to publish, a failed publish), or when a pull finds the channel missing its subtitle tables while this index holds rows; a counter, so a publish settles only the debt it read before its snapshot. The next session that may publish does. Uploader bookkeeping; readers ignore it. |
| `source:<set_id>` | Absolute path of the source file for a still-`pending` set, so `resume` can find it again. Deleted once the set completes. |
| `tmp:<set_id>` | Path of a faststart-remux temp file `add` produced for a set, so it can be cleaned up once the set completes. Only set when a remux actually happened. |

## 8. Versioning

- `v=4` is written on every new caption; `v=2` and `v=3` remain readable. A
  reader accepts both, because a channel holds a mix from before and after an
  uploader upgrade.
- `v=2` was the previous caption marker version. `mlib_spec::SPEC_VERSION`
  (now `3`) is written into every new `sets.spec_version` row.
- A parser accepts every version it can decode, not just the latest, so a
  channel holding a mix from before and after an uploader upgrade stays
  fully readable. The reference parser accepts `v=2`, `v=3` and `v=4`; the v3
  additions are optional fields, so a v2 caption decodes into the same
  structure with both absent.
- A future change bumps the marker again, and adds an accepted version
  rather than replacing one.
- A spec bump **never** requires re-uploading already-posted media: the
  caption is the only thing that changes shape, and old parts keep working
  with a parser that still understands their version.
