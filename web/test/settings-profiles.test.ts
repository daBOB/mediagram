/** Settings' Profiles tab: who is here, and the same management as the picker's, inline. */

import { afterEach, beforeEach, expect, test } from "bun:test";
import { renderSettings } from "../public/lib/catalog/settings-page.js";
import * as state from "../public/lib/watch-state.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { ANDRE, LEA, MAJA, TIM, writesOf } from "./support/household-fixture";
import { browserEnvironment, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
let household: object[];
beforeEach(async () => {
  env = browserEnvironment();
  household = [ANDRE, MAJA, { ...TIM, dailyLimit: 45 }, LEA];
  env.respondWith(async (url, options) => {
    if (url === "/api/profiles" && !options?.method) return Response.json({ remembers: true, profiles: household });
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

test("aGrownUpSeesAProfilesTab", () => {
  expect(tabsOf(settings(find("Maja")).main)).toEqual(["Appearance", "Profile", "Profiles"]);
});

test("aKidAndNobodySeeNoProfilesTab", () => {
  expect(tabsOf(settings(find("Tim")).main)).toEqual(["Appearance", "Profile"]);
  expect(tabsOf(settings(null).main)).toEqual(["Appearance", "Profile"]);
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
