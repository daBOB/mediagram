/** The owner's Households part of the Profiles page: every household, and the owner's changes to them with one PIN per visit. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { HOUSEHOLD_WORDS as W } from "../public/lib/household-words.js";
import { newPinTitle } from "../public/lib/pin-prompt.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { AT_HOME, FLAT, storage, writesOf } from "./support/household-fixture";
import { actionsOf, ATTIC, create, EVERYONE, fieldOf, fill, MAJA, open, optionsOf, OWNER, part, press, prompting, promptTitle, rowOf, rows } from "./support/households-part";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let everyone: object[];
let refusal: { status: number; body: object } | null;
let kept: ReturnType<typeof storage>;
beforeEach(async () => {
  env = browserEnvironment();
  everyone = EVERYONE;
  refusal = null;
  Object.assign(env.window, { localStorage: (kept = storage({ "mediagram.household": "home" })) });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard: true, profiles: everyone, households: [ATTIC, FLAT, AT_HOME] });
    if (refusal && url.startsWith("/api/households")) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
const unlocks = () => sent().filter(([, url]) => String(url).endsWith("/unlock"));

test("the owner sees every household with its admin and member count", () => {
  const { root } = part(OWNER);
  expect(textOf(root)).toStartWith(W.households);
  expect(rows(root).map((row) => [row.children[0]!.textContent, row.children[1]!.textContent])).toEqual([
    ["Attic", "No admin · 0 profiles"],
    ["Flat", "Admin Maja · 3 profiles"],
    ["Home", "Admin andre · 3 profiles"],
  ]);
  expect(textOf(root)).not.toContain(W.ownerNote);
  // Seeing them asks nothing: the PIN waits for the first change.
  expect(prompting(root)).toBe(false);
  expect(sent()).toEqual([]);
});

test("anyone else sees an Owner button that asks the owner's PIN once", async () => {
  expect(textOf(part(null).root)).toContain(W.ownerNote);
  const { root } = part(MAJA);
  expect(textOf(root)).toContain(W.ownerNote);
  expect(rows(root)).toEqual([]);
  buttonNamed(root, W.owner).fire("click");
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "1357");
  expect(sent()).toEqual([["POST", "/api/profiles/andre/unlock", { pin: "1357" }]]);
  expect(textOf(root)).not.toContain(W.ownerNote);
  expect(rows(root).length).toBe(3);
  await press(root, "Attic", W.delete);
  expect(prompting(root)).toBe(false);
  expect(sent().at(-1)).toEqual(["DELETE", "/api/households/attic", { actorId: "andre", pin: "1357" }]);
  expect(unlocks().length).toBe(1);
});

test("no Owner button on an install with no owner", async () => {
  everyone = EVERYONE.map((one) => ({ ...one, owner: false }));
  await state.loadProfiles();
  const { root } = part(MAJA);
  expect(textOf(root)).toContain(W.ownerNote);
  expect(descendants(root).some((node) => node.tagName === "BUTTON")).toBe(false);
});

test("create with a new grown-up sends the name, the admin's name and PIN", async () => {
  const { root } = part(OWNER);
  await create(root, "Oma", { name: "Ilse" });
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "1357");
  expect(promptTitle(root)).toBe(newPinTitle("Ilse"));
  await answerPin(root, "2468", "2468");
  expect(sent()).toEqual([
    ["POST", "/api/profiles/andre/unlock", { pin: "1357" }],
    ["POST", "/api/households", { actorId: "andre", pin: "1357", name: "Oma", adminName: "Ilse", newPin: "2468" }],
  ]);
  expect(prompting(root)).toBe(false);
});

test("create with an existing grown-up asks a PIN only when it has none", async () => {
  const { root } = part(OWNER);
  // Only those the rule lets move: never the owner, never Maja, who still has Ole to run the flat for.
  expect(optionsOf(fieldOf(root, W.admin))).toEqual([W.aNewGrownUp, "Ole", "Kai"]);
  await create(root, "Oma", { id: "kai" });
  await answerPin(root, "1357");
  expect(prompting(root)).toBe(false);
  expect(sent().at(-1)).toEqual(["POST", "/api/households", { actorId: "andre", pin: "1357", name: "Oma", adminId: "kai" }]);
  await create(root, "Upstairs", { id: "ole" });
  expect(promptTitle(root)).toBe(newPinTitle("Ole"));
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["POST", "/api/households", { actorId: "andre", pin: "1357", name: "Upstairs", adminId: "ole", newPin: "2468" }]);
  expect(unlocks().length).toBe(1);
});

test("rename, change admin, move and delete send the held PIN", async () => {
  const { root } = part(OWNER);
  await fill(root, "Flat", W.rename, "The flat");
  await answerPin(root, "1357");
  expect(sent().at(-1)).toEqual(["PUT", "/api/households/flat/name", { actorId: "andre", pin: "1357", name: "The flat" }]);

  // Ole, the flat's only other grown-up, has no PIN, and an admin needs one.
  await fill(root, "Flat", W.changeAdmin);
  expect(promptTitle(root)).toBe(newPinTitle("Ole"));
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["PUT", "/api/households/flat/admin", { actorId: "andre", pin: "1357", adminId: "ole", newPin: "2468" }]);

  // Into a household someone runs, nobody new needs a PIN.
  await fill(root, "Flat", W.moveHere);
  expect(prompting(root)).toBe(false);
  expect(sent().at(-1)).toEqual(["POST", "/api/households/flat/move", { actorId: "andre", pin: "1357", profileId: "kai" }]);

  // Into one nobody runs, the one moved runs it: Kai has a PIN, Ole is given one.
  await fill(root, "Attic", W.moveHere, "kai");
  expect(sent().at(-1)).toEqual(["POST", "/api/households/attic/move", { actorId: "andre", pin: "1357", profileId: "kai" }]);
  await fill(root, "Attic", W.moveHere, "ole");
  expect(promptTitle(root)).toBe(newPinTitle("Ole"));
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["POST", "/api/households/attic/move", { actorId: "andre", pin: "1357", profileId: "ole", newPin: "2468" }]);

  await press(root, "Attic", W.delete);
  expect(sent().at(-1)).toEqual(["DELETE", "/api/households/attic", { actorId: "andre", pin: "1357" }]);
  expect(unlocks().length).toBe(1);
});

test("Change admin is not offered for the owner's own household, Delete only for an empty one", () => {
  const { root } = part(OWNER);
  expect(actionsOf(rowOf(root, "Home"))).toEqual([W.rename, W.moveHere]);
  expect(actionsOf(rowOf(root, "Flat"))).toEqual([W.rename, W.changeAdmin, W.moveHere, W.useHere]);
  expect(actionsOf(rowOf(root, "Attic"))).toEqual([W.rename, W.moveHere, W.useHere, W.delete]);
  // A new admin is one of its own grown-ups but the one it has; a rename starts from the name it has.
  expect(optionsOf(fieldOf(open(root, "Flat", W.changeAdmin), W.changeAdmin))).toEqual(["Ole"]);
  expect(fieldOf(open(root, "Attic", W.rename), W.householdName).value).toBe("Attic");
  // The owner is never offered for a move, and Maja may not leave Ole behind without an admin.
  expect(optionsOf(fieldOf(open(root, "Home", W.moveHere), W.moveHere))).toEqual(["Ole"]);
  expect(optionsOf(fieldOf(open(root, "Flat", W.moveHere), W.moveHere))).toEqual(["Kai"]);
  expect(optionsOf(fieldOf(open(root, "Attic", W.moveHere), W.moveHere))).toEqual(["Ole", "Kai"]);
});

test("Use on this device switches the device with the owner's PIN", async () => {
  let switched = 0;
  const { root } = part(OWNER, () => switched++);
  expect(actionsOf(rowOf(root, "Home"))).not.toContain(W.useHere);
  await press(root, "Flat", W.useHere);
  expect(promptTitle(root)).toBe(W.ownersPin);
  await answerPin(root, "1357");
  expect(sent().at(-1)).toEqual(["POST", "/api/households/flat/join", { pin: "1357" }]);
  expect(switched).toBe(1);
  expect(kept.getItem("mediagram.household")).toBe("flat");
});

test("a taken household name says so in the household's own words", async () => {
  const { root } = part(OWNER);
  refusal = { status: 409, body: { reason: "name-taken" } };
  await fill(root, "Flat", W.rename, "home");
  await answerPin(root, "1357");
  expect(textOf(byClass(rowOf(root, "Flat"), "pin-message"))).toBe(W.householdNameTaken);
  // The form stays, holding what was typed, to be put right.
  expect(fieldOf(rowOf(root, "Flat"), W.householdName).value).toBe("home");
  const said = () => textOf(byClass(byClass(root, "household-create"), "pin-message"));
  await create(root, "Home", { id: "kai" });
  expect(said()).toBe(W.householdNameTaken);
  await create(root, "Home", { name: "Ilse" });
  await answerPin(root, "2468", "2468");
  expect(said()).toBe(W.householdNameTaken);
  // A new household's name is free; the new grown-up's is the one taken.
  await create(root, "Oma", { name: "maja" });
  await answerPin(root, "2468", "2468");
  expect(said()).toBe(W.profileNameTaken);
});

test("leaving the page drops the owner's PIN", async () => {
  const main = new Node();
  const stop = renderSettings(main, { profile: OWNER, switchProfile: () => {}, systemVisible: false, admin: null });
  const profiles = () => buttonNamed(main, "Profiles").fire("click");
  profiles();
  buttonNamed(rowOf(main, "Attic"), W.delete).fire("click");
  await answerPin(main, "1357");
  expect(sent().at(-1)).toEqual(["DELETE", "/api/households/attic", { actorId: "andre", pin: "1357" }]);

  buttonNamed(main, "Appearance").fire("click");
  profiles();
  const remove = buttonNamed(rowOf(main, "Attic"), W.delete);
  remove.fire("click");
  expect(promptTitle(main)).toBe(W.ownersPin);
  stop();
  expect(prompting(main)).toBe(false);
  const before = sent().length;
  // The old controls are still in hand: a change driven from them sends nothing.
  remove.fire("click");
  await settle();
  expect(sent().length).toBe(before);
  expect(rows(main)).toEqual([]);
});
