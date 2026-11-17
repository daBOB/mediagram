/**
 * The Profiles page as calm rows: a person is one row that opens their controls on a tap, one at a
 * time; a household's actions sit behind its ⋯; one Add someone per household opens one form; This
 * device is one line at the end; and anyone else who manages sees the same rows for their household.
 */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { DAILY_LIMIT_WORDS } from "../public/lib/daily-limit.js";
import { HOUSEHOLD_WORDS as W } from "../public/lib/household-words.js";
import { newPinTitle } from "../public/lib/pin-prompt.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf, tileNamed } from "./support/browser-application";
import { AT_HOME, FLAT, storage, writesOf } from "./support/household-fixture";
import {
  actionsOf, addSomeone, ATTIC, choose, EVERYONE, fieldOf, MAJA, menuOf, optionsOf, OWNER, part, personOf, promptTitle, rowOf, rows, tap,
} from "./support/households-part";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let refusal: { status: number; body: object } | null;
beforeEach(async () => {
  env = browserEnvironment();
  everyone = EVERYONE;
  refusal = null;
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }), confirm: () => true });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: everyone, households: [ATTIC, FLAT, AT_HOME] });
    if (refusal && !url.endsWith("/unlock")) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
const manage = (root: Node) => byClass(root, "manage-inline");
/** The part of the owner's view the household `name` heads. */
const householdOf = (root: Node, name: string) => rowOf(root, name).parent!;
/** Everything under `root` that takes input: a field, a choice, a switch, a form. */
const fields = (root: Node) => descendants(root).filter((node) => ["INPUT", "SELECT", "FORM"].includes(node.tagName) || node.getAttribute("role") === "switch");
const expanded = (node: Node) => node.getAttribute("aria-expanded");
const rowIn = (person: Node) => person.children[0]!;
const controlsOf = (person: Node) => person.children[1]!;
const peopleOf = (root: Node) => descendants(root).filter((node) => node.classes.has("manage-person"));
/** The kinds an Add someone form offers to choose between: none when it may add only one. */
const kindsOf = (form: Node) => {
  const group = descendants(form).find((node) => node.getAttribute("role") === "radiogroup");
  return group ? group.children.map((choice) => choice.children[1]!.textContent) : [];
};
const submitOf = (form: Node) => byClass(form, "who-create").textContent;
const escape = (node: Node) => node.dispatchEvent(Object.assign(new Event("keydown"), { key: "Escape" }));
const addsIn = (root: Node) => descendants(root).filter((node) => node.tagName === "BUTTON" && node.textContent === W.addSomeone);

test("a person is one row with their role line until tapped", () => {
  const { root } = part(OWNER);
  const people = peopleOf(manage(root));
  // Everyone in every household, this device's first: each a row, nothing more.
  expect(people.map((one) => rowIn(one).children[0]!.textContent)).toEqual(["andre", "Kai", "Lea", "Maja", "Ole", "Tim"]);
  for (const one of people) {
    expect(rowIn(one).tagName).toBe("BUTTON");
    expect(expanded(rowIn(one))).toBe("false");
    expect(rowIn(one).children.map((part) => part.className)).toEqual(["manage-name", "setting-value", "manage-chevron"]);
    expect(controlsOf(one).hidden).toBe(true);
  }
  expect(textOf(rowIn(personOf(root, "Tim")).children[1]!)).toBe("Kid · Maja · FSK 6");
  // Closed, the page holds no field, no switch, no form, and none of a person's buttons.
  expect(fields(manage(root))).toEqual([]);
  for (const control of ["Reset PIN", "Remove", "Change your PIN", "Add a grown-up", "Add a kid"]) expect(textOf(manage(root))).not.toContain(control);
  const tim = tap(root, "Tim");
  expect(expanded(rowIn(tim))).toBe("true");
  expect(controlsOf(tim).hidden).toBe(false);
  expect(fieldOf(controlsOf(tim), "Age limit for Tim").value).toBe("6");
});

test("tapping a person opens their controls under the row and closes the one open before", async () => {
  const { root } = part(OWNER);
  const maja = tap(root, "Maja");
  expect(buttonNamed(controlsOf(maja), "Reset PIN")).toBeDefined();
  expect(buttonNamed(controlsOf(maja), "Remove")).toBeDefined();
  expect(descendants(controlsOf(maja)).some((node) => node.getAttribute("aria-label") === "Sections for Maja")).toBe(true);
  // One open at a time: opening Tim closes Maja.
  const tim = tap(root, "Tim");
  expect(expanded(rowIn(personOf(root, "Maja")))).toBe("false");
  expect(controlsOf(personOf(root, "Maja")).hidden).toBe(true);
  expect(fields(personOf(root, "Maja"))).toEqual([]);
  expect(buttonNamed(controlsOf(tim), "Remove")).toBeDefined();
  // A change made there is followed by the same view: Tim still open, focus back on what was changed.
  const limit = fieldOf(controlsOf(tim), "Age limit for Tim");
  limit.value = "12";
  limit.fire("change");
  await settle();
  await answerPin(root, "1357");
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/tim/kids-age", { actorId: "andre", pin: "1357", age: 12 }]);
  expect(expanded(rowIn(personOf(root, "Tim")))).toBe("true");
  expect(env.document.activeElement).toBe(fieldOf(root, "Age limit for Tim"));
  // Tapped again, it closes.
  tap(root, "Tim");
  expect(controlsOf(personOf(root, "Tim")).hidden).toBe(true);
  expect(fields(manage(root))).toEqual([]);
});

