/** Settings' one Profiles tab: you, then who is here, then the management the picker opens too. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import { HOUSEHOLD_WORDS } from "../public/lib/household-words.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { ANDRE, AT_HOME, FLAT, LEA, MAJA, SPLIT, storage, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
/** The live households; left out, as the one-household tests leave them. */
let households: object[] | undefined;
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, { ...TIM, dailyLimit: 45 }, LEA];
  households = undefined;
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household, households });
    if (options?.method === "POST" && url === "/api/profiles") {
      const made = JSON.parse(String(options.body));
      household = [...household, { id: "ben", name: made.name, kids: true, kidsAge: made.kidsAge, parentId: made.actorId, admin: false, hasPin: false }];
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
  const stop = renderSettings(main, { profile, switchProfile: () => {}, systemVisible: false, admin: null });
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
  expect(sectionsOf(main)).toEqual(["Settings", "Who is watching", "Subtitles"]);
  expect(textOf(main)).toContain("Tim · Kids · FSK 6");
  expect(buttonNamed(main, "Switch profile")).toBeDefined();
});

test("a grown-up sees You, then Everyone here, then Manage profiles", () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", "Who is watching", "Subtitles", "Everyone here", "Manage profiles"]);
  expect(textOf(main)).toContain("Who are you?");
});

test("with nobody chosen there is no You, only Everyone here and Manage profiles", () => {
  const { main } = settings(null);
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", "Everyone here", "Manage profiles"]);
  expect(textOf(main)).not.toContain("Switch profile");
});

test("everyoneIsListedWithTheirRole", () => {
  const { main } = settings(find("andre"));
  openProfiles(main);
  const lines = descendants(main).filter((node) => node.classes.has("everyone-row")).map(textOf);
  expect(lines).toEqual([
    "andreAdmin · PIN set",
    "MajaGrown-up · PIN set",
    "TimKid · Maja · FSK 6 · 45 min",
    "LeaKid · andre · FSK 12",
  ]);
});

test("managingAsksWhoYouAreAndThePin, then a kid added appears in Everyone", async () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  expect(textOf(main)).toContain("Who are you?");
  buttonNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  const form = descendants(main).find((node) => node.tagName === "FORM" && textOf(node).includes("Add a kid"))!;
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "Ben";
  form.fire("submit");
  await settle();
  expect(writesOf(env.requests).at(-1)?.[2]).toMatchObject({ actorId: "maja", pin: "4321", name: "Ben" });
  expect(descendants(main).some((node) => node.classes.has("everyone-row") && textOf(node).startsWith("BenKid · Maja"))).toBe(true);
});

/** Every PIN a write carried, in order. */
const pinsSent = () => writesOf(env.requests).map((write) => (write[2] as { pin?: string }).pin).filter(Boolean);

/** Adds a kid named `name` through whatever controls are on the page. */
async function addKid(main: Node, name: string) {
  const form = descendants(main).find((node) => node.tagName === "FORM" && textOf(node).includes("Add a kid"))!;
  descendants(form).find((node) => node.tagName === "INPUT")!.value = name;
  form.fire("submit");
  await settle();
}

test("leavingTheTabDropsThePin", async () => {
  const { main } = settings(find("Maja"));
  openProfiles(main);
  buttonNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  expect(textOf(main)).toContain("As Maja");
  buttonNamed(main, "Appearance").fire("click");
  openProfiles(main);
  expect(textOf(main)).not.toContain("As Maja");
  expect(textOf(main)).toContain("Who are you?");
  buttonNamed(main, "Maja").fire("click");
  await answerPin(main, "9999");
  const after = writesOf(env.requests).length;
  await addKid(main, "Ben");
  expect(pinsSent().slice(after)).not.toContain("4321");
  expect(writesOf(env.requests).slice(after).at(-1)?.[2]).toMatchObject({ pin: "9999", name: "Ben" });
});

test("leavingThePageDropsThePin", async () => {
  const { main, stop } = settings(find("Maja"));
  openProfiles(main);
  buttonNamed(main, "Maja").fire("click");
  await answerPin(main, "4321");
  const form = descendants(main).find((node) => node.tagName === "FORM" && textOf(node).includes("Add a kid"))!;
  stop();
  const before = writesOf(env.requests).length;
  // The old controls are still in hand: a change driven from them sends nothing.
  descendants(form).find((node) => node.tagName === "INPUT")!.value = "Ben";
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
  expect(sectionsOf(alone)).not.toContain(HOUSEHOLD_WORDS.thisDevice);

  await inTheFlat();
  const { main } = settings(find("Maja"));
  openProfiles(main);
  expect(sectionsOf(main)).toEqual(["Settings", "Who is watching", "Subtitles", "Everyone here", "Manage profiles", HOUSEHOLD_WORDS.thisDevice]);
  const row = descendants(main).find((node) => node.className === "setting" && textOf(node).startsWith(HOUSEHOLD_WORDS.thisDevice))!;
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
