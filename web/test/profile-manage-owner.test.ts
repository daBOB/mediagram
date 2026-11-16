/**
 * The owner's view of the Profiles page: every household with its actions, its people with their
 * controls, and adding a grown-up or a kid right in it, behind one owner's PIN per visit — from
 * Settings, from the picker, and through the Owner button. Anyone else's Manage as before.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { SECTIONS } from "../public/lib/catalog/sections.js";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { openProfilesPage } from "../public/lib/profiles-page-overlay.js";
import { HOUSEHOLD_WORDS as W } from "../public/lib/household-words.js";
import { newPinTitle } from "../public/lib/pin-prompt.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { AT_HOME, FLAT, SPLIT, storage, writesOf } from "./support/household-fixture";
import { actionsOf, ATTIC, fieldOf, fill, optionsOf, press, prompting, promptTitle, rowOf as householdRow, rows } from "./support/households-part";
import { browserEnvironment, Node, settle } from "./support/player-environment";

/** A third household, listed first: Ida runs the loft. */
const LOFT = { id: "loft", name: "Loft", adminId: "ida" };
const IDA = { id: "ida", name: "Ida", createdAt: 7, kids: false, kidsAge: null, parentId: null, admin: true, hasPin: true, household: "loft" };
/** A second grown-up in the flat, listed before Maja, who runs it. */
const OLE = { id: "ole", name: "Ole", createdAt: 8, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true, household: "flat" };
const OWNER = SPLIT[0]!;
const MAJA = SPLIT[1]!;

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let households: object[];
/** The owner's PIN as the server has it: changing it stands for a change on another device. */
let ownersPin: string;
let refusal: { status: number; body: object } | null;
beforeEach(async () => {
  env = browserEnvironment();
  everyone = [...SPLIT, IDA];
  households = [LOFT, FLAT, AT_HOME];
  ownersPin = "1357";
  refusal = null;
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }), confirm: () => true });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: everyone, households });
    const body = JSON.parse(String(options?.body ?? "{}"));
    // The owner's PIN is checked as the server checks it: at an unlock, and with every change made as the owner.
    const unlock = url === "/api/profiles/andre/unlock";
    if ((unlock || body.actorId === "andre") && body.pin !== ownersPin) return Response.json({ reason: "wrong-pin" }, { status: 403 });
    if (refusal && !unlock) return Response.json(refusal.body, { status: refusal.status });
    if (options?.method === "DELETE") everyone = everyone.filter((one: any) => url !== `/api/profiles/${one.id}`);
    if (url === "/api/households/loft/name") households = households.map((one: any) => (one.id === "loft" ? { ...one, name: "Attic" } : one));
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
const unlocks = () => sent().filter(([, url]) => String(url).endsWith("/unlock"));
const manage = (root: Node) => byClass(root, "manage-inline");
const headings = (root: Node) => descendants(manage(root)).filter((node) => ["H1", "H2", "H3"].includes(node.tagName)).map(textOf);
const rowOf = (root: Node, name: string) => descendants(root).find((node) => node.className === "manage-row" && node.children[0]?.textContent === name);
/** The line a profile's row says who it is in. */
const lineOf = (root: Node, name: string) => textOf(byClass(rowOf(root, name)!, "setting-value"));
const switchOf = (root: Node, name: string, label: string) =>
  descendants(descendants(root).find((node) => node.getAttribute("aria-label") === `Sections for ${name}`)!)
    .find((node) => node.getAttribute("role") === "switch" && node.textContent === label)!;
