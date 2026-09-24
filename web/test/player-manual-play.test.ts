import { afterEach, beforeEach, expect, test } from "bun:test";
import { browserEnvironment, deferred, settle } from "./support/player-environment";
import * as state from "../public/lib/watch-state.js";

let env: ReturnType<typeof browserEnvironment>;
let openPlayer: (set: ReturnType<typeof title>) => void;
let serial = 0;
const title = (id: string) => ({
  setId: id, title: id, kind: "movie", duration: 600,
  container: "mp4", vcodec: "h264", acodec: "aac",
});

beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
  const player = await import(`../public/lib/playback/player.js?manual=${++serial}`);
  player.initializePlayer();
  ({ openPlayer } = player);
});

afterEach(async () => {
  env.node("player").close();
  await settle();
  env.restore();
});

function pressPlay(control: "button" | "keyboard" = "button") {
  if (control === "button") env.node("play-pause").fire("click");
  else {
    const event = new Event("keydown", { cancelable: true });
    Object.assign(event, { key: " ", ctrlKey: false, altKey: false, metaKey: false });
    env.node("player").dispatchEvent(event);
  }
}

test.each(["button", "keyboard"] as const)("manual %s failure is visible and a successful retry clears it", async (control) => {
  openPlayer(title("retry"));
  const play = env.video.play.bind(env.video);
  const pending = deferred<void>();
  env.video.play = () => pending.promise;
  pressPlay(control);
  pending.reject(new Error("Decoder refused the private media URL"));
  await settle();

  expect(env.node("note").hidden).toBe(false);
  expect(env.node("note").textContent).toContain("Could not start playback");
  expect(env.node("note").textContent).toContain("Play");
  expect(env.node("note").textContent).not.toContain("private media URL");
  expect(env.node("play-pause").getAttribute("aria-label")).toBe("Play");
  env.video.play = play;
  pressPlay(control);
  await settle();
  expect(env.video.paused).toBe(false);
  expect(env.node("note").textContent).not.toContain("Could not start playback");
});

test.each(["other title", "same title", "closed player", "audio source"] as const)(
  "a rejected manual request cannot report into the %s that superseded it", async (replacement) => {
    env.respondWith(async (url) => {
      if (url.endsWith("/audio")) return Response.json({ tracks: [{ index: 0 }, { index: 1 }] });
      if (url.includes("/transcode?")) return Response.json({ playlist: "/hls/0000000000000001/index.m3u8" });
      return new Response(null, { status: 404 });
    });
    openPlayer(title("first"));
    await settle();
    const pending = deferred<void>();
    env.video.play = () => pending.promise;
    pressPlay();
    if (replacement === "closed player") env.node("player").close();
    else if (replacement === "audio source") {
      env.node("audio-track").value = "1";
      env.node("audio-track").fire("change");
    } else {
      openPlayer(title("second"));
      if (replacement === "same title") openPlayer(title("first"));
    }
    await settle();
    const message = env.node("note").textContent;
    pending.reject(new Error("obsolete decoder failure"));
    await settle();
    expect(env.node("note").textContent).toBe(message);
    expect(env.node("player").open).toBe(replacement !== "closed player");
  },
);

test("an interrupted manual play does not display a failure", async () => {
  openPlayer(title("interrupted"));
  const pending = deferred<void>();
  env.video.play = () => pending.promise;
  pressPlay();
  pending.reject(new DOMException("The play request was interrupted", "AbortError"));
  await settle();
  expect(env.node("note").textContent).toBe("");
});

test("an older manual failure cannot overwrite a newer successful request", async () => {
  openPlayer(title("retry"));
  const play = env.video.play.bind(env.video);
  const pending = deferred<void>();
  env.video.play = () => pending.promise;
  pressPlay();
  env.video.play = play;
  pressPlay();
  await settle();
  pending.reject(new Error("old request failed"));
  await settle();
  expect(env.video.paused).toBe(false);
  expect(env.node("note").textContent).toBe("");
});

test("an older manual success cannot erase a newer failure", async () => {
  openPlayer(title("retry"));
  const earlier = deferred<void>();
  const latest = deferred<void>();
  env.video.play = () => earlier.promise;
  pressPlay();
  env.video.play = () => latest.promise;
  pressPlay();
  latest.reject(new Error("latest request failed"));
  await settle();
  expect(env.node("note").textContent).toContain("Could not start playback");
  earlier.resolve();
  await settle();
  expect(env.node("note").textContent).toContain("Could not start playback");
});

test("pausing withdraws a pending manual play request", async () => {
  openPlayer(title("pause"));
  const pending = deferred<void>();
  env.video.play = () => {
    env.video.paused = false;
    return pending.promise;
  };
  pressPlay();
  pressPlay();
  pending.reject(new Error("the withdrawn request failed"));
  await settle();
  expect(env.video.paused).toBe(true);
  expect(env.node("note").textContent).toBe("");
});

test("successful retry restores the current conversion explanation", async () => {
  env.respondWith(async (url) => url.includes("/transcode?")
    ? Response.json({ playlist: "/hls/0000000000000001/index.m3u8" })
    : new Response(null, { status: 404 }));
  openPlayer({ ...title("converted"), container: "mkv" });
  await settle();
  const explanation = env.node("note").textContent;
  expect(explanation).not.toBe("");
  const play = env.video.play.bind(env.video);
  env.video.play = () => Promise.reject(new Error("temporary decode failure"));
  pressPlay();
  await settle();
  expect(env.node("note").textContent).toContain("Could not start playback");
  env.video.play = play;
  pressPlay();
  await settle();
  expect(env.node("note").textContent).toBe(explanation);
  expect(env.node("note").hidden).toBe(false);
});
