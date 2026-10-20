/** This device's household: which one it shows, kept on the device, and the profiles the picker lists for it. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { needsJoin, thisHousehold } from "../public/lib/household.js";
import { HOUSEHOLD_WORDS } from "../public/lib/household-words.js";
import { chooseProfile } from "../public/lib/profile-picker.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, descendants, textOf } from "./support/browser-application";
import { AT_HOME, FLAT, SPLIT, storage } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let households: object[];
let heard: boolean;
let kept: ReturnType<typeof storage>;
beforeEach(() => {
  env = browserEnvironment();
  everyone = SPLIT;
  households = [FLAT, AT_HOME];
  heard = true;
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard, profiles: everyone, households });
    if (url.endsWith("/state")) return Response.json({});
    return new Response(null, { status: 204 });
  });
});
afterEach(async () => { await state.useProfile(null); env.restore(); });

/** A device whose `localStorage` holds `entries`, reading who there is. */
async function device(entries: Record<string, string> = {}) {
  kept = storage(entries);
  Object.assign(env.window, { localStorage: kept });
  await state.loadProfiles();
}

const names = () => state.profiles().map((one) => one.name);
const tiles = (root: Node) => descendants(root).filter((node) => node.className === "who-tile").map((node) => textOf(node));

test("a device that chose someone before joins that profile's household and keeps it", async () => {
  await device({ "mediagram.profile": "maja" });
  expect(names()).toEqual(["Maja", "Tim"]);
  expect(kept.getItem("mediagram.household")).toBe("flat");
  expect(thisHousehold()).toEqual(FLAT);
  // Nobody chosen any more, and still the flat.
  await state.useProfile(null);
  await state.loadProfiles();
  expect(names()).toEqual(["Maja", "Tim"]);
});

test("a new device joins the only household once the household is heard", async () => {
  everyone = SPLIT.map((one) => ({ ...one, household: "home" }));
  households = [AT_HOME];
  heard = false;
  await device();
  expect(names()).toEqual([]);
  expect(needsJoin()).toBe(false);
  expect(kept.getItem("mediagram.household")).toBeNull();
  heard = true;
  await state.loadProfiles();
  expect(names()).toEqual(["andre", "Maja", "Tim", "Lea"]);
  expect(kept.getItem("mediagram.household")).toBe("home");
});

test("with two households a new device asks to join one", async () => {
  await device();
  expect(names()).toEqual([]);
  expect(needsJoin()).toBe(true);
  expect(thisHousehold()).toBeNull();
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(root)).toContain(HOUSEHOLD_WORDS.joinAHousehold);
  expect(tiles(root)).toEqual([]);
});

test("the picker lists only this device's household", async () => {
  await device({ "mediagram.household": "home" });
  const root = new Node();
  void chooseProfile(root);
  expect(tiles(root)).toEqual(["Aandre", "LLeaKids · FSK 12"]);
  expect(textOf(root)).not.toContain(HOUSEHOLD_WORDS.joinAHousehold);
});

test("a profile remembered from another household is nobody here", async () => {
  await device({ "mediagram.household": "home", "mediagram.profile": "maja" });
  expect(state.rememberedProfile()).toBeNull();
  expect(names()).toEqual(["andre", "Lea"]);
  expect(kept.getItem("mediagram.household")).toBe("home");
});

test("a remembered profile, with nothing heard yet and two households, is still entered", async () => {
  heard = false;
  await device({ "mediagram.profile": "maja" });
  expect(state.rememberedProfile()).toBe("maja");
  expect(names()).toEqual(["Maja", "Tim"]);
  expect(needsJoin()).toBe(false);
});

test("a localStorage that throws on every call breaks neither here nor the picker", async () => {
  const refuse = () => { throw new Error("storage refused"); };
  Object.assign(env.window, { localStorage: { getItem: refuse, setItem: refuse, removeItem: refuse } });
  expect(await state.loadProfiles()).toBe(true);
  expect(needsJoin()).toBe(true);
  const root = new Node();
  void chooseProfile(root);
  buttonNamed(root, "Flat").fire("click");
  await answerPin(root, "5678");
  await settle();
  expect(tiles(root)).toEqual(["MMaja", "TTimKids · FSK 6"]);
  // Read again, and still the flat: the page keeps what storage would not.
  await state.loadProfiles();
  expect(names()).toEqual(["Maja", "Tim"]);
});
