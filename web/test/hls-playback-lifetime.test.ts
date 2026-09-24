import { afterEach, beforeEach, expect, mock, test } from "bun:test";
import { browserEnvironment, deferred, settle } from "./support/player-environment";

// The library is the IO boundary. The session request, ownership and cleanup
// below all run through the production adapter.
const library = deferred<void>();
let libraryLoad = Promise.resolve();
class Hls {
  static Events = { ERROR: "error" };
  static supported = true;
  static failure: string | null = null;
  static instances: Hls[] = [];
  static isSupported() {
    return Hls.supported;
  }
  destroyed = 0;
  attached = false;
  error: (_event: string, data: { fatal: boolean; details?: string }) => void = () => {};
  constructor(_options: unknown) {
    if (Hls.failure === "construct") throw new Error("construct failed");
    Hls.instances.push(this);
  }
  on(_event: string, callback: Hls["error"]) {
    this.error = callback;
  }
  loadSource(_url: string) {
    if (Hls.failure === "source") throw new Error("source failed");
  }
  attachMedia(_video: unknown) {
    this.attached = true;
    if (Hls.failure === "attach") throw new Error("attach failed");
  }
  destroy() {
    this.destroyed++;
  }
}
mock.module("/lib/hls.mjs", async () => {
  await libraryLoad;
  return { default: Hls };
});

let env: ReturnType<typeof browserEnvironment>;
let adapter: typeof import("../public/lib/playback/hls-playback.js");
let serial = 0;
const playlist = "/hls/0123456789abcdef/index.m3u8";
const released = () => env.requests.filter(({ options }) => options?.method === "DELETE");
const play = (options: any = {}) => adapter.playTranscoded(env.video, "title", options);
beforeEach(async () => {
  env = browserEnvironment();
  env.replace("MediaSource", {});
  env.respondWith(async () => Response.json({ playlist }));
  Hls.instances = [];
  Hls.supported = true;
  Hls.failure = null;
  const path = `../public/lib/playback/hls-playback.js?lifetime=${++serial}`;
  adapter = await import(path);
});
afterEach(() => env.restore());

test("abort during the library load releases immediately and never attaches when it arrives", async () => {
  libraryLoad = library.promise;
  const controller = new AbortController();
  let callbacks = 0;
  const playing = play({
    signal: controller.signal,
    onStarted: () => {
      callbacks++;
    },
  });
  const outcome = playing.catch((error) => error);
  await settle();
  controller.abort();
  const releasedBeforeLibrary = released().length;
  library.resolve();
  const result = await outcome;
  expect(releasedBeforeLibrary).toBe(1);
  expect(result.name).toBe("AbortError");
  expect(Hls.instances).toHaveLength(0);
  expect(callbacks).toBe(0);
});

test.each(["unsupported", "construct", "source", "attach"])(
  "%s startup failure releases the acquired session",
  async (failure) => {
    Hls.supported = failure !== "unsupported";
    Hls.failure = failure;
    await expect(play()).rejects.toThrow();
    expect(released()).toHaveLength(1);
    for (const hls of Hls.instances) expect(hls.destroyed).toBe(1);
    expect(env.timers.size).toBe(0);
  },
);

test("fatal HLS errors release once, stop keepalive and suppress later callbacks", async () => {
  let failures = 0;
  const release = await play({
    onFatal: () => {
      failures++;
    },
  });
  const hls = Hls.instances[0]!;
  hls.error("error", { fatal: false });
  expect(failures).toBe(0);
  hls.error("error", { fatal: true, details: "encoder stopped" });
  hls.error("error", { fatal: true });
  release();
  release();
  expect(failures).toBe(1);
  expect(hls.destroyed).toBe(1);
  expect(released()).toHaveLength(1);
  env.advance(120000);
  expect(env.requests.filter(({ url }) => url === playlist)).toHaveLength(0);
});

test("abort while the server is starting keeps only the response needed to release its session", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) => (url.includes("/transcode?") ? response.promise : new Response()));
  const controller = new AbortController();
  let started = 0;
  const pending = play({
    signal: controller.signal,
    onStarted: () => {
      started++;
    },
  }).catch((error) => error);
  controller.abort();
  env.video.src = "/another-title.mp4";
  response.resolve(Response.json({ playlist }));
  expect((await pending).name).toBe("AbortError");
  expect(started).toBe(0);
  expect(env.video.src).toBe("/another-title.mp4");
  expect(Hls.instances).toHaveLength(0);
  expect(released()).toHaveLength(1);
  expect(env.requests[0]!.options?.signal).toBeUndefined();
});

test("aborted before start sends no request", async () => {
  const controller = new AbortController();
  controller.abort();
  await expect(play({ signal: controller.signal })).rejects.toHaveProperty("name", "AbortError");
  expect(env.requests).toHaveLength(0);
});

test("native cleanup is idempotent and cannot clear a source attached after release", async () => {
  env.replace("MediaSource", undefined);
  const controller = new AbortController();
  const release = await play({ signal: controller.signal });
  expect(env.video.src).toBe(playlist);
  controller.abort();
  env.video.src = "/next.mp4";
  release();
  release();
  expect(env.video.src).toBe("/next.mp4");
  expect(released()).toHaveLength(1);
});

test("warming can be cancelled before the reply and releases its late session", async () => {
  const response = deferred<Response>();
  env.respondWith(async (url) => (url.includes("/transcode?") ? response.promise : new Response()));
  const controller = new AbortController();
  const warming = adapter
    .warmTranscode("next", { signal: controller.signal })
    .catch((error) => error);
  controller.abort();
  response.resolve(Response.json({ playlist }));
  expect((await warming).name).toBe("AbortError");
  expect(released()).toHaveLength(1);
  expect(env.timers.size).toBe(0);
});

test("warm keepalive stops on release and never releases twice", async () => {
  const release = await adapter.warmTranscode("next");
  env.advance(60000);
  expect(env.requests.filter(({ url }) => url === playlist)).toHaveLength(1);
  release();
  release();
  env.advance(120000);
  expect(env.requests.filter(({ url }) => url === playlist)).toHaveLength(1);
  expect(released()).toHaveLength(1);
});

test.each(["abort", "throw"])(
  "%s from the started callback still releases the attached media",
  async (action) => {
    const controller = new AbortController();
    await expect(
      play({
        signal: controller.signal,
        onStarted: () => {
          if (action === "abort") controller.abort();
          else throw new Error("callback failed");
        },
      }),
    ).rejects.toThrow();
    expect(Hls.instances[0]!.destroyed).toBe(1);
    expect(released()).toHaveLength(1);
    expect(env.timers.size).toBe(0);
  },
);
