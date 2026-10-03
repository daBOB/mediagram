/**
 * The web player's page address, both directions.
 *
 * `parse` turns the URL hash into a typed address; `href` turns an address
 * back into hash text; `go` is the one way this page ever navigates itself.
 * Every link the app builds and every `location.hash =` goes through one of
 * these (the nav's fixed links in `index.html` are plain markup), so the format — what gets `encodeURIComponent`-ed, what a folder
 * trail looks like, what an unrecognised section falls back to — lives in
 * one place rather than in every card, crumb and pager that builds a link.
 *
 * `parse` mirrors what `drawRoute` used to do inline, quirks included: a
 * section it does not recognise is treated exactly as if it had been typed
 * "movies", trailing segments and all — so `#/typo/page/3` still opens
 * Movies page 3, the same as `#/movies/page/3` always has. Changing that
 * would change what an already-bookmarked typo opens.
 *
 * @typedef {import("./address.js").Address} Address
 */

/**
 * Sections `drawRoute` already knew, before this module existed — the same
 * sixteen keys `SECTIONS`, the kept shelves and the plain pages answered for.
 * An unrecognised one is treated as "movies", matching every builder that
 * never had to ask which sections existed.
 */
const KNOWN_SECTIONS = new Set([
  "movies", "series", "tutorials", "documentaries", "anime",
  "continue", "watchlist", "collections",
  "home", "search", "system", "film", "genre", "genres", "latest", "settings", "person",
  "stats",
]);

/**
 * The page number a `/page/<text>` segment names; anything that is not a
 * positive whole number is the first page, so a mistyped address still shows
 * a shelf.
 * @param {string|undefined} text
 */
function normalizePage(text) {
  return /^[1-9]\d*$/.test(text ?? "") ? Number(text) : 1;
}

/**
 * @param {string} hash
 * @returns {Address}
 */
export function parse(hash) {
  const parts = (hash ?? "")
    .replace(/^#\/?/, "")
    .split("/")
    .filter((part) => part !== "");
  const [section = "movies", name, ...folders] = parts;
  const known = KNOWN_SECTIONS.has(section) ? section : "movies";

  if (known === "search") return { page: "search", query: decodeURIComponent(name ?? "") };
  if (known === "home") return { page: "home" };
  if (known === "film") return { page: "film", setId: decodeURIComponent(name ?? "") };
  if (known === "genre") return { page: "genre", name: decodeURIComponent(name ?? "") };
  if (known === "genres") return { page: "genres" };
  if (known === "latest") return { page: "latest" };
  // Not encoded: `cast.js` never encoded a person id either, so there is
  // nothing here for `decodeURIComponent` to undo.
  if (known === "person") return { page: "person", id: name ?? "" };
  if (known === "settings") return { page: "settings" };
  if (known === "stats") return { page: "stats" };
  if (known === "system") return { page: "system" };
  if (known === "continue") return { page: "continue" };
  if (known === "watchlist") return { page: "watchlist" };
  if (known === "collections") {
    if (name?.startsWith("tmdb-")) return { page: "franchise", id: name.slice(5) };
    return name ? { page: "list", id: decodeURIComponent(name) } : { page: "collections" };
  }
  if (known === "movies") {
    return name === "page"
      ? { page: "moviesPage", n: normalizePage(folders[0]) }
      : { page: "department", section: "movies" };
  }
  return name
    ? { page: "show", section: known, name: decodeURIComponent(name), folders: folders.map(decodeURIComponent) }
    : { page: "department", section: known };
}

/**
 * @param {Address} address
 * @returns {string}
 */
export function href(address) {
  switch (address.page) {
    case "home": return "#/home";
    case "department": return `#/${address.section}`;
    case "moviesPage": return `#/movies/page/${Math.max(1, address.n)}`;
    case "show": return `#/${address.section}/${[address.name, ...address.folders].map(encodeURIComponent).join("/")}`;
    case "film": return `#/film/${encodeURIComponent(address.setId)}`;
    case "genre": return `#/genre/${encodeURIComponent(address.name)}`;
    case "genres": return "#/genres";
    case "latest": return "#/latest";
    case "person": return `#/person/${address.id}`;
    case "search": return `#/search/${encodeURIComponent(address.query)}`;
    case "settings": return "#/settings";
    case "stats": return "#/stats";
    case "system": return "#/system";
    case "continue": return "#/continue";
    case "watchlist": return "#/watchlist";
    case "collections": return "#/collections";
    case "franchise": return `#/collections/tmdb-${address.id}`;
    case "list": return `#/collections/${encodeURIComponent(address.id)}`;
    default: throw new Error(`address.js: unknown page kind ${JSON.stringify(address)}`);
  }
}

/** Opens `address` — the one way this page ever navigates itself. */
export function go(address) {
  location.hash = href(address);
}

/**
 * The nav item and `body[data-page]` value `address` lights up — what
 * `drawRoute` used to call `known`.
 * @param {Address} address
 * @returns {string}
 */
export function sectionOf(address) {
  if (address.page === "moviesPage") return "movies";
  if (address.page === "department" || address.page === "show") return address.section;
  if (address.page === "franchise" || address.page === "list") return "collections";
  return address.page;
}
