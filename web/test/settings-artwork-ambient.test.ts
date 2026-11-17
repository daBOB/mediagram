/** Settings > Appearance > Artwork offers Ambient, and choosing it is stored and announced to the page. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { browserEnvironment, settle } from "./support/player-environment";
import { descendants } from "./support/browser-application";

let env: ReturnType<typeof browserEnvironment>;
const stored = new Map<string, string>();
const given = Object.getOwnPropertyDescriptor(globalThis, "localStorage");

beforeEach(() => {
  env = browserEnvironment();
  stored.clear();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: { getItem: (k: string) => stored.get(k) ?? null, setItem: (k: string, v: string) => void stored.set(k, v) },
  });
});
afterEach(async () => {
  await settle();
  if (given) Object.defineProperty(globalThis, "localStorage", given);
  else delete (globalThis as { localStorage?: unknown }).localStorage;
  env.restore();
});

test("choosing Ambient stores it and fires the appearance event", () => {
  const told: string[] = [];
  addEventListener("mediagram:appearance", (event) => told.push(event.type), { once: true });
  const main = env.node("main");
  renderSettings(main, { profile: null, switchProfile: () => {}, systemVisible: false, admin: null });
  const ambient = descendants(main).find((node) => node.tagName === "INPUT" && node.value === "ambient");
  expect(ambient).toBeDefined();
  ambient!.fire("change");
  expect(stored.get("mediagram.backdrop")).toBe("ambient");
  expect(told).toEqual(["mediagram:appearance"]);
});
