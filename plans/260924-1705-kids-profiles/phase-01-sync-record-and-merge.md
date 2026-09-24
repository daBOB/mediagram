# Phase 01 — Sync record and merge (web + Rust)

**Context:** spec § Sync. Web `web/src/state/sync-record.ts`, `web/src/state/merge.ts`; Rust ports `crates/mediagram-core/src/state/record.rs`, `merge.rs`. Shared fixtures `web/test/fixtures/watch-state/*.json` are run by `web/test/shared-watch-state-fixtures.test.ts` and `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` — both iterate every case, so a new case is picked up by both with no test-code change.

**Overview:** Priority high (everything else depends on it). Adds the optional per-profile `kids` flag to the record and the "any says yes" merge rule, identically on both implementations.

---

### Task 1: Web record parse and merge carry `kids`

**Files:**
- Modify: `web/test/fixtures/watch-state/record-parse.json` (append a case)
- Modify: `web/test/fixtures/watch-state/merge.json` (append a case)
- Modify: `web/src/state/sync-record.ts` (`ProfileState`, `parseRecord` profile loop ~`:137-156`)
- Modify: `web/src/state/merge.ts` (`MergedProfile`, held viewer state, result push ~`:91-145`)

**Interfaces:**
- Produces: `ProfileState.kids?: true`; `MergedProfile.kids?: true`. Absent means ordinary. Never `false` on either type — an ordinary profile's object is unchanged, so every existing fixture still compares equal.

- [ ] **Step 1: Add the failing fixture cases**

Append to `record-parse.json` (inside the top-level array):

```json
{
  "name": "a kids profile says so, and only a literal true counts",
  "input": "{\"format\":1,\"device\":\"tablet\",\"writtenAt\":1789000000000,\"profiles\":[{\"name\":\"Mia\",\"kids\":true,\"progress\":[],\"watched\":[]},{\"name\":\"Ben\",\"kids\":\"yes\",\"progress\":[],\"watched\":[]},{\"name\":\"Ada\",\"kids\":false,\"progress\":[],\"watched\":[]}]}",
  "expect": {
    "format": 1,
    "device": "tablet",
    "writtenAt": 1789000000000,
    "profiles": [
      { "name": "Mia", "kids": true, "progress": [], "watched": [] },
      { "name": "Ben", "progress": [], "watched": [] },
      { "name": "Ada", "progress": [], "watched": [] }
    ]
  }
}
```

Append to `merge.json`:

```json
{
  "name": "a viewer is a kids profile when any device says so",
  "records": [
    { "format": 1, "device": "laptop", "writtenAt": 0,
      "profiles": [{ "name": "Mia", "kids": true, "progress": [], "watched": [] }] },
    { "format": 1, "device": "desktop", "writtenAt": 0,
      "profiles": [{ "name": "mia", "progress": [], "watched": [] }] }
  ],
  "expect": {
    "profiles": [{ "name": "mia", "displayName": "Mia", "kids": true, "progress": [], "watched": [] }]
  }
}
```

