# Phase 04 — Web filter, profile switching and the create form

**Context:** spec § Rule, § Where the filter applies, § UI. Files: `web/public/lib/age-rating.js` (`kidsVerdict`), `web/public/app.js` (`loadCatalog` `:473-495`, startup `:740-770`, `showProfile` `:385`, `viewSearch` `:447`, `viewMovies`, `viewCollections`, `viewHome`), `web/public/lib/catalog/shelf-view.js` (`emptyState` `:86`), `web/public/lib/playback/player-library-marks.js` (`refreshKids`), `web/public/lib/profile-picker.js`, `web/public/lib/watch-state.js` (`createRecord` `:109`, `createProfile` `:154`), `web/public/style.css` (`.who*` `:233-304`). App-level tests: `web/test/browser-application.test.ts` with harness `web/test/support/browser-application.ts`. Depends on Phase 02.

**Found while planning:** the header's profile button (`#who`, `app.js:386`) has never had a click handler, so a running page cannot change profile; `chooseProfile` already supports `{ canCancel: true }` (resolves the current id on "Stay as I am"). Task 6 wires it — the spec's "switching profile rebuilds the library" needs it, and an adult needs a way out of a kids profile.

---

### Task 5: `forKidsProfile` — the one filter rule

**Files:**
- Modify: `web/public/lib/age-rating.js`
- Test: `web/test/age-rating.test.ts`

**Interfaces:**
- Produces: `forKidsProfile(sets: CatalogSet[], marked: Set<string>): CatalogSet[]` — keeps catalog order.

- [ ] **Step 1: Write the failing test** (append to `age-rating.test.ts`; update its import to include `forKidsProfile`)

```ts
describe("what a kids profile sees", () => {
  const sets = [
    film("Zero", "0"), film("Six", "6"), film("Twelve", "12"),
    film("Sixteen", "16"), film("Eighteen", "18"),
    film("Unrated", null), film("UnratedMarked", null),
    film("SixteenMarked", "16"),
    { setId: "lesson-1", kind: "tut", show: "Course", fsk: null },
    { setId: "lesson-2", kind: "tut", show: "Course", fsk: null },
    episode("Bluey", "0"), episode("Dexter", "18"),
  ];
  const marked = new Set(["UnratedMarked", "SixteenMarked", "lesson-2"]);
  const seen = forKidsProfile(sets, marked).map((set) => set.setId);

  test("rated twelve and under is shown; sixteen and eighteen are not", () => {
    expect(seen).toEqual(expect.arrayContaining(["Zero", "Six", "Twelve", "Bluey-1"]));
    expect(seen).not.toContain("Sixteen");
    expect(seen).not.toContain("Eighteen");
    expect(seen).not.toContain("Dexter-1");
  });

  test("unrated is hidden unless marked by hand, lessons included", () => {
    expect(seen).not.toContain("Unrated");
    expect(seen).toContain("UnratedMarked");
    expect(seen).not.toContain("lesson-1");
    expect(seen).toContain("lesson-2");
  });

  test("a hand mark does not override a rating", () => {
    expect(seen).not.toContain("SixteenMarked");
  });

  test("the catalog's order is kept", () => {
    expect(seen).toEqual(["Zero", "Six", "Twelve", "UnratedMarked", "lesson-2", "Bluey-1"]);
  });
});
```

- [ ] **Step 2: Run** `cd web && bun test test/age-rating.test.ts` — Expected: FAIL, `forKidsProfile` is not exported.

- [ ] **Step 3: Implement** (append to `age-rating.js`)

```js
/**
 * The catalog a kids profile sees: rated for kids, or unrated and marked by
 * hand. Applied once to the whole catalog, so every shelf, search and reel
 * built from it agrees. A rating decides on its own — a hand mark on a title
 * rated too old does not let it through, as on the Kids shelf.
 * @param {import("./library.js").CatalogSet[]} sets
 * @param {Set<string>} marked set ids marked for Kids by hand
 */
export function forKidsProfile(sets, marked) {
  return sets.filter((set) => {
    const verdict = kidsVerdict(set);
    return verdict === "safe" || (verdict === "unrated" && marked.has(set.setId));
  });
}
```

- [ ] **Step 4: Run** `cd web && bun test test/age-rating.test.ts` — Expected: PASS.

