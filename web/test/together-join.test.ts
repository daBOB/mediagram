import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants, textOf } from "./support/browser-application";
import { joinOr, togetherNotice } from "../public/lib/catalog/together-join.js";
import { heardTogether, refreshTogether, setTogetherOpener, takeJoin } from "../public/lib/together-now.js";
import type { Session } from "../public/lib/together/session.js";

let env: ReturnType<typeof browserEnvironment>;
const opened: string[] = [];
const sets = new Map<string, object>([
  ["film-1", { setId: "film-1", title: "Film", kind: "movie", duration: 600 }],
  ["ep-2", { setId: "ep-2", title: "The Detail", kind: "ep", show: "The Wire", season: 1, episode: "2", duration: 3_600 }],
]);
const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: true, pos: 0, rate: 1, at: 0, by: "tv",
  members: [{ screen: "tv", label: "TV" }, { screen: "tab", label: "Tablet" }], ended: false, ...over,
});
const live = (over: Partial<Session> = {}) => heardTogether({ id: 7, session: session(over), serverNow: 0 });
const button = (root: Node) => descendants(root).find((node) => node.tagName === "BUTTON");

beforeEach(async () => {
  env = browserEnvironment();
  opened.length = 0;
  setTogetherOpener((set) => { opened.push(set.setId); }, (setId) => sets.get(setId) as never ?? null);
  env.respondWith(async () => Response.json({ available: true, reason: null, live: null, serverNow: 0 }));
  // The module is one per tab: every test starts it from a read that found nothing.
  await refreshTogether();
  takeJoin();
});

afterEach(() => env.restore());

test("Home's notice is hidden with no session, and names the title and who is watching when there is one", () => {
  const notice = togetherNotice();
  expect(notice.hidden).toBe(true);
  live();
  expect(notice.hidden).toBe(false);
  expect(textOf(notice)).toBe("Watching together: Film · TV, TabletJoin");
  button(notice)!.fire("click");
  expect(opened).toEqual(["film-1"]);
  expect(takeJoin()).toBe(7);
  heardTogether({ id: 7, session: null, serverNow: 0 });
  expect(notice.hidden).toBe(true);
});

test("an episode is named by its show, and a title this profile can't see is not offered", () => {
  live({ set: "ep-2" });
  const notice = togetherNotice();
  expect(textOf(notice)).toStartWith("Watching together: The Wire S1 E2 · TV, Tablet");
  live({ set: "grown-ups-only" });
  expect(notice.hidden).toBe(true);
});

test("a title page's pill becomes Join while a session plays that title, and comes back after", () => {
  const play = env.document.createElement("button") as unknown as Node;
  play.textContent = "Play";
  const slot = joinOr(["film-1"], play as never) as unknown as Node;
  expect(slot.children).toEqual([play]);
  live({ set: "ep-2" });
  expect(slot.children).toEqual([play]);
  live();
  expect(textOf(slot)).toBe("Join");
  button(slot)!.fire("click");
  expect(opened).toEqual(["film-1"]);
  expect(takeJoin()).toBe(7);
  heardTogether({ id: 7, session: null, serverNow: 0 });
  expect(slot.children).toEqual([play]);
});

test("a page with no pill of its own shows nothing until there is a session to join", () => {
  const slot = joinOr(["ep-2"], null) as unknown as Node;
  expect(slot.children).toEqual([]);
  expect(textOf(slot)).toBe("");
  live({ set: "ep-2" });
  expect(textOf(slot)).toBe("Join");
});

test("a notice or slot a redraw replaced stops following, though nothing was heard while it showed", () => {
  const firstSlot = joinOr(["film-1"], null) as unknown as Node;
  const firstNotice = togetherNotice();
  const slot = joinOr(["film-1"], null) as unknown as Node;
  const notice = togetherNotice();
  live();
  expect([textOf(firstSlot), firstNotice.hidden]).toEqual(["", true]);
  expect([textOf(slot), notice.hidden]).toEqual(["Join", false]);
});