/** The household `name`'s part of the owner's view: its header row, its people, its add forms. */
const householdOf = (root: Node, name: string) => householdRow(root, name).parent!;
const namesOf = (root: Node) => rows(root).map((row) => row.children[0]!.textContent);
/** What the add forms under `root` add, by their buttons. */
const formsOf = (root: Node) => descendants(root).filter((node) => node.classes.has("who-create")).map((node) => node.textContent);
const formOf = (root: Node, label: string) => descendants(root).find((node) => node.tagName === "FORM" && formsOf(node).includes(label))!;
/** Where a form's refusal is said: right after it. */
const saidAfter = (form: Node) => form.parent!.children[form.parent!.children.indexOf(form) + 1]!;
const everyoneHere = (root: Node) => descendants(root).find((node) => node.children[0]?.textContent === "Everyone here")!;
const gate = (root: Node) => byClass(root, "households");

/** Types `name` into the household `household`'s `label` form, sets what `set` sets, and sends it. */
async function add(root: Node, household: string, label: string, name: string, set = (_form: Node) => {}) {
  const form = formOf(householdOf(root, household), label);
  descendants(form).find((node) => node.tagName === "INPUT")!.value = name;
  set(form);
  form.fire("submit");
  await settle();
}

/** Settings' Profiles tab with `profile` watching. */
function settingsAs(profile: { name: string } | null) {
  const main = new Node();
  const stop = renderSettings(main, { profile, switchProfile: () => {}, systemVisible: false, admin: null });
  buttonNamed(main, "Profiles").fire("click");
  return { main, stop };
}

/** Opens the picker's Manage profiles as `name`. */
async function manageAs(name: string, pin: string) {
  const root = new Node();
  openProfilesPage(root, () => {});
  buttonNamed(manage(root), name).fire("click");
  await answerPin(root, pin);
  return root;
}

/** Sets Tim's age limit: a change the owner's view always offers. */
async function changeTim(root: Node, age = "12") {
  const limit = fieldOf(root, "Age limit for Tim");
  limit.value = age;
  limit.fire("change");
  await settle();
}

const timTo = (age: number, pin = "1357") => ["PUT", "/api/profiles/tim/kids-age", { actorId: "andre", pin, age }];

test("watching as the owner, the page shows every household with its actions and no Who are you", () => {
  const { main } = settingsAs(OWNER);
  expect(textOf(main)).not.toContain("Who are you?");
  // This device's household first, then the others in the live order.
  expect(namesOf(main)).toEqual(["Home", "Loft", "Flat"]);
  expect(actionsOf(householdRow(main, "Home"))).toEqual([W.rename, W.moveHere]);
  expect(actionsOf(householdRow(main, "Flat"))).toEqual([W.rename, W.moveHere, W.useHere]);
  // Each household's people, each with the line Everyone here says and what the owner may change.
  expect(lineOf(householdOf(main, "Home"), "andre")).toBe(`${W.owner} · Admin · PIN set`);
  expect(buttonNamed(rowOf(main, "andre")!, "Change your PIN")).toBeDefined();
  expect(lineOf(householdOf(main, "Home"), "Lea")).toBe("Kid · andre · FSK 12");
  expect(lineOf(householdOf(main, "Flat"), "Tim")).toBe("Kid · Maja · FSK 6");
  for (const [household, name] of [["Loft", "Ida"], ["Flat", "Maja"]]) {
    const row = rowOf(householdOf(main, household!), name!)!;
    expect(textOf(byClass(row, "setting-value"))).toBe("Admin · PIN set");
    expect(buttonNamed(row, "Reset PIN")).toBeDefined();
    expect(buttonNamed(row, "Remove")).toBeDefined();
    expect(switchOf(row, name!, SECTIONS.anime.label)).toBeDefined();
  }
  for (const household of ["Home", "Loft", "Flat"]) expect(formsOf(householdOf(main, household))).toEqual(["Add a grown-up", "Add a kid"]);
  expect(formOf(main, W.createAHousehold)).toBeDefined();
  // Seeing it asks nothing: the PIN waits for the first change.
  expect(prompting(main)).toBe(false);
  expect(sent()).toEqual([]);
});

