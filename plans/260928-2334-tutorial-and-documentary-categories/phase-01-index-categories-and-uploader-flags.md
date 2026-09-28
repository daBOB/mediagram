---
phase: 1
title: "Index: schema v12 `categories`, the key rule, merge, `--category` flags"
status: completed
priority: P2
effort: 5h
dependencies: []
---

# Phase 1 — Index: schema v12 `categories`, the key rule, merge, `--category` flags

## Overview

Uploader-only. Schema v12 adds one table holding a hand-set category per unit (course,
documentary collection, standalone documentary); `mlib-spec` gains the one function that
says which unit a set belongs to; `add-course`/`add-docu` take `--category` at upload,
`edit` takes `--category`/`--clear-category` afterwards; `pull-index`/`push-index` merge
the table last-writer-wins between the two uploading machines. No reader changes
behaviour yet (readers never query a table they do not know). Ships alone, minor bump.

**Before starting:** `git log -1` on `main` must show the anime phase 4 release (0.78.0,
schema v11). Re-read `crates/mlib-spec/src/schema.rs` — `V11` is the last group.

## Requirements

- `mlib_spec::category_key::category_key(kind: &str, show: Option<&str>, title: Option<&str>)
  -> Option<(&'static str, String)>`:
  - kind `tut` or `doc` → department `"tutorials"`; `docu` → `"documentaries"`;
    anything else (incl. unknown spellings) → `None`;
  - item key = `package::title_art_key(show.or(title)?)` — `None` when the name slugs to nothing.
  - `&str` kind (parsed inside) so the uploader (`row.kind.as_str()`), the core (string
    kinds) and the TS twin share one signature and one fixture.
- Table `categories(department, item_key, category, set_at)`, PK `(department, item_key)`;
  `category` NULL = cleared (row kept so a clear reaches the other machine); `set_at` Unix seconds.
- `edit <set-id> --category "<name>"` / `edit <set-id> --clear-category`: index-only,
  `--dry-run` writes nothing, never pushes; conflicts with every other edit flag, `--anime`
  and each other (clap `conflicts_with_all`).
- `add-course <dir> --category "<name>"`, `add-docu <file|dir> --category "<name>"`: validated
  before anything is uploaded (and in `--dry-run`, which prints it and writes nothing); written
  once the index is opened, before the upload session. Absent flag = existing category untouched.
