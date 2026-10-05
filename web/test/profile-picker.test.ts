/** Who's watching: the three ways a household can stand, a kid opening freely, a grown-up's PIN, and staying as one is. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { chooseProfile } from "../public/lib/profile-picker.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { ANDRE, LEA, MAJA as MAJA_WITH_PIN, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

/** Maja is a grown-up from before PINs here. */
const MAJA = { ...MAJA_WITH_PIN, hasPin: false };

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
let refusal: { status: number; body: object } | null;
/** Whether the server has heard its household; left out, as a server from before the wait says nothing. */
let heard: boolean | undefined;
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, TIM, LEA];
  refusal = null;
  heard = undefined;
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, heard, profiles: household });
    if (url.endsWith("/state")) return Response.json({});
    if (refusal) return Response.json(refusal.body, { status: refusal.status });
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(async () => { await state.useProfile(null); env.restore(); });

const tile = (root: Node, name: string) => descendants(root).find((node) => node.className === "who-tile" && textOf(node).includes(name))!;
const sent = () => writesOf(env.requests);
const prompting = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));
const picking = (root: Node) => descendants(root).some((node) => node.className === "who");
const escape = () => env.document.dispatchEvent(Object.assign(new Event("keydown"), { key: "Escape" }));

test("a kid's tile names its limit and opens at once, with no PIN", async () => {
  const root = new Node();
  const chosen = chooseProfile(root);
  expect(textOf(tile(root, "Tim"))).toContain("Kids · FSK 6");
  expect(textOf(tile(root, "Lea"))).toContain("Kids · FSK 12");
  tile(root, "Tim").fire("click");
  expect(await chosen).toBe("tim");
  expect(sent()).toEqual([]);
});

test("a grown-up's tile asks the PIN, and only an accepted one enters", async () => {
  const root = new Node();
  let entered = false;
  const chosen = chooseProfile(root).then((id) => { entered = true; return id; });
  tile(root, "andre").fire("click");
  expect(prompting(root)).toBe(true);
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  await answerPin(root, "1111");
  expect(textOf(byClass(root, "pin-message"))).toBe("Wrong PIN.");
  expect(entered).toBe(false);
  refusal = null;
  await answerPin(root, "1234");
  expect(await chosen).toBe("andre");
  expect(sent().at(-1)).toEqual(["POST", "/api/profiles/andre/unlock", { pin: "1234" }]);
});

test("a waiting player's answer is shown in seconds", async () => {
  const root = new Node();
  void chooseProfile(root);
  tile(root, "andre").fire("click");
  refusal = { status: 429, body: { reason: "wait", retryAfter: 60 } };
  await answerPin(root, "1234");
  expect(textOf(byClass(root, "pin-message"))).toBe("Too many wrong PINs. Try again in 60 s.");
  expect(prompting(root)).toBe(true);
});

test("a grown-up with no PIN sets one, twice, before entering", async () => {
  const root = new Node();
  const chosen = chooseProfile(root);
  tile(root, "Maja").fire("click");
  await answerPin(root, "4321", "4312");
  expect(textOf(byClass(root, "pin-message"))).toBe("The two PINs are not the same.");
  expect(sent()).toEqual([]);
  household = [ANDRE, MAJA_WITH_PIN, TIM, LEA];
  await answerPin(root, "4321", "4321");
  expect(await chosen).toBe("maja");
  expect(sent()).toEqual([["PUT", "/api/profiles/maja/pin", { actorId: "maja", pin: "", newPin: "4321" }]]);
});

test("giving up on the PIN leaves the picker as it was", async () => {
  const root = new Node();
  void chooseProfile(root);
  tile(root, "andre").fire("click");
  buttonNamed(root, "Cancel").fire("click");
  await settle();
  expect(prompting(root)).toBe(false);
  expect(picking(root)).toBe(true);
  expect(env.requests.some((request) => request.url.endsWith("/state"))).toBe(false);
});

test("while nobody runs the household, the picker asks who does — grown-ups only", async () => {
  household = [{ ...ANDRE, admin: false }, MAJA, TIM, LEA];
  await state.loadProfiles();
  const root = new Node();
  void chooseProfile(root);
  const ask = byClass(root, "who-ask");
  expect(textOf(ask)).toContain("Who runs this household?");
  expect(descendants(ask).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["andre", "Maja"]);
  household = [ANDRE, MAJA, TIM, LEA];
  buttonNamed(ask, "andre").fire("click");
  await answerPin(root, "1234");
  expect(sent()).toEqual([["POST", "/api/profiles/andre/claim-admin", { pin: "1234" }]]);
  expect(descendants(root).some((node) => node.className === "who-ask")).toBe(false);
});

test("once someone runs the household, nobody is asked", () => {
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(root)).not.toContain("Who runs this household?");
  expect(textOf(root)).not.toContain("Create the first profile");
});

test("with no grown-up here, the first one is made — and runs the household; kids still open", async () => {
  household = [LEA];
  await state.loadProfiles();
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(root)).toContain("Create the first profile — it runs this household");
  expect(textOf(root)).not.toContain("Manage profiles");
  expect(textOf(tile(root, "Lea"))).toContain("Kids · FSK 12");
  const form = descendants(root).find((node) => node.tagName === "FORM" && node.className === "who-new")!;
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "andre";
  household = [ANDRE, LEA];
  form.fire("submit");
  await settle();
  await answerPin(root, "1234", "1234");
  await settle();
  expect(sent()).toEqual([["POST", "/api/profiles", { name: "andre", newPin: "1234" }]]);
  expect(textOf(root)).not.toContain("Create the first profile");
  expect(tile(root, "andre")).toBeDefined();
  expect(buttonNamed(root, "Manage profiles")).toBeDefined();
});

