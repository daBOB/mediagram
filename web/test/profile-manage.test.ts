/** Manage profiles: who you are first, then what your role offers, with the PIN held only while it is open. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { openManage } from "../public/lib/profile-manage.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { ANDRE, LEA, MAJA, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
let refusal: { status: number; body: object } | null;
let questions: string[];
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, TIM, LEA];
  refusal = null;
  questions = [];
  Object.assign(env.window, { confirm: (question: string) => { questions.push(question); return true; } });
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
    if (refusal) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const sent = () => writesOf(env.requests);
const rowOf = (root: Node, name: string) => descendants(root).find((node) => node.className === "manage-row" && textOf(node).startsWith(name));
const limitOf = (root: Node, name: string) => descendants(root).find((node) => node.tagName === "SELECT" && node.getAttribute("aria-label") === `Age limit for ${name}`)!;
const formOf = (root: Node, label: string) => descendants(root).find((node) => node.tagName === "FORM" && textOf(node).includes(label))!;

/** Opens the panel as `name`, answering the PIN prompt with `pins`. */
async function manageAs(name: string, ...pins: string[]) {
  const root = new Node();
  let closes = 0;
  openManage(root, () => { closes++; });
  buttonNamed(root, name).fire("click");
  await answerPin(root, ...pins);
  return { root, closes: () => closes };
}

test("only grown-ups are offered as who you are", () => {
  const root = new Node();
  openManage(root, () => {});
  expect(descendants(byClass(root, "who-ask")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["andre", "Maja"]);
});

test("a parent sees its own kids and its own PIN — no grown-ups, no other parent's kids", async () => {
  const { root } = await manageAs("Maja", "4321");
  expect(sent()).toEqual([["POST", "/api/profiles/maja/unlock", { pin: "4321" }]]);
  expect(rowOf(root, "Tim")).toBeDefined();
  expect(rowOf(root, "Lea")).toBeUndefined();
  expect(textOf(root)).not.toContain("Grown-ups");
  expect(textOf(root)).not.toContain("Add a grown-up");
  expect(buttonNamed(root, "Add a kid")).toBeDefined();
  expect(buttonNamed(root, "Change your PIN")).toBeDefined();
});

test("the admin sees every other grown-up and its own kids, and no way to remove itself", async () => {
  const { root } = await manageAs("andre", "1234");
  expect(textOf(rowOf(root, "Maja")!)).toContain("Reset PIN");
  expect(textOf(rowOf(root, "Maja")!)).toContain("Remove");
  expect(rowOf(root, "Lea")).toBeDefined();
  expect(rowOf(root, "Tim")).toBeUndefined();
  expect(rowOf(root, "andre")).toBeUndefined();
  expect(buttonNamed(root, "Add a grown-up")).toBeDefined();
});

test("a kid's limit changes with the PIN given once", async () => {
  const { root } = await manageAs("Maja", "4321");
  expect(limitOf(root, "Tim").value).toBe("6");
  limitOf(root, "Tim").value = "12";
  limitOf(root, "Tim").fire("change");
  await settle();
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/tim/kids-age", { actorId: "maja", pin: "4321", age: 12 }]);
});

test("a new kid starts at FSK 6 unless its parent picks 12", async () => {
  const { root } = await manageAs("Maja", "4321");
  const add = (name: string) => {
    const form = formOf(root, "Add a kid");
    descendants(form).find((node) => node.tagName === "INPUT")!.value = name;
    return form;
  };
  const limit = () => descendants(formOf(root, "Add a kid")).find((node) => node.tagName === "SELECT")!;
  expect(limit().value).toBe("6");
  expect(descendants(limit()).map((node) => node.value)).toEqual(["6", "12"]);
  add("Ben").fire("submit");
  await settle();
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "maja", pin: "4321", name: "Ben", kids: true, kidsAge: 6 }]);

  limit().value = "12";
  add("Ida").fire("submit");
  await settle();
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "maja", pin: "4321", name: "Ida", kids: true, kidsAge: 12 }]);
});

test("a new grown-up is given a first PIN, asked twice", async () => {
  const { root } = await manageAs("andre", "1234");
  const form = formOf(root, "Add a grown-up");
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "Oma";
  form.fire("submit");
  await settle();
  await answerPin(root, "2468", "2468");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles", { actorId: "andre", pin: "1234", name: "Oma", kids: false, newPin: "2468" }]);
});

test("removing asks first; a refusal is shown and the profile stays", async () => {
  const { root } = await manageAs("Maja", "4321");
  refusal = { status: 403, body: { reason: "not-allowed" } };
  buttonNamed(rowOf(root, "Tim")!, "Remove").fire("click");
  await settle();
  expect(questions).toEqual(["Remove Tim and everything they have watched?"]);
  expect(sent().at(-1)).toEqual(["DELETE", "/api/profiles/tim", { actorId: "maja", pin: "4321" }]);
  expect(textOf(byClass(root, "pin-message"))).toBe("That is not allowed.");
  expect(rowOf(root, "Tim")).toBeDefined();
});