test("someone the manager cannot change has no chevron and does not open", async () => {
  // Kai's own kid at home: the owner manages their own kids here, and Kai's are Kai's.
  everyone = [...EVERYONE, { id: "mia", name: "Mia", createdAt: 9, kids: true, kidsAge: 6, parentId: "kai", admin: false, hasPin: false, household: "home" }];
  await state.loadProfiles();
  const { root } = part(OWNER);
  const mia = personOf(root, "Mia");
  expect(rowIn(mia).tagName).toBe("DIV");
  expect(rowIn(mia).children.map((part) => part.className)).toEqual(["manage-name", "setting-value"]);
  expect(textOf(rowIn(mia).children[1]!)).toBe("Kid · Kai · FSK 6");
  expect(expanded(rowIn(mia))).toBeNull();
  rowIn(mia).fire("click");
  expect(personOf(root, "Mia").children.length).toBe(1);
  expect(fields(personOf(root, "Mia"))).toEqual([]);
});

test("the household line's menu holds the actions the rule allows", () => {
  const { root } = part(OWNER);
  const allowed: Array<[string, string[]]> = [
    ["Home", [W.rename, W.moveHere]],
    ["Attic", [W.rename, W.moveHere, W.useHere, W.delete]],
    ["Flat", [W.rename, W.changeAdmin, W.moveHere, W.useHere]],
  ];
  for (const [name, actions] of allowed) {
    const row = rowOf(root, name);
    const more = byClass(row, "more-button");
    // One ⋯, named for its household and closed: no action sits on the line itself.
    expect(more.tagName).toBe("BUTTON");
    expect(more.getAttribute("aria-label")).toBe(`${W.actionsFor} ${name}`);
    expect(expanded(more)).toBe("false");
    expect(byClass(row, "household-actions").hidden).toBe(true);
    expect(actionsOf(row)).toEqual(actions);
    expect(expanded(more)).toBe("true");
    expect(byClass(row, "household-actions").hidden).toBe(false);
    // Escape closes it and hands focus back to the ⋯.
    escape(byClass(row, "more-menu"));
    expect(byClass(row, "household-actions").hidden).toBe(true);
    expect(expanded(more)).toBe("false");
    expect(env.document.activeElement).toBe(more);
  }
  // An action that asks something opens its form under the line, the menu closed behind it.
  const flat = rowOf(root, "Flat");
  buttonNamed(menuOf(flat), W.rename).fire("click");
  expect(byClass(flat, "household-actions").hidden).toBe(true);
  expect(fieldOf(byClass(flat, "household-form"), W.householdName).value).toBe("Flat");
  expect(env.document.activeElement).toBe(fieldOf(flat, W.householdName));
});

test("Add someone opens one form that asks grown-up or kid", () => {
  const { root } = part(OWNER);
  // One per household, closed: no form until it is pressed.
  for (const name of ["Home", "Attic", "Flat"]) {
    expect(addsIn(householdOf(root, name)).length).toBe(1);
    expect(expanded(addsIn(householdOf(root, name))[0]!)).toBe("false");
    expect(fields(householdOf(root, name))).toEqual([]);
  }
  const home = householdOf(root, "Home");
  const form = addSomeone(home);
  expect(expanded(buttonNamed(home, W.addSomeone))).toBe("true");
  expect(descendants(home).filter((node) => node.tagName === "FORM")).toEqual([form]);
  expect(kindsOf(form)).toEqual([W.grownUp, W.kid]);
  // A grown-up first: a name, and nothing a kid is asked.
  expect(submitOf(form)).toBe("Add a grown-up");
  expect(byClass(form, "add-kid").hidden).toBe(true);
  fieldOf(form, "Name").value = "Ben";
  choose(form, W.kid);
  expect(submitOf(form)).toBe("Add a kid");
  expect(byClass(form, "add-kid").hidden).toBe(false);
  expect(fieldOf(form, "Age limit for the new kid").value).toBe("6");
  expect(fieldOf(form, `${DAILY_LIMIT_WORDS.label} for the new kid`).value).toBe("");
  expect(fieldOf(form, "Name").value).toBe("Ben");
  choose(form, W.grownUp);
  expect(byClass(form, "add-kid").hidden).toBe(true);
  // Pressed again, it closes.
  buttonNamed(home, W.addSomeone).fire("click");
  expect(expanded(buttonNamed(home, W.addSomeone))).toBe("false");
  expect(fields(home)).toEqual([]);
});

