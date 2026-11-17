/** Settings' one Profiles tab: you, then the management the picker opens too. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { HOUSEHOLD_WORDS } from "../public/lib/household-words.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf, tileNamed } from "./support/browser-application";
import { ANDRE, AT_HOME, FLAT, LEA, MAJA, SPLIT, storage, TIM, writesOf } from "./support/household-fixture";
import { addSomeone, create, fieldOf, fill, open, optionsOf, prompting, rowOf } from "./support/households-part";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
/** The live households; left out, as the one-household tests leave them. */
let households: object[] | undefined;
/** What a household write that went through changes on the stub server. */
let written: () => void;
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, { ...TIM, dailyLimit: 45 }, LEA];
  households = undefined;
  written = () => {};
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household, households });
    if (url.startsWith("/api/households")) written();
    if (options?.method === "POST" && url === "/api/profiles") {
      const made = JSON.parse(String(options.body));
      household = [...household, { id: made.name.toLowerCase(), name: made.name, kids: made.kids, kidsAge: made.kidsAge ?? null,
        parentId: made.kids ? made.actorId : null, admin: false, hasPin: !made.kids, household: "home" }];
    }
    return new Response(null, { status: 204 });
  });
  await state.loadProfiles();
});
afterEach(() => env.restore());

const find = (name: string) => household.find((entry: any) => entry.name === name) as { name: string; kids?: boolean };
const tabsOf = (main: Node) => descendants(main).filter((node) => node.tagName === "BUTTON" && node.getAttribute("role") === "tab").map((node) => node.textContent);

function settings(profile: { name: string; kids?: boolean } | null) {
  const main = new Node();
  const stop = renderSettings(main, { profile, switchProfile: () => {}, admin: null });
  return { main, stop };
}
const openProfiles = (main: Node) => buttonNamed(main, "Profiles").fire("click");

test("there is one Profiles tab and no Profile tab, for a grown-up, a kid and nobody", () => {
  for (const profile of [find("Maja"), find("Tim"), null]) {
    expect(tabsOf(settings(profile).main)).toEqual(["Appearance", "Profiles"]);
  }
});

/** The page's section titles, top to bottom. */
const sectionsOf = (main: Node) => descendants(main).filter((node) => ["H1", "H2"].includes(node.tagName)).map(textOf);

test("a kid sees only You", () => {
  const { main } = settings(find("Tim"));
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", "You"]);
  expect(textOf(main)).toContain("TimKid · FSK 6");
  expect(buttonNamed(main, "Switch profile")).toBeDefined();
});

test("a grown-up sees You, then Who's managing?", () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", "You", HOUSEHOLD_WORDS.whosManaging]);
});

test("with nobody chosen there is no You, only Who's managing?", () => {
  const { main } = settings(null);
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", HOUSEHOLD_WORDS.whosManaging]);
  expect(textOf(main)).not.toContain("Switch profile");
});

/** Every profile's row in the owner's view, by its name and the line that says who it is. */
const ownersLines = (main: Node) => descendants(byClass(main, "manage-inline")).filter((node) => node.className === "manage-row")
  .map((row) => `${row.children[0]!.textContent}${textOf(row.children[1]!)}`);

test("everyoneIsListedWithTheirRole in the owner's view", async () => {
  household = [{ ...ANDRE, owner: true }, MAJA, { ...TIM, dailyLimit: 45 }, LEA];
  await state.loadProfiles();
  const { main } = settings(find("andre"));
  openProfiles(main);
  expect(ownersLines(main)).toEqual([
    `andre${HOUSEHOLD_WORDS.owner} · Admin · PIN set`,
    "MajaGrown-up · PIN set",
    "TimKid · Maja · FSK 6 · 45 min",
    "LeaKid · andre · FSK 12",
  ]);
});