test("the owner's first change asks the PIN once, and the next change does not ask again", async () => {
  const { main } = settingsAs(OWNER);
  await changeTim(main);
  expect(promptTitle(main)).toBe(W.ownersPin);
  expect(sent()).toEqual([]);
  await answerPin(main, "1357");
  expect(sent()).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }], timTo(12)]);
  await fill(main, "Loft", W.rename, "Attic");
  expect(prompting(main)).toBe(false);
  expect(sent().at(-1)).toEqual(["PUT", "/api/households/loft/name", { actorId: "andre", pin: "1357", name: "Attic" }]);
  expect(unlocks().length).toBe(1);
});

test("Add a grown-up in another household sends that household", async () => {
  const { main } = settingsAs(OWNER);
  await add(main, "Flat", "Add a grown-up", "Opa");
  // The owner's PIN first, the first change of the visit; then the new grown-up's, twice.
  expect(promptTitle(main)).toBe(W.ownersPin);
  await answerPin(main, "1357");
  expect(promptTitle(main)).toBe(newPinTitle("Opa"));
  await answerPin(main, "2468", "2468");
  expect(prompting(main)).toBe(false);
  expect(sent()).toEqual([
    ["POST", "/api/profiles/andre/unlock", { pin: "1357" }],
    ["POST", "/api/profiles", { actorId: "andre", pin: "1357", name: "Opa", kids: false, newPin: "2468", household: "flat" }],
  ]);
});

test("Add a kid in another household belongs to its admin", async () => {
  const { main } = settingsAs(OWNER);
  // The flat has one grown-up, so there is nobody to choose between.
  expect(descendants(householdOf(main, "Flat")).some((node) => node.getAttribute("aria-label") === W.belongsTo)).toBe(false);
  await add(main, "Flat", "Add a kid", "Ben");
  await answerPin(main, "1357");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles",
    { actorId: "andre", pin: "1357", name: "Ben", kids: true, kidsAge: 6, dailyLimit: null, parentId: "maja" }]);
});

test("a household with two grown-ups asks who a new kid belongs to", async () => {
  everyone = [OLE, ...everyone];
  await state.loadProfiles();
  const { main } = settingsAs(OWNER);
  const parent = fieldOf(householdOf(main, "Flat"), W.belongsTo);
  expect(optionsOf(parent)).toEqual(["Ole", "Maja"]);
  // The household's admin, though not listed first.
  expect(parent.value).toBe("maja");
  await add(main, "Flat", "Add a kid", "Ben", (form) => { fieldOf(form, W.belongsTo).value = "ole"; });
  await answerPin(main, "1357");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles",
    { actorId: "andre", pin: "1357", name: "Ben", kids: true, kidsAge: 6, dailyLimit: null, parentId: "ole" }]);
});

test("an empty household offers Add a grown-up and Delete but not Add a kid", async () => {
  households = [ATTIC, ...households];
  await state.loadProfiles();
  const { main } = settingsAs(OWNER);
  expect(namesOf(main)).toEqual(["Home", "Attic", "Loft", "Flat"]);
  expect(actionsOf(householdRow(main, "Attic"))).toContain(W.delete);
  expect(formsOf(householdOf(main, "Attic"))).toEqual(["Add a grown-up"]);
});

test("Everyone here and the Owner button are gone while the owner view shows", async () => {
  const { main } = settingsAs(OWNER);
  expect(everyoneHere(main).hidden).toBe(true);
  expect(gate(main).hidden).toBe(true);
  // This device's household is the device's, not the owner's: it stays.
  expect(descendants(main).find((node) => node.children[0]?.textContent === W.thisDevice)!.hidden).toBe(false);
  // Anyone else keeps both.
  const other = settingsAs(MAJA).main;
  expect(everyoneHere(other).hidden).toBe(false);
  expect(gate(other).hidden).toBe(false);
  expect(textOf(gate(other))).toContain(W.ownerNote);
});

