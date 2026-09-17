# Tech Debt Audit — mediagram

Date: 2026-09-18 01:29 · Branch: main · HEAD: 5ce70c4 (+ uncommitted dedupe pass)

## Headline

Codebase is in good shape. 1030 tests green, clippy silent, zero TODO/FIXME
markers, `cargo audit` clean across 363 crates, all 3 web deps at latest.
Docs are current and partly pinned by tests.

Debt is concentrated in **what nothing checks**, not in what is written:
a spec fix that landed on one of two parallel server implementations and not
the other, and no automated gate that would have caught it.

Scoring: `Priority = (Impact + Risk) × (6 − Effort)`, each 1–5.

## Prioritized backlog

| # | Item | Cat | I | R | E | **P** |
|---|------|-----|---|---|---|-------|
| 1 | `mediagram serve` answers 400 to an unknown Range unit | Code | 3 | 4 | 1 | **35** |
| 2 | No automated test gate | Infra | 4 | 4 | 2 | **32** |
| 3 | 173 graphify tool-cache files tracked in git | Infra | 3 | 3 | 1 | **30** |
| 4 | Only one cross-stack invariant is pinned | Test | 3 | 4 | 3 | **21** |
| 5 | `routes.ts` — 492 lines, 11 branches in one chain | Code | 2 | 2 | 2 | **16** |
| 6 | No coverage measurement | Test | 2 | 2 | 2 | **16** |
| 7 | `tasks/` mandated by CLAUDE.md, does not exist | Docs | 1 | 2 | 1 | **15** |
| 8 | `commands/` — 2288 lines, 1 non-live test | Test | 3 | 3 | 4 | **12** |
| 9 | ~20 TS symbols exported, used only in own file | Code | 1 | 1 | 1 | **10** |
| 10 | 16 files over the project's 200-line guideline | Code | 2 | 1 | 3 | **9** |

---

### 1. `mediagram serve` answers 400 to an unknown Range unit — P35

**This is a live bug, not stylistic debt.**

