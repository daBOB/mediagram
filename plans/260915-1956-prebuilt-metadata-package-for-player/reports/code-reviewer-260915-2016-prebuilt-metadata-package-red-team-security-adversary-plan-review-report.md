# Red-team review: prebuilt metadata package (security adversary)

Reviewer: code-reviewer (hostile, attacker mindset). Plan under review:
`plans/260915-1956-prebuilt-metadata-package-for-player/`. Code evidence from
`crates/`, `docs/`, `Cargo.lock`, crates.io (2026-09-15).

---

## Finding 1: The rollback guard is built on data the attacker writes

- **Severity:** Critical
- **Location:** Phase 4, "Risk Assessment" → **Rollback**; Phase 6, "Reader algorithm" steps 2, 5, 6
- **Flaw:** GCM is used with **no associated data** (phase 4 "Architecture":
  `[ nonce 12 | ciphertext | tag 16 ]`, nothing else). Nothing binds the
  ciphertext to the pointer. Every field the reader makes a trust decision on
  — `created_at`, `bytes`, `sha256`, `file`, `key_id`, `schema` — lives in
  `latest.json`, unauthenticated, served by the same host as the archive. The
  mitigation text ("the reader algorithm requires refusing a `created_at`
  older than the package it already holds") compares an attacker-supplied
  number against local state.
- **Failure scenario:** Host compromise, a stale/poisoned CDN edge, or anyone
  who can write to the bucket. The attacker takes yesterday's archive —
  captured from the same public URL, still encrypted under the same key,
  still passing GCM — and republishes it as
  `latest.json {created_at: now+1, file: <old archive>, sha256: <old file's real hash>}`.
  Reader step 2 passes (timestamp is newer), step 3 passes (`key_id`
  unchanged, there is only ever one key), step 5 passes (hash matches the
  file actually served), step 6 passes (the tag verifies — it is a genuine
  package). The player silently rolls back to an older library: sets deleted
  after a copyright takedown reappear, `(chat_id, message_id)` rows that now
  point at deleted messages come back, and the "newest package" the player
  holds is permanently older than reality. The guard never fires because the
  only monotonic value the attacker cannot forge — `created_at` inside the
  encrypted `manifest.json` (phase 1 "Architecture") — is never compared.
  Freezing a player on a chosen snapshot is indefinite and undetectable.
- **Evidence:** phase-04 lines 39-43 (framing, no AAD), 128-130 (the
  mitigation); phase-01 lines 38-51 (`created_at` exists in the manifest but
  is unused by the reader), 55-67; phase-06 lines 34-50. The plan contains no
  occurrence of "AAD", "associated data" or "sign" (`grep -rni` over all six
  plan files returns only "by design"/"redesign" matches). The research
  report the plan is derived from explicitly flagged this:
  `plans/reports/researcher-260915-1956-prebuilt-metadata-package-crypto-and-packaging-report.md:306`
  — *"Signing: The `latest.json` should be signed (Ed25519 or RSA) to prevent
  MITM attacks. Not covered in this report but critical for security."* The
  plan drops that recommendation with no stated rationale.
- **Suggested fix:** (a) Pass the pointer's authenticated fields as AAD:
  `aad = format!("{format}|{created_at}|{key_id}|{file}")`, and have the
  reader reconstruct the AAD from the pointer it fetched — a swapped archive
  then fails the tag. (b) Move the rollback comparison to
  `manifest.created_at` (inside the ciphertext) and make it the normative
  step, keeping the pointer comparison as a pre-download optimisation only.
  (c) Add an Ed25519 signature over `latest.json` with the public key baked
  into the player — that is the only fix that survives an attacker who also
  holds the symmetric key (see Finding 5).

---

## Finding 2: "Path traversal has no surface" is false — `set_id` is remote input

- **Severity:** Critical
- **Location:** Phase 2, "Risk Assessment" (*"keys are ids and slugs only,
  never raw titles, so path traversal has no surface"*) and "Architecture"
  (`set-{set_id}-poster.jpg`); Phase 6, "Reader algorithm" step 7 ("Unpack")
- **Flaw:** The art key `set-{set_id}` is interpolated into a filename, into
  the manifest's `poster`/`backdrop` **path strings**, and thence into tar
  member names that an Android player writes to disk. `set_id` is not a
  locally minted ULID in all cases: `rescan` parses captions off Telegram
  messages and writes `caption.set` straight into `sets.set_id`, and
  `mlib_spec::caption_codec::parse` is a bare `serde_json::from_str` with
  **zero field validation**. `caption.set` is a free `String`.
- **Failure scenario:** A second channel admin, a compromised account, or a
  forwarded/edited message posts a part caption whose JSON carries
  `"set": "../../../../../../data/data/com.example.player/files/key"` (or
  simply `"set": "../../x"`). `rescan` ingests it — it is a well-formed
  caption. The next `export-package` derives `art_key = set-../../x`, writes
  `art/set-../../x-poster.jpg` into the staging tree and into the manifest,
  and the tar carries that member path verbatim. The uploader clobbers a file
  outside the staging directory; the player, following a spec whose step 7 is
  the single word "Unpack", writes outside its sandbox. The payload is
  authenticated (the attacker did not need the key — they only needed one
  message in the channel), so every integrity control in the design passes it
  through. The same input path also makes `art/` a write primitive for
  absolute paths and symlink members, neither of which the spec forbids.
  Secondary DoS on the same step: the reader downloads `bytes` from an
  attacker-controlled host with no cap — serving a 4 GB body to a TV that the
  plan itself budgets at 192-256 MB heap (phase 4, size table) is a trivial
  crash loop, and `bytes` is unauthenticated anyway (Finding 1).
- **Evidence:** `crates/mlib-spec/src/caption.rs:72-73` (`pub set: String`,
  "ULID minted when the set was added" — a comment, not a constraint);
  `crates/mlib-spec/src/caption_codec.rs:75-89` (`parse` = marker check +
  `serde_json::from_str`, no validation of `set`);
  `crates/mediagram/src/index/rescan.rs:57-71` (parsed caption →
  `upsert_set(conn, &caption, now)`); `crates/mlib-spec/src/schema.rs:9`
  (`set_id TEXT PRIMARY KEY`, no CHECK); phase-02 lines 41, 51, 91-92;
  phase-03 lines 30, 56; phase-06 line 51.
- **Suggested fix:** Validate at the boundary, not at the filename: reject a
  caption whose `set` is not 26 chars of Crockford base32 in
  `caption_codec::parse` (one `regex`, the crate already depends on `regex`),
  so `rescan` cannot poison the index. Independently, make `art::key_for`
  return a type whose constructor rejects anything outside `[A-Za-z0-9-]`,
  and make phase 6's step 7 normative: reject any tar member with `..`, a
  leading `/`, a drive/UNC prefix, a non-regular type (symlink/hardlink/
  device), a path outside `art/` or `{library.db, manifest.json}`, more than N
  members, or an uncompressed total above the 64 MB ceiling; and abort the
  download once it exceeds `min(bytes, 64 MB)`.

---

## Finding 3: Every plaintext copy of the package is written world-readable

- **Severity:** High
- **Location:** Phase 3, "Architecture" (staging tree) and step 1; Phase 4,
  "Architecture" (staging → tar → gzip → encrypt); Phase 3 success criterion
  "The staging directory is removed on success and on failure"
- **Flaw:** The plan's entire confidentiality argument is "a leaked URL must
  yield ciphertext only" (plan.md, Decisions → Protection). It says nothing
  about the plaintext that exists on the uploader host for the duration of
  every export: the `VACUUM INTO` copy of `library.db` (private channel id +
  every message id), the `art/` tree, `manifest.json`, and the intermediate
  `tar.gz`. All are created through `std::fs`/`VACUUM INTO` with the process
  umask — 0644/0755 on a stock system. This repo already has the opposite
  standard, written down and implemented, for exactly this class of data.
- **Failure scenario:** Uploader runs on a box with any second account, a
  container sharing `/var/lib`, a backup agent, or a Dropbox/Syncthing folder
  under `--out`. During the export window (minutes: TMDB fetches, gzip of
  ~35 MB, encryption) any local user reads `library.db` and the plaintext
  `tar.gz` and walks away with the private channel id and every message id —
  the exact asset encryption was added to protect. Worse, the window is not
  bounded: phase 3's "removed on success and on failure" is a success
  criterion with no mechanism behind it. A `?` return, a panic, SIGINT (the
  binary already registers a signal handler) or a kill leaves the whole
  plaintext tree behind, permanently and world-readable. The existing code
  shows this is not hypothetical: `push_index` leaks its temp snapshot on
  exactly those paths (`remove_file` is only reached on the normal return).
  Add to this that `config.toml` — which phase 4/5 turn into the file holding
  the AES-256 key *and* an arbitrary shell command — never gets its
  permissions checked or fixed anywhere in the codebase.
- **Evidence:** `crates/mediagram/src/telegram/client.rs:172-187` — data dir
  `0o700`, session `0o600`, with the comment "libsql keeps the auth key in
  WAL/SHM sidecars too, so the directory itself is private";
  `docs/code-standards.md:51-54` states this as a project rule;
  `crates/mediagram/src/index/snapshot.rs:39` (`VACUUM INTO` — inherits
  umask); `crates/mediagram/src/commands/push_index.rs:44-53` (temp snapshot
  removed only on the normal path); `crates/mediagram/src/paths.rs:13-19` and
  `crates/mediagram/src/config.rs:67-90` (config path is joined and read; no
  mode check anywhere — `grep -rn "set_permissions\|from_mode"` over
  `crates/` returns only `telegram/client.rs:178,186` and test files). The
  plan files contain no occurrence of `chmod`, `0600`, `0700` or `umask`.
- **Suggested fix:** Create the staging root with `0o700` before writing into
  it and `0o600` on `library.db`/`manifest.json`/`*.tar.gz`, mirroring
  `restrict_session_permissions`. Wrap the staging dir in a guard type whose
  `Drop` removes it, so panics and `?` clean up; keep a `--keep-staging` flag
  for debugging rather than relying on happy-path cleanup. Add a startup
  check that refuses to load a `config.toml` whose mode is group/other
  readable once `package_key` is present, and say so in `config.example.toml`.

---

## Finding 4: `publish_cmd` hands every secret in the process to an arbitrary shell

- **Severity:** High
- **Location:** Phase 5, step 2 ("Run through the shell so operators can pipe
  or chain") and "Risk Assessment" (*"It is the user's own config file, which
  already holds the API hash, so the trust boundary is unchanged"*)
- **Flaw:** Two distinct problems, one of them argued away incorrectly.
  (a) The trust-boundary claim is wrong. Today, an attacker with write access
  to `config.toml` can make the uploader leak an `api_hash` — a credential
  that is useless without the session. After phase 5, the same write gives
  **arbitrary code execution as the operator**, every time `export-package
  --publish` runs, with no prompt and no echo of the command outside
  `--dry-run`. Read-a-secret → run-code is an escalation, not a lateral move.
  (b) `std::process::Command` inherits the parent environment. `config::load`
  deliberately supports `MEDIAGRAM_API_HASH`, `MEDIAGRAM_TMDB_KEY`,
  `MEDIAGRAM_DATA_DIR` env overrides, and phase 4 adds `package_key` to the
  same `Config` (so a `MEDIAGRAM_PACKAGE_KEY` override will follow by
  symmetry). Nothing in phase 5 clears or filters the child's environment.
- **Failure scenario:** Operator follows the documented pattern and exports
  secrets from a systemd unit or `.envrc` (`MEDIAGRAM_API_HASH=…`,
  `MEDIAGRAM_PACKAGE_KEY=…`). `publish_cmd = "rclone copy {file} r2:mediagram/"`
  spawns `/bin/sh`, which spawns `rclone`, which inherits both. `rclone -vv`
  on a failure, an rclone plugin, a crash handler that uploads env to Sentry,
  or `RCLONE_CONFIG_PASS`-style debugging all put the package key and the
  Telegram api_hash somewhere the operator did not intend — and the package
  key is the *only* thing protecting every archive ever published (Finding
  5). Separately, the escaping rule is undefined: the plan says "Substitute
  `{file}` with the absolute path, quoted", but `{file}` is substituted into
  a string the operator already quoted. `publish_cmd = "scp \"{file}\" host:"`
  becomes `scp "'/path/x.enc'" host:` — a wrong filename; and any path
  containing `'` (a title-derived `--out` directory, a mount point) breaks the
  quoting outright. The success criterion "A filename or path containing
  spaces or quotes is passed correctly" cannot be met by a substitution into
  an arbitrary shell string; it can only be met by argv.
- **Evidence:** `crates/mediagram/src/config.rs:92-116` (env overrides for
  `API_HASH`, `TMDB_KEY`, `DATA_DIR`), `config.rs:12-31` (secrets live on
  `Config`, which phase 4 extends); `crates/mediagram/src/media/remux.rs:38-45`
  — the existing subprocess pattern is `Command::new("ffmpeg")` with `.arg()`,
  **no shell**, which phase 5 departs from without acknowledging it;
  phase-05 lines 70-82, 91-95.
- **Suggested fix:** Default to argv execution: split `publish_cmd` with a
  POSIX word splitter and replace the `{file}` **token** with one argv
  element — quoting stops existing as a concept. If shell chaining is kept,
  make it opt-in (`publish_shell = true`) and documented as RCE. Either way,
  call `.env_clear()` and pass through only an explicit allowlist
  (`PATH`, `HOME`, `RCLONE_*`/`AWS_*` as configured), so no
  `MEDIAGRAM_*` secret reaches the child. Do not add a
  `MEDIAGRAM_PACKAGE_KEY` env override at all — a key in the environment is
  in `/proc/self/environ` and in every child process.

---

## Finding 5: One shared symmetric key is also the forgery credential, and it ships to consumer TVs

- **Severity:** High
- **Location:** Phase 4, "Risk Assessment" → Key rotation; Phase 6, steps 2-3
  ("transcribe the key into the player"); plan.md, "Key dependencies"
- **Flaw:** AES-GCM with a key held by every reader provides confidentiality
  against outsiders and **no origin authentication at all**. The plan treats
  a successful tag verification as proof the package came from the uploader
  (phase 6 step 6: "A failure here means a wrong key or a corrupt file"). It
  means neither. There is one key, for all packages, forever; "rotation" is
  documented as "re-export and update the player", i.e. not a revocation —
  all previously published archives stay decryptable by whoever ever held the
  key, and they stay fetchable **by design** (plan.md Decisions → Discovery:
  "old packages remain fetchable").
- **Failure scenario:** The key lives in an Android TV app on a device the
  operator does not control — dumped via `adb`, an unencrypted app backup, a
  rooted box, a decompiled APK, a second-hand TV sold with the app installed,
  or a family member who once had the key transcribed to them. With it the
  attacker (1) decrypts every archive ever published, retroactively, because
  the dated URLs are stable and guessable, and (2) **forges** a package: a
  `library.db` whose rows point at `(chat_id, message_id)` of their choosing,
  sealed with the same key, indistinguishable from a real one. The player has
  no way to tell, because possession of the key *is* the authentication.
  Nothing in the plan revokes anything. Separately, `key_id` — the first four
  bytes of `sha256(key)` — is published in the clear at a fixed URL; it is a
  stable pseudonymous identifier that links every host, every archive and
  every rotation attempt of one operator, and it is a free offline oracle for
  confirming a guessed key (relevant precisely because phase 6 step 3
  institutionalises *hand-transcribed* keys, which operators shorten and
  mistype into memorable ones). `gen-key` also cannot be run in isolation:
  `main.rs:62-63` calls `config::load` before dispatching **any** subcommand,
  and `config::load` bails when `api_hash` or `channel` is empty
  (`config.rs:86-88`) — so generating a key on a clean or air-gapped machine
  requires inventing a fake Telegram config first, which is exactly how
  operators end up generating the key on the wrong box or reusing a dummy
  config they then forget to replace.
- **Evidence:** phase-04 lines 115-127 (risk list covers nonce reuse, key
  loss, rotation, rollback — never key compromise or forgery); phase-01 lines
  70-74 (`key_id` = `sha256(key)[0..4]`, published); phase-05 line 48;
  phase-06 lines 66-69; plan.md lines 58-62, 104-105; research report line
  306 (signing recommended, "critical for security", dropped).
- **Suggested fix:** Sign `latest.json` (and the ciphertext hash) with
  Ed25519; the player holds only the public key, so a leaked decryption key
  costs confidentiality of old archives but not integrity of new ones.
  Derive the per-package key with HKDF from the master key plus a random
  32-byte salt stored in the file header — then no two packages share a key,
  `key_id` need not be published, and archives stop being linkable. If
  `key_id` is kept, compute it as
  `HKDF-Expand(key, "mlib-package key-id")[0..4]`, not a raw hash prefix.
  Add a documented revocation story ("what to do when a TV is lost"): at
  minimum, `export-package` should be able to delete or refuse to keep old
  archives.

---

## Finding 6: `latest.json` plus immutable dated URLs is a free traffic-analysis feed

- **Severity:** Medium
- **Location:** Phase 1, "Requirements" (*"the pointer is published in the
  clear, so it must reveal nothing about the library's contents"*) and
  success criterion "A test proves the pointer contains no library content";
  Phase 5, "Architecture"
- **Flaw:** The stated test is "the pointer serializes with no field that
  names a title, a set, a chat or a message" (phase 1 step 5). Every field
  that actually leaks passes that test. `bytes` is a direct proxy for library
  size (the plan itself calibrates ~35 MB ↔ 300 titles, phase 4 size table,
  so ±1 poster ≈ ±1 title). `created_at` and the `YYYYMMDD[-N]` filename give
  the operator's activity calendar to the day, including how many exports ran
  per day. `key_id` links installations. `cipher`, `format`, `schema`, `spec`
  fingerprint the exact uploader build. The filename literally contains
  `prebuilt_mediagram_db`, identifying the tool. And because old archives are
  kept deliberately at predictable names, anyone with the base URL — which
  phase 6 step 2 correctly says is *not* a secret — can enumerate the whole
  history with a date loop and diff the `Content-Length`s to reconstruct a
  growth curve of the private library over months.
- **Failure scenario:** The base URL appears in a browser history, a router
  DNS log, a shared Chromecast, or a bucket with listing enabled. The observer
  never breaks any crypto and still learns: this person runs mediagram, has
  roughly N hundred titles, added content on these 47 dates, took the library
  from 12 MB to 61 MB over a year, and is the same operator as the library at
  that other URL (same `key_id`). For a private channel whose existence is the
  thing being protected, that is most of the metadata that matters.
- **Evidence:** phase-01 lines 30-32, 55-74, 94-95, 102, 104-107; phase-05
  lines 37-56; phase-04 lines 72-76 (the size↔title calibration that makes
  `bytes` informative); plan.md lines 60-62.
- **Suggested fix:** Drop `bytes`, `schema` and `spec` from the pointer — the
  reader learns all three from the manifest after decrypting, and `bytes` is
  worthless as a control anyway since it is unauthenticated (Finding 1). Pad
  the ciphertext to a coarse bucket (e.g. next 8 MB) so `Content-Length` stops
  tracking the catalogue. Use an opaque random archive name instead of a date.
  Rewrite the phase-1 test from "names no title/set/chat/message" to an
  exact-field allowlist that fails when *any* field is added.

---

## Finding 7: The `art_key` migration bricks every existing install on first run

- **Severity:** High
- **Location:** Phase 2, step 1 ("Add `art_key TEXT` to `sets` as a schema
  migration"); plan.md, "Cross-plan relationship" (*"Whichever lands second
  takes the next version number and appends its own migration group"*)
- **Flaw:** There is no migration framework to append a group to. `MIGRATIONS`
  is a flat `&[&str]` and `db::open` executes **every element on every open**,
  unconditionally, with `?` on failure. It works today only because all four
  statements are `CREATE … IF NOT EXISTS`. SQLite has no
  `ALTER TABLE … ADD COLUMN IF NOT EXISTS`. The moment `ALTER TABLE sets ADD
  COLUMN art_key TEXT` joins that list, the first open creates the column and
  the **second open fails** with `duplicate column name: art_key` — and
  `db::open` is the first thing `add`, `resume`, `rescan`, `verify`,
  `push-index` and the new `export-package` all call. There is also an
  existing test that asserts every migration string contains `IF NOT EXISTS`,
  so this lands as a red test, not a silent break.
- **Failure scenario:** Operator upgrades, runs `mediagram add`, it works; runs
  anything at all afterwards and every command dies at startup with a SQL
  error. The only recovery is downgrading the binary or hand-editing
  `library.db`. The plan's coordination note with the tutorial work ("take the
  next version number") describes a mechanism that does not exist, so both
  efforts will hit this independently, and the `schema_version` value recorded
  at `db.rs:29-34` is written *after* the migrations run — it cannot gate
  them.
- **Evidence:** `crates/mlib-spec/src/schema.rs:7-38` (flat list, all
  `IF NOT EXISTS`), `schema.rs:45-55` (the test asserting that property);
  `crates/mediagram/src/index/db.rs:24-34` (loop + `?`, then
  `set_meta("schema_version", …)` afterwards); phase-02 lines 64-66; plan.md
  lines 145-149. The version-gated `MIGRATIONS: &[&[&str]]` the plan's
  wording presumes exists only as an unimplemented **proposal** in the
  tutorial spec
  (`docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:112-128`),
  and plan.md:142-144 explicitly allows this package plan to land first — in
  which case there is no framework at all.
- **Suggested fix:** Before adding the column, add real versioning: read
  `PRAGMA user_version`, apply only the statements above it, bump it in the
  same transaction — then relax the `IF NOT EXISTS` test to "idempotent by
  construction". Cheap alternative if that is too much for this plan: probe
  `PRAGMA table_info(sets)` and run the `ALTER` only when `art_key` is
  absent. Either way `SCHEMA_VERSION` must move from 1 to 2 in the same
  change — note the plan's examples already print `"schema": 2` while the
  constant is still `1` (see fact-check below).

---

## Finding 8: `snapshot_to` mutates the live database, so `--dry-run` is not dry

- **Severity:** Medium
- **Location:** Phase 3, step 1 ("Reuse `index::snapshot::checkpoint` then
  `snapshot_to`") and step 7 (*"`--dry-run` … performs no downloads and no
  writes outside a temp directory"*)
- **Flaw:** `snapshot_to` is not a pure copy: its first action is
  `db::set_meta(conn, "last_push_at", now)` **on the live connection**, and
  `checkpoint` runs `PRAGMA wal_checkpoint(TRUNCATE)`, also a write. Two
  consequences. (a) `--dry-run` writes to `library.db`, contradicting its own
  success criterion and breaking the one guarantee a dry run exists to give.
  (b) `last_push_at` means "when the recovery copy was pushed to Telegram"
  and is documented as such; an export silently overwrites it with the export
  time. After a `library.db` loss, the operator reads a `last_push_at` that
  describes an export, not the pinned recovery copy, and mis-judges how much
  history the channel actually holds. The published package also carries this
  falsified timestamp inside it — which additionally makes plan.md's success
  criterion *"Running the export twice with no library changes produces a
  byte-identical inner `tar.gz`"* (plan.md:125-127) **false by
  construction**: `library.db` differs on every run because `last_push_at`
  was just rewritten, so the determinism the whole phase-4 hash table rests
  on can never be observed end to end.
- **Failure scenario:** Operator runs `export-package --dry-run` to see what
  would be published (the safe, recommended first step per phase 6 step 5).
  `last_push_at` is destroyed. Later, after a disk failure, they check the
  pinned index's freshness against `last_push_at` and conclude the channel is
  current when the last real push was three weeks earlier.
  Secondary exposure on the same code path: the export copies `library.db`
  wholesale, so the published package includes the `meta` table — which holds
  `source:{set_id}` = the **canonicalised absolute path of the source video**
  for every set that is still pending or was interrupted, plus `tmp:{set_id}`
  and `index_message_id`. Phase 6 step 2 tells the reader the archive holds
  "the private channel id and every message id"; it also holds
  `/home/<username>/Videos/…` for in-flight sets. Understating package
  contents in the normative security section is exactly the kind of error a
  player author will rely on.
- **Evidence:** `crates/mediagram/src/index/snapshot.rs:24-31` (the
  `set_meta` write inside `snapshot_to`), `snapshot.rs:15-18` (checkpoint
  writes); `docs/mlib-spec-v2.md:254` (documented meaning of `last_push_at`);
  `crates/mediagram/src/commands/add.rs:106-119` (`source:`/`tmp:` written
  with `source_path.canonicalize()`), `add.rs:132` (deleted only on
  successful completion); phase-03 lines 45-47, 59-61.
- **Suggested fix:** Split the write out of `snapshot_to` (e.g.
  `snapshot_to_with_push_time` for `push_index`, a plain `snapshot_to` for
  the export), or have the export `DELETE FROM meta WHERE key LIKE 'source:%'
  OR key LIKE 'tmp:%'` **in the snapshot** before packaging — it should do
  that regardless, since the player has no use for the uploader's local
  paths. Make `--dry-run` open the database read-only so the guarantee is
  enforced by SQLite, not by review. Correct the phase-6 security-model
  sentence to enumerate what the DB actually carries.

---

## Finding 9: New dependency surface is larger and younger than the task needs

- **Severity:** Medium
- **Location:** plan.md, "Verified dependencies"; Phase 4, "Architecture"
  (crate table)
- **Flaw:** Three separate problems in one table. (a) `aes-gcm` **0.11.1** is
  25 days old and 0.11.0 is the first stable of a reworked `aead` line
  (0.11.0-rc.2 was November 2025); the plan picks it for the project's single
  cryptographic primitive *and* notes in the same breath that "the 0.11 API
  differs from its 0.10 snippets" — i.e. the reviewed, audited example code
  from the research does not apply, and the implementer will be writing
  novel calls against a three-week-old AEAD API with no in-repo precedent.
  0.10.3 has years of field exposure for identical functionality. (b) `image`
  0.25 is pulled in to downscale posters; it will be fed **bytes fetched over
  the network** (phase 3 step 4) and the plan sets no `image::Limits`, so a
  hostile or corrupted response — a 40000×40000 PNG that decompresses to
  gigabytes — OOMs the export. The download caps in phase 3 step 4 cap
  *compressed* bytes and do nothing here. The dependency is also avoidable:
  TMDB serves pre-sized derivatives (`/t/p/w500/…`), so the correct size can
  be requested instead of decoding and re-encoding locally. (c) `rand` 0.10
  is added as a direct dependency solely for `OsRng`, which `aes-gcm`
  already re-exports through `aead`/`rand_core` — a redundant direct edge and
  a future version-skew trap between `rand_core` 0.9 and 0.10, both of which
  are already in this lockfile.
- **Failure scenario:** A regression in a three-week-old GCM implementation,
  or a misuse of an API nobody in the project has used, produces packages
  that decrypt incorrectly or reuse state — and the failure is silent until a
  player somewhere cannot open a package. Meanwhile the first export against
  a poisoned CDN response for a poster kills the uploader with an
  allocation failure that looks like a bug in mediagram.
- **Evidence:** crates.io (2026-09-15): `aes-gcm` max 0.11.1, released
  2026-08-21; 0.11.0 2026-06-28; 0.11.0-rc.2 2025-11-05. `image` max 0.25.10
  (2026-03-10). `Cargo.lock:143-151` already carries base64 0.22.1 **and**
  0.23.1; `Cargo.lock:1989-2001` carries rand 0.9.5 and 0.10.2;
  `Cargo.lock:2020-2031` carries rand_core 0.9.5 and 0.10.1; lockfile is at
  350 crates before this plan adds `image`'s decoder tree. `tar` 0.4.46 is
  not present in the lockfile and I did not verify it against crates.io.
  plan.md lines 90-98; phase-04 lines 45-58.
- **Suggested fix:** Use `aes-gcm` 0.10.3 unless there is a concrete reason
  for 0.11, and if 0.11 is kept, add a round-trip **and** a known-answer test
  against an NIST GCM vector so an upstream regression is caught locally.
  Drop `image` and request the sized TMDB derivative; if it is kept, set
  `image::Limits` (max dimensions and allocation) before decoding anything
  fetched over the network. Drop the direct `rand` dependency and use
  `aes_gcm::aead::OsRng`. Add `cargo deny`/`cargo audit` to CI in the same
  phase that lands the crypto dependency, since this is the first time the
  project ships one.

---

## Fact check (claims verified against the codebase)

| # | Claim (where) | Result |
|---|---|---|
| 1 | `index::snapshot::checkpoint` (plan.md:68, phase-03:45) | VERIFIED `crates/mediagram/src/index/snapshot.rs:15` |
| 2 | `index::snapshot::snapshot_to` (same) | VERIFIED `snapshot.rs:24` — but it **writes to the live DB** (`snapshot.rs:29`), see Finding 8 |
| 3 | "per-process temp name as `push_index` does" (phase-03:46) | VERIFIED `commands/push_index.rs:44` |
| 4 | `mlib_spec::schema::SCHEMA_VERSION` exists (phase-01:24) | VERIFIED `crates/mlib-spec/src/schema.rs:4` |
| 5 | Example manifests/pointers use `"schema": 2` (phase-01:43,65; phase-05:50) | FAILED — the constant is `1` (`schema.rs:4`); phase 6 turns these examples into normative docs |
| 6 | `mlib_spec::SPEC_VERSION` exists (phase-01:25) | VERIFIED `crates/mlib-spec/src/lib.rs:27` |
| 7 | Examples use `"spec": 3` (phase-01:44,66; phase-05:51) | FAILED — the constant is `2` (`lib.rs:27`) |
| 8 | `add` records `source:{set_id}` and deletes it on completion (plan.md:71-74, phase-02:19-21) | VERIFIED `commands/add.rs:106,116,132` |
| 9 | That deletion happens in `upload/pipeline.rs` (plan.md:73) | FAILED as attributed — `pipeline.rs:155-165` deletes `tmp:{set_id}`, not `source:`; the `source:` delete is `add.rs:132` |
| 10 | "TmdbApi trait behind DiskCachedApi" (plan.md:76-77) | VERIFIED `metadata/tmdb_client.rs:18` and `:96,125` |
| 11 | TMDB types lack `poster_path`/`backdrop_path` (plan.md:78-79, phase-02:59) | VERIFIED `metadata/tmdb_types.rs:5-55` (no such fields) |
| 12 | `crates/mediagram/src/index/set_row.rs` exists (phase-02:62) | VERIFIED |
| 13 | "Reuse the subprocess pattern in `media::remux`" (phase-02:71) | VERIFIED `media/remux.rs:38-45` — argv, **no shell**, which phase 5 contradicts |
| 14 | `PLAYABLE_SQL` (phase-06:53) | VERIFIED `crates/mlib-spec/src/schema.rs:41` |
| 15 | `api_hash`/`tmdb_key` kept out of `Debug` (phase-04:96) | VERIFIED `config.rs:33-48`, test `config.rs:158-167`, standard at `docs/code-standards.md:45-49` |
| 16 | `config.example.toml` exists (phase-04:87, phase-05:68) | VERIFIED (11 lines, no permission guidance) |
| 17 | Research report path (plan.md:86-87) | VERIFIED `plans/reports/researcher-260915-1956-prebuilt-metadata-package-crypto-and-packaging-report.md` |
| 18 | Report "cites aes-gcm 0.10.3 and flate2 1.0.31" (plan.md:87-88) | VERIFIED report lines 15, 76 |
| 19 | Report's own recommendations fully carried into the plan | FAILED — report:306 calls signing "critical for security"; no plan file mentions signing |
| 20 | `aes-gcm` 0.11.1 is current (plan.md:93, phase-04:51) | VERIFIED crates.io — released 2026-08-21 (25 days old), see Finding 9 |
| 21 | `base64` 0.23.1 (plan.md:96) | VERIFIED crates.io (2026-08-04); already transitively in `Cargo.lock:149-151` |
| 22 | `rand` 0.10.2 (plan.md:97) | VERIFIED `Cargo.lock:1999-2001`; redundant as a direct dep (Finding 9) |
| 23 | `flate2` 1.1.10 (plan.md:95) | VERIFIED `Cargo.lock:620-622` |
| 24 | `image` 0.25.10 (plan.md:96) | VERIFIED crates.io (2026-03-10); not currently in the tree |
| 25 | `tar` 0.4.46 (plan.md:94) | UNVERIFIED — not in `Cargo.lock`, not checked against crates.io |
| 26 | "appends its own migration group … next version number" (plan.md:145-149) | FAILED — no such mechanism; `schema.rs:7-38` is a flat list run unconditionally by `index/db.rs:24-27`, see Finding 7 |
| 27 | "keys are ids and slugs only … path traversal has no surface" (phase-02:91-92) | FAILED — `caption.set` is an unvalidated remote string (`caption.rs:73`, `caption_codec.rs:88`) reaching `sets.set_id` via `index/rescan.rs:57-71`, see Finding 2 |
| 28 | Session file/dir hardening precedent (implied by "as … already are") | VERIFIED `telegram/client.rs:172-187`, `docs/code-standards.md:51-54`; the plan applies none of it to its own plaintext artefacts |
| 29 | `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md` (plan.md:6,135) | VERIFIED |
| 30 | "Export twice with no library changes → byte-identical inner tar.gz" (plan.md:125-127) | FAILED — `snapshot.rs:29` rewrites `meta.last_push_at` on every snapshot, so `library.db` differs every run |
| 31 | `mediagram gen-key` is a standalone key generator (phase-04:97-98) | FAILED as specified — `main.rs:62-63` loads and validates config before dispatch, and `config.rs:86-88` bails without `api_hash`/`channel` |
| 32 | Version-gated migration framework available to phase 2 (plan.md:145-149) | FAILED — exists only as a proposal in `docs/superpowers/specs/2026-09-15-tutorial-course-support-design.md:112-128` |
| 33 | Phase-6 doc targets exist (`docs/system-architecture.md`, `development-roadmap.md`, `project-changelog.md`, `README.md`) | VERIFIED; `docs/mlib-package-v1.md` does not exist yet (to be created) |

## Unresolved questions

1. Who holds the package key besides the operator, and what is the plan when a
   TV is lost or sold? The plan documents rotation as re-export + re-transcribe;
   that is not revocation (Finding 5).
2. Is the player's storage of the key confidential (Android Keystore) or a
   config string? The plan specifies transcription but not storage.
3. Will `MEDIAGRAM_PACKAGE_KEY` exist as an env override? If yes, it collides
   with the `publish_cmd` environment inheritance (Finding 4).
4. Does the base URL have directory listing disabled, and is old-archive
   retention actually wanted given it makes historical decryption permanent
   once the key leaks (Findings 5, 6)?
5. `tar` 0.4.46 unverified (claim 25).

**Status:** DONE_WITH_CONCERNS
**Summary:** Nine findings; the package's stated protection goal is not met by the
design as written — the rollback guard rests on unauthenticated pointer data, a
remote-controllable `set_id` reaches tar member paths, and every plaintext copy
of the index is written with default permissions in a repo that already chmods
0600 for the same class of secret.
**Concerns/Blockers:** Critical 2 (Finding 1 unauthenticated pointer / no AAD /
no signature; Finding 2 path traversal + unbounded reader). High 4 (Finding 3
plaintext at rest; Finding 4 publish_cmd shell + env inheritance; Finding 5
shared key doubles as forgery credential; Finding 7 `art_key` migration bricks
existing installs). Medium 3 (Finding 6 pointer metadata leak; Finding 8
`--dry-run` writes to the live DB + `meta` leaks local paths; Finding 9
dependency surface). Fact check: 8 claims FAILED, 1 UNVERIFIED of 33.
