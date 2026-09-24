import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, deferred, settle } from "./support/player-environment";
import * as state from "../public/lib/watch-state.js";

let env: ReturnType<typeof browserEnvironment>;
let openPlayer: (set: any, options?: any) => void;
let serial = 0;
const set = (id: string, convert = false) => ({
  setId: id,
  title: id,
  kind: "movie",
  duration: 600,
  container: convert ? "mkv" : "mp4",
  vcodec: "h264",
  acodec: "aac",
});
const playlist = (id: number) => `/hls/${String(id).padStart(16, "0")}/index.m3u8`;
const starts = () => env.requests.filter(({ url }) => url.includes("/transcode?"));
const releases = () => env.requests.filter(({ options }) => options?.method === "DELETE");
const seek = (at: number) => {
  env.video.ended = false;
  env.node("seek-to").value = String(at);
  env.node("seek-to").fire("change");
};

beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  const path = `../public/lib/playback/player.js?lifetime=${++serial}`;
  const player = await import(path);
  player.initializePlayer();
  ({ openPlayer } = player);
});

afterEach(async () => {
  env.node("player").close();
  await settle();
  env.restore();
});

test("close cancels pending startup and releases its late session without reattaching", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? response.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("closed", true));
  env.node("player").close();
  const note = env.node("note").textContent;
  response.resolve(Response.json({ playlist: playlist(1) }));
  await settle();
  expect(env.video.src).toBe("");
  expect(env.video.attachments).toEqual([]);
  expect(env.node("note").textContent).toBe(note);
  expect(releases().map(({ url }) => url)).toEqual(["/hls/0000000000000001"]);
});

test("a late converted title cannot replace the direct title opened after it", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? response.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("old", true));
  openPlayer(set("new"));
  response.resolve(Response.json({ playlist: playlist(2), copied: true }));
  await settle();
  expect(env.video.src).toBe("/api/sets/new/stream");
  expect(env.node("note").textContent).toBe("");
  expect(releases()).toHaveLength(1);
});

test("out-of-order repeated seeks attach only the latest source and release every older one", async () => {
  const replies = [deferred<Response>(), deferred<Response>(), deferred<Response>()];
  let requested = 0;
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? replies[requested++]!.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("seeks", true));
  seek(120);
  seek(240);
  expect(starts().map(({ url }) => new URL(url, "http://local").searchParams.get("seek"))).toEqual([
    "0",
    "120",
    "240",
  ]);
  replies[2]!.resolve(Response.json({ playlist: playlist(3) }));
  await settle();
  replies[0]!.resolve(Response.json({ playlist: playlist(1) }));
  replies[1]!.resolve(Response.json({ playlist: playlist(2) }));
  await settle();
  expect(env.video.attachments).toEqual([playlist(3)]);
  expect(env.video.src).toBe(playlist(3));
  expect(releases()).toHaveLength(2);
  env.node("player").close();
  expect(releases()).toHaveLength(3);
});

test("a rejected obsolete startup cannot overwrite the new title's message", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? response.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("failure", true));
  openPlayer(set("current"));
  response.reject(new Error("encoder unavailable"));
  await settle();
  expect(env.node("note").textContent).toBe("");
});

test("discovered default audio is applied at the current position and not mistaken for an applied choice", async () => {
  const audio = deferred<Response>();
  env.respondWith(async (url) => {
    if (url.endsWith("/audio")) return audio.promise;
    if (url.includes("/transcode?")) return Response.json({ playlist: playlist(4) });
    return new Response("", { status: 404 });
  });
  openPlayer(set("audio"));
  env.video.currentTime = 75;
  audio.resolve(
    Response.json({
      tracks: [
        { index: 0, lang: "en" },
        { index: 1, lang: "de", isDefault: true },
      ],
    }),
  );
  await settle();
  expect(starts()).toHaveLength(1);
  const url = new URL(starts()[0]!.url, "http://local");
  expect(url.searchParams.get("audio")).toBe("1");
  expect(url.searchParams.get("seek")).toBe("75");
  expect(env.node("audio-track").value).toBe("1");
  env.node("audio-track").fire("change");
  expect(starts()).toHaveLength(1);
  seek(180);
  expect(new URL(starts()[1]!.url, "http://local").searchParams.get("audio")).toBe("1");
});

