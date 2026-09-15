# Red team: scope & complexity critic — prebuilt metadata package plan

Lens: YAGNI enforcer. Target: `plans/260915-1956-prebuilt-metadata-package-for-player/`
(plan.md + 6 phase files, 753 lines). Context: single user, one private channel,
one Android TV client that does not exist, pinned `library.db` already in the
channel. No praise below. 9 findings.

---

## Finding 1: The package duplicates the pinned index; the only genuine delta is artwork
- **Severity:** Critical
- **Location:** plan.md, "Decisions (settled with the user)" and the whole of phases 1, 4, 5
- **Flaw:** The plan's own reader algorithm has the player authenticate to
  Telegram and stream parts by `(chat_id, message_id)` (`phase-06:53-54`). A
  client that can do that can also read the channel's pinned messages — where
  `library.db` is already pushed, pinned and unpinned-on-replace after every
  completed set (`push_index.rs:95-140`), with a caption carrying
  `pushed_at`/`sets`/`schema` for a cheap change check (`push_index.rs:64-71`),
  documented as "a reader looking for the current index reads the channel's
  pinned messages and picks the newest `#mlib-index` one"
  (`docs/mlib-spec-v2.md:245-247`). Encryption, `key_id`, `latest.json`,
  `publish_cmd`, `publish_base_url`, the static host, the rollback guard and the
  size ceiling exist *only* because the plan chose to move data to a public
  HTTPS origin instead of the channel the player is already authenticated
  against. What the package buys over the pin, precisely: (a) artwork bytes,
  (b) no TMDB key in the player. Both fit in a second pinned document.
  Everything else is self-inflicted.
- **Failure scenario:** ~3 of 4.5 planned days and 6 new crate dependencies
  (`plan.md:90-97`, a 23% increase over the 26 direct deps in
  `crates/mediagram/Cargo.toml:9-36`) buy a transport swap, not a capability.
  Recurring cost afterwards: a hosting bill, a key that the operator must
  transcribe into a TV app with a D-pad (`phase-06:69` — 44 base64 characters
  on a remote control), and two publish paths to keep consistent instead of
  one. The plan itself concedes the pin "is unaffected; this package is an
  additional, player-facing convenience" (`plan.md:63-64`).
- **Evidence:** `crates/mediagram/src/commands/push_index.rs:27-37,95-140`;
  `docs/mlib-spec-v2.md:227-247`; `phase-06-docs-and-player-integration.md:53-54`;
  `plan.md:55-64`; `crates/mediagram/Cargo.toml:9-36`
- **Suggested fix:** Publish `art.tar` (or `art.zip`) as a second pinned
  document in the same channel, caption `#mlib-art v=1` with `{art_count,
  bytes, pushed_at}`. Deletes phases 1, 4, 5 and half of 6. If HTTPS delivery
  is later genuinely wanted (it is not, for one user), it is a strictly
  additive change on top of a working artwork pipeline.

---

## Finding 2: The determinism requirement is both unobservable and provably unachievable
- **Severity:** Critical
- **Location:** plan.md "Success (whole plan)" line 124-126; phase 4 "Key insight" and step 2
- **Flaw:** Two independent defects. (1) *Unobservable*: no consumer ever sees
  the inner `tar.gz`. `latest.json` carries only the sha256 of the **encrypted**
  file (`phase-01:63`, `phase-05:47`), which the plan states changes every run
  (`phase-04:23`). The claimed benefit — "a player can compare the manifest
  cheaply" (`phase-04:28-29`) — is served by `created_at`/`bytes` in the
  pointer, not by tar byte-identity. (2) *Unachievable as stated*: the success
  criterion is "running the export **twice with no library changes** produces a
  byte-identical inner tar.gz". `manifest.json` contains `created_at`
  (`phase-01:41`), a wall-clock timestamp, so the tar differs every run. Worse,
  `snapshot_to` writes `meta.last_push_at = now` into the database *before*
  `VACUUM INTO` (`index/snapshot.rs:24-30`), so the `library.db` member differs
  every run too. Phase 3 step 1 mandates reusing that exact function
  (`phase-03:46-47`).
- **Failure scenario:** Sorted-entry / zeroed-mtime / fixed-uid tar writing plus
  a determinism test is implemented and maintained; the whole-plan success
  criterion then fails on the first run and someone spends an afternoon
  discovering that two timestamps they wrote themselves are the cause. Nobody
  downstream would have noticed either way.
