# Phase 3: One action, both gaps

**Deliverable:** one core call that fetches descriptions and artwork in a
single pass, in the language the library was described in, and reports both.

## Context

- `crates/mediagram-core/src/api/artwork.rs` — `plan_fetch`, `verify_then_fetch`, `split_titles`: the pass this phase asks one question further
- `crates/mediagram-core/src/dto.rs` — `PosterReport`, which becomes a report about both halves
- `mediagram_tmdb::details::{details, from_details}` — the request and the mapping (phase 1)
- `crates/mediagram-core/src/api/details.rs` — where a row is kept (phase 2)
- `crates/mediagram/src/commands/metadata.rs:33-62` — the uploader's loop over the same two calls, as a reference for the shape

## Key insight

`plan_fetch` already resolves the current directory once, reads `library.db`
from it, and splits titles into those a provider can be asked about and those
it cannot. That plan is everything the description fetch needs too — the same
ids, the same classification, the same key. Asking TMDB for a title's artwork
and for its description are two questions about one title, and the payload for
both comes out of one cache.

**The language is in the data, not in a setting.** `shows.lang` on the index's
own rows records which language the library was described in — `de-DE` on this
library, on the phone and on the machine that pushed it. The core reads it and
asks TMDB in that language. A library with no rows yet falls back to whatever
the caller passes, which is the device's own locale.

This is the plan's one deliberate divergence from spec §6, which describes a
poster fetch alone and a hardcoded `en-US`. §6 is the earlier word; this is the
later one, and the §9 entry recording `en-US` as "a difference that may prove a
defect" is resolved here — it was a defect.

---

### Task 1: The language the library speaks

**Files:**
- Modify: `crates/mediagram-core/src/api/details.rs`
- Test: `crates/mediagram-core/src/api/details_tests.rs`

**Interfaces — Produces:** `pub(super) fn language_of(conn: &Connection, fallback: &str) -> String`.

- [ ] **Step 1: Write the failing test**

```rust
    /// The library says what language it was described in. Asking a
    /// provider in a different one produces a shelf where some titles read
    /// in German and the rest in English.
    #[test]
    fn the_language_is_read_from_the_rows_the_index_already_carries() {
        // shows rows all lang='de-DE' → "de-DE", whatever the fallback says
    }

    /// A library nobody has described yet has nothing to read, so the
    /// caller's own locale is the best guess available.
    #[test]
    fn a_library_with_no_descriptions_falls_back_to_the_caller() {
        // no shows rows → the fallback, unchanged
    }

    /// Rows in more than one language mean the library was described
    /// twice; the one it mostly speaks is the one to keep asking in.
    #[test]
    fn a_mixed_library_keeps_the_language_most_of_it_uses() {
        // 9 rows de-DE, 2 rows en-US → "de-DE"
    }
```

- [ ] **Step 2: Run it**

```bash
cargo test -p mediagram-core language_
```

Expected: FAIL to compile — `cannot find function language_of`.

- [ ] **Step 3: Implement**

One `GROUP BY lang ORDER BY COUNT(*) DESC LIMIT 1` over the index's `shows`,
ignoring empty strings, falling back to the parameter when there is no row.

- [ ] **Step 4: Run it, then commit**

```bash
cargo test -p mediagram-core
CARGO_SUB=build cargo $CARGO_SUB -p mediagram-core
git add crates/mediagram-core/
git commit -m "feat(core): ask the provider in the language the library was described in"
```

---

### Task 2: One pass, both questions

**Files:**
- Modify: `crates/mediagram-core/src/api/artwork.rs`, `crates/mediagram-core/src/api/details.rs`
- Modify: `crates/mediagram-core/src/dto.rs` — the report
- Modify: `crates/mediagram-core/src/api/mod.rs` — the exported call
- Test: `crates/mediagram-core/tests/artwork_fetch.rs` (widen), `details_tests.rs`

**Interfaces — Produces:** `FetchReport { posters_fetched, posters_already_held,
details_recorded, details_already_known, no_provider_id, failed }` and a core
method that fills both. **Consumes:** `plan_fetch`, `split_titles`,
`language_of`, `details::upsert`.

- [ ] **Step 1: Decide the report, and say why in its doc**

`PosterReport`'s four counts become six. This is a breaking change to the
core's exported surface and to every Kotlin caller, which is acceptable
pre-release and is not acceptable silently: the rename is `PosterReport` →
`FetchReport`, and the doc comment says what each count is counted in.

**Counts are per title, not per file.** `no_provider_id` already counts sets
where the others count deduplicated titles, which makes a 162-lesson course
read as "162 titles have no provider entry" beside "3 posters fetched". Fix it
here while the type is being rewritten: count titles throughout, and say so.

- [ ] **Step 2: Write the failing test**

