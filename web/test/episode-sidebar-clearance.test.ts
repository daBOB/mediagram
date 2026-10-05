/**
 * With the episode sidebar open, the card and the top bar stand clear of it on
 * a window wide enough to show both, and clear of the notes column too. The
 * sidebar's width is one custom property, so what clears it cannot drift from
 * what it is.
 */

import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const css = readFileSync(new URL("../public/styles/episode-sidebar.css", import.meta.url), "utf8");
const block = (query: string) => css.slice(css.indexOf(`@media (${query}) {`)).split(/\n}\n/)[0]!;
const rightOf = (scope: string, selector: string) =>
  scope.match(new RegExp(`${selector.replace(/[.\s]/g, "\\$&")} \\{[^}]*?right: ([^;]*);`))?.[1];

test("the sidebar's width is one property, used by its own rule and by every clearance", () => {
  expect(css).toMatch(/--sidebar-width: 360px;/);
  expect(css).toMatch(/\.episode-sidebar \{[^}]*width: min\(var\(--sidebar-width\), 100%\)/);
});

test("the card stands clear of the sidebar once its bottom row fits beside it, and of notes when they are wider", () => {
  const wide = block("min-width: 900px");
  expect(rightOf(wide, "dialog.sidebar-open .card-dock")).toBe("calc(var(--sidebar-width) + 24px)");
  expect(rightOf(wide, "dialog.sidebar-open.with-notes .card-dock")).toBe("calc(max(var(--notes-width), var(--sidebar-width)) + 24px)");
});

test("the top bar stands clear of the sidebar from 768px up, and of notes when they are wider", () => {
  const medium = block("min-width: 768px");
  expect(rightOf(medium, "dialog.sidebar-open .hud-top")).toBe("var(--sidebar-width)");
  expect(rightOf(medium, "dialog.sidebar-open.with-notes .hud-top")).toBe("max(var(--notes-width), var(--sidebar-width))");
});

test("a long section name is held to two lines", () => {
  expect(css).toMatch(/\.sidebar-title \{[^}]*-webkit-line-clamp: 2/);
});