- **Evidence:** `plan.md:124-126`; `phase-01-package-format-and-manifest.md:41`;
  `phase-04-encryption-and-archive.md:20-30,90-91,106`;
  `crates/mediagram/src/index/snapshot.rs:24-30`; `phase-03-export-assembly.md:46-47`
- **Suggested fix:** Drop deterministic tar and its test. Use `tar` defaults. If
  a "did anything change?" signal is wanted, it already exists for free: the
  `sets` count plus `max(created_at)` from the snapshot, which the pin's caption
  publishes today.

---

## Finding 3: `sets.art_key` needs a migration framework that does not exist, and the column is derivable in one SQL expression
- **Severity:** High
- **Location:** Phase 2, "Implementation Steps" step 1; plan.md "Cross-plan relationship"
- **Flaw:** `MIGRATIONS` is a flat `&[&str]` of `CREATE TABLE IF NOT EXISTS`
  statements re-executed unconditionally on **every** `db::open`
  (`mlib-spec/src/schema.rs:7-38`, `index/db.rs:24-27`), guarded by a test
  asserting every entry contains `IF NOT EXISTS` (`schema.rs:48-55`). There are
  no version numbers. `ALTER TABLE sets ADD COLUMN art_key TEXT` cannot be made
  idempotent in that shape: the second `db::open` on an existing database fails
  with `duplicate column name: art_key`. plan.md:145-149 asserts "whichever
  lands second takes the next version number and appends its own migration
  group … must not reuse the first one's number" — those numbers are a
  *proposal* in an unimplemented spec
  (`docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:113-128`),
  not code. Phase 2 budgets one line (0.25d of a 1d phase) for work that means
  changing `MIGRATIONS` to `&[&[&str]]`, rewriting `db::open` to read
  `meta.schema_version` and apply groups transactionally, rewriting the schema
  test, and updating `docs/mlib-spec-v2.md`. **Separately, the column is
  unnecessary**: the priority table (`phase-02:45-52`) is
  `COALESCE('tmdb-'||tmdb, 'set-'||set_id)` over columns that already exist
  (`schema.rs:8-23`), evaluated in the one place that needs it (the export).
  The stated justification — "avoids re-deriving priority rules in two places"
  (`phase-02:66-68`) — names no second place.
- **Failure scenario:** Ship the ALTER as written and *every* command
  (`add`, `resume`, `verify`, `rescan`, `push-index`) fails at startup on the
  user's existing database until the framework is retrofitted.
- **Contract verification — consumers of the interfaces phase 2 changes:**
  | Interface | Consumers | Count |
  |---|---|---|
  | `mlib_spec::schema::MIGRATIONS` | `index/db.rs:24`; `schema.rs:49,51` (test) | 3 |
  | `db::open(...)` (breaks if a migration errors) | src: `add.rs`, `resume.rs`, `verify.rs`, `rescan.rs`, `push_index.rs`, `index/snapshot.rs`; tests: `index_state.rs`, `index_snapshot.rs`, `index_rescan.rs`, `upload_pipeline.rs`, `live_add.rs`, `edge_cases_probe_index_rescan.rs`, `edge_cases_probe_upload_pipeline.rs` | 26 call sites / 13 files |
  | `sets::COLUMNS` (insert + 2 selects must list a new column) | `index/sets.rs:9,22,81,92` | 1 const, 3 queries |
  | `SetRow` struct (new field) | `index/set_row.rs:10-39`, `from_caption:44-76`; `sets::insert_set` params | 2 construction sites |
- **Evidence:** `crates/mlib-spec/src/schema.rs:4-7,48-55`;
  `crates/mediagram/src/index/db.rs:24-34`; `plan.md:145-149`;
  `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:113-128`;
  `phase-02-artwork-capture-at-add-time.md:64-68`. Prior review already called
  this out: `plans/260914-1954-…/reports/code-reviewer-260914-2046-phase-01-mlib-spec-crate-review-report.md:66`
- **Suggested fix:** Cut `art_key` and the migration entirely. Derive the key in
  the export query. Let the tutorial work, which actually needs a new column,
  pay for the migration framework once.

---