test("a first profile refused because a grown-up arrived meanwhile shows who is here now", async () => {
  household = [];
  await state.loadProfiles();
  const root = new Node();
  void chooseProfile(root);
  const form = descendants(root).find((node) => node.tagName === "FORM" && node.className === "who-new")!;
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "andre";
  form.fire("submit");
  await settle();
  refusal = { status: 403, body: { reason: "not-allowed" } };
  household = [ANDRE];
  await answerPin(root, "1234", "1234");
  await settle();
  expect(prompting(root)).toBe(false);
  expect(textOf(root)).toContain("That is not allowed.");
  expect(textOf(root)).not.toContain("Create the first profile");
  expect(tile(root, "andre")).toBeDefined();
});

test("before the household has been heard, no first profile is offered: the picker waits, and Try again reads again", async () => {
  household = [LEA];
  heard = false;
  await state.loadProfiles();
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(root)).toContain("Waiting for this household’s profiles…");
  expect(textOf(root)).not.toContain("Create the first profile");
  expect(tile(root, "Lea")).toBeDefined();
  heard = true;
  buttonNamed(root, "Try again").fire("click");
  await settle();
  expect(textOf(root)).toContain("Create the first profile — it runs this household");
  expect(textOf(root)).not.toContain("Waiting for");
});

test("a first profile the server holds until it has heard the household says it is waiting", async () => {
  household = [];
  await state.loadProfiles();
  const root = new Node();
  void chooseProfile(root);
  const form = descendants(root).find((node) => node.tagName === "FORM" && node.className === "who-new")!;
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "andre";
  form.fire("submit");
  await settle();
  refusal = { status: 409, body: { reason: "not-synced" } };
  heard = false;
  await answerPin(root, "1234", "1234");
  await settle();
  expect(textOf(root)).toContain("Waiting for this household’s profiles…");
  expect(textOf(root)).not.toContain("Create the first profile");
});

test("a PIN-less grown-up whose PIN was set elsewhere is told once, and its tile then asks for it", async () => {
  const root = new Node();
  void chooseProfile(root);
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  household = [ANDRE, MAJA_WITH_PIN, TIM, LEA];
  tile(root, "Maja").fire("click");
  await answerPin(root, "4321", "4321");
  await settle();
  expect(prompting(root)).toBe(false);
  expect(sent()).toHaveLength(1);
  expect(textOf(root)).toContain("Wrong PIN.");
  tile(root, "Maja").fire("click");
  expect(textOf(byClass(root, "pin-prompt"))).toContain("Maja’s PIN");
});

test("profiles are made and removed in Manage profiles, not on the picker", () => {
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(root)).not.toContain("New profile");
  expect(textOf(root)).not.toContain("Rename or remove");
  buttonNamed(root, "Manage profiles").fire("click");
  expect(textOf(byClass(root, "who-manage"))).toContain("Who are you?");
});

test("the note says what a PIN does, and that it is not a login", () => {
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(byClass(root, "who-note"))).toContain("it is not a login");
});

test("a reopened picker offers to stay as one is, and Escape means stay", async () => {
  await state.useProfile("lea");
  const root = new Node();
  const chosen = chooseProfile(root, { canCancel: true });
  expect(buttonNamed(root, "Stay as I am").hidden).toBe(false);
  escape();
  expect(await chosen).toBe("lea");
  expect(picking(root)).toBe(false);
});

test("Escape closes a PIN prompt or Manage profiles first, and only then the picker", async () => {
  await state.useProfile("lea");
  const root = new Node();
  let settled = false;
  void chooseProfile(root, { canCancel: true }).then(() => { settled = true; });
  tile(root, "andre").fire("click");
  escape();
  await settle();
  expect(settled).toBe(false);
  buttonNamed(root, "Cancel").fire("click");
  await settle();
  buttonNamed(root, "Manage profiles").fire("click");
  escape();
  await settle();
  expect(settled).toBe(false);
  expect(picking(root)).toBe(true);
  expect(descendants(root).some((node) => node.className.includes("who-manage"))).toBe(false);
  escape();
  await settle();
  expect(settled).toBe(true);
});

test("a first run has nobody to stay as, so Escape does nothing", async () => {
  const root = new Node();
  let settled = false;
  void chooseProfile(root).then(() => { settled = true; });
  escape();
  await settle();
  expect(settled).toBe(false);
  expect(picking(root)).toBe(true);
});

test("with this device's profile gone, there is nothing to stay as", async () => {
  await state.useProfile("lea");
  household = [ANDRE, MAJA, TIM];
  await state.loadProfiles();
  const root = new Node();
  let settled = false;
  void chooseProfile(root, { canCancel: true }).then(() => { settled = true; });
  expect(buttonNamed(root, "Stay as I am").hidden).toBe(true);
  escape();
  await settle();
  expect(settled).toBe(false);
});

test("opened from a running page, the picker reads who there is again", async () => {
  household = [ANDRE, MAJA, TIM, LEA, { ...TIM, id: "ben", name: "Ben", createdAt: 5 }];
  const root = new Node();
  void chooseProfile(root, { canCancel: true });
  expect(descendants(root).some((node) => node.className === "who-tile" && textOf(node).includes("Ben"))).toBe(false);
  await settle();
  expect(textOf(tile(root, "Ben"))).toContain("Kids · FSK 6");
});
