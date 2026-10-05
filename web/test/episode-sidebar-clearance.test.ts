/**
 * On a wide screen the open episode sidebar leaves the top bar and the card
 * their own room, so My List, Notes and the player's close stay reachable.
 */

import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";

const css = readFileSync(new URL("../public/styles/player-card.css", import.meta.url), "utf8");
const wide = css.slice(css.indexOf("@media (min-width: 768px) {\n  dialog.sidebar-open"));
const rule = (selector: string) => new RegExp(`dialog\\.sidebar-open ${selector.replace(".", "\\.")} \\{[^}]*right: [^;]*360px`);

test("while the sidebar is open the card and the top bar both stop short of it, from 768px up", () => {
  expect(wide).toMatch(rule(".card-dock"));
  expect(wide).toMatch(rule(".hud-top"));
});
