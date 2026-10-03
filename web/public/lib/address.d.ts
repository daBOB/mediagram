/**
 * Types for the address format `address.js` derives.
 *
 * The module itself is plain JavaScript because the browser loads it
 * directly; this declares its shape the way `library.d.ts` does for
 * `library.js`.
 */

/** A department's own front page: the whole shelf, unpaged. */
export type DepartmentSection = "movies" | "series" | "tutorials" | "documentaries" | "anime";

/** A show or course opened by name, with the folder trail (if any) into it. */
export type ShowSection = "series" | "tutorials" | "documentaries" | "anime";

export type Address =
  | { page: "home" }
  | { page: "department"; section: DepartmentSection }
  | { page: "moviesPage"; n: number }
  | { page: "show"; section: ShowSection; name: string; folders: string[] }
  | { page: "film"; setId: string }
  | { page: "genre"; name: string }
  | { page: "genres" }
  | { page: "latest" }
  | { page: "person"; id: string }
  | { page: "search"; query: string }
  | { page: "settings" }
  | { page: "stats" }
  | { page: "system" }
  | { page: "continue" }
  | { page: "watchlist" }
  | { page: "collections" }
  | { page: "franchise"; id: string }
  | { page: "list"; id: string };

/** The address `hash` names, falling back the way a bookmarked typo always has. */
export function parse(hash: string): Address;

/** The hash text `address` opens, in the one format every builder now shares. */
export function href(address: Address): string;

/** Opens `address` — the one way this page ever navigates itself. */
export function go(address: Address): void;

/** The nav item and `body[data-page]` value `address` lights up. */
export function sectionOf(address: Address): string;
