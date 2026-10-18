import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, type Node } from "./support/player-environment";
import { textOf } from "./support/browser-application";
import { mountCastBar } from "../public/lib/cast/cast-bar.js";
import { closeRemote, openRemote } from "../public/lib/cast/cast-remote.js";
import { togetherNotice } from "../public/lib/catalog/together-join.js";
import { barLine } from "../public/lib/together/cast-words.js";
import { heardTogether, refreshTogether, screen, setTogetherOpener, takeJoin } from "../public/lib/together-now.js";
import type { Session } from "../public/lib/together/session.js";

let env: ReturnType<typeof browserEnvironment>;
const cast = { to: "TV", from: "Browser", profile: "mia", state: "on", tv: "tv", note: "", tracks: { audio: [], text: [] }, audio: "", text: "" };
const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: true, pos: 0, rate: 1, at: 0, by: "tv",
  members: [{ screen: "tv", label: "TV", remote: false }], ended: false, cast, ...over,
} as Session);
const heard = (over: Partial<Session> = {}) => heardTogether({ id: 7, session: session(over), serverNow: 0 });
const node = (id: string) => env.document.getElementById(id) as unknown as Node;
const posts = () => env.requests.filter((r) => r.options?.method === "POST").map((r) => JSON.parse(String(r.options!.body)));
const tick = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };

beforeEach(async () => {
  env = browserEnvironment();
  setTogetherOpener(() => {}, (id) => (id === "film-1" ? ({ setId: id, title: "Dune", kind: "movie", duration: 7200 } as never) : null));
  env.respondWith(async () => Response.json({ available: true, reason: null, live: null, serverNow: 0 }));
  await refreshTogether();
  takeJoin();
  mountCastBar();
});
afterEach(() => { closeRemote(); env.restore(); });

test("a cast on the TV shows the bar with its title", () => {
  expect(node("cast-bar").hidden).toBe(true);
  heard();
  expect(node("cast-bar").hidden).toBe(false);
  expect(node("cast-bar-open").textContent).toBe(barLine("Dune"));
  expect(node("cast-bar-play").attributes.get("aria-label")).toBe("Pause");
});

test("an offer shows no bar", () => {
  heard({ cast: { ...cast, state: "offered" } } as never);
  expect(node("cast-bar").hidden).toBe(true);
});

test("a title hidden on this profile shows no bar", () => {
  heard({ set: "grown-ups-only" });
  expect(node("cast-bar").hidden).toBe(true);
});

test("Home shows no Watching together notice for a cast", () => {
  const notice = togetherNotice();
  heard();
  expect(notice.hidden).toBe(true);
  heard({ cast: null });
  expect(notice.hidden).toBe(false);
  expect(textOf(notice)).toStartWith("Watching together: Dune");
});

test("play/pause sends the opposite at the expected position", () => {
  heard();
  node("cast-bar-play").fire("click");
  expect(posts().at(-1)).toEqual({ op: "act", screen, id: 7, change: { playing: false, pos: 0 } });
});

test("the bar steps aside while the remote is open and returns when it closes", async () => {
  heard();
  node("cast-bar-open").fire("click");
  await tick();
  expect(node("cast-remote").open).toBe(true);
  expect(node("cast-bar").hidden).toBe(true);
  closeRemote();
  expect(node("cast-bar").hidden).toBe(false);
});
