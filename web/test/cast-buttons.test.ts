import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants } from "./support/browser-application";
import { castPill } from "../public/lib/cast/play-on-tv.js";
import { mountPlayerCast } from "../public/lib/playback/player-cast.js";
import { CAST_WORDS } from "../public/lib/together/cast-words.js";
import { UNREACHABLE, refreshTogether, screen, setInTogether, setTogetherOpener } from "../public/lib/together-now.js";
import { closeRemote } from "../public/lib/cast/cast-remote.js";
import * as state from "../public/lib/watch-state.js";
import { here } from "../public/lib/household.js";

let env: ReturnType<typeof browserEnvironment>;
let available: boolean;
let answer: Record<string, unknown>;
const tv = { screen: "tv", label: "TV", remote: false };
const live = { id: 7, session: { session: "s-1", set: "film-1", playing: true, pos: 0, rate: 1, at: 0, by: "tv", members: [tv, { screen, label: "Browser", remote: true }], ended: false, cast: null } };
const posts = () => env.requests.filter((r) => r.options?.method === "POST").map((r) => JSON.parse(String(r.options!.body)));
const tick = async () => { for (let i = 0; i < 30; i++) await Promise.resolve(); };
const node = (id: string) => env.document.getElementById(id) as unknown as Node;

beforeEach(async () => {
  env = browserEnvironment();
  available = true;
  answer = { answer: "on", live, note: "", serverNow: 0 };
  await state.useProfile(null);
  here([], [], null, true);
  setTogetherOpener(() => {}, (id) => ({ setId: id, title: "Dune", duration: 7200 }) as never, () => null);
  env.respondWith(async (_url, options) => {
    if (!options?.body) return Response.json({ available, reason: available ? null : UNREACHABLE, live: null, serverNow: Date.now() });
    const body = JSON.parse(String(options.body));
    return body.op === "join" ? Response.json({ id: 7, session: live.session, serverNow: 0 }) : Response.json(answer);
  });
  await refreshTogether();
});
afterEach(() => { setInTogether(false); closeRemote(); env.restore(); });

function player() {
  const button = env.document.createElement("button") as unknown as Node & { disabled: boolean; title: string };
  const video = { paused: false, playbackRate: 1.5 };
  let closed = 0;
  mountPlayerCast({ button: button as never, video: video as never, filmTime: () => 321, setId: () => "film-1", close: () => { closed++; } });
  return { button, video, closed: () => closed };
}

test("the pill asks with the resume point", async () => {
  const pill = castPill("film-1", 90) as unknown as Node;
  expect(pill.textContent).toBe(CAST_WORDS.playOnTv);
  pill.fire("click");
  await tick();
  expect(posts()[0]).toEqual({ op: "offer", screen, profile: state.profileId(), household: "home", set: "film-1", playing: true, pos: 90, rate: 1 });
});

test("the pill is greyed with the reason while Telegram is unreachable", async () => {
  available = false;
  await refreshTogether();
  const pill = castPill("film-1", 0) as unknown as Node & { disabled: boolean; title: string };
  expect(pill.disabled).toBe(true);
  expect(pill.title).toBe(UNREACHABLE);
});

test("a device that has joined no household offers no TV: the pill is greyed with the reason", () => {
  // Two households and nothing chosen or stored: this device has not joined one yet.
  here([], [{ id: "h2", name: "Upstairs", adminId: null }] as never, null, true);
  const pill = castPill("film-1", 0) as unknown as Node & { disabled: boolean; title: string };
  expect(pill.disabled).toBe(true);
  expect(pill.title).toBe(CAST_WORDS.joinFirst);
});

test("the player's button is greyed while in watch together", () => {
  setInTogether(true);
  const { button } = player();
  expect(button.disabled).toBe(true);
  expect(button.title).toBe(CAST_WORDS.leaveFirst);
});

test("the player's button is enabled again once the viewer leaves watch together", () => {
  setInTogether(true);
  const { button } = player();
  expect(button.disabled).toBe(true);
  setInTogether(false);
  expect(button.disabled).toBe(false);
  expect(button.title).toBe("");
});

test("an accepted cast closes the player and opens the remote", async () => {
  const { button, closed } = player();
  button.fire("click");
  await tick();
  expect(posts()[0]).toEqual({ op: "offer", screen, profile: state.profileId(), household: "home", set: "film-1", playing: true, pos: 321, rate: 1.5 });
  expect(closed()).toBe(1);
  expect(node("cast-remote").open).toBe(true);
});

test("a refused cast leaves the player playing", async () => {
  answer = { answer: "refused", live: null, note: "it's rated above Mia's age limit", serverNow: 0 };
  const { button, closed } = player();
  button.fire("click");
  await tick();
  expect(closed()).toBe(0);
  expect(node("cast-remote").open).toBe(false);
  // The refusal waits on its OK; answer it so the ask does not stay open for the next test or file.
  descendants(node("cast-ask-actions")).find((n) => n.tagName === "BUTTON")!.fire("click");
  await tick();
  expect(node("cast-ask").open).toBe(false);
});
