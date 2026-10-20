/** Join a household: the households to choose from, the PIN each asks, and what joining changes on this device. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import * as households from "../public/lib/household-api.js";
import { joinChoices } from "../public/lib/household-join.js";
import { HOUSEHOLD_WORDS, joinPinTitle } from "../public/lib/household-words.js";
import { chooseProfile } from "../public/lib/profile-picker.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { ANDRE, AT_HOME, FLAT, LEA, MAJA, SPLIT, storage, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let kept: ReturnType<typeof storage>;
let refusal: { status: number; body: object } | null;
let everyone: object[];
/** Whether the profile list can be read; a join can go through and the read after it fail. */
let readable: boolean;
beforeEach(() => {
  env = browserEnvironment();
  refusal = null;
  everyone = SPLIT;
  readable = true;
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) {
      if (!readable) return new Response(null, { status: 500 });
      return Response.json({ remembers: true, heard: true, profiles: everyone, households: [FLAT, AT_HOME] });
    }
    if (url.endsWith("/state")) return Response.json({});
    if (refusal) return Response.json(refusal.body, { status: refusal.status });
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

const pills = (root: Node) => descendants(byClass(root, "who-ask")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent);
const prompting = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));
const tiles = (root: Node) => descendants(root).filter((node) => node.className === "who-tile").map((node) => textOf(node));

test("Join a household lists the households, then asks the admin's PIN", async () => {
  await device();
  const root = new Node();
  void chooseProfile(root);
  expect(textOf(byClass(root, "who-ask"))).toContain(HOUSEHOLD_WORDS.joinAHousehold);
  expect(textOf(byClass(root, "who-ask"))).toContain(HOUSEHOLD_WORDS.joinNote);
  expect(pills(root)).toEqual(["Flat", "Home"]);
  buttonNamed(root, "Flat").fire("click");
  expect(textOf(byClass(root, "pin-prompt"))).toContain(joinPinTitle({ name: "Maja", owner: false }));
  buttonNamed(root, "Cancel").fire("click");
  await settle();
  // Where the owner runs it, the owner's PIN is the one asked.
  buttonNamed(root, "Home").fire("click");
  expect(textOf(byClass(root, "pin-prompt"))).toContain(HOUSEHOLD_WORDS.ownersPin);
});

test("the owner's PIN joins too", async () => {
  await device();
  const root = new Node();
  void chooseProfile(root);
  buttonNamed(root, "Flat").fire("click");
  // The route answers 204 for the flat's admin's PIN and for the owner's alike.
  await answerPin(root, "1357");
  expect(writesOf(env.requests)).toEqual([["POST", "/api/households/flat/join", { pin: "1357" }]]);
  expect(prompting(root)).toBe(false);
  expect(kept.getItem("mediagram.household")).toBe("flat");
});

test("a wrong PIN says so and stays", async () => {
  await device();
  const root = new Node();
  void chooseProfile(root);
  buttonNamed(root, "Flat").fire("click");
  refusal = { status: 403, body: { reason: "wrong-pin" } };
  await answerPin(root, "0000");
  expect(textOf(byClass(root, "pin-message"))).toBe("Wrong PIN.");
  expect(prompting(root)).toBe(true);
  expect(kept.getItem("mediagram.household")).toBeNull();
  expect(pills(root)).toEqual(["Flat", "Home"]);
});

test("joining shows that household's profiles", async () => {
  await device();
  const root = new Node();
  void chooseProfile(root);
  buttonNamed(root, "Flat").fire("click");
  await answerPin(root, "5678");
  await settle();
  expect(tiles(root)).toEqual(["MMaja", "TTimKids · FSK 6"]);
  expect(textOf(root)).not.toContain(HOUSEHOLD_WORDS.joinAHousehold);
});

