import { afterAll, afterEach, beforeAll, beforeEach, expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { staticResponse } from "../src/http/static-files";
import { htmlApplicationEnvironment } from "./support/html-application";
import { descendants } from "./support/browser-application";
import { settle, TrackElement } from "./support/player-environment";

let directory: string;
let html: string;
let serial = 0;
let env: Awaited<ReturnType<typeof htmlApplicationEnvironment>>;
let app: { state: typeof import("../public/lib/watch-state.js"); player: typeof import("../public/lib/playback/player.js") };
const episode = (id: string, subtitles = ["en", "de"], converted = false) => ({
  setId: id, title: id, kind: "ep", show: "Series", season: 1, episode: "1", duration: 600,
  addedAt: 1, total: 1000, container: converted ? "mkv" : "mp4", vcodec: "h264", acodec: "aac", subtitles,
  chap: null, path: null, year: null, partCount: 1,
});
const tracks = () => env.video.querySelectorAll("track").filter((node): node is TrackElement => node instanceof TrackElement);
const selected = () => [...env.video.textTracks].filter((track) => track.mode === "showing").map((track) => track.language);
const pick = (value: string) => { env.node("sub-track").value = value; env.node("sub-track").fire("change"); };
const cue = () => ({ startTime: 10, endTime: 12 });

beforeAll(async () => {
  const response = staticResponse({ method: "GET", path: "/", range: null });
  expect(response.status).toBe(200);
  expect(response.headers["content-type"]).toContain("text/html");
  if (!(response.body instanceof Uint8Array)) throw new Error("Missing served HTML document");
  html = new TextDecoder().decode(response.body);
  directory = await mkdtemp(join(tmpdir(), "mediagram-html-player-"));
  const entry = join(directory, "entry.js");
  await writeFile(entry, `import ${JSON.stringify(join(import.meta.dir, "../public/app.js"))};
export * as state from ${JSON.stringify(join(import.meta.dir, "../public/lib/watch-state.js"))};
export * as player from ${JSON.stringify(join(import.meta.dir, "../public/lib/playback/player.js"))};`);
  const result = await Bun.build({ entrypoints: [entry], outdir: directory, naming: "application.js", target: "browser", external: ["/lib/hls.mjs"] });
  if (!result.success) throw new AggregateError(result.logs, "Building real HTML application fixture");
});
afterAll(() => rm(directory, { recursive: true, force: true }));
beforeEach(async () => { env = await htmlApplicationEnvironment(html); });
afterEach(async () => {
  env.document.getElementById("player")?.close();
  await env.visibility("hidden");
  await settle();
  env.restore();
});

async function start() {
  env.respondWith(async (url, options) => {
    if (url === "/api/sets") return Response.json([{ ...episode("Film"), kind: "movie", show: null }]);
    if (url === "/api/player") return Response.json({ remote: false });
    if (url === "/api/profiles") return Response.json({ remembers: true, profiles: [{ id: "viewer", name: "Viewer" }] });
    if (url.endsWith("/state")) return Response.json({});
    if (url === "/api/kids") return Response.json({ kids: [] });
    if (url.includes("/transcode?")) return Response.json({ playlist: "/hls/0000000000000001/index.m3u8" });
    if (options?.method && options.method !== "HEAD") return new Response(null, { status: 204 });
    return new Response(null, { status: 404 });
  });
  app = await import(`${join(directory, "application.js")}?case=${++serial}`);
}

test("shipped HTML mounts the actual application and its player controls respond", async () => {
  expect(env.document.getElementById("not-in-the-page")).toBeNull();
  expect(env.document.querySelector(".not-in-the-page")).toBeNull();
  expect(env.document.querySelector("script")?.getAttribute("src")).toBe("/app.js");
  await start();
  await env.navigate("#/film/Film");
  descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
  await settle();
  expect(env.node("player").tagName).toBe("DIALOG");
  expect(env.node("video").tagName).toBe("VIDEO");
  expect(env.node("player").contains(env.node("video"))).toBe(true);
  expect(env.node("player").open).toBe(true);
  expect(env.video.src).toBe("/api/sets/Film/stream");
  expect(env.node("sub-track").tagName).toBe("SELECT");
  expect(env.document.querySelector(".hud-bottom")?.contains(env.node("cue-settings"))).toBe(true);
  env.node("play-pause").fire("click");
  await settle();
  expect(env.video.paused).toBe(false);
  expect(env.node("play-pause").getAttribute("aria-label")).toBe("Pause");
  env.node("skip-forward").fire("click");
  expect(env.video.currentTime).toBe(10);
  env.node("mute").fire("click");
  expect(env.video.muted).toBe(true);
  env.node("cue-settings").fire("click");
  expect(env.node("cue-settings").getAttribute("aria-expanded")).toBe("true");
  env.node("close").fire("click");
  expect(env.node("player").open).toBe(false);
  expect(env.video.src).toBe("");
});

test.each(["play-pause", "sub-track", "close"])("renaming required HTML control %s makes actual controller initialization fail", async (id) => {
  env.restore();
  env = await htmlApplicationEnvironment(html.replace(`id="${id}"`, `id="renamed-${id}"`));
  expect(env.document.getElementById(id)).toBeNull();
  await expect(start()).rejects.toThrow();
});

test("subtitle language and explicit Off survive episode track order changes", async () => {
  await start();
  app.player.openPlayer(episode("one"));
  expect(selected()).toEqual(["en"]);
  pick("1");
  expect(app.state.preferenceOf("show:Series", "subtitle")).toBe("de");
  const previous = tracks();
  app.player.openPlayer(episode("two", ["fr", "en", "de"]));
  expect(selected()).toEqual(["de"]);
  expect(env.node("sub-track").value).toBe("2");
  expect(previous.every((track) => track.parent === null)).toBe(true);
  expect(tracks().every((track) => !track.default)).toBe(true);
  pick("off");
  app.player.openPlayer(episode("three"));
  expect(env.node("sub-track").value).toBe("off");
  expect(selected()).toEqual([]);
  expect(app.state.preferenceOf("show:Series", "subtitle")).toBe("off");
});

test("subtitle shortcut restores the selected track and resets its ordinal for a new title", async () => {
  await start();
  const toggle = () => env.node("player").dispatchEvent(Object.assign(new Event("keydown"), { key: "c" }));
  app.player.openPlayer(episode("one", ["en", "fr", "de"]));
  pick("2");
  toggle();
  expect(selected()).toEqual([]);
  toggle();
  expect(selected()).toEqual(["de"]);
  expect(env.node("sub-track").value).toBe("2");
  pick("off");
  toggle();
  expect(selected()).toEqual(["de"]);
  // The remembered Off preference applies to the next episode. Its first
  // track is the fallback when the shortcut enables subtitles again.
  app.player.openPlayer(episode("two", ["fr", "en"]));
  expect(selected()).toEqual([]);
  toggle();
  expect(selected()).toEqual(["fr"]);
  toggle();
  expect(selected()).toEqual([]);
  toggle();
  expect(selected()).toEqual(["fr"]);
});

test("late cue load and TextTrackList change apply remembered offsets without drift", async () => {
  await start();
  app.state.setPreference("show:Series", "cue-offset", "0.4");
  app.player.openPlayer(episode("late-cues"));
  const english = tracks()[0]!;
  const first = cue();
  english.loadCues([first]);
  expect(first).toEqual({ startTime: 10.4, endTime: 12.4 });
  const german = tracks()[1]!;
  const second = cue();
  german.track.cues = [second];
  pick("1"); // Eventful mode change, independently of track load.
  expect(second).toEqual({ startTime: 10.4, endTime: 12.4 });
  env.video.textTracks.fire("change");
  english.fire("load");
  expect(first).toEqual({ startTime: 10.4, endTime: 12.4 });
  const later = descendants(env.node("player")).find((node) => node.getAttribute("aria-label") === "Subtitles later")!;
  later.fire("click");
  expect(second).toEqual({ startTime: 10.5, endTime: 12.5 });
  expect(app.state.preferenceOf("show:Series", "cue-offset")).toBe("0.5");
  descendants(env.node("player")).find((node) => node.textContent === "Reset")!.fire("click");
  expect(first).toEqual(cue());
  expect(second).toEqual(cue());
  expect(app.state.preferenceOf("show:Series", "cue-offset")).toBe("0");
});

test("conversion seeks retain subtitle choice and replaced episode tracks receive its offset", async () => {
  await start();
  app.state.setPreference("show:Series", "subtitle", "de");
  app.state.setPreference("show:Series", "cue-offset", "-0.5");
  app.player.openPlayer(episode("converted", ["en", "de"], true));
  await settle();
  const beforeSeek = tracks();
  beforeSeek[1]!.loadCues([cue()]);
  env.node("seek-to").value = "90";
  env.node("seek-to").fire("change");
  await settle();
  expect(env.requests.filter(({ url }) => url.includes("/transcode?")).map(({ url }) => new URL(url, "http://local").searchParams.get("seek"))).toEqual(["0", "90"]);
  expect(selected()).toEqual(["de"]);
  expect(tracks()).toEqual(beforeSeek); // <track> children survive replacing the media source.
  const reloaded = cue();
  beforeSeek[1]!.loadCues([reloaded]);
  expect(reloaded).toEqual({ startTime: 9.5, endTime: 11.5 });
  env.node("cue-settings").fire("click");
  expect(env.node("cue-settings").getAttribute("aria-expanded")).toBe("true");
  app.player.openPlayer(episode("next-converted", ["de", "en"], true));
  await settle();
  expect(beforeSeek.every((track) => track.parent === null)).toBe(true);
  expect(selected()).toEqual(["de"]);
  const following = cue();
  tracks()[0]!.loadCues([following]);
  expect(following).toEqual({ startTime: 9.5, endTime: 11.5 });
  expect(env.node("cue-settings").getAttribute("aria-expanded")).toBe("false");
  env.node("close").fire("click");
  expect(env.video.textTracks).toHaveLength(0);
});
