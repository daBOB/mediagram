/**
 * Who's managing?: the picker's Manage profiles, and Settings for a grown-up who is not the owner, ask one
 * question with the picker's own tiles — the grown-ups here, the owner first and labelled Owner, or last
 * on a device showing another household — and a tile opens that person's management after their PIN.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { chooseProfile } from "../public/lib/profile-picker.js";
import { openProfilesPage } from "../public/lib/profiles-page-overlay.js";
import { HOUSEHOLD_WORDS as W } from "../public/lib/household-words.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf, tileNamed as tileOf } from "./support/browser-application";
import { AT_HOME, FLAT, storage, writesOf } from "./support/household-fixture";
import { ATTIC, EVERYONE, opened, prompting, promptTitle, rows } from "./support/households-part";
import { browserEnvironment, Node } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let refusal: { status: number; body: object } | null;
beforeEach(async () => {
  env = browserEnvironment();
  // Kai, a grown-up at home, made before the owner: the owner still comes first.
  everyone = EVERYONE.map((one: any) => (one.id === "kai" ? { ...one, createdAt: 0 } : one));
  refusal = null;
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }) });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: everyone, households: [ATTIC, FLAT, AT_HOME] });
    if (url.endsWith("/state")) return Response.json({});
    if (refusal) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
/** The note the Households gate showed beside the Owner button, both gone now. */
const OWNER_NOTE = "Households are the owner’s to set up.";
const page = (root: Node) => byClass(root, "who-manage");
/** The Who's managing? tiles under `root`, by the name each says, and the line under it. */
const tilesOf = (root: Node) => descendants(root).filter((node) => node.className === "who-tile")
  .map((tile) => tile.children.slice(1).map((part) => part.textContent).join(" · "));
const headings = (root: Node) => descendants(root).filter((node) => ["H1", "H2"].includes(node.tagName)).map(textOf);

/** The picker's Manage profiles, opened over `root`. */
function manageRoute() {
  const root = new Node();
  openProfilesPage(root, () => {});
  return root;
}

function settingsAs(id: string) {
  const main = new Node();
  renderSettings(main, { profile: everyone.find((one: any) => one.id === id) as never, switchProfile: () => {}, systemVisible: false, admin: null });
  buttonNamed(main, "Profiles").fire("click");
  return main;
}

test("the picker's manage route asks who's managing with the picker's tiles", () => {
  const root = new Node();
  void chooseProfile(root);
  const watching = byClass(root, "who-tiles");
  buttonNamed(root, "Manage profiles").fire("click");
  expect(headings(page(root))[0]).toBe(W.whosManaging);
  // The grown-ups here, the owner first; no kid, nobody from another household.
  expect(tilesOf(page(root))).toEqual([`andre · ${W.owner}`, "Kai"]);
  // Drawn as Who's watching? draws its own: the same row, the same tile, the initial then the name.
  const managing = byClass(page(root), "who-tiles");
  expect(managing.className).toBe(watching.className);
  expect(tileOf(page(root), "Kai").children.map((part) => part.className)).toEqual(["who-initial", "who-name"]);
  expect(tileOf(page(root), "Kai").children[0]!.textContent).toBe("K");
});

test("the owner's tile says Owner and opens the owner view after the owner's PIN", async () => {
  const root = manageRoute();
  tileOf(root, "andre").fire("click");
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "1357");
  expect(sent()).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }]]);
  expect(rows(root).map((row) => row.children[0]!.textContent)).toEqual(["Home", "Attic", "Flat"]);
  expect(textOf(root)).toContain("As andre");
  expect(tilesOf(root)).toEqual([]);
  // This device is the owner's to switch: it shows in the owner's view.
  expect(byClass(root, "device-line").hidden).toBe(false);
});

test("a grown-up's tile opens their own management after their PIN", async () => {
  const root = manageRoute();
  tileOf(root, "Kai").fire("click");
  expect(promptTitle(root)).toBe("Kai’s PIN");
  await answerPin(root, "2468");
  expect(sent()).toEqual([["POST", "/api/profiles/kai/unlock", { pin: "2468" }]]);
  expect(textOf(root)).toContain("As Kai");
  expect(buttonNamed(opened(root, "Kai"), "Change your PIN")).toBeDefined();
  expect(rows(root)).toEqual([]);
  expect(tilesOf(root)).toEqual([]);
});

test("a device showing another household ends the tiles with an Owner tile", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const root = manageRoute();
  expect(tilesOf(root)).toEqual(["Maja", "Ole", `andre · ${W.owner}`]);
  tileOf(root, "andre").fire("click");
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "1357");
  expect(sent()).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }]]);
  expect(rows(root).map((row) => row.children[0]!.textContent)).toEqual(["Flat", "Attic", "Home"]);
});

test("a wrong PIN says so on the tiles and nothing opens", async () => {
  const root = manageRoute();
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  for (const name of ["Kai", "andre"]) {
    tileOf(root, name).fire("click");
    await answerPin(root, "1111");
    expect(textOf(byClass(root, "pin-prompt"))).toContain("Wrong PIN.");
    buttonNamed(root, "Cancel").fire("click");
    expect(prompting(root)).toBe(false);
    expect(tilesOf(root)).toEqual([`andre · ${W.owner}`, "Kai"]);
    expect(textOf(root)).not.toContain("As ");
    expect(rows(root)).toEqual([]);
  }
});

test("Settings for a grown-up who is not the owner shows the same tiles and no Everyone here", () => {
  const main = settingsAs("kai");
  // This device stays theirs to switch, a line below the tiles.
  expect(headings(main)).toEqual(["Settings", "Who is watching", "Subtitles", W.whosManaging]);
  expect(byClass(main, "device-line").hidden).toBe(false);
  expect(tilesOf(main)).toEqual([`andre · ${W.owner}`, "Kai"]);
  expect(textOf(main)).not.toContain("Everyone here");
  expect(textOf(main)).not.toContain("Who are you?");
  expect(textOf(main)).not.toContain(OWNER_NOTE);
  // A kid still sees only You.
  expect(headings(settingsAs("lea"))).toEqual(["Settings", "Who is watching", "Subtitles"]);
});

test("the manage route shows no Everyone here, no Households gate and no This device block", () => {
  const root = manageRoute();
  // Three households, so This device would have something to switch to.
  expect(headings(page(root))).toEqual([W.whosManaging]);
  for (const gone of ["Everyone here", "Who are you?", OWNER_NOTE, W.thisDevice, W.switchHousehold]) expect(textOf(root)).not.toContain(gone);
  expect(descendants(root).some((node) => node.classes.has("households"))).toBe(false);
  expect(buttonNamed(root, "Done")).toBeDefined();
});