`crates/mediagram-core/tests/artwork_fetch.rs` already has everything these
need: `StubApi` and `RejectingApi` (`:15-38`), `catalog_with_kinds(dir, kinds)`
(`:40`), `core_at(dir)` (`:58`), `write_existing_poster` (`:67`),
`fetch_with(dir, api)` (`:77`) and `offline_client()` (`:96`). Widen
`fetch_with` to return the new report rather than writing a second harness.

```rust
/// One pass answers both questions about a title. They come from one
/// cached payload, and a viewer who asked for the missing pieces did not
/// ask for half of them.
#[tokio::test]
async fn one_run_records_a_description_and_fetches_a_poster() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    let report = fetch_with(dir.path(), StubApi::default()).await;

    assert_eq!(report.details_recorded, 1);
    assert_eq!(report.posters_fetched, 1);
    assert_eq!(report.failed, 0);
}

/// A title already described is not asked about again, the same way a
/// poster already held is not downloaded again.
#[tokio::test]
async fn a_title_already_described_is_left_alone() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    fetch_with(dir.path(), StubApi::default()).await;
    let second = fetch_with(dir.path(), StubApi::default()).await;

    assert_eq!(second.details_recorded, 0);
    assert_eq!(second.details_already_known, 1);
    assert_eq!(second.posters_fetched, 0);
    assert_eq!(second.posters_already_held, 1);
}

/// A provider that will not answer about one title costs that title its
/// description and nothing else.
#[tokio::test]
async fn one_unanswerable_title_does_not_end_the_run() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550)), ("movie", Some(999_999_999))]);

    let report = fetch_with(dir.path(), StubApi::answering_only(550)).await;

    assert_eq!(report.details_recorded, 1);
    assert_eq!(report.failed, 1);
}

/// A rejected key is reported as a key problem before anything is spent,
/// and never by quoting the key back.
#[tokio::test]
async fn a_rejected_key_stops_the_run_before_it_starts() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550))]);

    let err = fetch_rejecting(dir.path()).await.unwrap_err();

    assert!(matches!(err, CoreError::NotAuthorized(_)));
}
```

`StubApi::answering_only` is the one helper these add — `StubApi` today answers
for anything, and the third test needs one title it will not answer for.
**Live TMDB is never called from a test.**

- [ ] **Step 3: Implement**

Extend the existing pass rather than writing a second one: after `verify_key`,
walk the same title list once, and for each title record its description and
fetch its poster. The verification stays on the uncached client — `81f6a10` put
it there because a warm cache otherwise vouches for a rotated key.

`artwork.rs` is 198 lines. The description half belongs in `details.rs`, which
`artwork.rs` calls; if the caller still crosses 200, the split is the loop, not
a line count.

- [ ] **Step 4: Run everything**

```bash
cargo test -p mediagram-core
CARGO_SUB=build cargo $CARGO_SUB -p mediagram-core
ANDROID_HOME=/home/andre/android-sdk ./scripts/check.sh
wc -l crates/mediagram-core/src/api/*.rs crates/mediagram-core/src/dto.rs
```

`check.sh` will now fail on the Kotlin side: the renamed report has no
callers that compile. That is expected and is phase 4's work — **say so in the
report rather than patching Kotlin here.** Confirm the Rust half is green on
its own first.

- [ ] **Step 5: Regenerate the bindings**

```bash
./scripts/generate-android-bindings.sh
```

`check.sh` does not do this, which has already bitten this project once: a
Rust record changed and Kotlin stayed compiled against the old shape. Run it
whenever a `uniffi::Record` changes, and commit the result.

- [ ] **Step 6: Commit**

```bash
git add crates/ android/core/rust/
git commit -m "feat(core): fill both gaps a library leaves, in one pass"
```

## Todo list

- [ ] The language is read from the index, with the caller's locale as fallback
- [ ] One pass records descriptions and fetches posters
- [ ] `FetchReport` counts titles throughout, and says so
- [ ] Verification still happens off the cached client
- [ ] Bindings regenerated and committed
- [ ] Every file under 200 lines

## Success criteria

One core call fills both gaps for a library, in the library's own language,
reporting six counts that all mean titles. `cargo test -p mediagram-core` and
`cargo build -p mediagram-core` are green; Kotlin is knowingly broken until
phase 4.

## Risk assessment

| Risk | Mitigation |
|---|---|
| A warm cache vouches for a rotated key again | Step 3 keeps verification on the uncached client and names why. |
| The two halves drift into two passes over one list | Step 3 extends the existing pass; the review checks there is one walk. |
| The report's counts mean different things again | Step 1 makes them all titles and documents it. |
| Kotlin left compiled against the old record | Step 5 regenerates; phase 4 consumes. |

## Next steps

Phase 4 renames the menu item and reads the six counts back to the viewer.