test("the owner's line reads Owner · Admin, on the owner's own row in their view", async () => {
  household = [{ ...ANDRE, owner: true }, MAJA, TIM, LEA];
  await state.loadProfiles();
  const { main } = settings(find("andre"));
  openProfiles(main);
  expect(ownersLines(main).slice(0, 2)).toEqual([`andre${HOUSEHOLD_WORDS.owner} · Admin · PIN set`, "MajaGrown-up · PIN set"]);
});

test("the role line says which sections someone is not shown, in the nav's order — never for the owner", async () => {
  household = [
    { ...ANDRE, owner: true, hiddenSections: ["anime"] },
    { ...MAJA, hiddenSections: ["tutorials", "movies"] },
    { ...TIM, dailyLimit: 45, hiddenSections: ["tutorials", "anime"] },
    { ...LEA, hiddenSections: ["podcasts"] },
  ];
  await state.loadProfiles();
  const { main } = settings(find("andre"));
  openProfiles(main);
  expect(ownersLines(main)).toEqual([
    `andre${HOUSEHOLD_WORDS.owner} · Admin · PIN set`,
    "MajaGrown-up · PIN set · hides Movies, Tutorials",
    "TimKid · Maja · FSK 6 · 45 min · hides Anime, Tutorials",
    "LeaKid · andre · FSK 12",
  ]);
});

test("managingAsksWhoYouAreAndThePin, then a kid added appears in their list", async () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  expect(textOf(main)).toContain(HOUSEHOLD_WORDS.whosManaging);
  tileNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  const form = addSomeone(main);
  fieldOf(form, "Name").value = "Ben";
  form.fire("submit");
  await settle();
  expect(writesOf(env.requests).at(-1)?.[2]).toMatchObject({ actorId: "maja", pin: "4321", name: "Ben" });
  expect(descendants(main).some((node) => node.className === "manage-row" && node.children[0]?.textContent === "Ben")).toBe(true);
});

/** Every PIN a write carried, in order. */
const pinsSent = () => writesOf(env.requests).map((write) => (write[2] as { pin?: string }).pin).filter(Boolean);

/** Adds a kid named `name` through whatever controls are on the page. */
async function addKid(main: Node, name: string) {
  const form = addSomeone(main);
  fieldOf(form, "Name").value = name;
  form.fire("submit");
  await settle();
}

test("leavingTheTabDropsThePin", async () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  tileNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  // Past Who's managing?, and not told "As Maja": she is the one watching.
  expect(textOf(main)).not.toContain(HOUSEHOLD_WORDS.whosManaging);
  expect(textOf(main)).not.toContain("As Maja");
  buttonNamed(main, "Appearance").fire("click");
  openProfiles(main);
  expect(textOf(main)).not.toContain("As Maja");
  expect(textOf(main)).toContain(HOUSEHOLD_WORDS.whosManaging);
  tileNamed(main, "Maja").fire("click");
  await answerPin(main, "9999");
  const after = writesOf(env.requests).length;
  await addKid(main, "Ben");
  expect(pinsSent().slice(after)).not.toContain("4321");
  expect(writesOf(env.requests).slice(after).at(-1)?.[2]).toMatchObject({ pin: "9999", name: "Ben" });
});

test("leavingThePageDropsThePin", async () => {
  const { main, stop } = settings(find("Maja"));
  openProfiles(main);
  tileNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  const form = addSomeone(main);
  stop();
  const before = writesOf(env.requests).length;
  // The old controls are still in hand: a change driven from them sends nothing.
  fieldOf(form, "Name").value = "Ben";
  form.fire("submit");
  await settle();
  expect(writesOf(env.requests).length).toBe(before);
  expect(textOf(main)).not.toContain("As Maja");
});

/** A device in the flat, of the two households. */
async function inTheFlat() {
  household = SPLIT;
  households = [FLAT, AT_HOME];
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "flat" }) });
  await state.loadProfiles();
}