test("failed audio selection can be retried without choosing a different language", async () => {
  let attempts = 0;
  env.respondWith(async (url) => {
    if (url.endsWith("/audio")) return Response.json({ tracks: [{ index: 0 }, { index: 1 }] });
    if (url.includes("/transcode?")) {
      return ++attempts === 1
        ? Response.json({ error: "try again" }, { status: 503 })
        : Response.json({ playlist: playlist(5) });
    }
    return new Response("", { status: 404 });
  });
  openPlayer(set("retry"));
  await settle();
  env.node("audio-track").value = "1";
  env.node("audio-track").fire("change");
  await settle();
  expect(env.node("note").textContent).toContain("try again");
  env.node("audio-track").fire("change");
  await settle();
  expect(starts()).toHaveLength(2);
  expect(env.video.src).toBe(playlist(5));
});

test("new title state is installed before transport controls read its runtime", () => {
  openPlayer({ ...set("unknown"), duration: null });
  expect(env.node("skip-forward").disabled).toBe(true);
  openPlayer(set("known"));
  expect(env.node("skip-forward").disabled).toBe(false);
});

test.each([100, 590])(
  "seeking to %i leaves the ended phase and cancels its countdown",
  async (at) => {
    let opened = 0;
    openPlayer(set(`end-${at}`), {
      next: set("next"),
      onOpenNext: () => {
        opened++;
      },
    });
    env.video.currentTime = 600;
    env.video.ended = true;
    env.video.fire("ended");
    expect(env.node("up-next-in").textContent).toBe("starting in 10…");
    env.advance(2000);
    seek(at);
    env.video.fire("timeupdate");
    expect(env.node("up-next").hidden).toBe(at === 100);
    if (at === 590) expect(env.node("up-next-in").textContent).toBe("when this ends");
    env.advance(15000);
    expect(opened).toBe(0);
  },
);

test("time updates after a natural end retain one countdown and open next once", () => {
  let opened = 0;
  openPlayer(set("natural-end"), {
    next: set("next"),
    onOpenNext: () => {
      opened++;
    },
  });
  env.video.currentTime = 600;
  env.video.ended = true;
  env.video.fire("ended");
  env.advance(3000);
  env.video.fire("timeupdate");
  env.advance(7000);
  expect(opened).toBe(1);
  env.advance(20000);
  expect(opened).toBe(1);
});

test("changing audio after the end cancels its countdown while the new source starts", async () => {
  let opened = 0;
  env.respondWith(async (url) => {
    if (url.endsWith("/audio")) return Response.json({ tracks: [{ index: 0 }, { index: 1 }] });
    if (url.includes("/transcode?")) return Response.json({ playlist: playlist(6) });
    return new Response("", { status: 404 });
  });
  openPlayer(set("end-audio"), {
    next: set("next"),
    onOpenNext: () => {
      opened++;
    },
  });
  await settle();
  env.video.currentTime = 600;
  env.video.ended = true;
  env.video.fire("ended");
  env.node("audio-track").value = "1";
  env.node("audio-track").fire("change");
  await settle();
  expect(env.node("up-next-in").textContent).toBe("when this ends");
  env.advance(15000);
  expect(opened).toBe(0);
});