- [ ] **Step 5: Commit** `git add web/public/lib/age-rating.js web/test/age-rating.test.ts && git commit -m "feat(web-kids): filter a catalog down to what a kids profile may see"`

---

### Task 6: Apply the filter where the page takes in its catalog; switch profiles

**Files:**
- Modify: `web/public/app.js`
- Modify: `web/public/lib/catalog/shelf-view.js` (`emptyState`)
- Modify: `web/public/lib/playback/player-library-marks.js` (`refreshKids`, click guard)
- Test: `web/test/browser-application.test.ts`

**Interfaces:**
- Consumes: `forKidsProfile` (Task 5); `Profile.kids` from `GET /api/profiles` (Task 3); `state.profile()`, `state.profileId()`, `state.kids()` (existing, `watch-state.js:138-139,382`); `chooseProfile(root, { canCancel: true })` (existing).
- Produces: `emptyState(section, { kids = false } = {})`.

- [ ] **Step 1: Write the failing app-level tests** (append to `browser-application.test.ts`; `film`, `catalog`, `intercept`, `start`, `page`, `stream`, `env` are the file's existing helpers)

```ts
describe("kids profiles", () => {
  const rated = (setId: string, fsk: string | null) => ({ ...film(setId), fsk });
  const profiles = [
    { id: "viewer", name: "Viewer", createdAt: 1, kids: true },
    { id: "adult", name: "Adult", createdAt: 2, kids: false },
  ];
  beforeEach(() => {
    catalog = JSON.stringify([rated("Family", "6"), rated("Grown", "16"), rated("Unknown", null), rated("Marked", null)]);
    intercept = (url) => {
      if (url === "/api/profiles") return Response.json({ remembers: true, profiles });
      if (url === "/api/kids") return Response.json({ kids: ["Marked"] });
      if (url.startsWith("/api/search")) {
        return Response.json({ query: "x", hits: [rated("Family", "6"), rated("Grown", "16")] });
      }
      return null;
    };
  });

  test("a kids profile's shelves hold only what is rated for kids or marked by hand", async () => {
    await start();
    expect(env.node("n-movies").textContent).toBe("2");
    await env.navigate("#/movies");
    expect(page()).toContain("Family");
    expect(page()).toContain("Marked");
    expect(page()).not.toContain("Grown");
    expect(page()).not.toContain("Unknown");
  });

  test("search on a kids profile drops hits outside its library", async () => {
    await start();
    await env.navigate("#/search/x");
    expect(page()).toContain("Family");
    expect(page()).not.toContain("Grown");
  });

  test("a catalog refresh on a kids profile is filtered too", async () => {
    await start();
    catalog = JSON.stringify([rated("Family", "6"), rated("Grown", "16"), rated("Later", "18"), rated("Also", "0")]);
    stream().fire("catalog");
    await settle();
    expect(env.node("n-movies").textContent).toBe("2");
  });

  test("choosing an adult profile brings the whole library back without fetching it again", async () => {
    await start();
    const fetched = env.requests.filter((request) => request.url === "/api/sets").length;
    env.node("who").fire("click");
    await settle();
    const adult = descendants(env.document.body)
      .find((node) => node.className === "who-tile" && textOf(node).includes("Adult"))!;
    adult.fire("click");
    await settle();
    expect(env.node("who").textContent).toBe("Adult");
    expect(env.node("n-movies").textContent).toBe("4");
    expect(env.requests.filter((request) => request.url === "/api/sets").length).toBe(fetched);
  });

  test("an empty kids library says what it is waiting for", async () => {
    catalog = JSON.stringify([rated("Grown", "16")]);
    await start();
    await env.navigate("#/movies");
    expect(page()).toContain("Nothing rated FSK 12 or under yet.");
  });

  test("the player offers no Kids mark on a kids profile", async () => {
    await start();
    await env.navigate("#/film/Family");
    descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
    await settle();
    expect(env.node("kids").hidden).toBe(true);
  });
});
```

If `env.requests` is not on the returned env object, it is: `browserEnvironment()` returns `requests` and `applicationEnvironment()` spreads `...env`.

- [ ] **Step 2: Run** `cd web && bun test test/browser-application.test.ts` — Expected: the six new tests FAIL (shelves unfiltered; `#who` has no handler).

- [ ] **Step 3: Implement**

`shelf-view.js`:

```js
/**
 * @param {"movies"|"series"|"tutorials"} section
 * @param {{kids?: boolean}} [options] a kids profile is waiting for ratings,
 *   not uploads, so it is told that instead of how to upload
 */
export function emptyState(section, { kids = false } = {}) {
  if (kids) return el("p", "empty", "Nothing rated FSK 12 or under yet.");
  const p = el("p", "empty");
  // …existing body unchanged…
}
```

`app.js` — import beside `kidsShelf`: `import { forKidsProfile, kidsShelf } from "./lib/age-rating.js";`. Add under `let catalogText = "";`:

```js
/** The catalog as the server last sent it, before any profile's filter. */
let catalogSets = [];

const kidsProfile = () => state.profile()?.kids === true;

/**
 * Builds the library the current profile sees from the last catalog.
 *
 * The one place a kids profile's filter is applied: every shelf, search,
 * reel and Play next reads `library` or `byId`, so none can miss it. Cheap
 * enough to run on every profile change, which is what lets switching
 * profile skip a second download.
 */
function applyCatalog() {
  const visible = kidsProfile() ? forKidsProfile(catalogSets, new Set(state.kids())) : catalogSets;
  library = groupLibrary(visible);
  byId = new Map(visible.map((set) => [set.setId, set]));
  document.getElementById("n-movies").textContent = String(library.movies.length);
  document.getElementById("n-series").textContent = String(library.series.length);
  document.getElementById("n-tutorials").textContent = String(library.tutorials.length);
  renderColophon(visible);
}
```

`loadCatalog` — after `if (text === catalogText) return false;`:

```js
  catalogSets = JSON.parse(text);
  catalogText = text;
  applyCatalog();
  return true;
```

(remove the old `groupLibrary`/`byId`/counts/`renderColophon` lines it replaces; keep the parse before assigning `catalogText` so a malformed body throws without recording it, as today.)

Startup — the catalog is read before the profile is known, so re-apply once it is. After `showProfile();`:

```js
  // Read before anyone was chosen; a kids profile sees less of it.
  applyCatalog();
```

Profile switching — after the `showProfile` function:

```js
// The name in the header is also the way to become somebody else. A kids
// profile is a filter, not a lock, so anyone can switch back from here.
document.getElementById("who").addEventListener("click", async () => {
  const before = state.profileId();
  const chosen = await chooseProfile(document.body, { canCancel: true });
  if (chosen === before) return;
  showProfile();
  applyCatalog();
  refreshShelfCounts();
  route();
});
```

`viewSearch` — replace `renderSearch(main, query, hits, play);` with:

```js
    // The server's search knows no profile; keep only what this one can see.
    renderSearch(main, query, hits.filter((hit) => byId.has(hit.setId)), play);
```

Every `emptyState(x)` call in `app.js` (`:134`, `:167`, `:257`) becomes `emptyState(x, { kids: kidsProfile() })`.

`player-library-marks.js` — at the top of `refreshKids()`:

```js
    // A child does not approve titles for themselves; marking is for the
    // grown-ups' profiles.
    kids.hidden = state.profile()?.kids === true;
```

and in the click handler: `if (!title || kids.hidden || kidsVerdict(title) !== "unrated") return;`.

- [ ] **Step 4: Run** `cd web && bun test && bunx tsc --noEmit -p . && bun run lint` — Expected: all PASS.

- [ ] **Step 5: Commit** `git add web/public web/test && git commit -m "feat(web-kids): show a kids profile only its titles and let viewers switch profile"`

---

### Task 7: Create form with a Kids switch; Kids label on tiles

**Files:**
- Modify: `web/public/lib/profile-picker.js` (the "New profile" tile `:78-91`, tiles loop `:52-76`)
- Modify: `web/public/lib/watch-state.js` (`createRecord`, `createProfile`, the collections caller `:401`)
- Modify: `web/public/style.css` (after `.who-tile:hover` `:304`)
- Test: `web/test/browser-application.test.ts`

**Interfaces:**
- Consumes: `POST /api/profiles {name, kids}` (Task 3); `#who` handler (Task 6).
- Produces: `state.createProfile(name: string, kids = false)`; `createRecord(path, body: object)`.

- [ ] **Step 1: Write the failing test** (inside Task 6's `describe("kids profiles")`)

```ts
  test("a new profile can be made a kids profile, and its tile says so", async () => {
    let posted: unknown = null;
    const base = intercept;
    intercept = (url, init) => {
      if (url === "/api/profiles" && init?.method === "POST") {
        posted = JSON.parse(String(init.body));
        return Response.json({ id: "mia", name: "Mia", createdAt: 3, kids: true }, { status: 201 });
      }
      return base(url, init);
    };
    await start();
    env.node("who").fire("click");
    await settle();
    descendants(env.document.body).find((node) => node.className.includes("who-add"))!.fire("click");
    await settle();
    const form = descendants(env.document.body).find((node) => node.className === "who-new")!;
    const [name, kids] = descendants(form).filter((node) => node.tagName === "INPUT") as unknown as
      { value: string; checked: boolean }[];
    name.value = "Mia";
    kids.checked = true;
    form.fire("submit");
    await settle();
    expect(posted).toEqual({ name: "Mia", kids: true });
    const tile = descendants(env.document.body)
      .find((node) => node.className === "who-tile" && textOf(node).includes("Mia"))!;
    expect(textOf(tile)).toContain("Kids");
  });
```

- [ ] **Step 2: Run** `cd web && bun test test/browser-application.test.ts` — Expected: FAIL (the tile still calls `window.prompt`, which the fake window lacks).

- [ ] **Step 3: Implement**

`watch-state.js`:

```js
async function createRecord(path, body) {
  try {
    const response = await write(path, "POST", body);
    return response ? await response.json() : null;
  } catch {
    return null;
  }
}
```

`createProfile(name, kids = false)` posts `createRecord("/api/profiles", { name, kids })`; the collections caller becomes `createRecord(under("/collections"), { name })`.

`profile-picker.js` — add `let naming = false; let createFailed = false;` beside `let selecting = false;`. In the tiles loop, after the `who-name` span:

```js
        if (entry.kids) tile.append(el("span", "who-kids", "Kids"));
```

Replace the "New profile" click handler and add the form (drawn in `draw()` right after `choices.append(tiles);`):

```js
      add.addEventListener("click", () => {
        if (closed || selecting) return;
        naming = true;
        createFailed = false;
        draw();
      });
```

```js
      if (naming) {
        const form = el("form", "who-new");
        const name = el("input");
        name.required = true;
        name.maxLength = 120;
        name.placeholder = "Name";
        name.setAttribute("aria-label", "Name for this profile");
        const kidsChoice = el("label", "who-kids-choice");
        const kids = el("input");
        kids.type = "checkbox";
        kidsChoice.append(kids, " Kids profile — only FSK 12 and under");
        const create = el("button", null, "Create");
        create.type = "submit";
        const cancel = el("button", "quiet", "Cancel");
        cancel.type = "button";
        cancel.addEventListener("click", () => {
          naming = false;
          draw();
        });
        form.addEventListener("submit", (event) => {
          event.preventDefault();
          void state.createProfile(name.value, kids.checked).then((made) => {
            if (closed) return;
            naming = made === null;
            createFailed = made === null;
            draw();
          });
        });
        form.append(name, kidsChoice, create, cancel);
        if (createFailed) form.append(el("p", "error", "Could not create the profile. Please try again."));
        choices.append(form);
        name.focus();
      }
```

`style.css` — after `.who-tile:hover`:

```css
/* A kids profile's tile says so, quietly: a fact about the profile, set in
   the same small capitals the page uses for every other such fact. */
.who-kids {
  font-size: 0.65rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--held);
}
.who-new {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 0.6rem 1rem;
  margin: 1.4rem 0 0.4rem;
}
.who-new input:not([type]) {
  font: inherit;
  padding: 0.35rem 0.1rem;
  border: none;
  border-bottom: 1px solid var(--rule);
  background: none;
  color: var(--ink);
  min-width: 12rem;
}
.who-kids-choice { font-size: 0.9rem; color: var(--ink-2); }
```

If the fake DOM's `PageNode` lacks `focus`, it has it (`focus() {}` in `browser-application.ts`).

- [ ] **Step 4: Run** `cd web && bun test && bunx tsc --noEmit -p . && bun run lint` — Expected: all PASS.

- [ ] **Step 5: Commit** `git add web/public web/test && git commit -m "feat(web-kids): create kids profiles from the profile picker"`

## Success criteria

On a kids profile every shelf, count, search, Featured reel and Play next shows only allowed titles; switching profile in a running page restores or filters the library with no catalog fetch; a profile can be created as kids and is labelled.