test("removing a grown-up says their kids go too, and the panel follows the list", async () => {
  const { root } = await manageAs("andre", "1234");
  household = [ANDRE, LEA];
  buttonNamed(rowOf(root, "Maja")!, "Remove").fire("click");
  await settle();
  expect(questions).toEqual(["Remove Maja, their kids, and everything they have watched?"]);
  expect(rowOf(root, "Maja")).toBeUndefined();
});

test("a wrong PIN on a change asks who you are again", async () => {
  const { root } = await manageAs("Maja", "4321");
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  limitOf(root, "Tim").value = "12";
  limitOf(root, "Tim").fire("change");
  await settle();
  expect(textOf(byClass(root, "pin-message"))).toBe("Your PIN is no longer valid. Choose who you are again.");
  expect(textOf(root)).toContain("Who are you?");
});

const prompting = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));
const writes = (method: string) => sent().filter(([sentMethod]) => sentMethod === method).length;

test("a held PIN that no longer works ends a new-PIN dialog at once: nothing is sent twice", async () => {
  const { root } = await manageAs("Maja", "4321");
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  buttonNamed(root, "Change your PIN").fire("click");
  await answerPin(root, "9999", "9999");
  expect(prompting(root)).toBe(false);
  expect(writes("PUT")).toBe(1);
  expect(textOf(byClass(root, "pin-message"))).toBe("Your PIN is no longer valid. Choose who you are again.");
  expect(textOf(root)).toContain("Who are you?");
});

test("so does a reset of another grown-up's PIN", async () => {
  const { root } = await manageAs("andre", "1234");
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  buttonNamed(rowOf(root, "Maja")!, "Reset PIN").fire("click");
  await answerPin(root, "2468", "2468");
  expect(prompting(root)).toBe(false);
  expect(writes("PUT")).toBe(1);
  expect(textOf(root)).toContain("Who are you?");
});

test("a grown-up's name already here is said, and the PIN dialog is gone", async () => {
  const { root } = await manageAs("andre", "1234");
  refusal = { status: 409, body: { reason: "name-taken" } };
  const form = formOf(root, "Add a grown-up");
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "maja";
  form.fire("submit");
  await settle();
  await answerPin(root, "2468", "2468");
  expect(prompting(root)).toBe(false);
  expect(writes("POST")).toBe(2);
  expect(textOf(byClass(root, "pin-message"))).toBe("A profile with that name already exists.");
  expect(rowOf(root, "Maja")).toBeDefined();
});

test("a kid's name already here is said, and the panel stays as it was", async () => {
  const { root } = await manageAs("Maja", "4321");
  refusal = { status: 409, body: { reason: "name-taken" } };
  const form = formOf(root, "Add a kid");
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "Lea";
  form.fire("submit");
  await settle();
  expect(textOf(byClass(root, "pin-message"))).toBe("A profile with that name already exists.");
  expect(rowOf(root, "Tim")).toBeDefined();
});

test("Escape closes the panel as Done does, but a PIN prompt over it goes first", async () => {
  const escape = () => env.document.dispatchEvent(Object.assign(new Event("keydown"), { key: "Escape" }));
  const { root, closes } = await manageAs("Maja", "4321");
  buttonNamed(root, "Change your PIN").fire("click");
  escape();
  expect(closes()).toBe(0);
  buttonNamed(root, "Cancel").fire("click");
  await settle();
  escape();
  expect(closes()).toBe(1);
  expect(descendants(root).some((node) => node.className.includes("who-manage"))).toBe(false);
  escape();
  expect(closes()).toBe(1);
});

test("changing your own PIN changes the one sent after it", async () => {
  const { root } = await manageAs("Maja", "4321");
  buttonNamed(root, "Change your PIN").fire("click");
  await answerPin(root, "9999", "9999");
  expect(sent().at(-1)).toEqual(["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "4321", newPin: "9999" }]);
  limitOf(root, "Tim").value = "12";
  limitOf(root, "Tim").fire("change");
  await settle();
  expect(sent().at(-1)?.[2]).toMatchObject({ pin: "9999" });
});

test("a grown-up with no PIN sets one before managing", async () => {
  household = [ANDRE, { ...MAJA, hasPin: false }, TIM, LEA];
  await state.loadProfiles();
  household = [ANDRE, MAJA, TIM, LEA];
  const { root } = await manageAs("Maja", "4321", "4321");
  expect(sent()).toEqual([["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }]]);
  expect(rowOf(root, "Tim")).toBeDefined();
});

test("closing drops the PIN: the next opening asks who you are", async () => {
  const { root, closes } = await manageAs("Maja", "4321");
  buttonNamed(root, "Done").fire("click");
  expect(closes()).toBe(1);
  expect(descendants(root).some((node) => node.className.includes("who-manage"))).toBe(false);
  openManage(root, () => {});
  expect(textOf(root)).toContain("Who are you?");
  expect(rowOf(root, "Tim")).toBeUndefined();
});
