import { afterEach, beforeEach, expect, test } from "bun:test";
import { chooseProfile } from "../public/lib/profile-picker.js";
import { listControls, listsView } from "../public/lib/catalog/collections-view.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, deferred, Node, settle } from "./support/player-environment.js";

let env: ReturnType<typeof browserEnvironment>;
let alerts: string[];
let answer: string;

function button(root: Node, label: string): Node {
  const found = root.children.find((child) => child.tagName === "BUTTON" && child.textContent === label);
  if (found) return found;
  for (const child of root.children) {
    try { return button(child, label); } catch { /* Search remaining branches. */ }
  }
  throw new Error(`Missing button: ${label}`);
}

function byClass(root: Node, className: string): Node {
  if (root.className.split(" ").includes(className)) return root;
  for (const child of root.children) {
    try { return byClass(child, className); } catch { /* Search remaining branches. */ }
  }
  throw new Error(`Missing class: ${className}`);
}

beforeEach(async () => {
  env = browserEnvironment();
  alerts = [];
  answer = "New name";
  Object.assign(env.window, {
    prompt: () => answer,
    confirm: () => true,
    alert: (message: string) => alerts.push(message),
  });
  env.respondWith(async (url) => Response.json(url === "/api/profiles"
    ? { remembers: true, profiles: [{ id: "alice", name: "Alice" }] }
    : { collections: [{ id: "list", name: "Old list", items: [] }] }));
  await state.loadProfiles();
  await state.useProfile("alice");
});

afterEach(async () => {
  await state.useProfile(null);
  env.restore();
});

test("a rejected new profile remains retryable and reports the failure", async () => {
  const root = new Node();
  void chooseProfile(root, { canCancel: true });
  env.respondWith(async () => new Response(null, { status: 503 }));
  byClass(root, "who-add").fire("click");
  await settle();
  expect(alerts).toEqual(["Could not create the profile. Please try again."]);
  expect(state.profiles().map((profile) => profile.name)).toEqual(["Alice"]);

  env.respondWith(async () => Response.json({ id: "new", name: answer }));
  byClass(root, "who-add").fire("click");
  await settle();
  expect(state.profiles().map((profile) => profile.name)).toEqual(["Alice", "New name"]);
  expect(alerts).toHaveLength(1);
});

test("profile deletion failure keeps the chooser and the profile", async () => {
  const root = new Node();
  void chooseProfile(root, { canCancel: true });
  answer = "Alice";
  env.respondWith(async () => { throw new Error("offline"); });
  button(root, "Rename or remove…").fire("click");
  await settle();
  expect(alerts).toEqual(["Could not remove the profile. Please try again."]);
  expect(state.profiles()).toHaveLength(1);
  expect(root.children).toHaveLength(1);

  env.respondWith(async () => new Response(null, { status: 204 }));
  button(root, "Rename or remove…").fire("click");
  await settle();
  expect(state.profiles()).toHaveLength(0);
  expect(alerts).toHaveLength(1);
});

test("an unreadable list creation reply reports failure and leaves the shelf usable", async () => {
  const root = listsView(() => {});
  env.respondWith(async () => new Response("not json"));
  button(root, "＋  New list").fire("click");
  await settle();
  expect(alerts).toEqual(["Could not create the list. Please try again."]);
  expect(state.collections()).toHaveLength(1);

  env.respondWith(async () => Response.json({ id: "new", name: answer, items: [] }));
  button(root, "＋  New list").fire("click");
  await settle();
  expect(state.collections()).toHaveLength(2);
  expect(alerts).toHaveLength(1);
});

test("a failed rename keeps the old list name and permits another attempt", async () => {
  const list = state.collections()[0];
  if (!list) throw new Error("Fixture collection missing");
  const root = listControls(list, () => {}, () => {}, null);
  env.respondWith(async () => new Response(null, { status: 503 }));
  button(root, "Rename").fire("click");
  await settle();
  expect(alerts).toEqual(["Could not rename the list. Please try again."]);
  expect(list.name).toBe("Old list");

  env.respondWith(async () => new Response(null, { status: 204 }));
  button(root, "Rename").fire("click");
  await settle();
  expect(list.name).toBe("New name");
  expect(alerts).toHaveLength(1);
});

test("a failed list deletion retains the list and navigates only after acknowledgement", async () => {
  let departures = 0;
  const root = listControls(state.collections()[0], () => {}, () => departures++, null);
  env.respondWith(async () => { throw new Error("offline"); });
  button(root, "Delete list").fire("click");
  await settle();
  expect(alerts).toEqual(["Could not delete the list. Please try again."]);
  expect(state.collections()).toHaveLength(1);
  expect(departures).toBe(0);

  env.respondWith(async () => new Response(null, { status: 204 }));
  button(root, "Delete list").fire("click");
  await settle();
  expect(state.collections()).toHaveLength(0);
  expect(departures).toBe(1);
  expect(alerts).toHaveLength(1);
});


test("profile discovery retry admits one request and redraws recovered management controls", async () => {
  const root = new Node();
  const pending = deferred<Response>();
  let reads = 0;
  env.respondWith(() => { reads++; return pending.promise; });
  void chooseProfile(root, { canCancel: true, discoveryFailed: true });
  const retry = button(root, "Retry profiles");
  retry.fire("click");
  retry.fire("click");
  expect(reads).toBe(1);
  expect(retry.disabled).toBe(true);
  pending.resolve(Response.json({ remembers: true, profiles: [{ id: "alice", name: "Alice", createdAt: 1 }] }));
  await settle();
  expect(button(root, "Rename or remove…")).toBeDefined();
  expect(byClass(root, "who-note").textContent).toContain("Profiles keep your places");
  expect(state.profileId()).toBe("alice");
  button(root, "Stay as I am").fire("click");
});

test("canceling a profile discovery retry leaves the selected profile and dismissed view alone", async () => {
  const root = new Node();
  const pending = deferred<Response>();
  env.respondWith(() => pending.promise);
  const chosen = chooseProfile(root, { canCancel: true, discoveryFailed: true });
  const screen = root.children[0]!;
  button(root, "Retry profiles").fire("click");
  button(root, "Stay as I am").fire("click");
  expect(await chosen).toBe("alice");
  expect(root.children).toHaveLength(0);
  pending.resolve(Response.json({ remembers: true, profiles: [] }));
  await settle();
  expect(state.profileId()).toBe("alice");
  expect(root.children).toHaveLength(0);
  expect(button(screen, "Retry profiles")).toBeDefined();
});
