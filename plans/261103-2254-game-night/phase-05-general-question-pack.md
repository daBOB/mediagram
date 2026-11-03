# Phase 05: General-knowledge question pack

Spec: "General questions" (German, about 300, family-friendly, each checked against a source, shipped in the app); Calls #7 (the user may skim the file; not blocking).

Outcome: `crates/mediagram-game/questions/allgemein.json` holds at least 300 checked questions, and a test holds the file to its rules.

---

### Task 5.1: Write and check the pack

**Files:**
- Modify: `crates/mediagram-game/questions/allgemein.json`, `crates/mediagram-game/src/general_tests.rs`

**Interfaces:**
- Consumes: Task 3.2 `general_pack()` and the entry shape `{ "category", "text", "correct", "wrong": [3], "source" }`.

Rules for every entry:
- Category is one of: `Geografie`, `Natur & Tiere`, `Geschichte`, `Wissenschaft & Technik`, `Sport`, `Musik`, `Kunst & Literatur`, `Essen & Trinken`, `Sprache`, `Film & Fernsehen`, `Kinderwissen`, `Alltag` — about 25 each.
- Family-friendly: no violence, sex, drugs or divisive politics. Nothing that goes stale: no current office holders, records, prices or "latest" anything.
- Text ends with `?` and is at most 140 characters. Answers are at most 40 characters (they must fit a phone button) and the three wrong ones are plausible.
- `source` is an `https://` page (German Wikipedia preferred) that states the answer. Fetch it and confirm the answer before adding the entry.
- No questions about the brand games named in the spec's anti-goals.

- [ ] **Step 1: Write the failing test:**

```rust
#[test]
fn pack_has_300_sourced_questions() {
    let pack = general_pack_entries(); // raw entries, including `source`
    assert!(pack.len() >= 300);
    let texts: HashSet<_> = pack.iter().map(|q| &q.text).collect();
    assert_eq!(texts.len(), pack.len(), "texts are unique");
    for q in &pack {
        assert!(CATEGORIES.contains(&q.category.as_str()), "{}", q.text);
        assert!(q.text.ends_with('?') && q.text.chars().count() <= 140, "{}", q.text);
        assert!(q.source.starts_with("https://"), "{}", q.text);
        assert!(std::iter::once(&q.correct).chain(&q.wrong).all(|o| !o.is_empty() && o.chars().count() <= 40), "{}", q.text);
    }
}
```

- [ ] **Step 2: Run** `cargo test -p mediagram-game pack_has_300` — FAIL (14 entries without sources).
- [ ] **Step 3: Write the pack in batches of two categories.** For each batch: write the entries, fetch each source and confirm, then run the test and commit (`feat(game): general questions: Geografie and Natur & Tiere`, …). Re-source or drop the draft's 14.
- [ ] **Step 4: Spot check:** pick 30 entries at random across categories and re-verify them against their sources; fix any error and re-check its whole batch.
- [ ] **Step 5: Run** `cargo test -p mediagram-game` — PASS.
- [ ] **Step 6: Commit** the last batch.