- Normalisation, writer-side only: `split_whitespace().join(" ")`; empty → refused ("a category
  needs a name; use --clear-category to remove one"); `other` in any case → refused ("uncategorised
  items already sit under Other"); a case-insensitive (`to_lowercase`) match of a category already
  used by *another* unit in the same department → that spelling is used, with a note.
- Merge: newer `set_at` wins outright (NULL included); channel snapshot without the table
  (≤ v11) merges as 0; a second merge reports 0.
- Web `EXPECTED_SCHEMA = 12` in the same commit (lockstep test `crates/mediagram/tests/shared_playable_sql.rs:39`).

## Architecture

**Why this key (decision).** The unit a department page draws as one card is grouped by
`show` (courses, docu collections; web `library.js:248` `collections`, Android `Shelves.kt` `collections`) or is
a lone set (docu singles, `documentaries.js:19-23`, `Documentaries.kt:20-33`). Its custom art
is already stored under `title_art_key(show ?? title)` (`commands/artwork.rs:62-71`,
web `posterKeyFor`, core `dto/summary.rs:163-177`). Using the same key:
- re-uploads / resumes: same title → same key (cid-based skip identity is untouched);
- a course uploaded from two folders under one `--course` title: one key, one category,
  one card — even if the two runs used different `--cid`s (which `group_key` would split);
- a docu single re-uploaded (new set id): same title → keeps its category (`set_id` would not);
- a rename (`edit --show`/`--title`): the category, like the custom art, stays with the old
  name → set it again. Accepted: renames are per set and rare.
- `department` in the PK keeps a course and a documentary of the same name apart (their art
  is shared today; their categories need not be). Within Documentaries, a collection and a
  single with the same slug share one category — the same narrow limit the art has.

**Data flow**

```
add-course/add-docu --category ─┐   (validated before upload; written after db::open)
edit <set> --category/--clear ──┴─> edit::category::{planned, write}
      normalise ─> adopt existing spelling ─> index::categories::set(dept, key, name|NULL, now)
push-index / pull-index ─> merge_from: … + merge_categories (newer set_at wins)
readers (phases 2–3) ─> category_key(kind, show, title) ─> categories[(dept, key)] ─> set.category
```

**Schema v12** (`crates/mlib-spec/src/schema.rs`, `const V12` after `V11`):

```rust
const V12: &[&str] = &["CREATE TABLE IF NOT EXISTS categories(
        department TEXT NOT NULL,
        item_key TEXT NOT NULL,
        category TEXT,
        set_at INTEGER NOT NULL,
        PRIMARY KEY(department, item_key)
    )"];
```

Doc comment: what a unit is; the key is the unit's `title-<slug>` art key; kept apart from
`sets` because it is not in any caption (index-only) and must survive `metadata`; NULL is a
kept row so a clear merges; `set_at` decides a merge; optional to every reader.

**Merge** (`merge_categories::merge`, the `merge_anime_overrides.rs` statement with this
table's key): `INSERT … SELECT … FROM channel.categories WHERE true ON CONFLICT(department,
item_key) DO UPDATE SET category = excluded.category, set_at = excluded.set_at WHERE
excluded.set_at > categories.set_at`; 0 when `shared_columns(conn, "categories")` is empty.

**CLI output**

```
$ mediagram edit 01M3… --category "Trading"
set 01M3… — Forex Mentor - Trendline Mastery (the course: every lesson and document in it)
  category: - -> Trading
wrote the category; run `mediagram push-index` to publish
```
Unit label: "(the course: …)" for `tut`/`doc`, "(the collection: every documentary in it)"
for `docu` with a show, "(this documentary)" for a single. Refusals: film/episode → "set … is
a <kind>; categories are for courses and documentaries"; no key → "set … has no name a category
can be filed under"; same value → "already says <x>; nothing to do"; clear with none → "has no
category; nothing to do".

## Related Code Files

**Create**
- `crates/mlib-spec/src/category_key.rs` — `TUTORIALS`, `DOCUMENTARIES`, `category_key`.
- `crates/mlib-spec/tests/shared_category_keys.rs` — runs `web/test/fixtures/categories/keys.json`
  (path via `env!("CARGO_MANIFEST_DIR")/../../web/…`, the `mediagram-core/tests/shared_anime_fixtures.rs` pattern).
- `web/test/fixtures/categories/keys.json` — `[{ name, kind, show, title, key: [dept, itemKey] | null }]`:
  course lesson; course `doc` keys to its course; docu in a collection; docu single by its title;
  `movie` → null; `ep` → null; name of only punctuation → null; unknown kind → null; a title
  with umlauts (expected value taken from `mlib_spec::slug::slug`, not hand-typed). Written by
  this phase because the writer defines the key; phase 2 adds the TS run.
- `crates/mediagram/src/index/categories.rs` (+ `categories_tests.rs`) — `get(conn, dept, key)
  -> Result<Option<String>>` (None for no row or NULL row), `set(conn, dept, key, Option<&str>, at)`,
  `in_use(conn, dept, except_key) -> Result<Vec<String>>` (distinct non-NULL).
- `crates/mediagram/src/index/merge_categories.rs` — `pub(super) fn merge(conn) -> Result<usize>`.
- `crates/mediagram/src/edit/category.rs` (+ `category_tests.rs`) — pure `normalise(raw,
  in_use) -> Result<(String, Option<String> /*adopted from*/)>`; `planned(kind, name, raw) ->
  Result<Planned>` (key + normalised, pure: used before a dry run); `write(conn, planned)`;
  `run(conn, row, requested: Option<&str>, dry_run)` for `edit`.
- `crates/mediagram/src/commands/args_edit.rs` — `EditArgs` moved out of `args.rs` (189 lines;
  two new fields would pass 200), re-exported like `args_docu.rs` (`args.rs:3-10`).

**Modify**
- `crates/mlib-spec/src/lib.rs` — `pub mod category_key;` + module list doc line.
- `crates/mlib-spec/src/schema.rs` — `V12`; `SCHEMA_VERSION` 11→12 (:8); `READABLE_SCHEMAS`
  add 12 (:28); `GROUPS` add `V12` (:42); `OLDEST_READABLE_SCHEMA` doc (:10-22) "and v12 only
  the wholly new `categories` table". ~167 → ~188 lines.
- `crates/mediagram/src/index/mod.rs` — `pub mod categories; mod merge_categories;`.
- `crates/mediagram/src/index/merge.rs` — doc table row (:16) "`categories` | Missing keys
  inserted; shared keys take the later `set_at`"; `MergeReport.categories_taken` (:58); call after
  `merge_anime_overrides::merge` (:159); field in the struct literal (:169). 176 → ~181.
- `crates/mediagram/src/channel_index/report.rs` — "N category row(s) taken" beside :54-56.
- `crates/mediagram/src/commands/args.rs` — `#[path = "args_edit.rs"] mod args_edit; pub use args_edit::EditArgs;`.
- `EditArgs` (now in `args_edit.rs`) — `category: Option<String>` and `clear_category: bool`,
  each `conflicts_with_all` the other edit fields + `anime` + each other.
- `crates/mediagram/src/commands/args_docu.rs` — `AddCourseArgs.category`, `AddDocuArgs.category`.
- `crates/mediagram/src/commands/edit.rs` — after the anime dispatch (:32-34): `if args.category.is_some()
  || args.clear_category { return crate::edit::category::run(&conn, &row, args.category.as_deref(), args.dry_run); }`. 190 → 193.
- `crates/mediagram/src/commands/add_course.rs` — `planned(Kind::Tut, &course, raw)` right after `cid`
  (:23); dry run prints `category: <name>` (:43-48); `write` after `db::open` (:50), before `drop(conn)` (:58).
- `crates/mediagram/src/commands/add_docu/collection.rs` — same, `Kind::Docu`, `&collection` (:22, :41-46, :48).
- `crates/mediagram/src/commands/add_docu/mod.rs` — `run_file`: key from the same
  `resolve::docu_file(args.title, …)` title the dry run already computes (:43-50) and the upload
  resolves (`upload/prepare_set.rs:64`); open the index and `write` before `Session::new` (:71).
- `crates/mediagram/src/edit/mod.rs` — `pub mod category;`.
- `web/src/catalog.ts` — `EXPECTED_SCHEMA = 12` (:24); `OLDEST_READABLE_SCHEMA` doc (:26-37) names v12.
- `docs/mlib-package-v1.md` — the three `"schema":11` examples (:43, :69, :91) → 12
  (`crates/mediagram/tests/package_spec_examples.rs` checks them against `SCHEMA_VERSION`).
- Tests: `crates/mediagram/src/index/merge_tests.rs`, `crates/mediagram/src/cli_tests.rs` (beside :155),
  `crates/mediagram/tests/schema_migrations.rs` (a v11 file gains the table; upgraded == fresh).
- Docs: `docs/mlib-spec.md` "Schema v12 additions" after v11's (:312); `docs/system-architecture.md`
  §10.1 heading → v12, a `categories` bullet after `anime_overrides` (:805), version line (:824-828);
  `README.md` rows :222, :223 (`[--category <name>]`), :232 (`edit <set-id> --category <name>|--clear-category`);
  `CONTEXT.md` new term **Category** ("a hand-set label on a course, a documentary collection or a
  standalone documentary, one each, that files it into a row on its department page. _Avoid_: genre,
  tag"); `docs/project-changelog.md`.
- Versions: `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` `versionName` — minor
  (0.78.0 → 0.79.0 if main is there). `Cargo.lock` via `cargo check`.

**Delete** — none.

## Implementation Steps

1. `category_key.rs` + `keys.json` + `shared_category_keys.rs` (red → green).
2. Schema v12 + web lockstep + package doc examples; `cargo test -p mlib-spec -p mediagram --test shared_playable_sql --test package_spec_examples --test schema_migrations`.
3. `index/categories.rs` + tests.
4. `merge_categories.rs`, `MergeReport`, report line; merge tests (channel-only inserted, local-only
   kept, newer replaces, older does not, newer NULL clears a local name, v11 channel → 0, second merge → 0).
5. `edit/category.rs` + tests: normalise (trim/collapse, empty, `OTHER`/`other`, adopt "trading" →
   existing "Trading", exact kept, own row excluded from adoption); refusals; `tut` and `doc` of one
   course → one row; collection vs single keys; dry run writes nothing; clear keeps a NULL row.
6. `args_edit.rs` split + flags + `edit.rs` dispatch; `cli_tests.rs` (parse both flags, reject
   `--category x --clear-category`, `--category x --title y`, `--category x --anime yes`; add-course/add-docu parse).
7. `add-course`/`add-docu` wiring (collection + file); a dry run with `--category Other` fails
   before any upload.
8. Docs, changelog, version. No comment or test name mentions a plan or phase.
9. `scripts/check.sh` green.
10. **Operator steps — spelled out, NOT executed by the implementing agent.** One machine, no
    upload running, after installing this release:
    1. `mediagram pull-index` (resume pending uploads first, one machine at a time, as usual).
    2. If the anime backfill has not run yet (local `meta.schema_version` was 10 on 2026-09-28):
       `mediagram metadata` now (offline, TMDB cache) — see the anime plan's phase 1 step 10.
    3. One set id per unit:
       `sqlite3 ~/.local/share/mediagram/library.db "SELECT show, MIN(set_id) FROM sets WHERE kind='tut' GROUP BY show"`;
       `… "SELECT COALESCE(show, title), MIN(set_id) FROM sets WHERE kind='docu' GROUP BY COALESCE(show, 'single ' || set_id)"`.
    4. Per unit the user wants filed: `mediagram edit <set-id> --category "<name>" --dry-run`, then
       again without `--dry-run`. (4 courses today; 7 docu collections, 6 singles.)
    5. `mediagram push-index` (or `sync-index`).
    6. **Upgrade the second uploader before it publishes again**; its first `pull-index` takes the rows.

## Success Criteria

- [x] A v11 `library.db` opened by the new build is v12 with the table; matches a fresh v12.
- [x] `edit <lesson> --category Trading` writes `('tutorials','title-<course slug>','Trading',now)`; the
      same on a `doc` of that course is "already says Trading"; `--clear-category` leaves a NULL row.
- [x] `add-course <dir> --category Trading --dry-run` prints it and writes nothing; the real run writes
      the row before the first lesson uploads; `--category other` refuses before uploading.
- [x] Merge tests pass; `pull-index --dry-run` twice → 0 category rows the second time.
- [x] `keys.json` passes in `shared_category_keys.rs`; `code_standards.rs` green; `scripts/check.sh` green.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| A v11 uploader publishes after the v12 one: channel loses the table until the next v12 publish | M×M | Operator step 10.6; merge never deletes local rows, so the next v12 publish restores them. |
| Two machines create "trading" and "Trading" before merging → two rows | L×L | One person sets categories; `edit --category Trading` on the stray unit fixes it. |
| Re-casing a whole category is blocked by adoption | L×L | Documented in `--help`: clear the others, set one, set the rest (adopts the new spelling). |
| A renamed unit loses its category | L×L | Same as its custom art; set it again. Stated in the v12 doc comment. |
| A name that slugs to nothing (only punctuation/non-Latin) cannot be filed | L×L | Refused with a message; the art has the same limit. |
| Line limits (`args.rs` 189, `edit.rs` 190, `merge.rs` 176, `schema.rs` 167) | M×L | `args_edit.rs` split; others stay < 200 with the listed additions. |
| `rescan` rebuilds sets from captions; categories are not in captions | L×L | Table is untouched locally; the channel snapshot restores it on `pull-index`. |

**Rollback:** revert the commit. v12 indexes still read fine on v11 readers (newer schemas accepted);
a v11 uploader ignores the table and its next publish drops it from the channel (risk 1). Nothing to migrate back.

## Security Considerations

Free text from the operator only; bound SQL parameters; readers (phases 2–3) render it as text
(no HTML path). No network, no Telegram writes.