test("the Profiles page says this device's household and offers Switch only with two", async () => {
  const alone = settings(find("Maja")).main;
  openProfiles(alone);
  expect(byClass(alone, "device-line").hidden).toBe(true);

  await inTheFlat();
  const { main } = settings(find("Maja"));
  openProfiles(main);
  // This device is a line under Who's managing?, not a section of its own.
  expect(sectionsOf(main)).toEqual(["Settings", "You", HOUSEHOLD_WORDS.whosManaging]);
  const row = byClass(main, "device-line");
  expect(row.hidden).toBe(false);
  expect(textOf(row)).toStartWith(HOUSEHOLD_WORDS.thisDevice);
  expect(textOf(byClass(row, "setting-value"))).toBe("Flat");
  buttonNamed(row, HOUSEHOLD_WORDS.switchHousehold).fire("click");
  // Every other household, never the one this device is in.
  expect(descendants(byClass(row, "who-ask")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent)).toEqual(["Home"]);
});

test("a kid sees no household row", async () => {
  await inTheFlat();
  const { main } = settings(find("Tim"));
  openProfiles(main);
  expect(textOf(main)).not.toContain(HOUSEHOLD_WORDS.thisDevice);
  expect(textOf(main)).not.toContain(HOUSEHOLD_WORDS.switchHousehold);
});

test("a household change shows on the rest of the page: this device's household, and the owner's view", async () => {
  household = [{ ...ANDRE, owner: true, household: "home" }, { ...MAJA, household: "home" }, { ...TIM, household: "home" }, { ...LEA, household: "home" }];
  households = [AT_HOME];
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }) });
  await state.loadProfiles();
  const { main } = settings(find("andre"));
  openProfiles(main);
  expect(byClass(main, "device-line").hidden).toBe(true);

  // Maja runs the new flat, and her Tim goes with her.
  written = () => {
    household = household.map((one: any) => (["maja", "tim"].includes(one.id) ? { ...one, household: "flat", admin: one.id === "maja" } : one));
    households = [FLAT, AT_HOME];
  };
  await create(main, "Flat", { id: "maja" });
  await answerPin(main, "1357");
  expect(byClass(main, "device-line").hidden).toBe(false);
  // The owner's view draws the new household with those moved into it, and is still the owner's.
  const people = (name: string) => descendants(rowOf(main, name).parent!).filter((node) => node.className === "manage-row").map((row) => row.children[0]!.textContent);
  expect(people("Home")).toEqual(["andre", "Lea"]);
  expect(people("Flat")).toEqual(["Maja", "Tim"]);
  expect(textOf(main)).not.toContain(HOUSEHOLD_WORDS.whosManaging);
});

test("a grown-up added in the owner's view can be moved at once, and a rename keeps the owner's PIN", async () => {
  household = SPLIT;
  households = [FLAT, AT_HOME];
  Object.assign(env.window, { localStorage: storage({ "mediagram.household": "home" }) });
  await state.loadProfiles();
  const { main } = settings(find("andre"));
  openProfiles(main);
  const form = addSomeone(rowOf(main, "Home").parent!);
  fieldOf(form, "Name").value = "Opa";
  form.fire("submit");
  await settle();
  await answerPin(main, "1357");
  await answerPin(main, "2468", "2468");
  expect(textOf(rowOf(main, "Home").children[1]!)).toBe("Admin andre · 3 profiles");
  expect(optionsOf(fieldOf(open(main, "Flat", HOUSEHOLD_WORDS.moveHere), HOUSEHOLD_WORDS.moveHere))).toContain("Opa");

  // Renaming another household changes nobody here: the view keeps the owner's PIN.
  await fill(main, "Flat", HOUSEHOLD_WORDS.rename, "The flat");
  expect(prompting(main)).toBe(false);
  expect(writesOf(env.requests).at(-1)?.[1]).toBe("/api/households/flat/name");
  expect(textOf(main)).not.toContain(HOUSEHOLD_WORDS.whosManaging);
});