## Finding 4: Phase 2's `add`-path surgery serves only `--manual` sets and a command that does not exist
- **Severity:** High
- **Location:** Phase 2, "Architecture" priority table and steps 3, 4, 6
- **Flaw:** Three of the phase's six steps build for cases that are empty today.
  Row 1 of the priority table is `cid-{cid}`, sourced from `add-course`
  (`phase-02:49`) — there is no `add-course`: the CLI has 8 subcommands and none
  is it (`main.rs:22-54`), and the tutorial design has no implementation plan
  (`plan.md:134-137`). Step 6 admits it: "Guard this behind existence of the
  command so the phases stay orderable" (`phase-02:77-78`) — that is
  speculative generality, written down. Row 3 (`set-{set_id}`, ffmpeg frame
  capture) fires "only when the set has no provider id" (`phase-02:72-73`),
  i.e. only `--manual` adds (`commands/args.rs:29-31`). So a movie/TV library
  resolved through TMDB — the documented main path — exercises exactly one row
  of a three-row table, and that row (`tmdb-*`) needs no code in `add` at all.
  The black-frame retry heuristic (`phase-02:88-90`) is the sole reason the
  `image` crate appears in the dependency list; `plan.md:95` justifies `image`
  as "poster downscaling", which is also unnecessary — TMDB serves pre-sized
  poster paths (`w342`/`w500`) and the capture already scales via ffmpeg
  (`phase-02:69-71`).