test("the real buffer monitor falls back to HLS at the measured position and keeps its cap on seek", async () => {
  env.respondWith(async (url) =>
    url.includes("/transcode?")
      ? Response.json({ playlist: playlist(6) })
      : new Response("", { status: 404 }),
  );
  openPlayer({ ...set("slow-link"), total: (13_900_000 * 600) / 8 });
  env.video.readyState = 4;
  env.video.duration = 600;
  env.video.bufferEnd = 8;
  await env.video.play();
  let switchedAt = 0;
  for (let tick = 0; tick < 15 && starts().length === 0; tick++) {
    env.advance(1000);
    env.video.currentTime += Math.max(0, Math.min(1, env.video.bufferEnd - env.video.currentTime));
    env.video.bufferEnd += 0.4;
    switchedAt = env.video.currentTime;
    env.video.fire("progress");
  }
  await settle();
  expect(starts()).toHaveLength(1);
  const request = new URL(starts()[0]!.url, "http://local");
  expect(Number(request.searchParams.get("seek"))).toBe(Math.floor(switchedAt));
  expect(Number(request.searchParams.get("maxrate"))).toBeGreaterThan(0);
  expect(env.video.src).toBe(playlist(6));
  env.video.readyState = 3;
  env.advance(500);
  expect(env.video.paused).toBe(false);
  seek(300);
  expect(new URL(starts()[1]!.url, "http://local").searchParams.get("maxrate")).toBe(
    request.searchParams.get("maxrate"),
  );
  await settle();
});

test("remembered language follows stream ordinals between episodes and resumes playback", async () => {
  await state.useProfile("viewer");
  state.setPreference("show:Series", "audio", "de");
  let response = deferred<Response>();
  env.respondWith(async (url) => {
    if (url.endsWith("/audio")) return response.promise;
    if (url.includes("/transcode?")) return Response.json({ playlist: playlist(7) });
    return new Response("", { status: 404 });
  });
  openPlayer({ ...set("episode-one"), show: "Series" });
  env.video.currentTime = 42;
  await env.video.play();
  response.resolve(
    Response.json({
      tracks: [
        { index: 0, lang: "en", isDefault: true },
        { index: 1, lang: "de" },
      ],
    }),
  );
  await settle();
  expect(new URL(starts()[0]!.url, "http://local").searchParams.get("audio")).toBe("1");
  env.video.readyState = 3;
  env.advance(500);
  expect(env.video.paused).toBe(false);
  response = deferred<Response>();
  openPlayer({ ...set("episode-two"), show: "Series" });
  response.resolve(
    Response.json({
      tracks: [
        { index: 0, lang: "de", isDefault: true },
        { index: 1, lang: "en" },
      ],
    }),
  );
  await settle();
  expect(env.node("audio-track").value).toBe("0");
  expect(env.video.src).toBe("/api/sets/episode-two/stream");
  expect(starts()).toHaveLength(1);
});

test("the old resume metadata listener cannot seek the next title", () => {
  state.setProgress("resume-old", 140, 600);
  openPlayer(set("resume-old"));
  openPlayer(set("resume-new"));
  env.video.fire("loadedmetadata");
  expect(env.video.currentTime).toBe(0);
});

test("seeking before resume metadata arrives replaces the pending resume", () => {
  state.setProgress("resume-seek", 140, 600);
  openPlayer(set("resume-seek"));
  seek(250);
  env.video.fire("loadedmetadata");
  expect(env.video.currentTime).toBe(250);
});

test("audio discovery before metadata preserves the pending resume position", async () => {
  state.setProgress("resume-audio", 140, 600);
  env.respondWith(async (url) => {
    if (url.endsWith("/audio"))
      return Response.json({
        tracks: [{ index: 0 }, { index: 1, isDefault: true }],
      });
    if (url.includes("/transcode?")) return Response.json({ playlist: playlist(8) });
    return new Response("", { status: 404 });
  });
  openPlayer(set("resume-audio"));
  await settle();
  expect(new URL(starts()[0]!.url, "http://local").searchParams.get("seek")).toBe("140");
  env.video.fire("loadedmetadata");
  expect(env.video.currentTime).toBe(0);
});

