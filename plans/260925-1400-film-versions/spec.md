# Film versions — design spec

Date: 2026-09-25 · Status: awaiting review · Target version: 0.45.0

## Intent

One film can be held in more than one **version**: different qualities (HD,
4K, HDR) and different **cuts** (Director's Cut, Extended, …). The library
shows the film **once**, and the viewer picks which version to play. The two
kinds of version are treated differently because they are different things:
quality copies are the same film at another size, cuts are different films
with different timelines.

Decisions taken with the user (2026-09-25):

| Question | Decision |
|---|---|
| Shelf | One card per film; its page lists the versions |
| Which plays | Ask every time there is more than one version |
| Progress / played | Played per film; position carries between quality copies of the same cut, never across cuts |
| Cut label at upload | Guessed from the filename, confirmed; `--variant` overrides |
| Approach | Group on read by TMDB id; index format, state DB and sync format unchanged (approach A) |

Rejected: an explicit group written into `group_key` (overloads the course id,
needs a backfill and index push); watch state keyed per film (changes the
state DB and sync format on both surfaces for rules computable on read).

## Starting point

- The index has carried `sets.variant TEXT` since schema v1, and
  `mediagram add --variant` already stores it (`commands/args.rs`,
  `index/set_row.rs`) and puts it in the caption. No player reads it.
- Live library (2026-09-25): 581 films, 93 at 2160p, **none held twice**,
  `variant` never set, every film has a TMDB id and none relies on IMDb alone.
  Nothing to migrate.
- Watch state (progress, watched) is keyed by `set_id`, i.e. per file.

## Terms

- **Film**: every `kind = movie` set sharing a poster key (`tmdb-movie-<id>`).
  A set with no poster key is a film of its own.
- **Version**: one set within a film.
- **Cut**: a version's `variant`; `null` is the **standard** cut.
- **Quality**: the existing `quality` (`2160p`, `1080p`, `720p`, `480p`) and
  `hdr` (`SDR`, `HDR10`, `HLG`, `DV`) columns, read from the stream at upload.

## Uploader

**Cut from the filename** — a pure function in `media/file_names.rs`,
films only, matching whole tokens case-insensitively across `.`, `_`, `-` and
space separators. Only the tokens **after the year** are read, which is where
release names put their tags (`Blade.Runner.1982.Final.Cut.2160p`); a name
with no year is read whole. Without that, `DC.League.of.Super-Pets.2022` would
be a Director's Cut and "The Extended Family" an extended one. Matches map to
one canonical spelling:

| Filename says | Stored `variant` |
|---|---|
| `Directors Cut`, `Director's Cut`, `DC` (as a whole token) | `Director's Cut` |
| `Extended`, `Extended Edition`, `Langfassung` | `Extended` |
| `Uncut`, `Ungeschnitten`, `Unrated` | `Uncut` |
| `Final Cut` | `Final Cut` |
| `Remastered` | `Remastered` |
| `Theatrical`, `Kinofassung`, nothing | `null` (standard) |

Canonical spellings matter because players group copies by cut: two
spellings of one cut would never share a position.

**Confirmation** — the existing resolve/confirm step shows
`Cut: Director's Cut (from the filename)`, editable. `--variant` skips the
guess. Stored where it is today: `sets.variant` and the caption.

**Existing film notice** — when the resolved TMDB id is already held:
`Adds a version to "Blade Runner (1982)" — already held: 1080p · standard`.
If the new file matches an existing version's **quality, HDR and cut**, the
uploader asks before uploading (almost certainly a duplicate costing
gigabytes of upload).

Unchanged: index schema, quality/HDR detection, episodes and courses.

## Grouping rule

Shared by both surfaces; the web is the reference.

**Version order**:
1. standard cut first, then other cuts by label, alphabetically;
2. within a cut, higher resolution first (`2160p` > `1080p` > `720p` > `480p` > unknown);
3. at equal resolution, HDR (any non-`SDR` value) before SDR;
4. then by `set_id`, so the order is total and stable.

**Representative** — the first version in that order draws the card and the
page header: poster, title, year, genres, rating, description, age rating.

**Version label** — `<cut or "Standard"> · <quality>[ HDR] · <runtime> · <size>`,
e.g. `Standard · 4K HDR · 2 h 11 min · 58 GB`,
`Director's Cut · 1080p · 2 h 57 min · 14 GB`. `2160p` reads as `4K`.
Runtime is always shown: it is how cuts are told apart. Parts missing from
the index are omitted, as elsewhere.

**Card** — gains a line `N versions` only when N > 1. Single-version films
render exactly as today.

**Where films replace sets**:
- Film shelves, genre shelves and search results list each film once (a hit
  on any version shows the film).
- Kids profiles filter each version by its own rating **before** grouping, so
  a film is shown when any version is allowed, only allowed versions are
  offered, and the representative is the first allowed version.
- Watchlist and collections keep storing a `set_id`. Adding a film stores its
  representative; any stored version displays as its whole film.
- Offline badge: per version on the rows; on the card when any version is
  held in full.

## Watch-state rules

Nothing stored changes: progress and watched rows stay per `set_id`, written
for the version actually played. The database, the sync record and both
merges are untouched. All of the below is computed on read.

- **Played** — a film is played when any version has a watched row. The row
  of each finished version says `Played`. Marking played by hand marks the
  version on screen; unmarking clears the watched rows of **every** version.
- **Position** — for a version *v*, take the newest progress row among
  versions with *v*'s cut; offer it unless a version of that cut was
  finished after it was saved. It never crosses cuts. The existing
  resume thresholds (`resume-point`) then apply per row, unchanged.
- **Continue** — a film appears once when any version has a resumable
  position, ordered by its newest such position. Opening it opens the
  chooser.

Accepted limit: carrying a position assumes quality copies of one cut share a
timeline; two releases may differ by seconds.

## Web UI

- **Film page** (`public/lib/catalog/film-page.js`), more than one version:
  the Play button is replaced by a **Versions** list in version order. Each
  row: label, technical line, `Resume at mm:ss` / `Played` / nothing, and its
  own Play. Runtime and quality leave the header for the rows.
- **Chooser** — cards that play directly (Continue, Watchlist, collections,
  search) open a dialog in the audio chooser's style with the same rows. Esc
  or an outside click cancels; nothing plays.
- The player itself is unchanged: no switching mid-playback.

## Data plumbing

- Grouping key: the poster key, already delivered to the browser and to
  Android. Nothing new.
- `variant`: added to the browser's set in `web/src/catalog/routes.ts`, and to
  the core's film record handed to Kotlin, with the bindings regenerated
  (`MediaSet.variant`).

## Android

A port of the web decisions:

- `Entry.Film` holds its versions in version order; `Entry.Film.set` remains
  the representative so existing card code is untouched.
- Title page: the same Versions list and rows.
- Chooser: a **bottom sheet** instead of a centred dialog — the platform's
  widget for a short pick-one list. Behaviour is identical; this is the one
  deliberate difference between the surfaces.
- `N versions` line, played per film, one Continue entry per film: as on the web.

## Out of scope

Switching version during playback; remembering a preferred version; automatic
version choice; versions of episodes or lessons; an IMDb fallback for grouping
(no film in the library needs it).

## Testing

- Rust unit tests for the filename cut rules, including names that must not
  match: a tag inside the title before the year (`DC.League.of.Super-Pets.2022`,
  `The.Extended.Family.2019`) and a tag inside a longer word. A year-less name
  with a title word that looks like a tag is a known limit; the confirmation
  step is where it is caught.
- Two shared fixtures, read by the web tests and the Android tests, in the
  pattern of `web/test/fixtures/watch-state/next-up.json`:
  - `web/test/fixtures/film-versions/grouping.json` — sets in; cards, version
    order, representative and labels out.
  - `web/test/fixtures/film-versions/watch-state.json` — versions, progress and
    watched rows in; played flag, per-row position and Continue entries out.
- Web UI checked with the stub harness, never the real player.
- Android on the real device with a **test profile only**; every title played
  is reported, since test plays land in Continue.

## Delivery order

Each step ships on its own:

1. Uploader — cut guess and existing-film notice.
2. Web — reference implementation with both shared fixtures.
3. Core and Android — `variant` plumbing, then the port against the fixtures.

## Docs and release

Update `docs/system-architecture.md` (film grouping, the two fixtures) and
`docs/project-changelog.md`. Minor version bump in all three manifests
(new feature, no breaking change).