- **Failure scenario:** ~0.6d building a frame capturer, a luminance heuristic
  and a dead `cid` branch, plus a new heavy dependency tree, to produce JPEGs
  for the minority of sets; the majority path gets nothing that a single export
  query would not give. Success criterion `phase-02:84` ("not a black or
  **letterbox-only** frame, for a sample of fixtures") cannot pass: no
  letterbox detection is specified anywhere, and the repo's fixtures are
  synthetic (`crates/mediagram/src/media/test_fixtures.rs`).
- **Second defect in the same design:** phase 2 modifies only
  `commands/add.rs` (`phase-02:56-62`), and captures "after a successful
  upload" (step 4). A set finished by `resume` never gets art — `resume_one`
  completes the set and deletes `source:{set_id}` (`commands/resume.rs:76-83`)
  with no capture hook — and the source path is then unrecoverable, which is
  the exact irreversibility the phase exists to avoid (`phase-02:18-23`).
  Capturing after a 1-hour upload instead of during inspect also means a
  `kill -9` in that window loses the frame permanently.
- **Evidence:** `crates/mediagram/src/main.rs:22-54`;
  `crates/mediagram/src/commands/args.rs:29-31`;
  `crates/mediagram/src/commands/resume.rs:46-84`;
  `phase-02-artwork-capture-at-add-time.md:45-52,64-78,84,88-90`; `plan.md:95,134-144`
- **Suggested fix:** Ship TMDB posters only. Delete steps 1, 2, 3, 4, 6, the
  `image` dependency and the black-frame heuristic. Sets with no `tmdb` id get
  no art — a case the player must already handle (`plan.md:143-144`). Revisit
  frame capture if and when `--manual` sets actually dominate the library.

---

## Finding 5: The TMDB image paths need no new columns — they are already on disk
- **Severity:** High
- **Location:** Phase 2, step 5 ("store them on the set row"); plan.md "Constraints discovered in the codebase"
- **Flaw:** `DiskCachedApi` caches the **entire raw** TMDB response as
  `serde_json::Value`, keyed by path + sorted query
  (`metadata/tmdb_client.rs:126-149`), and `resolve` fetches `/movie/{id}` and
  `/tv/{id}` (`metadata/resolve.rs:128-132`). TMDB details and search payloads
  carry `poster_path`/`backdrop_path`; the typed mirror simply drops them
  (`metadata/tmdb_types.rs:69-82`). The export can call
  `api.get_json("/movie/{id}", …)` and hit the on-disk cache — no network, no
  schema change, no `add`-path change. Phase 2 step 5 instead proposes storing
  both paths "on the set row" — two further columns that appear in no
  migration step, no manifest, and no consumer list, on top of the `art_key`
  column of Finding 3.
- **Failure scenario:** Three new columns (`art_key`, `poster_path`,
  `backdrop_path`) and a migration framework are built to persist data that is
  already cached in `~/.local/share/mediagram/tmdb-cache/*.json`, and every
  future `sets` query (`index/sets.rs:9,22,81,92`) carries them forever.
- **Evidence:** `crates/mediagram/src/metadata/tmdb_client.rs:94-149`;
  `crates/mediagram/src/metadata/resolve.rs:128-132`;
  `crates/mediagram/src/metadata/tmdb_types.rs:69-82`;
  `phase-02-artwork-capture-at-add-time.md:74-76`; `plan.md:76-79`
- **Suggested fix:** Add `poster_path`/`backdrop_path` to `DetailsResponse`
  (2 lines, `#[serde(default)]`) and read them at export through the existing
  cached `TmdbApi`. Zero schema change, zero `add` change.

---

## Finding 6: Per-file sha256 in the manifest is dead weight under an AEAD
- **Severity:** High
- **Location:** Phase 4 "Key insight" table; Phase 3 step 6 and success criteria
- **Flaw:** The plan's own crypto framing puts a 128-bit GCM tag over the whole
  ciphertext and requires `Cipher.doFinal` so plaintext is returned only after
  the tag verifies (`phase-04:32-34,60-66`), with the success criterion "a
  flipped bit **anywhere** in the file makes decryption fail"
  (`phase-04:108`). A manifest sha256 per art file
  (`phase-01:47-49`, `phase-03:56-57`) claims to "detect a corrupt member"
  (`phase-04:22`) — a state that cannot exist: any corrupt member means the tag
  fails and no bytes are released. The "dual-hash design" is one real hash (the
  ciphertext hash, which catches a truncated download before decrypt) plus one
  decorative one.
- **Failure scenario:** For 300 titles with posters and backdrops: ~600 extra
  sha256 passes per export, ~40 KB of manifest bloat inside a size-ceilinged
  package, a phase 3 success criterion to maintain (`phase-03:64`), and a spec
  section a future player author must implement and verify — for zero detection
  power. If the player *does* implement the check, it duplicates work the AEAD
  already did on every startup.
- **Evidence:** `phase-04-encryption-and-archive.md:20-23,32-34,108`;
  `phase-01-package-format-and-manifest.md:46-49`;
  `phase-03-export-assembly.md:56-57,64`
- **Suggested fix:** Drop `sha256` from `ArtEntry`. Keep the pointer's
  ciphertext sha256 (it guards the download, before any tag is available).

---

## Finding 7: `key_id`, the rollback guard and same-day suffixes defend a one-user setup against threats that cannot occur — and one of them is a footgun
- **Severity:** Medium
- **Location:** Phase 1 "Architecture" (key_id, naming); Phase 4 "Risk Assessment" (rollback); Phase 6 "Reader algorithm" steps 2-3
- **Flaw:** Three mechanisms, one user, one key, one host.
  - **`key_id`** (`phase-01:70-74`) saves one wasted 35 MB download in the
    only scenario it can fire: a key mismatch, which for one operator with one
    key happens at most once, during setup. Cost: a field in the public
    pointer, a spec rule, a reader step (`phase-06:42-43`), a success criterion
    (`phase-01:99`), and 32 bits of `sha256(key)` published in the clear.
  - **Rollback guard** (`phase-04:128-131`, `phase-06:38-40`) requires the
    not-yet-existing player to persist `created_at` across reinstalls. The
    worst outcome it prevents is an attacker who controls the static host
    replaying an archive *the user published themselves*, whose contents are a
    subset of the current catalog: the player shows fewer titles. The realistic
    trigger is the operator legitimately republishing an older package (host
    restore, clock skew, a re-run on a machine with a stale library) — and the
    spec provides no override, so the player refuses the only package on the
    server with no documented escape.
  - **Same-day `-2`/`-3` suffixes** (`phase-01:76-78`) have no defined source
    of truth for the collision check. The export writes to a local `--out`
    directory (`phase-03:18`) while collisions matter on the remote host. Clean
    the local output directory, export twice in a day, and the helper re-issues
    `-2`, overwriting the published `-2` — precisely the "a file is never
    silently replaced" property the feature claims. The "a player can pin one"
    justification is also unused: nothing in the plan's reader algorithm ever
    pins a version (`phase-06:34-54`).
- **Failure scenario:** Three mechanisms, three spec sections, three success
  criteria, one silent-overwrite bug, and one way to brick a TV client — all
  in defence of a single-operator deployment where the disaster-recovery copy
  is already pinned in Telegram (`plan.md:63-64`).
- **Evidence:** `phase-01-package-format-and-manifest.md:70-78,99-100`;
  `phase-04-encryption-and-archive.md:124-131`;
  `phase-06-docs-and-player-integration.md:34-45`; `phase-03-export-assembly.md:18`
- **Suggested fix:** Drop all three. Name the file
  `mediagram-art-<sha256[0..8]>.tar` (collision-free by construction, no state,
  no suffix allocator). If the wrong-key case is ever hit, the error message at
  decrypt is the diagnostic.

---

## Finding 8: `gen-key` as a subcommand — and it cannot run before the config it is meant to fill exists
- **Severity:** Medium
- **Location:** Phase 4, step 4
- **Flaw:** `mediagram gen-key` (`phase-04:97-98`) replaces
  `head -c 32 /dev/urandom | base64`. It is also structurally broken as
  specified: `main.rs` calls `config::load` **before** dispatching to any
  subcommand (`main.rs:62-64`), and `load` bails when `api_hash` or `channel`
  is empty (`config.rs:86-88`). The one command whose job is to produce a
  config value therefore requires a complete, valid config to run — and would
  need either a special-case in `main` (breaking the uniform load-then-dispatch
  shape) or a documented "fill the file first, then generate the key" dance.
- **Failure scenario:** A new `Cmd` variant, a new module, a doc section and a
  test are added; the first person to run it on a fresh machine gets
  "api_hash and channel must be set" from a key generator.
- **Contract verification — consumers of the `Cmd` enum / CLI surface:**
  `crates/mediagram/src/main.rs:21-54` (enum) and `main.rs:64-78` (match) — 2
  sites, 1 file; plus `crates/mediagram/src/commands/mod.rs` (module list) and
  `README.md` (104 lines, command list). `Config` itself is only ever built
  through serde — grep for struct-literal construction outside `config.rs`
  returns **0** sites, and `config::load` has 8 call sites (`main.rs:63`,
  `tests/live_add.rs:34`, `tests/edge_cases_probe_config_retry.rs:34,59,79,97,122,140`) —
  so adding optional keys is genuinely backwards compatible. That part of
  phase 5 is safe; `gen-key` is the unsafe part.
- **Evidence:** `crates/mediagram/src/main.rs:21-78`;
  `crates/mediagram/src/config.rs:67-90`;
  `phase-04-encryption-and-archive.md:97-98`
- **Suggested fix:** Delete the command. Put the one-liner in the README next
  to `package_key`. If a key is ever needed and absent, the export error can
  print the exact shell command.

---

## Finding 9: Six phases and 4.5 days for a convenience feature, against 7 phases and 7 days for the entire product
- **Severity:** Medium
- **Location:** plan.md "Phases" table; phase 1 in its entirety; phase 5
- **Flaw:** The previous plan spent 7d across 7 phases to build the whole
  uploader: Telegram auth, streaming 3.5 GiB part upload with resume, TMDB
  resolution, index push/rescan, verify
  (`plans/260914-1954-…/plan.md:20-26`). This plan spends 4.5d — 64% of that —
  to write a DB copy and some JPEGs to a URL. The inflation is visible per
  phase: phase 1 (0.5d) defines four serde structs, a filename helper and a
  `key_id` function, yet carries its own Key Insight, Requirements,
  Architecture, five success criteria and a Risk Assessment; phase 5 (0.5d) is
  "spawn a shell command with `{file}` substituted, then write a JSON file";
  phase 6 (0.5d) is docs. Phases 1, 3, 4 and 5 are one command and two modules.
  The ceremony did not even buy accuracy: every manifest and pointer example
  states `"schema": 2, "spec": 3` (`phase-01:43-44,65-66`, `phase-05:49-50`)
  while the actual constants are `SCHEMA_VERSION = 1`
  (`mlib-spec/src/schema.rs:4`) and `SPEC_VERSION = 2`
  (`mlib-spec/src/lib.rs:27`) — a player author implementing from the phase-1
  examples would hard-code the wrong compatibility floor.
- **Failure scenario:** Four days of a single-user project's budget spent
  before the Android client — the only consumer, which does not exist — has
  proven it wants the format at all. If the client is never written, 100% of
  phases 1, 4, 5, 6 is waste; if it is written, the format will change on
  contact with the first real UI.
- **Evidence:** `plan.md:36-48`; `plans/260914-1954-telegram-linux-uploader-mlib-spec-v2/plan.md:18-28`;
  `phase-01-package-format-and-manifest.md:43-44,65-66,97-109`;
  `crates/mlib-spec/src/schema.rs:4`; `crates/mlib-spec/src/lib.rs:27`
- **Suggested fix:** Two phases (see MVP below). Fix the version examples to
  `schema: 1, spec: 2` wherever they survive, or generate them from the
  constants.

---

## Bonus: the size ceiling is policy for a problem that does not exist yet
- **Severity:** Medium (folded here; not counted among the 9)
- `phase-04:68-81` and `plan.md:107-114` build a warn-at-48 MB / refuse-at-64 MB
  rule, a "largest artwork contributors" ranking, and a pre-declared format v2
  with chunked framing — for a measured ~35 MB payload. The refusal has no
  override flag, so the single operator whose library crosses the line gets a
  hard stop with no documented workaround. The cheap cut is upstream: drop
  backdrops (the plan's own 35 MB figure is "posters **and** backdrops",
  `plan.md:112-113`), roughly halving the payload and making the ceiling moot.

---

## The MVP

**Smallest genuinely useful version: artwork in the channel, two phases, ~1 day.**

*Phase A — art fetch (0.5d).* Add `poster_path` to `DetailsResponse`
(`metadata/tmdb_types.rs:69-82`, 2 lines). New `export/art.rs`: read the
snapshot, `SELECT set_id, tmdb FROM sets`, fetch `w342` posters for distinct
`tmdb` ids through the existing `DiskCachedApi`, write
`art/tmdb-<id>.jpg`. Sets with no `tmdb` id get no art.

*Phase B — publish (0.5d).* `mediagram push-art`: tar the `art/` directory,
`upload_stream` it to the channel, pin it with caption
`#mlib-art v=1\n{"pushed_at":…,"count":…,"bytes":…}`, unpin the previous one —
the same 40 lines `push_index.rs:79-184` already proved in production.

**What the MVP cuts, and why each cut is safe**

| Cut | Why it is safe |
|---|---|
| AES-256-GCM, `package_key`, `key_id`, `gen-key` | Data stays in the private channel, protected by the same Telegram auth that protects every part. No key to transcribe with a remote. |
| `latest.json`, `publish_cmd`, `publish_base_url`, static host | The pinned message *is* the pointer; `pushed_at` is the freshness check. No hosting, no credentials, no shell-exec config key. |
| Rollback guard, same-day suffixes, format-v2 framing, size ceiling | All are consequences of publishing to an untrusted origin. Telegram supersedes-by-pin, and a 4 GB part limit dwarfs a poster tarball. |
| `sets.art_key`, `poster_path`/`backdrop_path` columns, schema migration | Derivable at export (`COALESCE('tmdb-'||tmdb,…)`) and already in the TMDB disk cache. Leaves the migration framework for the tutorial work that actually needs it. |
| ffmpeg frame capture, black-frame heuristic, `cid-` branch, `image` crate | Serves `--manual` sets and a command that does not exist. Placeholder art is a player-side one-liner. |
| Deterministic tar, per-file sha256, manifest | Unobservable, redundant under an AEAD, and replaceable by the pin caption's counts. |
| Backdrops | Halves the payload; a TV grid shows posters. |

**Total kept:** 0 new dependencies, 0 schema changes, 0 changes to `add`, 0 new
config keys, ~120 lines of new code. **Total cut:** 6 dependencies, 1 migration
framework, 3 config keys, 2 new commands, 1 hosting dependency, 1 normative
spec document, and ~3.5 of 4.5 planned days.

If the Android client is later built and *proves* it wants HTTPS delivery (it
will not: it needs MTProto anyway to stream the media), the encrypted-package
work is strictly additive on top of a working art pipeline — and by then it
will be designed against a real UI instead of a hypothetical one.

## Unresolved questions

1. Is there any consumer of this package other than the single Android TV
   client — e.g. sharing the catalog with someone who is *not* a member of the
   private channel? If yes, Finding 1 weakens substantially and the HTTPS path
   is justified; nothing in the plan states such a consumer, and `plan.md:55-64`
   implies there is none. This is the one answer that could keep phases 1/4/5.
2. Does the operator actually want `--manual` (non-TMDB) titles in the library
   at meaningful volume? If the library is >90% TMDB-resolved, phase 2 collapses
   to the two-line change in Finding 5.
3. `SCHEMA_VERSION` is 1 and `SPEC_VERSION` is 2, but every example in phases 1
   and 5 says 2 and 3. Intentional placeholder, or copied from the tutorial
   spec's *proposed* bump? Either way the examples must not ship as written.
