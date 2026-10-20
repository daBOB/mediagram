import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, type Node } from "./support/player-environment";
import { descendants } from "./support/browser-application";
import { closeRemote, openRemote, remoteOpen } from "../public/lib/cast/cast-remote.js";
import { CAST_WORDS } from "../public/lib/together/cast-words.js";
import { heardTogether, refreshTogether, screen, setTogetherOpener, togetherNow, type Live } from "../public/lib/together-now.js";
import { writeSession, type Session } from "../public/lib/together/session.js";
import { TogetherHub } from "../src/together/hub";
import { FakeChannel } from "./together-fixture";

let env: ReturnType<typeof browserEnvironment>;
let hub: TogetherHub;
const TV = { screen: "tv", label: "TV", remote: false };
const ME = { screen, label: "Browser", remote: true };
const cast = {
  to: "TV", from: "Browser", profile: "mia", state: "on", tv: "tv", note: "",
  tracks: { audio: [{ id: "a1", label: "English" }], text: [{ id: "de", label: "German" }] }, audio: "a1", text: "",
};
const session = (over: Partial<Session> = {}): Session => ({
  session: "s-1", set: "film-1", playing: true, pos: 100, rate: 1, at: 0, by: "tv", members: [TV, ME], ended: false, cast, ...over,
} as Session);
const live = (over: Partial<Session> = {}) => ({ id: 7, session: session(over) }) as unknown as Live;
const node = (id: string) => env.document.getElementById(id) as unknown as Node;
const posts = () => env.requests.filter((r) => r.options?.method === "POST").map((r) => JSON.parse(String(r.options!.body)));
const tick = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };
const items = () => descendants(node("cast-remote-menu")).filter((n) => n.tagName === "BUTTON");
/** The TV's edit, heard by the server and announced to this tab as its event stream would carry it. */
const heard = (s: Session) => hub.heard(7, writeSession(s));

async function opened(over: Partial<Session> = {}, next: string | null = null) {
  setTogetherOpener(() => {}, (id) => ({ setId: id, title: "Dune", duration: 7200, backdrop: "dune-b" }) as never, () => next);
  env.respondWith(async () => Response.json({ id: 7, session: session(over), serverNow: 0 }));
  openRemote(live(over));
  await tick();
}

beforeEach(() => {
  env = browserEnvironment();
  hub = new TogetherHub(new FakeChannel(), (id, session, lost) => heardTogether({ id, session, serverNow: Date.now(), lost }));
});
afterEach(() => { closeRemote(); env.restore(); });

test("opening joins as a remote", async () => {
  await opened();
  expect(posts()).toEqual([{ op: "join", screen, id: 7, remote: true }]);
  expect(node("cast-remote").open).toBe(true);
  expect(remoteOpen()).toBe(true);
  expect(node("cast-remote-label").textContent).toBe(CAST_WORDS.playingOnTv);
});

test("play/pause sends playing:false at the expected position", async () => {
  await opened();
  env.advance(10_000);
  node("cast-remote-play").fire("click");
  expect(posts().at(-1)).toEqual({ op: "act", screen, id: 7, change: { playing: false, pos: 110 } });
});

test("forward skips by the player's 15 s step", async () => {
  await opened();
  env.advance(10_000);
  node("cast-remote-forward").fire("click");
  expect(posts().at(-1)).toEqual({ op: "act", screen, id: 7, change: { pos: 125 } });
});

test("choosing German subtitles sends tracks text:de", async () => {
  await opened();
  expect(node("cast-remote-audio").hidden).toBe(true);
  node("cast-remote-cc").fire("click");
  expect(items().map((b) => b.textContent)).toEqual(["Off", "German"]);
  items()[1]!.fire("click");
  expect(posts().at(-1)).toEqual({ op: "tracks", screen, id: 7, text: "de" });
});

test("Stop sends end and closes", async () => {
  await opened();
  node("cast-remote-stop").fire("click");
  expect(posts().at(-1)).toEqual({ op: "end", screen, id: 7 });
  expect(node("cast-remote").open).toBe(false);
  expect(remoteOpen()).toBe(false);
});