`displayName` is "Mia" because the spelling comes from the lexically greater device id, and `"laptop" > "desktop"`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts`
Expected: FAIL on the two new cases (`kids` missing from parsed/merged output).

- [ ] **Step 3: Implement**

`sync-record.ts` — the type:

```ts
export interface ProfileState {
  /** The viewer. See the plan's Identity section: the name, not the id. */
  name: string;
  /** The writing device's own id for this profile — provenance, not identity. */
  localId?: string;
  /**
   * Present only on a kids profile. Written only when true, so an ordinary
   * profile's entry reads exactly as it did before the flag existed.
   */
  kids?: true;
  progress: ProgressRow[];
  watched: WatchedRow[];
  /** Absent on a document from before this existed — not the same as empty. */
  watchlist?: ListRow[];
  collections?: CollectionRow[];
}
```

In `parseRecord`'s profile loop, replace the `profiles.push({ name, localId: …,` opening with:

```ts
    profiles.push({
      name,
      localId: text_(row.localId) ?? undefined,
      // Only a literal `true`: a flag that restricts what a child sees must
      // not be switched on by a string that merely looks truthy.
      ...(row.kids === true ? { kids: true as const } : {}),
```

(keep the remaining fields as they are). If `localId: undefined` currently appears in parsed output and fixtures compare with `toEqual`, keep that behaviour unchanged — only add the spread.

`merge.ts` — add to `MergedProfile` after `displayName`:

```ts
  /** A kids profile if any device's document says so; absent otherwise. */
  kids?: true;
```

Add `kids: boolean` to the held-viewer object type and initialise it `kids: false` where `held` is created. After the `nameFrom` block, before the row loops:

```ts
      // Sticky: once any device calls this viewer a kids profile, no document
      // that merely lacks the flag — an older device's, say — can undo it.
      if (profile.kids === true) held.kids = true;
```

In the result push:

```ts
    profiles.push({
      name,
      displayName: held.displayName,
      ...(held.kids ? { kids: true as const } : {}),
      progress,
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd web && bun test test/shared-watch-state-fixtures.test.ts test/state-merge.test.ts test/state-sync-record.test.ts`
Expected: PASS, including every pre-existing case.

- [ ] **Step 5: Commit**

```bash
git add web/test/fixtures/watch-state web/src/state/sync-record.ts web/src/state/merge.ts
git commit -m "feat(state-sync): carry a sticky kids flag on synced profiles"
```

---

### Task 2: Rust record parse and merge carry `kids`

**Files:**
- Modify: `crates/mediagram-core/src/state/record.rs` (`ProfileState` ~`:45-59`, `profile_state` ~`:136-157`)
- Modify: `crates/mediagram-core/src/state/merge.rs` (`MergedProfile` ~`:26-43`, `ViewerState` ~`:60-68`, loop ~`:90-104`, push ~`:146-153`)
- Create: `crates/mediagram-core/tests/kids_profile_record.rs`
- Test: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` (no change — it runs Task 1's new cases)

Note: serde here does not deny unknown fields, so the shared fixtures alone would *pass* before this change (the `kids` key is silently dropped on both sides of the comparison). The new test file is what fails first.

**Interfaces:**
- Consumes: Task 1's fixture cases.
- Produces: `ProfileState.kids: bool` and `MergedProfile.kids: bool`, serde `default` and skipped when false — so fixture `expect` objects without the key deserialize to `false`, and exported JSON carries the key only when true.

- [ ] **Step 1: Write the failing test**

`crates/mediagram-core/tests/kids_profile_record.rs`:

```rust
//! The kids flag on a synced profile: read only from a literal `true`,
//! written only when set, and sticky across a merge.

use mediagram_core::state::merge::merge_states;
use mediagram_core::state::record::parse_record;

const KIDS_AND_NOT: &str = r#"{"format":1,"device":"tablet","writtenAt":1,"profiles":[
  {"name":"Mia","kids":true,"progress":[],"watched":[]},
  {"name":"Ben","kids":"yes","progress":[],"watched":[]}]}"#;

#[test]
fn only_a_literal_true_marks_a_kids_profile() {
    let record = parse_record(KIDS_AND_NOT).unwrap();
    assert!(record.profiles[0].kids);
    assert!(!record.profiles[1].kids);
}

#[test]
fn an_ordinary_profile_is_written_without_the_key() {
    let record = parse_record(KIDS_AND_NOT).unwrap();
    let written = serde_json::to_value(&record).unwrap();
    assert_eq!(written["profiles"][0]["kids"], serde_json::Value::Bool(true));
    assert!(written["profiles"][1].get("kids").is_none());
}

#[test]
fn any_device_saying_kids_makes_the_merged_viewer_kids() {
    let with = parse_record(r#"{"format":1,"device":"laptop","writtenAt":0,"profiles":[{"name":"Mia","kids":true}]}"#).unwrap();
    let without = parse_record(r#"{"format":1,"device":"desktop","writtenAt":0,"profiles":[{"name":"mia"}]}"#).unwrap();
    for order in [vec![with.clone(), without.clone()], vec![without, with]] {
        let merged = merge_states(&order);
        assert_eq!(merged.profiles.len(), 1);
        assert!(merged.profiles[0].kids);
    }
}
```

Run: `cargo test -p mediagram-core --test kids_profile_record`
Expected: FAIL to compile — `no field kids on type ProfileState`.

- [ ] **Step 2: Implement**

`record.rs`, add a helper near the other helpers:

```rust
/// For `skip_serializing_if`: an ordinary profile carries no `kids` key.
fn is_false(value: &bool) -> bool {
    !*value
}
```

(If `merge.rs` needs it too, make it `pub(crate)`.)

`ProfileState`, after `local_id`:

```rust
    /// Present only on a kids profile. Written only when true, so an
    /// ordinary profile's entry reads exactly as it did before the flag.
    #[serde(default, skip_serializing_if = "is_false")]
    pub kids: bool,
```

`profile_state`:

```rust
    Some(ProfileState {
        name,
        local_id: text_(row.get("localId")),
        // Only a literal `true`, as on the web: a restricting flag must not be
        // switched on by a value that merely looks truthy.
        kids: row.get("kids") == Some(&Value::Bool(true)),
        progress: …unchanged…
```

`merge.rs` — `MergedProfile`, after `display_name`:

```rust
    /// A kids profile if any device's document says so.
    #[serde(default, skip_serializing_if = "crate::state::record::is_false")]
    pub kids: bool,
```

`ViewerState` gains `kids: bool`, initialised `kids: false` in `or_insert_with`. After the `name_from` block:

```rust
            // Sticky: a document lacking the flag — an older device's — cannot
            // undo another device's word that this viewer is a kids profile.
            if profile.kids {
                held.kids = true;
            }
```

In the push: `kids: held.kids,`.

Every other `ProfileState { … }` / `MergedProfile { … }` literal in the crate (e.g. `exchange.rs:40`, tests) must add `kids: false` for now — find them with `grep -rn "ProfileState {\|MergedProfile {" crates/mediagram-core`. Task 4 replaces the exchange one with the real value.

- [ ] **Step 3: Run to verify**

Run: `cargo test -p mediagram-core --test kids_profile_record && cargo test -p mediagram-core`
Expected: PASS, including `shared_watch_state_fixtures` with Task 1's cases.

- [ ] **Step 4: Commit**

```bash
git add crates/mediagram-core
git commit -m "feat(core-sync): read and merge the kids profile flag like the web"
```

## Success criteria

Both implementations pass the same two new fixture cases; every existing case passes unchanged; an ordinary profile never gains a `kids` key.