test("from the picker, choosing the owner opens the owner view", async () => {
  const root = await manageAs("andre", "1357");
  expect(namesOf(root)).toEqual(["Home", "Loft", "Flat"]);
  expect(textOf(root)).not.toContain("Who are you?");
  expect(everyoneHere(root).hidden).toBe(true);
  expect(gate(root).hidden).toBe(true);
  // The PIN given there is held: a change asks nothing more.
  await changeTim(root);
  expect(prompting(root)).toBe(false);
  expect(sent()).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }], timTo(12)]);
});

test("on a device in another household, the Owner button opens the whole owner view", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const { main } = settingsAs(MAJA);
  // The owner is not one of this household's to choose.
  expect(descendants(byClass(main, "who-ask")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["Maja"]);
  buttonNamed(gate(main), W.owner).fire("click");
  expect(promptTitle(main)).toBe(W.ownersPin);
  await answerPin(main, "1357");
  expect(namesOf(main)).toEqual(["Flat", "Loft", "Home"]);
  expect(textOf(main)).not.toContain("Who are you?");
  expect(everyoneHere(main).hidden).toBe(true);
  expect(gate(main).hidden).toBe(true);
  // The owner's PIN is held: adding asks only the new grown-up's.
  await add(main, "Loft", "Add a grown-up", "Opa");
  expect(promptTitle(main)).toBe(newPinTitle("Opa"));
  await answerPin(main, "2468", "2468");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "andre", pin: "1357", name: "Opa", kids: false, newPin: "2468", household: "loft" }]);
  expect(unlocks().length).toBe(1);
});

test("a wrong PIN is asked again at the next change, and nothing is sent", async () => {
  const { main } = settingsAs(OWNER);
  await changeTim(main);
  await answerPin(main, "1357");
  // The owner's PIN is changed on another device meanwhile: the one held is refused, and dropped.
  ownersPin = "2580";
  await changeTim(main, "6");
  expect(prompting(main)).toBe(false);
  expect(textOf(manage(main))).toContain("Wrong PIN.");
  expect(namesOf(main)).toEqual(["Home", "Loft", "Flat"]);
  const before = sent().length;
  await changeTim(main);
  expect(promptTitle(main)).toBe(W.ownersPin);
  expect(sent().length).toBe(before);
  // A wrong one there is said in the prompt, and the change still waits.
  await answerPin(main, "1357");
  expect(textOf(byClass(main, "pin-prompt"))).toContain("Wrong PIN.");
  expect(sent().slice(before)).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }]]);
  await answerPin(main, "2580");
  expect(sent().slice(before + 1)).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "2580" }], timTo(12, "2580")]);
});

test("a taken name is said beside the form it was typed in", async () => {
  const { main } = settingsAs(OWNER);
  refusal = { status: 409, body: { reason: "name-taken" } };
  await add(main, "Flat", "Add a grown-up", "maja");
  await answerPin(main, "1357");
  await answerPin(main, "2468", "2468");
  expect(prompting(main)).toBe(false);
  const form = formOf(householdOf(main, "Flat"), "Add a grown-up");
  expect(textOf(saidAfter(form))).toBe(W.profileNameTaken);
  // The form keeps what was typed, to be put right; no other form says anything.
  expect(descendants(form).find((node) => node.tagName === "INPUT")!.value).toBe("maja");
  expect(textOf(saidAfter(formOf(householdOf(main, "Loft"), "Add a grown-up")))).toBe("");
  expect(textOf(manage(main))).not.toContain("Wrong PIN.");
});

test("leaving the page and coming back asks the PIN again", async () => {
  const { main } = settingsAs(OWNER);
  await changeTim(main);
  await answerPin(main, "1357");
  buttonNamed(main, "Appearance").fire("click");
  buttonNamed(main, "Profiles").fire("click");
  await changeTim(main, "6");
  expect(promptTitle(main)).toBe(W.ownersPin);
  await answerPin(main, "1357");
  expect(unlocks().length).toBe(2);
  expect(sent().at(-1)).toEqual(timTo(6));
});

