/** Manage profiles as the owner: every household's profiles, each under its name, this device's first; anyone else's as before. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { SECTIONS } from "../public/lib/catalog/sections.js";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { openProfilesPage } from "../public/lib/profiles-page-overlay.js";
import { HOUSEHOLD_WORDS } from "../public/lib/household-words.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { AT_HOME, FLAT, SPLIT, storage, writesOf } from "./support/household-fixture";
import { fill, rowOf as householdRow } from "./support/households-part";
import { browserEnvironment, Node, settle } from "./support/player-environment";

/** A third household, listed first: Ida runs the loft. */
const LOFT = { id: "loft", name: "Loft", adminId: "ida" };
const IDA = { id: "ida", name: "Ida", createdAt: 7, kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true, household: "loft" };

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let households: object[];
beforeEach(async () => {
  env = browserEnvironment();
  everyone = [...SPLIT, IDA];
  households = [LOFT, FLAT, AT_HOME];
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }), confirm: () => true });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: everyone, households });
    if (options?.method === "DELETE") everyone = everyone.filter((one: any) => url !== `/api/profiles/${one.id}`);
    if (url === "/api/households/loft/name") households = households.map((one: any) => (one.id === "loft" ? { ...one, name: "Attic" } : one));
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
const manage = (root: Node) => byClass(root, "manage-inline");
const headings = (root: Node) => descendants(manage(root)).filter((node) => ["H1", "H2", "H3"].includes(node.tagName)).map(textOf);
/** The household `name`'s part of Manage. */
const householdOf = (root: Node, name: string) =>
  descendants(manage(root)).find((node) => node.classes.has("manage-household") && node.children[0]?.textContent === name)!;
const rowOf = (root: Node, name: string) => descendants(root).find((node) => node.className === "manage-row" && textOf(node).startsWith(name));
const switchOf = (root: Node, name: string, label: string) =>
  descendants(descendants(root).find((node) => node.getAttribute("aria-label") === `Sections for ${name}`)!)
    .find((node) => node.getAttribute("role") === "switch" && node.textContent === label)!;
/** What the add forms under `root` add, by their buttons. */
const formsOf = (root: Node) => descendants(root).filter((node) => node.classes.has("who-create")).map((node) => node.textContent);

/** Opens the picker's Manage profiles as `name`. */
async function manageAs(name: string, pin: string) {
  const root = new Node();
  openProfilesPage(root, () => {});
  buttonNamed(manage(root), name).fire("click");
  await answerPin(root, pin);
  return root;
}

test("the owner sees every household under its name: this device's first, then the others in the live order", async () => {
  const root = await manageAs("andre", "1357");
  expect(headings(root)).toEqual(["Manage profiles", "Home", "Grown-ups", "Kids", "Loft", "Grown-ups", "Flat", "Grown-ups", "Kids", "Your PIN"]);
  // The rows Manage already has, another household's admin's too.
  for (const [household, name] of [["Loft", "Ida"], ["Flat", "Maja"]]) {
    const row = rowOf(householdOf(root, household!), name!)!;
    expect(buttonNamed(row, "Reset PIN")).toBeDefined();
    expect(buttonNamed(row, "Remove")).toBeDefined();
    expect(switchOf(row, name!, SECTIONS.anime.label)).toBeDefined();
  }
  expect(rowOf(householdOf(root, "Flat"), "Tim")).toBeDefined();
  expect(rowOf(householdOf(root, "Home"), "Lea")).toBeDefined();
  expect(rowOf(root, "andre")).toBeUndefined();
  // Adding stays with this device's household; the Households part makes another's people.
  expect(formsOf(householdOf(root, "Home"))).toEqual(["Add a grown-up", "Add a kid"]);
  expect(formsOf(householdOf(root, "Loft"))).toEqual([]);
  expect(formsOf(householdOf(root, "Flat"))).toEqual([]);
});

