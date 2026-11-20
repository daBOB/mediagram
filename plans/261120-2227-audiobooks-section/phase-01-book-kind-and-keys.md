# Phase 01 — The `book` kind and its keys

**Context:** spec § Spec, § Core. Model: how `docu` was added (commit 90546aba).
**Status:** todo. **Priority:** first; every other phase reads `book`.

## Requirements

- `mlib_spec::Kind::Book`, spelled `book`, last in `Kind::ALL`.
- A book is a standalone set: `title` only; `container` `mp3`; `acodec` `mp3`; no `vcodec`, `q`, `hdr`, `show`, `s`, `e`.
- Wherever a match over `Kind` exists, `Book` takes the arm a **standalone** `Docu` (`show` absent) takes: reads as a film in `display_name` and part names, never looked up at TMDB, poster keyed by `title_art_key(title)`, may carry a category. `Book` is **not** in `edit::plan::EDITABLE_KINDS` (a video cannot become a book, nor a book a video).
- `category_key("book", None, Some(title))` → `("audiobooks", title_art_key(title))`.
- Core: `poster_key_for("book", None, None, Some(title))` → `title-{slug(title)}`.
- Web server: a book's `posterKeyFor` and `categoryKey` give the same keys.

## Files

- Modify: `crates/mlib-spec/src/caption/mod.rs` (enum + doc), `crates/mlib-spec/src/kind_spelling.rs`, `crates/mlib-spec/src/category_key.rs` (new `pub const AUDIOBOOKS: &str = "audiobooks";`), every file `cargo build --workspace` reports a non-exhaustive match in (today: `caption/display.rs`, `part_name.rs`, `mediagram-tmdb/src/{certification,credits,details,posters}.rs`, `mediagram/src/{edit/category.rs,edit/anime.rs,export/titles.rs,metadata/lookup.rs,metadata/search.rs,course/report.rs,course/upload.rs,commands/subtitles/dry_run.rs}`, `mediagram-core/src/state/stats/achievements.rs`)
- Modify: `crates/mediagram-core/src/dto/summary/poster_key.rs:27` (`Tut | Docu` → `Tut | Docu | Book`)
- Modify: `web/src/catalog/categories.ts:30` (book → `AUDIOBOOKS`), `web/src/state/achievements.ts:14` (`TitleKind` gains `"book"`)
- Modify: `web/test/fixtures/categories/keys.json` (shared with `crates/mlib-spec/tests/shared_category_keys.rs` and `web/test/category-keys.test.ts`)
- Test: `crates/mlib-spec/src/caption/tests.rs`, `crates/mediagram-core/src/dto/summary/poster_key_tests.rs`, `web/test/posters.test.ts`

## Interfaces

- Produces: `mlib_spec::Kind::Book`; `Kind::Book.as_str() == "book"`; `mlib_spec::category_key::AUDIOBOOKS == "audiobooks"`; web `AUDIOBOOKS` constant beside `TUTORIALS`/`DOCUMENTARIES` in `categories.ts`.

## Steps

- [ ] **1: Failing Rust tests** in `caption/tests.rs`:
  - `book_is_spelled_book`: `"book".parse::<Kind>() == Ok(Kind::Book)` and `Kind::Book.to_string() == "book"`.
  - `a_book_caption_round_trips`: a `Caption { t: Kind::Book, title: Some("Red Rising - Asche zu Asche"), container: "mp3", acodec: Some("mp3"), vcodec: None, q: None, hdr: None, dur: Some(94137), .. }` encodes and decodes equal; its `display_name()` is `"Red Rising - Asche zu Asche"`.
  - In `poster_key_tests.rs`: `a_book_keys_by_its_own_title`: `poster_key_for("book", None, None, Some("Die unendliche Geschichte")) == Some("title-die-unendliche-geschichte")`.
- [ ] **2: Fixture cases** appended to `keys.json`:
  - `"a book keys by its own title"`: kind `book`, show null, title `"Die unendliche Geschichte"`, key `["audiobooks", "title-die-unendliche-geschichte"]`.
  - `"a book title loses the letters the slug drops"`: title `"Über die Psychologie des Geldes: Zeitlose Lektionen über Reichtum, Gier und Glück"`, key `["audiobooks", "title-ber-die-psychologie-des-geldes-zeitlose-lektionen-ber-reichtum-gier-und-gl-ck"]`.
- [ ] **3: Run, expect failures:** `cargo test -p mlib-spec -p mediagram-core` (unknown kind `book`) and `cd web && bun test test/category-keys.test.ts` (null key for book).
- [ ] **4: Implement** the requirements above. Let `cargo build --workspace` list each non-exhaustive match and give each the standalone-docu arm. In `edit/category.rs` the description for a book is `"this audiobook"`; the refusal message for movies/episodes becomes `"categories are for courses, documentaries and audiobooks"`. In `achievements.rs` a book counts in no ladder (no new rung).
- [ ] **5: Web posters test:** in `web/test/posters.test.ts` add `"a book keys by its own title"` asserting the catalog route's poster key for a `book` row with title `"Die unendliche Geschichte"` is `"title-die-unendliche-geschichte"`; make the caller pass the title as `fallbackTitle` if it does not already.
- [ ] **6: Run all Rust and web checks** (plan.md § Check commands). Expected: green.
- [ ] **7: Commit** `feat(spec): the book kind for audiobooks, keyed by its own title`.

## Success criteria

All four check suites green; `grep -rn '"book"' crates/mlib-spec/src` shows the spelling once in `kind_spelling.rs`.

## Risks

A match arm copied from `Docu` with `show` present (collection) instead of `show` absent. The display/part-name tests above catch it.
