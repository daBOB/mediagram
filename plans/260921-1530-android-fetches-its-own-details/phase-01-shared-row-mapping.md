# Phase 1: One row mapping, in the crate both sides share

**Deliverable:** `ShowRow` and `from_details` move into `mediagram-tmdb`, so
the uploader and the core read one copy of the mapping rather than two.

## Context

- `crates/mediagram-tmdb/src/details.rs` — `details(api, kind, id)`, already shared, already the one request
- `crates/mediagram/src/index/shows.rs:25-41` (`ShowRow`) and `:44-80` (`from_details`) — what moves
- `crates/mediagram/src/index/shows.rs:82-148` — `upsert`, `get`, `count`: SQL over the uploader's own database, which stays put
- `crates/mediagram-tmdb/src/posters.rs` — the sibling that already lives here, for the shape a moved module takes

## Key insight

The request is already shared and the payload type is already shared
(`DetailsResponse` in `tmdb_types.rs`). Only the mapping from payload to row
sits in the uploader, and the core needs exactly that mapping. Moving it is a
move, not a design.

What does **not** move is the SQL. `upsert`, `get` and `count` write the
uploader's `library.db`; the core writes a different file for a different
reason, and phase 2 gives it its own. A row type shared between them is the
seam; a statement is not.

`from_details` is pure — payload in, row out, no connection, no IO — which is
why it can move at all and why its tests move with it unchanged.

---

### Task 1: Move the row and its mapping

**Files:**
- Modify: `crates/mediagram-tmdb/src/details.rs` — gains `ShowRow` and `from_details`
- Modify: `crates/mediagram-tmdb/src/lib.rs` — export them
- Modify: `crates/mediagram/src/index/shows.rs` — loses both, keeps the SQL
- Modify: `crates/mediagram/src/metadata/show_details.rs` — imports from the new home

**Interfaces — Produces:** `mediagram_tmdb::details::{ShowRow, from_details}`.
**Consumes:** `DetailsResponse` (`tmdb_types.rs`), `Kind` (`mlib-spec`).

- [x] **Step 1: Read what moves, before moving it**

```bash
cd /home/andre/Workspace/mediagram-android
sed -n '20,80p' crates/mediagram/src/index/shows.rs
sed -n '1,40p' crates/mediagram-tmdb/src/details.rs
```

`ShowRow` is at `:25-41` and `from_details` at `:44-80`. `from_details` calls
`kind_key` from `mediagram_tmdb::posters` — already in the destination crate, so
that import shortens rather than breaking.

- [x] **Step 2: Move them, byte for byte**

Cut both into `crates/mediagram-tmdb/src/details.rs` below `details()`, and
export from `lib.rs` the way `posters`' items already are. Change nothing but
the imports the move forces.

This is a pure move. If a field, a doc comment or a `join` changes, it was not
a move, and the review will say so.

`crates/mediagram/src/index/shows.rs` keeps `upsert`, `get` and `count`, and
re-imports `ShowRow` from its new home rather than redefining it.

- [x] **Step 3: Move the tests that belong to the mapping**

Any test in `crates/mediagram/src/index/shows.rs` (or its sibling test file)
that exercises `from_details` without a `Connection` moves with it. A test that
opens a database stays — it is testing the SQL, which did not move.

Move the assertions unchanged. A moved test whose expectations shifted is a
rewritten test wearing a move's clothes.

- [x] **Step 4: Build both sides**

```bash
cargo test -p mediagram-tmdb
cargo test -p mediagram
CARGO_SUB=build cargo $CARGO_SUB -p mediagram-core
ANDROID_HOME=/home/andre/android-sdk ./scripts/check.sh
```

Expected: green. Nothing behavioural changed, so a failure here is a move that
was not one.

- [x] **Step 5: Check the line budget**

```bash
wc -l crates/mediagram-tmdb/src/*.rs crates/mediagram/src/index/shows.rs
```

`details.rs` is 29 lines and gains roughly 55; `shows.rs` is 149 and loses
them. Both land well under 200. Report both counts.

- [x] **Step 6: Commit**

```bash
git add crates/
git commit -m "refactor(tmdb): one mapping from a provider payload to a row"
```

## Todo list

- [x] `ShowRow` and `from_details` live in `mediagram-tmdb`
- [x] The uploader's `shows.rs` keeps only SQL and imports the row
- [x] The mapping's tests moved with it, assertions unchanged
- [x] `cargo build -p mediagram-core` alone is clean
- [x] Both files under 200 lines

## Success criteria

One definition of `ShowRow` in the workspace, one `from_details`, and
`./scripts/check.sh` green. `grep -rn "struct ShowRow" crates` returns one hit.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The move quietly edits the mapping | Step 2 says byte for byte; the review checks the diff's added and removed hunks against each other. |
| A test moves and its expectations drift | Step 3 says assertions unchanged, for the same reason. |
| The uploader keeps a second `ShowRow` by accident | Success criteria greps for it. |

## Next steps

Phase 2 consumes `from_details` to build rows the core can keep.