test("the owner's changes in another household reach that profile: a PIN, a section, a kid's limit, a removal", async () => {
  const root = await manageAs("andre", "1357");
  buttonNamed(rowOf(root, "Ida")!, "Reset PIN").fire("click");
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/ida/pin", { actorId: "andre", pin: "1357", newPin: "2468" }]);
  switchOf(root, "Maja", SECTIONS.anime.label).fire("click");
  await settle();
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/maja/hidden-sections", { actorId: "andre", pin: "1357", sections: ["anime"] }]);
  const limit = descendants(rowOf(root, "Tim")!).find((node) => node.getAttribute("aria-label") === "Age limit for Tim")!;
  limit.value = "12";
  limit.fire("change");
  await settle();
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/tim/kids-age", { actorId: "andre", pin: "1357", age: 12 }]);
  buttonNamed(rowOf(root, "Ida")!, "Remove").fire("click");
  await settle();
  expect(sent().at(-1)).toEqual(["DELETE", "/api/profiles/ida", { actorId: "andre", pin: "1357" }]);
  // The loft has nobody left to manage, so it is not drawn.
  expect(headings(root)).toEqual(["Manage profiles", "Home", "Grown-ups", "Kids", "Flat", "Grown-ups", "Kids", "Your PIN"]);
});

test("anyone else manages only this device's household, unnamed, as before", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const root = await manageAs("Maja", "4321");
  expect(headings(root)).toEqual(["Manage profiles", "Grown-ups", "Kids", "Your PIN"]);
  expect(rowOf(root, "Tim")).toBeDefined();
  for (const name of ["Ida", "Lea", "andre"]) expect(rowOf(root, name)).toBeUndefined();
});

test("the owner of one household sees Manage as before, unnamed", async () => {
  everyone = SPLIT.map((one) => ({ ...one, household: "home", admin: one.id === "andre" }));
  households = [AT_HOME];
  await state.loadProfiles();
  const root = await manageAs("andre", "1357");
  expect(headings(root)).toEqual(["Manage profiles", "Grown-ups", "Kids", "Your PIN"]);
  expect(rowOf(root, "Maja")).toBeDefined();
});

test("a removal in another household redraws who is here and the Households counts", async () => {
  const main = new Node();
  renderSettings(main, { profile: SPLIT[0]!, switchProfile: () => {}, systemVisible: false, admin: null });
  buttonNamed(main, "Profiles").fire("click");
  expect(textOf(householdRow(main, "Loft").children[1]!)).toBe("Admin Ida · 1 profile");
  buttonNamed(manage(main), "andre").fire("click");
  await answerPin(main, "1357");
  buttonNamed(rowOf(main, "Ida")!, "Remove").fire("click");
  await settle();
  expect(textOf(householdRow(main, "Loft").children[1]!)).toBe("No admin · 0 profiles");
  expect(descendants(main).filter((node) => node.classes.has("everyone-row")).map((row) => row.children[0]!.textContent)).toEqual(["andre", "Lea"]);
  expect(textOf(main)).toContain("As andre");
});

test("a household renamed in the Households part shows in Manage at once, and Manage keeps who you are", async () => {
  const main = new Node();
  renderSettings(main, { profile: SPLIT[0]!, switchProfile: () => {}, systemVisible: false, admin: null });
  buttonNamed(main, "Profiles").fire("click");
  buttonNamed(manage(main), "andre").fire("click");
  await answerPin(main, "1357");
  await fill(main, "Loft", HOUSEHOLD_WORDS.rename, "Attic");
  await answerPin(main, "1357");
  expect(headings(main)).toEqual(["Manage profiles", "Home", "Grown-ups", "Kids", "Attic", "Grown-ups", "Flat", "Grown-ups", "Kids", "Your PIN"]);
  expect(textOf(main)).toContain("As andre");
});

test("a grown-up moved out of this device's household elsewhere is asked who they are again, not kept", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const root = await manageAs("Maja", "4321");
  expect(textOf(root)).toContain("As Maja");
  // Another device moves Maja home; a change made elsewhere on the page reads the list again.
  everyone = everyone.map((one: any) => (one.id === "maja" ? { ...one, household: "home" } : one));
  await state.loadProfiles();
  env.window.dispatchEvent(new Event("mediagram:profiles"));
  expect(textOf(root)).not.toContain("As Maja");
  expect(textOf(root)).toContain("Who are you?");
});

test("a household is headed by its name, never its id", async () => {
  // Ida's household is not in the list read; the server never sends one so, as it reads a gone household as Home.
  households = [FLAT, AT_HOME];
  await state.loadProfiles();
  const root = await manageAs("andre", "1357");
  expect(headings(root)).toEqual(["Manage profiles", "Home", "Grown-ups", "Kids", "Flat", "Grown-ups", "Kids", "Your PIN"]);
});