test("a household added or removed elsewhere redraws the view and keeps the PIN", async () => {
  const { main } = settingsAs(OWNER);
  await changeTim(main);
  await answerPin(main, "1357");
  // Another device creates the attic, and empties and deletes the loft.
  everyone = everyone.filter((one: any) => one.id !== "ida");
  households = [ATTIC, FLAT, AT_HOME];
  await state.loadProfiles();
  env.window.dispatchEvent(new Event("mediagram:profiles"));
  expect(namesOf(main)).toEqual(["Home", "Attic", "Flat"]);
  await press(main, "Attic", W.delete);
  expect(prompting(main)).toBe(false);
  expect(sent().at(-1)).toEqual(["DELETE", "/api/households/attic", { actorId: "andre", pin: "1357" }]);
  expect(unlocks().length).toBe(1);
});

test("the owner's changes in another household reach that profile: a PIN, a section, a kid's limit, a removal", async () => {
  const root = await manageAs("andre", "1357");
  buttonNamed(rowOf(root, "Ida")!, "Reset PIN").fire("click");
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/ida/pin", { actorId: "andre", pin: "1357", newPin: "2468" }]);
  switchOf(root, "Maja", SECTIONS.anime.label).fire("click");
  await settle();
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/maja/hidden-sections", { actorId: "andre", pin: "1357", sections: ["anime"] }]);
  await changeTim(root);
  expect(sent().at(-1)).toEqual(timTo(12));
  buttonNamed(rowOf(root, "Ida")!, "Remove").fire("click");
  await settle();
  expect(sent().at(-1)).toEqual(["DELETE", "/api/profiles/ida", { actorId: "andre", pin: "1357" }]);
  // The loft is still there, empty now, so it may be deleted.
  expect(rowOf(root, "Ida")).toBeUndefined();
  expect(textOf(householdRow(root, "Loft").children[1]!)).toBe("No admin · 0 profiles");
  expect(actionsOf(householdRow(root, "Loft"))).toContain(W.delete);
});

test("anyone else manages only this device's household, unnamed, as before", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const root = await manageAs("Maja", "4321");
  expect(headings(root)).toEqual(["Manage profiles", "Grown-ups", "Kids", "Your PIN"]);
  expect(rowOf(root, "Tim")).toBeDefined();
  for (const name of ["Ida", "Lea", "andre"]) expect(rowOf(root, name)).toBeUndefined();
  expect(rows(root)).toEqual([]);
});

test("the owner of one household gets the owner's view too", async () => {
  everyone = SPLIT.map((one) => ({ ...one, household: "home", admin: one.id === "andre" }));
  households = [AT_HOME];
  await state.loadProfiles();
  const root = await manageAs("andre", "1357");
  expect(namesOf(root)).toEqual(["Home"]);
  expect(buttonNamed(rowOf(root, "Maja")!, "Reset PIN")).toBeDefined();
  expect(formOf(root, W.createAHousehold)).toBeDefined();
});

test("a household renamed in the owner's view shows at once, and the view keeps its PIN", async () => {
  const root = await manageAs("andre", "1357");
  await fill(root, "Loft", W.rename, "Attic");
  expect(prompting(root)).toBe(false);
  expect(namesOf(root)).toEqual(["Home", "Attic", "Flat"]);
  expect(textOf(root)).toContain("As andre");
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
  expect(namesOf(root)).toEqual(["Home", "Flat"]);
  expect(textOf(manage(root))).not.toContain("loft");
});

test("a household the list does not name keeps its rows, unheaded, instead of breaking the view", async () => {
  // Mid-sync the list can lack this device's household; its people still show, without a header.
  households = [LOFT, FLAT];
  await state.loadProfiles();
  const root = await manageAs("andre", "1357");
  expect(namesOf(root)).toEqual(["Loft", "Flat"]);
  expect(rowOf(root, "andre")).toBeDefined();
  expect(rowOf(root, "Lea")).toBeDefined();
  expect(textOf(manage(root))).not.toContain("home");
});