test("switching away from the chosen profile's household lets go of it", async () => {
  await device({ "mediagram.household": "home" });
  await state.useProfile("andre");
  const root = new Node();
  let joined = 0;
  root.append(joinChoices(root, { except: "home", onJoined: () => joined++ }));
  expect(pills(root)).toEqual(["Flat"]);
  buttonNamed(root, "Flat").fire("click");
  await answerPin(root, "1357");
  await settle();
  expect(joined).toBe(1);
  expect(state.profileId()).toBeNull();
  expect(state.profiles().map((one) => one.name)).toEqual(["Maja", "Tim"]);
  expect(kept.getItem("mediagram.household")).toBe("flat");
  expect(kept.getItem("mediagram.profile")).toBeNull();
});

test("a household nobody could let this device in is not offered", async () => {
  // The owner has no PIN, and runs home: nobody has one to open it with. Maja has hers for the flat.
  everyone = SPLIT.map((one) => (one.id === "andre" ? { ...one, hasPin: false } : one));
  await device();
  const root = new Node();
  void chooseProfile(root);
  expect(pills(root)).toEqual(["Flat"]);
});

test("a device whose household has no grown-up left is offered the others, not a first profile", async () => {
  // Maja moved home with her Tim; only Lea, nobody's kid, is left in the flat.
  everyone = [{ ...ANDRE, owner: true, household: "home" }, { ...MAJA, household: "home" }, { ...TIM, household: "home" }, { ...LEA, household: "flat" }];
  await device({ "mediagram.household": "flat" });
  const root = new Node();
  void chooseProfile(root);
  expect(pills(root)).toEqual(["Home"]);
  expect(textOf(root)).not.toContain("Create the first profile");
  expect(tiles(root)).toEqual(["LLeaKids · FSK 12"]);
});

test("a join whose list cannot be read again asks again, not for a first profile", async () => {
  await device();
  const root = new Node();
  void chooseProfile(root);
  buttonNamed(root, "Flat").fire("click");
  readable = false;
  await answerPin(root, "5678");
  await settle();
  expect(textOf(root)).toContain(HOUSEHOLD_WORDS.joinAHousehold);
  expect(textOf(root)).not.toContain("Create the first profile");
  readable = true;
  await state.loadProfiles();
  expect(state.profiles().map((one) => one.name)).toEqual(["Maja", "Tim"]);
});

test("a join a full storage will not take still holds for this page", async () => {
  // Full: what was stored before can be read, and nothing new can be written.
  kept = { ...storage({ "mediagram.household": "home" }), setItem: () => { throw new Error("quota exceeded"); } };
  Object.assign(env.window, { localStorage: kept });
  await state.loadProfiles();
  const root = new Node();
  root.append(joinChoices(root, { onJoined: () => {} }));
  buttonNamed(root, "Flat").fire("click");
  await answerPin(root, "5678");
  await settle();
  expect(state.profiles().map((one) => one.name)).toEqual(["Maja", "Tim"]);
  await state.loadProfiles();
  expect(state.profiles().map((one) => one.name)).toEqual(["Maja", "Tim"]);
});

test("each household call goes to its route, the PIN in the body", async () => {
  await device({ "mediagram.household": "home" });
  const calls = [
    () => households.create("andre", "1357", "Oma", { adminName: "Oma", newPin: "2468" }),
    () => households.rename("andre", "1357", "flat", "Loft"),
    () => households.setAdmin("andre", "1357", "flat", "maja"),
    () => households.move("andre", "1357", "flat", "lea-mum", "4321"),
    () => households.remove("andre", "1357", "flat"),
  ];
  for (const call of calls) expect(await call()).toEqual({ ok: true });
  expect(writesOf(env.requests)).toEqual([
    ["POST", "/api/households", { actorId: "andre", pin: "1357", name: "Oma", adminName: "Oma", newPin: "2468" }],
    ["PUT", "/api/households/flat/name", { actorId: "andre", pin: "1357", name: "Loft" }],
    ["PUT", "/api/households/flat/admin", { actorId: "andre", pin: "1357", adminId: "maja" }],
    ["POST", "/api/households/flat/move", { actorId: "andre", pin: "1357", profileId: "lea-mum", newPin: "4321" }],
    ["DELETE", "/api/households/flat", { actorId: "andre", pin: "1357" }],
  ]);
});