test("close records the held position before source teardown resets the media clock", async () => {
  env.respondWith(async (url) =>
    url.includes("/transcode?")
      ? Response.json({ playlist: playlist(8) })
      : new Response("", { status: 404 }),
  );
  openPlayer(set("progress", true));
  await settle();
  env.video.currentTime = 90;
  await env.video.play();
  env.node("player").close();
  expect(state.progressOf("progress")?.at).toBe(90);
});

test("cancelled warm startup releases its late response and cannot become the current source", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? response.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("near-end"), { next: set("warm-next", true) });
  env.video.currentTime = 580;
  env.video.fire("timeupdate");
  env.video.fire("timeupdate");
  expect(starts()).toHaveLength(1);
  env.node("up-next-cancel").fire("click");
  response.resolve(Response.json({ playlist: playlist(9) }));
  await settle();
  expect(releases().map(({ url }) => url)).toContain("/hls/0000000000000009");
  expect(env.video.src).toBe("/api/sets/near-end/stream");
});

test("opening a warmed conversion joins it before releasing the warm watcher", async () => {
  const responses = [deferred<Response>(), deferred<Response>()];
  let request = 0;
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? responses[request++]!.promise : new Response("", { status: 404 }),
  );
  const next = set("warmed", true);
  openPlayer(set("warming"), { next });
  env.video.currentTime = 580;
  env.video.fire("timeupdate");
  responses[0]!.resolve(Response.json({ playlist: playlist(9) }));
  await settle();
  openPlayer(next);
  env.video.fire("timeupdate");
  expect(releases().filter(({ url }) => url.startsWith("/hls/"))).toHaveLength(0);
  responses[1]!.resolve(Response.json({ playlist: playlist(9) }));
  await settle();
  expect(env.video.src).toBe(playlist(9));
  expect(releases().filter(({ url }) => url.startsWith("/hls/"))).toHaveLength(1);
  env.node("player").close();
  expect(releases().filter(({ url }) => url.startsWith("/hls/"))).toHaveLength(2);
});

test("a stale thumbnail HEAD cannot replace the new title's preview", async () => {
  const old = deferred<Response>();
  env.respondWith(async (url) => {
    if (url.includes("/old-preview/thumbs")) return old.promise;
    if (url.endsWith("/thumbs.jpg")) return new Response();
    return new Response("", { status: 404 });
  });
  openPlayer(set("old-preview"));
  openPlayer(set("new-preview"));
  await settle();
  old.resolve(new Response());
  await settle();
  env.node("seek-to").dispatchEvent(Object.assign(new Event("pointermove"), { clientX: 100 }));
  const strip = env.node("seek").children.find((node) => node.className === "peek")!;
  expect(strip.hidden).toBe(false);
  expect(strip.children[0]!.style.backgroundImage).toBe('url("/api/sets/new-preview/thumbs.jpg")');
});

test("page exit cancels pending playback and warming even without a dialog close event", async () => {
  const replies = [deferred<Response>(), deferred<Response>(), deferred<Response>()];
  let requested = 0;
  env.respondWith(async (url) =>
    url.includes("/transcode?") ? replies[requested++]!.promise : new Response("", { status: 404 }),
  );
  openPlayer(set("leaving", true), {
    next: set("unwatched", true),
    autoplay: "asap",
  });
  seek(580); // Replaces the first startup and warms the following title.
  expect(starts()).toHaveLength(3);
  env.window.dispatchEvent(new Event("pagehide"));
  replies[0]!.resolve(Response.json({ playlist: playlist(1) }));
  replies[1]!.resolve(Response.json({ playlist: playlist(2) }));
  replies[2]!.resolve(Response.json({ playlist: playlist(3) }));
  await settle();
  expect(env.video.attachments).toEqual([]);
  expect(releases().filter(({ url }) => url.startsWith("/hls/"))).toHaveLength(3);
  env.video.readyState = 4;
  env.advance(1000);
  expect(env.video.paused).toBe(true);
});
