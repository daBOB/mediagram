# Phase 1 review — package format and manifest

Reviewed: `crates/mlib-spec/src/package.rs` (173), `crates/mlib-spec/tests/package_format.rs` (234, 20 tests),
`crates/mlib-spec/src/lib.rs` (+3). No callers exist yet (`grep` for `package::`/`associated_data`/
`package_file_name`/`poster_key_is_valid` outside the module: only the `pub use` at `lib.rs:26`), so every
finding below is latent rather than live. Baseline (260 green, clippy clean) taken from the caller, not re-run.

Cross-phase claims are checked against `phase-03-publish-pointer-and-spec.md` ("Reader algorithm, as the spec
will state it", lines 53-71) because several phase-1 decisions are only safe if phase 3 behaves a certain way.

## Overall

The format is well shaped for its job and two of the hardest things are right: the associated data is a
fixed-order struct rather than a map (the plan's own stated mitigation, correctly implemented), and the date
algorithm is a faithful transcription of `civil_from_days` with `div_euclid`, verified below against 1.36M
reference values with zero mismatches. The module is 173 lines, under the 200-line rule.

The weakness is asymmetric hardening. The one input that is *trusted by construction* — the poster key, which
the phase spec itself calls "defensive rather than load-bearing here, because ids come from an integer column"
— gets a strict grammar. The six strings and integers that are genuinely attacker-controlled, because they
arrive in a plaintext file published at a known URL (`file`, `url`, `bytes`, `sha256`, `key_id`, `created_at`),
get no validation at all, and two of them reach a file name and a JSON encoder. The threat model is inverted
relative to where the checks landed.

---

## Critical

### C1 — Excluding `sha256` from the authenticated set enables silent update suppression
`package.rs:72-79` (rationale), `package.rs:46-60`, with `phase-03:58-59`

This answers question 1 directly: **yes, the exclusion enables an attack the plan missed**, though not via the
route the doc comment anticipates.

Phase 3 step 4 is "Skip the download when `sha256` matches the package already held. Compare the hash, never
the filename." `sha256` is not in the associated data, so an attacker who can rewrite `latest.json` replaces
the genuine `sha256` with the digest of the archive the reader *already holds*. That digest is not a secret —
it was published in the previous pointer.

The reader then takes the skip branch and never downloads, never decrypts, and never reaches a tag check. The
AEAD binding is not defeated; it is bypassed, because no ciphertext is ever processed. Cost to the attacker:
one edited JSON file. Observable symptom on the player: none — it looks like "already up to date".

Two follow-on harms:
- If the reader records the pointer's `created_at` as "held" on the skip branch, the phase-3 step-2 rollback
  floor rises without any package being installed, so the attacker can also push the floor forward and make a
  later genuine package look like a rollback.
- The condition is self-contradictory and therefore detectable: a package with a *newer* `created_at` always
  has different bytes. `created_at > held.created_at && sha256 == held.sha256` cannot happen honestly.

This also contradicts the reasoning in `plan.md` open question 1 ("signing now only protects against denial and
metadata edits an attacker gains nothing from"). An attacker gains a permanent, silent update freeze from a
metadata edit alone. That is new data relative to when signing was deferred, so the deferral is worth
re-deciding rather than silently keeping — see "Decisions for the user" at the end. Note that moving `sha256`
into the associated data is *not* available as a fix: it is the digest of the ciphertext, so it cannot be an
input to the encryption that produces that ciphertext. The doc comment at `package.rs:76-79` attributes the
exclusion of all four fields to the renaming requirement, which is only true for `file` and `url`; `sha256` and
`bytes` are excluded because they are structurally unavailable at encryption time. Worth correcting, because a
second implementer reading that comment will assume the choice was free.

**Fix** (all in the reader contract, phase 3, plus one sentence in the format doc):
1. The skip branch must never update held state — it is "no action", not "update applied".
2. Treat `pointer.created_at > held.created_at && pointer.sha256 == held.sha256` as tampering: refuse and
   surface it, do not silently continue.
3. State in the format doc that `sha256` and `bytes` are transport-integrity hints only and carry no
   authenticity whatsoever, so no decision may rest on them alone.

---

## High

### H1 — `package_file_name` builds a path component from an unvalidated string
`package.rs:124-128`

```rust
let hash: String = ciphertext_sha256.chars().take(NAME_HASH_LEN).collect();
format!("{FILE_PREFIX}_{y:04}{m:02}{d:02}-{hash}{FILE_SUFFIX}")
```

Nothing checks that the argument is hex, or 64 chars, or non-empty. `package_file_name(t, "../../../etc")`
returns `prebuilt_mediagram_db_20260616-../../...tar.gz.enc` — verified by inspection, `take(8)` of that input
is `"../../.."`. An empty or short argument silently produces `..._20260616-.tar.gz.enc`, which defeats the
"two exports on one day cannot collide" claim in the same doc comment without failing anything.

The realistic path to attacker data: a reader or a publish step that recomputes the expected name from the
pointer's `sha256` to cross-check `pointer.file`. Phase 3 step 4 tells readers to compare hashes rather than
names, which reduces but does not remove the chance, and this is a `pub fn` in a crate whose stated purpose is
to be implemented against by a second codebase.

The same function has a second, non-security failure: `{y:04}` on a negative year emits a leading `-` and a
variable width. Measured outputs from a transcription of the function:

| `created_at` | date segment |
|---|---|
| `-1` | `19691231` |
| `-62135596800` | `00010101` |
| `-70000000000` | `-2491015` |
| `i64::MIN` | `-2922770226570127` |
| `i64::MAX` | `2922770265961204` |

No panic and no overflow (see the positives section), but any reader regex expecting `[0-9]{8}` breaks, and the
generated name gains a leading `-`, which several CLI tools read as an option.

**Fix:** make the function total over its own precondition — validate `^[0-9a-f]{64}$` and a plausible
`created_at` and return `Result<String, _>`, or take `&[u8; 32]` and hex-encode internally so the type carries
the guarantee. Reject `created_at <= 0` and anything beyond a sane upper bound at the same point.

### H2 — The associated data is only canonical for one JSON writer
`package.rs:62-90`

Within Rust the serialization is stable: field order is fixed by the struct, integers go through `itoa` (plain
decimal, no `+`, no `-0` for integer types), and no floats or maps are involved. A round-trip through
`LatestPointer` cannot change the bytes, because the AAD is rebuilt from typed fields rather than reformatted
text. That part holds.

The problem is `key_id: &'a str`. It is a `String` on `LatestPointer` (`package.rs:57`), arrives from the
network, and is never validated. Canonicality of a JSON string depends on the writer's escape policy, and
serde_json's table (`serde_json-1.0.151/src/ser.rs:2147-2165`) escapes only `"` `\` and bytes below `0x20`. It
does **not** escape `<` `>` `&` `=` `'` `/` `U+007F` `U+2028` `U+2029`. Gson's default writer is HTML-safe and
escapes `<>&='` as `\u00XX`; org.json escapes `</`; several writers escape `U+2028/U+2029` unconditionally.

Concrete failure: a `key_id` containing `<` — whether from a typo in a config, a future non-hex key label, or a
deliberately crafted pointer — yields `"<"` from the Rust exporter and `"<"` from a Gson-based Android
reader. Different AAD bytes, `AEADBadTagException`, and the diagnosis the phase-3 spec prescribes ("a wrong
key, a corrupt file, or an edited pointer") names none of the three actual causes. The same class of divergence
covers `created_at` if any implementation ever routes it through a double (JS `JSON.stringify`, org.json
`getLong` on `1.78e9`).

**Fix, cheapest first:**
1. Validate `key_id` as `^[0-9a-f]{8}$` before it is used in the AAD or accepted from a pointer. This removes
   the entire escaping question, because no character in that set is escaped by any JSON writer.
2. Better, and it also deletes the `expect` at `package.rs:89`: define the AAD as bytes rather than JSON —
   e.g. `b"mediagram/package/v1" || format:u32be || created_at:i64be || key_id:[u8;4] || schema:i64be ||
   spec:u32be`. A fixed-width byte encoding is unambiguous in every language and needs no serializer.
   Either way the format doc must publish the exact byte layout, not "minified JSON", because "minified JSON"
   does not pin escaping.

### H3 — The test that is supposed to stop the pointer growing a leaky field cannot fail
`tests/package_format.rs:82-93`, against `phase-01:120-122`

The phase spec names this as the mitigation for its top risk: "the pointer's fields are enumerated here and a
test fails if that set grows". The implemented test is a denylist of 8 quoted tokens. Adding
`"set_count": 312` to `LatestPointer` passes it — the JSON text is `"set_count"`, which does not contain the
substring `"set"` (the closing quote does not line up). So do `"library_title"`, `"owner"`, `"channel"`,
`"first_title"`. The criterion "a test proves the pointer contains no library content" is therefore listed as
met while the guarantee is absent.

**Fix:** invert it to an allowlist over the parsed object:
```rust
let v: serde_json::Value = serde_json::to_value(pointer()).unwrap();
let keys: Vec<&str> = v.as_object().unwrap().keys().map(String::as_str).collect();
assert_eq!(keys, ["format","created_at","file","url","bytes","sha256","cipher","key_id","schema","spec"]);
```
(`serde_json`'s default feature set preserves insertion order, so this pins field order too and makes
`manifest_serializes_in_declared_field_order` redundant for the pointer.)

### H4 — Two genuine packages can share associated data
`package.rs:62-70`

The authenticated tuple is `(format, created_at, key_id, schema, spec)`. Four of the five are near-constant for
a given publisher, so `created_at` alone distinguishes packages. Any two exports that share a second share
identical AAD: a publish retried after a failed upload, a CI job run twice, a re-encrypt of the same snapshot,
or an exporter that stamps `created_at` from the snapshot/manifest rather than from encryption time (nothing in
phase 2 says which).

When that happens, an attacker can serve archive A₁ under pointer P₂ — rewriting `sha256`, `bytes`, `url` and
`file` freely, since none are authenticated — and it decrypts cleanly. The reader installs a different genuine
package than the one it asked for, with no error anywhere.

**Fix:** guarantee AAD uniqueness structurally rather than by clock luck. The crate already depends on `ulid`:
add `package_id: String` to `LatestPointer` and to `AssociatedData`. One field, no new dependency, and it
removes the reliance on the exporter's timestamp source entirely.

### H5 — The attacker-controlled half of the pointer has no validator
`package.rs:46-60`, `package.rs:159-173`

`poster_key_is_valid` exists because a key "reaches a file name, a manifest path and a tar member name". Every
word of that applies to `pointer.file`, which reaches a local download path, and none of it is applied. Nor is
anything applied to `url` (scheme, host), `sha256` (shape), `key_id` (shape, see H2), `bytes` (ceiling) or
`created_at` (range). `pointer_is_readable` checks three fields out of ten.

Concrete failures, all from one edited plaintext file:
- `file: "../../../../data/data/<player>/files/x"` written verbatim by a reader that trusts the published name.
- `url: "http://attacker/"` — downgraded scheme, arbitrary host, traffic redirection.
- `bytes: 9_999_999_999` with a matching endless stream. Phase 3 step 5 says "enforcing a size limit *from*
  `bytes`", which is a stream-overrun guard, not a ceiling: the attacker picks the limit. A TV box with 8 GB of
  storage fills up before the tag is ever checked. Phase 2's 24/48 MB thresholds are export-side only.

**Fix:** one `pointer_is_well_formed` in this crate, used by both sides, checking
`file =~ ^prebuilt_mediagram_db_[0-9]{8}-[0-9a-f]{8}\.tar\.gz\.enc$`, `url` starts with `https://` (and,
ideally, equals `{configured_base}/{file}` so the pointer cannot redirect at all), `sha256 =~ ^[0-9a-f]{64}$`,
`key_id =~ ^[0-9a-f]{8}$`, `bytes <= MAX_PACKAGE_BYTES` with an absolute constant in this module, and
`created_at` inside a plausible range. Readers must never construct a path from `pointer.file`; derive the
local name themselves.

### H6 — Nothing ties the associated-data field set to the pointer field set
`package.rs:49-70`

`AssociatedData` and `LatestPointer` are two independent structs. Adding a field to the pointer compiles,
serializes, round-trips and passes all 20 tests while being completely unauthenticated. Because `LatestPointer`
has no `deny_unknown_fields`, an older reader also silently ignores it — so a future security-relevant field
(`min_reader`, `revoked`, `not_after`) can be stripped in transit *and* ignored by old clients, with nothing
failing.

**Fix:** a test that derives both key sets and asserts
`pointer_keys - aad_keys == {"file","url","bytes","sha256","cipher"}` (cipher is also currently outside the AAD
— worth an explicit decision, since it is the one field that selects an algorithm). Adding a field then forces
a conscious choice instead of defaulting to "unauthenticated".

---

## Medium

### M1 — Attacker-controlled string echoed into an error message
`package.rs:152-153`, `package.rs:167`

```rust
#[error("package cipher `{0}` is not recognised")]
UnsupportedCipher(String),
```
`cipher` is unbounded and arbitrary. A pointer carrying 10 MB of text, embedded newlines, or ANSI escapes gets
cloned into an error that will be logged on the uploader's console and rendered on a TV: log-line forgery,
terminal escape injection, UI overflow. This is the same family as the rule at `docs/code-standards.md:56-59`
about never letting a remote-derived string reach an error surface unfiltered. No secret leaks — the function
only touches public data and `supported_schema`, which is local — so the exposure is injection, not disclosure.

**Fix:** do not echo it, or truncate and filter: `cipher.chars().take(32).filter(char::is_ascii_graphic)`.

### M2 — `UnsupportedFormat` claims "newer" for any mismatch
`package.rs:148-149`, `package.rs:163-165`

`format != PACKAGE_FORMAT` fires for older formats too, and the message says "is newer than this reader
understands". A reader meeting `format: 0` reports the opposite of the truth, and `tests/package_format.rs:206`
only ever exercises `PACKAGE_FORMAT + 1`, so the wrong half is untested.

Check order itself is right: `format` gates the meaning of every other field, so it must be first; cipher
before schema is arbitrary but harmless. Worth noting that `pointer_is_readable` never takes a `key_id`, so the
"a reader holding the wrong key can stop before downloading" benefit documented at `package.rs:93-94` and
required by phase-3 step 3 is neither implemented nor tested here.

**Fix:** neutral wording (`"package format {0} is not supported; this reader implements {PACKAGE_FORMAT}"`)
plus a test for a lower format. Consider an optional `key_id` parameter so step 3 has a home in the shared crate.

### M3 — Poster keys are unbounded in length and non-canonical for leading zeros
`package.rs:102-118`

The charset is airtight (see positives). Two structural gaps remain:
- No length cap. A 300-character key is "valid", becomes `posters/<300 chars>.jpg`, and hits the ustar 100-byte
  name field; readers fall back to pax/GNU extensions or truncate, and a truncated name can collide with
  another poster. The phase spec explicitly anticipates reuse for "any future key whose input is less
  trustworthy", which is exactly where an unbounded length bites.
- `tmdb-movie-0693134` and `tmdb-movie-693134` are both valid and denote the same TMDB title, producing two
  files, two cache entries, and an ambiguous key→id mapping on the player.

**Fix:** `key.len() <= 64`, id segment `<= 10` digits, and reject a leading `0` unless the id is exactly `"0"`.

### M4 — The format constants are not sufficient for a second implementation
`package.rs:16-24`, `package.rs:26-31`

Phase 1's overview promises "the archive layout", but the module defines only `PACKAGE_FORMAT` and `CIPHER`.
The nonce placement (`phase-02:102-103`, prepended, 12 bytes), the 128-bit tag, and the member names
(`manifest.json`, `library.db`, `posters/`, `phase-02:37-39`) exist only in plan prose. An Android implementer
working from the crate cannot decrypt or unpack.

Relatedly, `PosterEntry.file` is a free-form `String` that is a path, with no validator and no constructor —
the only path-bearing field in the module without one. It is inside the ciphertext, so it is authenticated, but
authenticated by the publisher's key, which is not the same as safe to join onto a directory. Phase-3 step 7
does require refusing `..`, absolute and link members, which is the real defence; the crate should still make
the safe construction the easy one.

**Fix:** add `NONCE_LEN`, `TAG_LEN`, `MANIFEST_MEMBER`, `DB_MEMBER`, `POSTER_DIR` and a
`poster_file_for_key(key) -> Option<String>` that is the only way to build the path, then have readers compare
`entry.file` against it rather than trusting it.

### M5 — Test gaps: several behaviours would pass against a broken implementation
`tests/package_format.rs`

Answering the question directly — here is what a broken implementation could do and still go green:

| Broken implementation | Caught? |
|---|---|
| `civil_from_unix` using `/` instead of `div_euclid` | **No.** Both tested timestamps (`0`, `1_781_568_000`) are non-negative, where the two agree. Verified: they diverge at `-1` (correct `1969-12-31` vs broken `1970-01-01`), `-86_401`, `-100`, `-1_000_000_000`. |
| `is_digits` using `char::is_numeric` | **No.** No test uses a non-ASCII digit; `poster_key_is_valid("tmdb-movie-１２３")` would be accepted, yielding a name that normalizes to a near-duplicate on NFD/Windows filesystems. |
| `is_lower_alpha` using `char::is_lowercase` | **No.** `"tmdb-mövie-1"` is untested. |
| Dropping `format`/`schema`/`spec` from the AAD | Yes, but only via the single golden string at `:96-102`; there is no per-field change test for those three, unlike `created_at` and `key_id`. |
| Adding a leaky field to the pointer | **No** — see H3. |
| Emitting a non-lowercase or wrong-length `key_id` | Partly: `:137` pins the length, nothing pins the alphabet, and there is no known-answer vector a Kotlin port could check against. |
| A `package_file_name` that ignores its hash argument | Yes (`:189-194`). A `package_file_name` fed a short/empty/non-hex hash: **no test**. |
| Wrong date for a leap day, a century non-leap year, or a year boundary | **No test** — `2000-02-29`, `2024-02-29`, `2100-03-01`, `1900-01-01` are all absent. |

Two further issues:
- `:96-102` hard-codes `"schema":1,"spec":2` in the expected string while the fixture at `:20-21` reads
  `SCHEMA_VERSION`/`SPEC_VERSION`. The moment the tutorial/course work bumps either constant, this unrelated
  test fails with a confusing diff. Either build the expectation with `format!` from the constants, or make the
  fixture use literals so it is a true golden vector. It cannot be half of each.
- `manifest_round_trips_byte_identically` (`:41-45`) compares the implementation against itself, so it holds
  for any struct with a stable field order and would not notice a renamed or reordered field. The wire format
  deserves one pinned golden string per struct — which would also satisfy `phase-01:69-70`, "every example in
  the eventual specification is generated by a test rather than typed by hand", currently with no test at all.
- `manifest_serializes_in_declared_field_order` (`:56-78`) uses `str::find` on quoted names and `at >= last`.
  It works today, but `find` matches inside string *values* too, and `>=` tolerates equality; replacing it with
  the key-set assertion from H3 is both stronger and shorter.

### M6 — `key_id` is an undomain-separated hash of the key, published
`package.rs:95-98`

Answering question 4: for a key that is 32 CSPRNG bytes, publishing 32 bits of `SHA-256(key)` costs nothing
real. It reduces the set of keys consistent with the published value by 2⁻³², which is meaningless against a
2²⁵⁶ search, and it gives an attacker a cheaper per-candidate test (one SHA-256 instead of one GCM trial
decryption) — a constant-factor speedup on an already infeasible search.

It stops being free if the key is ever derived from something with low entropy. If a later phase lets a user
type a passphrase, or derives the key with a bare hash, then `key_id` is a published, unsalted, unstretched
verifier: an offline dictionary attack at one SHA-256 per guess, with no need to download the archive first.
Phase 2 currently specifies key bytes from `getrandom`, which is the right answer — this is a constraint to
keep, not a defect today.

Collisions are fine for the stated purpose: 32 bits gives a 50% birthday collision at ~65k distinct keys, and
the plan says key rotation is manual with a single reader. At even 100 keys ever, P(collision) ≈ 1.2e-6, and a
collision is fail-safe — the reader proceeds to download and the tag rejects.

Two refinements worth taking:
- Domain-separate: `SHA-256("mediagram/package-key-id/v1" || key)`, so this public value can never coincide
  with some other use of `SHA-256(key)` introduced later (an integrity checksum, a derived subkey).
- Document that `key_id` mismatch is a *hint*, never authentication, and that an attacker can flip it freely to
  make a reader refuse to update (a free denial channel, related to C1).

### M7 — Manifest/pointer agreement has no helper in the crate that owns the format
`package.rs:33-60`

`PackageManifest` and `LatestPointer` both carry `format`, `created_at`, `schema`, `spec` with no rule binding
them. Phase-3 step 7 requires the reader to "confirm its `created_at` matches the pointer", which is the right
rule, but it lives only in plan prose. Both values are authenticated once decryption succeeds, so the risk is a
publisher-side inconsistency that every reader then has to detect independently and might not.

**Fix:** `manifest_matches_pointer(&PackageManifest, &LatestPointer) -> Result<(), PointerError>` here, plus a
test, so the exporter and the player share one definition.

---

## Low

- **`expect` on network-derived data** — `package.rs:89`. Genuinely unreachable: `serde_json::to_vec` fails only
  on a failing `Serialize` impl, a non-string map key, or non-finite floats, and `AssociatedData` has none of
  those. But `docs/code-standards.md:40` says "No `unwrap()`/`expect()` on data that crosses a process or
  network boundary", and the pointer crosses one; this also becomes a panic across a future UniFFI/JNI
  boundary, where it aborts rather than raising. Adopting the byte encoding in H2 removes it for free.
- **Public structs with public fields and no `#[non_exhaustive]`** — `package.rs:28,36,49`. Adding a field later
  breaks every struct-literal construction in and out of the workspace. On the wire the defaults are sensible
  (unknown fields ignored → old readers tolerate new ones; missing fields are hard errors → renames break
  loudly, which `format` exists to prevent). Just make the choice explicit in the doc comment so the next
  author does not have to re-derive it.
- **"Two exports on one day cannot collide"** — `package.rs:121-123`. It is a 32-bit prefix, so this is
  "overwhelmingly unlikely", not "cannot". Harmless today; an absolute claim in a format doc invites a reader
  to build on it.
- **Strict `format` equality plus a single pointer URL** — `package.rs:163`. A format bump strands every
  deployed player at once, since the one `latest.json` can only carry one format. Worth a `latest-v1.json`
  convention before there is a second reader, not after.

---

## Verified as correct (positives)

- **`civil_from_unix` is right.** Transcribed the function verbatim and compared against Python's proleptic
  Gregorian calendar over 1,362,222 timestamps spanning `-62135596800 .. 69999991284` (years 1 through 4187),
  plus targeted values: **0 mismatches**. Leap days `2000-02-29` and `2024-02-29`, the non-leap century
  boundary `2100-03-01`, `1900-01-01`, `1969-12-31` (`secs = -1`) and `1970-01-01` all correct. `div_euclid` is
  used exactly where flooring matters, and every subsequent division operates on a non-negative value where
  truncation and flooring coincide.
- **No integer overflow anywhere in the date math**, including `i64::MIN` and `i64::MAX`. `div_euclid` only
  overflows for `MIN / -1`; `z = days + 719_468` peaks near 1.07e14, `era * 146_097` near 1.07e14, and
  `yoe + era * 400` near 2.9e11 — all far inside `i64`. `5 * doy + 2` is bounded by `doy <= 365`.
- **The poster-key charset is genuinely airtight.** `is_ascii_lowercase`/`is_ascii_digit` are byte-exact, so no
  Unicode digit, homoglyph, NFC/NFD pair, percent-encoding, `/`, `\`, `:`, NUL, space or `.` can pass. Exactly
  three `'-'`-separated segments with non-empty checks rejects leading, trailing and doubled hyphens. No
  Windows drive letter (needs `:`), no UNC (needs `\`), no trailing dot or space, and no reserved device name
  is reachable, since every accepted key contains two hyphens and `con-aux-1` is not reserved. Hand-rolled
  rather than pulling in the already-present `regex` — the right call for a hot-ish validator.
- **The associated data is a fixed-order struct, not a map**, which is precisely the mitigation
  `phase-01:123-125` asks for, and integer formatting through `itoa` is stable.
- **serde's derive rejects duplicate JSON keys** (`duplicate field` error), so the classic parser-differential
  on `latest.json` fails closed on the Rust side. Note that most other-language parsers accept last-wins, so
  the *spec* should say duplicate keys are invalid rather than relying on serde's behaviour.
- Allocation is unremarkable: one `Vec` per AAD, one `String` per name, nothing in a loop.
- 173 lines, within the 200-line rule; `thiserror` in the library crate as `docs/code-standards.md:30-34` requires.

---

## Success criteria: coverage

| Criterion (`phase-01:110-117`) | State |
|---|---|
| Manifest and pointer round-trip byte-identically | Tested, but self-referential (M5) |
| AAD changes when any identifying field changes, and not otherwise | Partial: `created_at`, `key_id` tested; `format`/`schema`/`spec` only via one golden string (M5) |
| `key_id` stable for a key, differs for another | Tested; no known-answer vector for a second implementation (M5) |
| Poster keys with a separator, `..`, or a control char rejected | Tested; unicode-digit and length cases absent (M3, M5) |
| Unknown `format` rejected | Tested for newer only; older untested and misreported (M2) |
| A test proves the pointer contains no library content | **Not met** — the test cannot fail on a new field (H3) |
| `schema`/`spec` read from crate constants, not literals | Met in fixtures; violated in the AAD expectation (M5) |
| "Every example in the specification is generated by a test" (`:69-70`) | **No test** emits either JSON example |

---

## Recommended actions, in order

1. H3 — invert `pointer_reveals_nothing_about_the_library` to a key-set assertion. One test, restores the
   plan's headline mitigation.
2. H2 — validate `key_id` as `^[0-9a-f]{8}$`, and publish the AAD byte layout rather than "minified JSON".
3. H1 — validate or re-type `package_file_name`'s hash argument and bound `created_at`.
4. H5 — add `pointer_is_well_formed` covering `file`, `url`, `sha256`, `key_id`, `bytes`, `created_at`, and give
   the reader an absolute size ceiling that does not come from `bytes`.
5. C1 — write the skip-branch rule into phase 3: no held-state update on skip, and treat
   `created_at` newer + `sha256` unchanged as tampering.
6. H4 — add a `package_id` (ULID, already a dependency) to the pointer and the AAD.
7. H6, M7, M4 — the key-set test, `manifest_matches_pointer`, and the missing layout constants.
8. M1, M2, M3 — error hygiene, format wording plus an older-format test, key length and leading-zero rules.
9. M5 — the missing tests: negative/leap/boundary dates, unicode digits, a `key_id` vector, golden wire strings.

## Decisions for the user (not applied, per the "no silent reversal" rule)

1. **Signing `latest.json`.** `plan.md` open question 1 defers it because "the AEAD binding makes a replayed
   archive fail, so signing now only protects against denial and metadata edits an attacker gains nothing
   from". C1 is a concrete metadata-only edit that yields a permanent silent update freeze, and there is a
   second case the deferral did not weigh: a **first install** has no held `created_at`, so the phase-3 step-2
   floor is vacuous and a year-old genuine (pointer, archive) pair is perfectly authenticated and accepted.
   The cheap alternative to signing is a build-time floor shipped in the player (`created_at` must be ≥ the
   app's build date) plus a staleness alarm when no update succeeds for N days. Keep the deferral, add the
   floor, or sign?
2. **Putting `cipher` in the associated data.** It is currently outside, so an attacker can rewrite it; the
   only consequence today is a refusal at `package.rs:166`, because there is one cipher. It becomes a
   downgrade vector the moment a second cipher exists. Include now (free) or leave for the format bump?

## Unresolved questions

- Does `created_at` come from encryption time or from the snapshot/manifest? H4's severity depends on it and
  phase 2 does not say.
- Is the Android reader's JSON library chosen yet? H2's concrete failure depends on Gson's HTML-safe default;
  kotlinx.serialization matches serde and would not diverge.
- Is the package key intended to stay a raw 32-byte CSPRNG value forever (M6), or is a user-supplied passphrase
  anywhere on the roadmap?