Commit 1858b32 fixed RFC 9110 §14.2 ("an origin server MUST ignore a Range
header field that contains a range unit it does not understand") — but touched
only `web/src/response.ts` and `web/test/http.test.ts`. The Rust server was
never updated.

- `web/src/response.ts:52` — unparsable Range → `planResponse(null, total)`,
  i.e. 200 + whole file. Correct.
- `crates/mediagram/src/serve/response.rs:49` — `RangeError::Malformed` → **400**.

`crates/mediagram/src/serve/range.rs:55` classifies `items=0-1` as `Malformed`,
and `serve_range.rs:104` asserts exactly that, with `items=0-1` in the case
list. Two tests lock the wrong behaviour in:
`serve_http.rs:265` and `serve_response.rs:76`, both named
`a_malformed_range_is_a_bad_request`, both asserting 400.

The commit's own justification applies verbatim to the Rust server: *"a client
that sent a header this server could not parse got no video at all rather than
the video it asked about."*

**Fix:** mirror the TS change — map `Malformed` to the no-Range plan in
`serve/response.rs`; update the two tests. Parse layer stays as is (the TS side
kept its `"malformed"` error too; only the response mapping changed).

**Business case:** a known, already-diagnosed playback failure still shipping in
half the product. Smallest fix on this list.

**Effort:** ~30 min.

---

### 2. No automated test gate — P32

No `.github/workflows`, **no git remote at all** (local-only repo), no
pre-push hook. 1030 tests run when someone remembers.

Item #1 is the direct evidence of cost: a fix applied to one implementation,
its sibling left behind, nothing to notice for as long as it takes someone to
re-read both files.

**Fix, cheap version (no remote needed):** a `pre-push` hook or a checked-in
`make check` / `scripts/check.sh` running `cargo clippy --all-targets -D warnings`,
`cargo test --all`, `bun test`. Whole suite is ~4s Rust + ~2s web.

**Fix, full version:** add a remote, port the same three commands to CI.

**Business case:** the suite already exists and is fast and green. Nothing is
being bought by not running it automatically.

**Effort:** 1–2 h for the local gate.

---

### 3. 173 graphify tool-cache files tracked in git — P30

`git ls-files graphify-out | wc -l` → **173**, including 165 AST cache blobs
under `cache/ast/v0.9.63-s3/` and `.graphify_ast.json` (82,111 lines, swept
into 5ce70c4 alongside an unrelated refactor). `.gitignore` covers `/target`,
`/config.toml`, `*.db`, `/.claude/`, `.gstack/` — not `graphify-out/`.

Already causing noise: four of these tracked files show as deleted right now
because a graphify run consumed its own scratch files mid-session.

**Fix:** add `graphify-out/` to `.gitignore`, `git rm -r --cached graphify-out`.

**Business case:** every graphify run produces a spurious dirty tree and
misleading diffs; the 82k-line blob distorts history and review.

**Effort:** 15 min. **Caveat:** confirm nothing is meant to be published from
`graphify-out/` (`GRAPH_REPORT.md` may be worth keeping deliberately).

---

### 4. Only one cross-stack invariant is pinned — P21

The project has the right instinct — `shared_playable_sql.rs` fails when the
player's `PLAYABLE_SQL` copy or `EXPECTED_SCHEMA` drifts from the Rust
constant, and `codec-policy-doc.test.ts` fails when the docs table disagrees
with the code. Both are good.

But Rust `serve/` and TS `web/src/` independently implement Range parsing,
response planning, catalog queries and Telegram byte sourcing, and **only the
catalog definition is pinned**. Item #1 is what the unpinned gap produced.

Near-identical prose already sits in both (`serve/range.rs:52-54` vs
`range.ts:88-90`, word for word), which is the tell.

**Fix:** a shared table of `(Range header, total) → (status, Content-Range)`
cases as JSON, asserted by both suites. Covers the exact class of drift that
bit item #1.

**Business case:** the dual stack is deliberate and documented
(`docs/system-architecture.md` §7, "Two MTProto implementations, on purpose")
— so the carrying cost is accepted, but it needs paying with parity tests
rather than vigilance.

**Effort:** half a day.

---

### 5. `routes.ts` — 492 lines, 11 branches in one chain — P16

`createRouter` spans lines 232–427 (~195 lines) as one linear `if`-chain
dispatching catalog, search, poster, summary, subtitle, stream, HLS
begin/serve/delete, static files and the hls library. 2.5× the project's own
200-line file guideline.

**Fix:** extract per-concern route modules alongside the existing
`assets.ts` / `catalog.ts` / `range.ts` split. Table-driven dispatch over the
regexes already declared at 103–116.

**Effort:** half a day. Low risk — `http.test.ts` covers the routes end to end.

---

### 6. No coverage measurement — P16

No `llvm-cov`, `tarpaulin`, or nextest config; nothing in `package.json`.
1030 tests but no read on what they reach — item #8 was found by reading, not
by measurement.

**Fix:** `cargo llvm-cov --all` + `bun test --coverage` in the gate from #2.
Report only at first; no threshold until a baseline exists.

**Effort:** 2 h.

---

### 7. `tasks/` mandated by CLAUDE.md, does not exist — P15

`CLAUDE.md` §"Task Management" requires `tasks/todo.md` and `tasks/lessons.md`;
§3 makes the lessons file the self-improvement loop's storage. Directory is
absent. The loop CLAUDE.md describes has nowhere to write.

**Fix:** create both, or amend CLAUDE.md to point at `plans/` which is where
work actually lands (3 plan dirs + `reports/`).

**Business case:** a rule nothing follows trains everyone to skim the file that
holds the rules that matter.

**Effort:** 15 min — but it is a **decision**, not a task. See questions.

---

### 8. `commands/` — 2288 lines, 1 non-live test — P12

19 files, 2288 lines of CLI orchestration (config → index → telegram → output)
reached by exactly two test files: `index_pin_bookkeeping.rs` and
`live_add.rs`, the latter `#[ignore]`d behind `MEDIAGRAM_LIVE=1` and real
credentials.

Units beneath are well covered; the wiring is not. Genuinely hard — it needs
network seams — which is why it scores low despite real size.

**Fix (incremental):** as each command is next touched, lift its orchestration
behind a trait already used elsewhere (`Transport` is the working precedent)
and test against a fake. No big-bang refactor.

**Effort:** 1–2 days if done wholesale; ~0 marginal if done per-touch.

---

### 9. ~20 TS symbols exported but used only in own file — P10

`PACKAGE_FORMAT`, `CIPHER` (`package/pointer.ts`), `REQUEST_SIZES`
(`range.ts`), `FIELDS` (`search/index.ts`), `memberNameIsSafe`
(`package/unpack.ts`), `bareChannelId` (`telegram/client.ts`), plus ~14 types.
Each has self-references — over-broad visibility, **not dead code**.

**Fix:** drop `export` where nothing imports it. Mechanical; `bun test` proves it.

**Effort:** 1 h. Lowest value on the list — nothing is removed, only narrowed.

---

### 10. 16 files over the 200-line guideline — P9

Beyond `routes.ts` (#5): `cache/reader.ts` 274, `package/refresh.ts` 271,
`login.ts` 263, `commands/prepare.rs` 254, `commands/push_index.rs` 251,
`transcode/registry.ts` 245, and 10 more in the 200–240 band.

Most are cohesive single-concern modules that happen to be long. Treat the
guideline as a review prompt, not a target.

**Fix:** split opportunistically when next edited.

---

## Accepted — document, do not "fix"

These are deliberate, documented decisions. Recording them so a future audit
does not propose reversing them.

| Decision | Where justified | Why it stands |
|---|---|---|
| Two MTProto implementations (grammers / teleproto) | `system-architecture.md` §7 | Player runs on a different host; Rust is the reference; player bytes verified against recorded `parts.sha256` |
| System sqlite, not bundled | `crates/mediagram/Cargo.toml` inline comment | `grammers-session` statically links its own sqlite3; two bundled copies collide on duplicate `sqlite3_*` symbols at link time |
| `teleproto` as sole player Telegram dep | `system-architecture.md` §7 | Maintained GramJS fork; at latest (1.229.0, published 2026-08-25); 3 direct deps total |
| AAD field list restated in `package-refresh.test.ts` | test intent | Test must build associated data independently or it proves nothing |

## Dependency debt: none found

- `cargo audit` — 363 crates scanned, zero advisories.
- Rust deps current: axum 0.8.9, tokio 1.53.1, reqwest 0.13.5, rusqlite 0.40.2.
- Web: `teleproto@1.229.0`, `hls.js@1.7.3`, `@types/bun@1.4.2` — all latest.
- 17 packages installed total. Unusually small attack surface.

## Phased plan (alongside feature work)

**Phase 1 — this week, ~4 h.** Stop the bleeding.
1. Fix #1 (`serve/response.rs` + 2 tests) — 30 min
2. Ignore + untrack `graphify-out/` (#3) — 15 min
3. Local `pre-push` gate: clippy, `cargo test --all`, `bun test` (#2) — 2 h
4. Decide `tasks/` vs `plans/` and make CLAUDE.md true (#7) — 15 min

Phase 1 closes the two highest-priority items and the cheapest two.

**Phase 2 — next sprint, ~1.5 days.** Stop it recurring.
5. Cross-stack Range/response parity fixture (#4) — 0.5 d
6. Coverage reporting into the gate (#6) — 2 h
7. Split `createRouter` into route modules (#5) — 0.5 d

**Phase 3 — continuous, no scheduled block.** Pay down by touching.
8. `commands/` seams as each command is next edited (#8)
9. Un-export file-local TS symbols in one sweep (#9) — 1 h when convenient
10. Split long files opportunistically (#10)

Nothing in Phase 2 or 3 blocks feature work; Phase 1 items 1–2 should land
before the next release.

## Unresolved questions

1. **`graphify-out/`** — is anything there meant to be committed on purpose
   (e.g. `GRAPH_REPORT.md`), or is the whole directory tool scratch? Answer
   changes whether #3 is a blanket ignore or a selective one.
2. **Remote** — is this repo staying local-only? If a remote is planned, #2
   should go straight to CI rather than building a hook that CI replaces.
3. **`tasks/` vs `plans/`** — CLAUDE.md mandates `tasks/`; actual work lives in
   `plans/`. Which is authoritative? This is a process decision, not mine.
4. **Item #1 blast radius** — `mediagram serve` is loopback-only; are any of
   its clients (ffmpeg, browser, the Bun player) known to send a non-`bytes=`
   unit today? Does not change the fix, but changes whether it is urgent or
   merely correct.