test("Add someone in a household with no grown-up offers only a grown-up", async () => {
  const { root } = part(OWNER);
  const form = addSomeone(householdOf(root, "Attic"));
  expect(kindsOf(form)).toEqual([]);
  expect(submitOf(form)).toBe("Add a grown-up");
  expect(descendants(form).some((node) => node.classes.has("add-kid"))).toBe(false);
  fieldOf(form, "Name").value = "Opa";
  form.fire("submit");
  await settle();
  await answerPin(root, "1357");
  expect(promptTitle(root)).toBe(newPinTitle("Opa"));
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "andre", pin: "1357", name: "Opa", kids: false, newPin: "2468", household: "attic" }]);
});

test("a kid added from Add someone belongs to the chosen grown-up", async () => {
  const { root } = part(OWNER);
  const form = addSomeone(householdOf(root, "Flat"));
  choose(form, W.kid);
  const parent = fieldOf(form, W.belongsTo);
  // The flat's grown-ups, its admin chosen to start with.
  expect(optionsOf(parent)).toEqual(["Maja", "Ole"]);
  expect(parent.value).toBe("maja");
  fieldOf(form, "Name").value = "Ben";
  parent.value = "ole";
  fieldOf(form, `${DAILY_LIMIT_WORDS.label} for the new kid`).value = "30";
  form.fire("submit");
  await settle();
  await answerPin(root, "1357");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles",
    { actorId: "andre", pin: "1357", name: "Ben", kids: true, kidsAge: 6, dailyLimit: 30, parentId: "ole" }]);
});

test("a refused add keeps the open form as typed", async () => {
  const { root } = part(OWNER);
  refusal = { status: 409, body: { reason: "name-taken" } };
  const form = addSomeone(householdOf(root, "Flat"));
  choose(form, W.kid);
  fieldOf(form, "Name").value = "Tim";
  fieldOf(form, "Age limit for the new kid").value = "12";
  fieldOf(form, W.belongsTo).value = "ole";
  form.fire("submit");
  await settle();
  await answerPin(root, "1357");
  // Said under the form, which is still the one open, as it was typed.
  const flat = householdOf(root, "Flat");
  expect(descendants(flat).filter((node) => node.tagName === "FORM")).toEqual([form]);
  expect(expanded(buttonNamed(flat, W.addSomeone))).toBe("true");
  expect(textOf(byClass(form.parent!, "pin-message"))).toBe(W.profileNameTaken);
  expect(submitOf(form)).toBe("Add a kid");
  expect(fieldOf(form, "Name").value).toBe("Tim");
  expect(fieldOf(form, "Age limit for the new kid").value).toBe("12");
  expect(fieldOf(form, W.belongsTo).value).toBe("ole");
  expect(byClass(form, "add-kid").hidden).toBe(false);
});

test("This device is one line at the end", () => {
  const { root } = part(OWNER);
  const line = byClass(root, "settings-panel").children.at(-1)!;
  // No heading of its own: which household this device shows, and the way to another.
  expect(line.classes.has("device-line")).toBe(true);
  expect(descendants(line).some((node) => /^H\d$/.test(node.tagName))).toBe(false);
  expect(line.children.map(textOf)).toEqual([W.thisDevice, "Home", W.switchHousehold]);
  // After the households and Create a household.
  expect(descendants(manage(root)).filter((node) => node.tagName === "BUTTON").at(-1)!.textContent).toBe(W.createAHousehold);
  // Anyone else in Settings has the same line, under Who's managing?.
  expect(byClass(part(MAJA).root, "settings-panel").children.at(-1)!.children.map(textOf)).toEqual([W.thisDevice, "Home", W.switchHousehold]);
});

test("a household's admin sees the same rows for their household and no menu", async () => {
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
  const { root } = part(MAJA);
  tileNamed(root, "Maja").fire("click");
  await answerPin(root, "4321");
  const people = peopleOf(manage(root));
  // Herself first, then the grown-up and the kid she manages, drawn as the owner's rows are.
  expect(people.map((one) => rowIn(one).children[0]!.textContent)).toEqual(["Maja", "Ole", "Tim"]);
  for (const one of people) expect(rowIn(one).tagName).toBe("BUTTON");
  expect(textOf(rowIn(people[0]!).children[1]!)).toBe("Admin · PIN set");
  // No household line, no ⋯, no Create a household: the household is hers, unnamed.
  expect(rows(root)).toEqual([]);
  expect(descendants(root).some((node) => node.classes.has("more-button"))).toBe(false);
  expect(textOf(manage(root))).not.toContain(W.createAHousehold);
  expect(fields(manage(root))).toEqual([]);
  expect(buttonNamed(controlsOf(tap(root, "Ole")), "Reset PIN")).toBeDefined();
  const herself = controlsOf(tap(root, "Maja"));
  expect(buttonNamed(herself, "Change your PIN")).toBeDefined();
  expect(textOf(herself)).not.toContain("Remove");
  // One Add someone, which may add either: she runs the flat.
  expect(addsIn(manage(root)).length).toBe(1);
  expect(kindsOf(addSomeone(manage(root)))).toEqual([W.grownUp, W.kid]);
});
