/**
 * The nav names the page showing, over the shipped document: a rail page
 * lights its rail row and no department pill, a department's own pages keep
 * its pill, and a link left holding focus by Back lets go of it. Also the
 * rail's own words, which only the shipped document holds.
 */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { staticResponse } from "../src/http/static-files";
import { parse, sectionOf } from "../public/lib/address.js";
import { markCurrent } from "../public/lib/nav-current.js";
import { htmlApplicationEnvironment } from "./support/html-application";
import { textOf } from "./support/browser-application";
import type { Node } from "./support/player-environment";

const served = staticResponse({ method: "GET", path: "/", range: null });
if (!(served.body instanceof Uint8Array)) throw new Error("Missing served HTML document");
const html = new TextDecoder().decode(served.body);

let env: Awaited<ReturnType<typeof htmlApplicationEnvironment>>;
let page: { activeElement: Node | null; querySelectorAll(selector: string): Node[] };
beforeEach(async () => {
  env = await htmlApplicationEnvironment(html);
  page = env.document as unknown as typeof page;
});
afterEach(() => env.restore());

/** The nav for `hash`, drawn the way the router draws it. */
const show = (hash: string) => markCurrent(page.querySelectorAll("nav a"), sectionOf(parse(hash)));
const lit = (selector: string) =>
  page.querySelectorAll(selector).filter((link) => link.classes.has("active")).map((link) => link.dataset.section);
const link = (section: string) => page.querySelectorAll("nav a").find((node) => node.dataset.section === section)!;

describe("a rail page lights its rail row and no department pill", () => {
  test.each(["watchlist", "continue", "latest", "genres", "stats", "settings", "system"])("#/%s", (section) => {
    show("#/collections");
    show(`#/${section}`);
    expect(lit(".departments a")).toEqual([]);
    expect(lit(".rail-nav a")).toEqual([section]);
    expect(link(section).getAttribute("aria-current")).toBe("page");
    expect(link("collections").getAttribute("aria-current")).toBeNull();
  });
});

test("a department and its own pages keep its pill lit, and no rail row", () => {
  for (const [hash, section] of [
    ["#/collections", "collections"],
    ["#/collections/tmdb-10", "collections"],
    ["#/collections/list-1", "collections"],
    ["#/movies/page/2", "movies"],
    ["#/series/Crime%20101/Season%201", "series"],
  ] as const) {
    show("#/watchlist");
    show(hash);
    expect(lit(".departments a"), hash).toEqual([section]);
    expect(lit(".rail-nav a"), hash).toEqual([]);
  }
});

test("the rail's resume row reads Continue, the name its page wears", () => {
  const row = link("continue");
  expect(textOf(row.children.find((node) => node.className === "label")!)).toBe("Continue");
  expect(row.children.some((node) => node.id === "n-continue")).toBe(true);
});

test("a pill still focused after Back lets go of it; the link naming the page keeps it", () => {
  const collections = link("collections");
  let blurred = 0;
  Object.assign(collections, { blur: () => { blurred++; page.activeElement = null; } });
  // Clicked to get to Collections, so it holds focus there.
  page.activeElement = collections;
  show("#/collections");
  expect(blurred).toBe(0);
  show("#/watchlist");
  expect(blurred).toBe(1);
  expect(page.activeElement).toBeNull();
});