test("a pause by the TV shows Paused on TV", async () => {
  await opened();
  heard(session({ playing: false, pos: 105, at: Date.now() }));
  expect(node("cast-remote-note").textContent).toBe("Paused on TV");
  env.advance(2000);
  expect(node("cast-remote-note").textContent).toBe("");
});

test("the TV leaving closes with Stopped on the TV", async () => {
  await opened();
  heard(session({ members: [ME], ended: true }));
  expect(node("cast-remote-note").textContent).toBe(CAST_WORDS.stopped);
  env.advance(2000);
  expect(node("cast-remote").open).toBe(false);
});

test("the title ending closes with Finished", async () => {
  await opened();
  heard(session({ ended: true, playing: false }));
  expect(node("cast-remote-note").textContent).toBe(CAST_WORDS.finished);
  env.advance(2000);
  expect(node("cast-remote").open).toBe(false);
});

test("Next episode is hidden without a next title", async () => {
  await opened();
  expect(node("cast-remote-next").hidden).toBe(true);
  closeRemote();
  await opened({}, "film-2");
  expect(node("cast-remote-next").hidden).toBe(false);
  node("cast-remote-next").fire("click");
  expect(posts().at(-1)).toEqual({ op: "act", screen, id: 7, change: { set: "film-2", pos: 0, playing: true } });
});

test("Esc closes the remote and leaves the session; reopening works", async () => {
  await opened();
  node("cast-remote").fire("cancel");
  expect(posts().at(-1)).toEqual({ op: "leave", screen, id: 7 });
  expect(node("cast-remote").open).toBe(false);
  expect(remoteOpen()).toBe(false);
  await opened();
  expect(remoteOpen()).toBe(true);
  expect(node("cast-remote").open).toBe(true);
});

test("the backdrop is drawn from its artwork key", async () => {
  await opened();
  expect((node("cast-remote-art") as unknown as { src: string }).src).toBe("/api/posters/dune-b.jpg");
  expect(node("cast-remote-title").textContent).toBe("Dune");
});

test("a move to another title redraws the title and Next", async () => {
  await opened();
  setTogetherOpener(() => {}, (id) => ({ setId: id, title: id === "film-2" ? "Dune Part Two" : "Dune", duration: 100 }) as never, () => null);
  heard(session({ set: "film-2", pos: 0, at: Date.now() }));
  expect(node("cast-remote-title").textContent).toBe("Dune Part Two");
  expect(node("cast-remote-next").hidden).toBe(true);
});

test("a read that no longer holds the session closes with Stopped", async () => {
  await opened();
  env.respondWith(async () => Response.json({ available: true, reason: null, live: null, serverNow: Date.now() }));
  await refreshTogether();
  expect(node("cast-remote-note").textContent).toBe(CAST_WORDS.stopped);
});

test("a read with a changed session applies it", async () => {
  await opened();
  env.respondWith(async () => Response.json({ available: true, reason: null, live: { id: 7, session: session({ playing: false, pos: 50, at: Date.now() }) }, serverNow: Date.now() }));
  await refreshTogether();
  expect(node("cast-remote-play").getAttribute("aria-label")).toBe("Play");
});

test("a move the server no longer has closes the remote and forgets the session", async () => {
  await opened();
  heard(session({ at: Date.now() }));
  env.respondWith(async () => new Response(null, { status: 404 }));
  node("cast-remote-play").fire("click");
  await tick();
  expect(togetherNow().live).toBeNull();
  expect(node("cast-remote-note").textContent).toBe(CAST_WORDS.stopped);
});

test("the seek bar is left alone while it is held", async () => {
  await opened();
  node("cast-remote-seek").value = "30";
  env.document.activeElement = node("cast-remote-seek");
  env.advance(1000);
  expect(node("cast-remote-seek").value).toBe("30");
});

test("a dialog closed natively still leaves the session, and reopening works", async () => {
  await opened();
  node("cast-remote").fire("close");
  expect(posts().at(-1)).toEqual({ op: "leave", screen, id: 7 });
  expect(remoteOpen()).toBe(false);
  await opened();
  expect(remoteOpen()).toBe(true);
});
