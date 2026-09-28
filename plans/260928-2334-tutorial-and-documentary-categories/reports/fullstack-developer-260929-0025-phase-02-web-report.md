## Phase Implementation Report

### Executed Phase
- Phase: phase-02-web-category-rows
- Plan: plans/260928-2334-tutorial-and-documentary-categories
- Status: completed
- Worktree: /home/andre/Workspace/mediagram/.claude/worktrees/agent-a63aa1f601429ee8f
- Branch: feat/categories-web, commit 2cf7150d ("feat(web): Tutorials and Documentaries grouped by category; release 0.80.0")

### Files Modified
Created:
- `web/src/catalog/categories.ts` (69 lines) — `categoryKey`, `categoryNames(db)` (probes `sqlite_master`, `WHERE category IS NOT NULL`), `categoryOf`.
- `web/public/lib/categories.js` (34 lines) + `categories.d.ts` — `OTHER`, `categoryRows(units, categoryOf)`.
- `web/public/lib/catalog/category-rows.js` (50 lines) — `courseCategoryRows(shows, open)`, `documentaryCategoryRows(groups, singles, openGroup, play)`.
- `web/test/fixtures/categories/rows.json` — 8 cases per the phase's spec (no-rows, empty, mixed, all-filed, natural order, Other-last, input-order-kept, literal-"Other").
- `web/test/category-keys.test.ts`, `web/test/category-rows.test.ts`.

Modified: `web/src/catalog/routes.ts` (+`category` in `forBrowser`, 133→136 lines); `web/public/lib/library.d.ts` (+`category` field); `web/test/support/catalog-set.ts` (default `category: null`); `web/public/lib/catalog/department-pages.js` (175→186 lines: both rows wired, module/section doc updated); `docs/{web-player.md,project-changelog.md}`; version 0.79.0→0.80.0 in `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`, `android/app/build.gradle.kts` (`versionCode` untouched); phase frontmatter + `plan.md` table.

### Tasks Completed
All 7 implementation steps. Series/Anime untouched by code; `renderShowsDept`'s new `courseCategoryRows` call is unconditional but resolves to `[]` for Series since no episode ever carries a category (matches the phase's "no branch needed").

### Tests Status
- Typecheck: pass (`tsc --noEmit`)
- Lint: pass (`eslint public --max-warnings 0`)
- Unit tests: pass — 2365 pass, 0 fail across 178 files, including all 8 `rows.json` and all 9 `keys.json` cases plus new `categoryNames`/`categoryOf` table-shape tests
- `web/test/code-standards.test.ts`: pass (all touched files ≤ 200 lines; no ceiling edits needed)
- `scripts/check.sh`: **all checks passed** (clippy -D warnings, `cargo test --all` — no failures across the whole workspace, bun lint/test, gradle test/lint)
- `cargo metadata --locked --offline`: succeeds

### Visual/integration check (deviation from the phase's screenshot step)
No browser-automation tool was available in this session (only Read/Edit/Write/Bash/WebFetch/WebSearch/Agent/SendMessage), so the literal "screenshots to the user" step could not be done. Verified instead, against a scratch v12 index built by hand in a temp dir (never `~/.local/share/mediagram/library.db`, never the real player):
- `PREVIEW_INDEX=<scratch>/preview.db bun run preview`, then `curl /api/sets`: two courses (one `Programming`, one uncategorised), one documentary collection + two singles (one sharing the collection's `Science` category, one uncategorised) — every row's `category` matched, films/episodes not present in this fixture but covered by unit tests.
- A second scratch index with the `categories` table present but zero rows: `category: null` on every set, confirming the ≤v11/no-rows case reads identically.
- Ran the actual shipped `departments.js` + `categories.js` (no DOM, so short of the rendered strip itself) against the real `/api/sets` response: produced `Programming:[Rust Course]`/`Other:[Cooking Basics]` for Tutorials and `Science:[Terra X, Icebound]`/`Other:[Free Solo]` for Documentaries — matches spec (department order kept, collection before its matching single, Other last).
- `category-rows.js`'s DOM assembly (`collectionGrid` + `movieGrid(...).childNodes`) and `department-pages.js`'s row placement were verified by code review against `shelf-view.js`/`department-hero.js`'s actual signatures, matching the Series "Popular series" row's shape exactly as specified.
- Scratch files and background server processes cleaned up before commit; `git status` was empty of anything untracked.

### Issues Encountered
None against the phase text or the codebase; no file-ownership conflicts (Android untouched). One environment gap only: no browser tool this session, noted above and in the phase file's success criteria.

### Next Steps
Phase 3 (Android) unblocked: reads `web/test/fixtures/categories/{keys,rows}.json` and this phase's `categories.ts`/`categories.js`/`category-rows.js` as its web-side reference.
