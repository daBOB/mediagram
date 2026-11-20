# Phase 04 — Web: Audiobooks department, search, kids

**Context:** spec § Web player, § Kids. Model: Documentaries (`departments.js:80-86`, `department-pages.js:86` `renderDocumentariesDept`, `category-rows.js`).
**Status:** todo. **Depends on:** Phase 01.

## Requirements

- `book` sets leave every other shelf: pulled out in `groupDepartments` like `docu`, excluded from `groupLibrary`'s film filter, from search's lessons group.
- `Library.audiobooks: CatalogSet[]`, sorted by title.
- Nav: `Audiobooks` last, with its count; `#/audiobooks` routes to the department page; the Sections switches and household hiding cover it (`departmentOf(book) == "audiobooks"`).
- Department page, top to bottom: hero (title `Audiobooks`, line `countOf(n, "audiobook")`, no lead art); **Continue listening** (books with a position); one row per genre (category) via `categoryRows`, "Other" last; **Recently added**; **All audiobooks** (every book, by title). Empty: `emptyState("audiobooks", …)` with upload hint `mediagram add-audiobook <file or folder>`.
- Cards show the whole square cover: a book card's image is not cropped (contain within the card frame, or a 1:1 frame; match the existing card system).
- Search: an `Audiobooks` group and filter, counted.
- Kids: `forKidsProfile` treats `kind === "book" && category === "Kinderbücher"` as safe at every limit. No FSK badge on it.
- Home: a book with a position appears in the Home Continue rail (verify; add only if missing). No new Home band.

## Files

- Modify: `web/public/lib/departments.js` (`groupDepartments` returns `audiobooks`), `web/public/lib/library.js:281`, `web/public/lib/library.d.ts:107-113`
- Modify: `web/public/lib/catalog/sections.js` (`audiobooks: { label: "Audiobooks", empty: "No audiobooks yet.", extent: "audiobook" }`, last), `web/public/lib/section-access.js:23`
- Modify: `web/public/lib/address.js:26-31` `KNOWN_SECTIONS`, `web/public/lib/address.d.ts:10` `DepartmentSection`, `docs/web-player.md` route table (`#/audiobooks`) + `web/test/address.test.ts` kinds map (`"#/audiobooks": "department"`)
- Modify: `web/public/index.html:88` nav link `<a href="#/audiobooks" data-section="audiobooks">Audiobooks<span class="n" id="n-audiobooks"></span></a>`; `web/public/app.js` (:41 import, :110 count, :603 route)
- Create: `web/public/lib/catalog/audiobooks-department.js` — `export function renderAudiobooksDept(main, cx)` (≤200 lines; reuse `departmentHero`, `departmentUnderway`, `resumeCards`, `deptRow`, `movieGrid`, `categoryRows`)
- Modify: `web/public/lib/catalog/shelf-view.js:86-92` `UPLOAD_HINT.audiobooks` (+ JSDoc unions :97, :211); card CSS for the uncropped cover
- Modify: `web/public/lib/catalog/search-view.js:88-89,99,125`
- Modify: `web/public/lib/age-rating.js:57-60` (`export const KIDS_BOOK_CATEGORY = "Kinderbücher";`)
- Modify: the stub harness's preview catalog (where `bun run preview` reads its sets): three books, one `Kinderbücher`, one with no category, one with a position
- Test: `web/test/departments.test.ts`, `web/test/documentaries.test.ts` (or new `web/test/audiobooks.test.ts`), `web/test/section-access.test.ts`, `web/test/age-rating.test.ts`, `web/test/address.test.ts`, the search test beside `search-view.js`

## Interfaces

- Consumes: `kind: "book"`, `category` on `CatalogSet` (Phase 01 keys).
- Produces: `library.audiobooks`; `renderAudiobooksDept(main, cx)`; `KIDS_BOOK_CATEGORY`; section key `audiobooks`. Phase 06 mirrors these names on Android.

## Steps

- [ ] **1: Failing tests**
  - `"groupDepartments files books under audiobooks only"`: sets `[movie, docu, book "B", book "A"]` → `audiobooks` titles `["A","B"]`; `movies` holds the movie only; `documentaries` the docu only.
  - `"a book's department is audiobooks"` in `section-access.test.ts`'s table; hiding `audiobooks` removes books and nothing else.
  - `"a Kinderbücher book is for every kid"`: `forKidsProfile([book Kinderbücher, book Sachbuch, book no category], new Map(), 6)` → only the first; with a hand mark `from 6` on the Sachbuch book → both.
  - `"a book without a genre sits under Other"`: the page's category rows for books with categories `Sachbuch`, `null` → titles `["Sachbuch", "Other"]`.
  - Search: a query matching a book returns it in the `audiobooks` group, not lessons.
  - `address.test.ts`: `#/audiobooks` parses to `{ page: "department", section: "audiobooks" }`.
- [ ] **2: Run, expect failures:** `cd web && bun test`.
- [ ] **3: Implement** per Files.
- [ ] **4: Web checks** green.
- [ ] **5: Look at it** in the stub harness (`cd web && bun run preview`), desktop and phone width, dark and light: nav shows Audiobooks last with its count; the page draws hero, Continue listening, genre rows with Other last, Recently added, All audiobooks; covers whole. Screenshot both widths into `plans/261120-2227-audiobooks-section/visuals/`.
- [ ] **6: Commit** `feat(web): an Audiobooks department, grouped by genre`.

## Success criteria

Tests green; screenshots show every row and uncropped covers.

## Risks

`app.js`'s route chain falls through to `renderShowsDept` for an unknown section and would crash: the route branch must sit before it.
