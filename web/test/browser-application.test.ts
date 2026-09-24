import { afterAll, afterEach, beforeAll, beforeEach, expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { applicationEnvironment, descendants, textOf } from "./support/browser-application";
import { deferred, settle } from "./support/player-environment";

let bundleDir: string;
let serial = 0;
let env: ReturnType<typeof applicationEnvironment>;
let state: typeof import("../public/lib/watch-state.js");
let catalog: string;
let snapshot: object;
let intercept: (url: string, init?: RequestInit) => Promise<Response> | null;
const film = (setId: string) => ({ setId, title: setId, kind: "movie", duration: 600, addedAt: 1, total: 1000, container: "mp4", vcodec: "h264", acodec: "aac" });
const page = () => textOf(env.node("main"));
const stream = () => env.streams.at(-1)!;

beforeAll(async () => {
  bundleDir = await mkdtemp(join(tmpdir(), "mediagram-browser-app-"));
  // Bundle the actual entry and its modules together, so each test gets a
  // fresh application lifetime without replacing any production functions.
  const entry = join(bundleDir, "entry.js");
  await writeFile(entry, `import ${JSON.stringify(join(import.meta.dir, "../public/app.js"))};\nexport * as state from ${JSON.stringify(join(import.meta.dir, "../public/lib/watch-state.js"))};`);
  const result = await Bun.build({ entrypoints: [entry], outdir: bundleDir, naming: "application.js", target: "browser", external: ["/lib/hls.mjs"] });
  if (!result.success) throw new AggregateError(result.logs, "Building browser application fixture");
});

afterAll(() => rm(bundleDir, { recursive: true, force: true }));

beforeEach(() => {
  env = applicationEnvironment();
  catalog = JSON.stringify([film("First")]);
  snapshot = { progress: [], collections: [{ id: "list", name: "My list", items: [] }] };
  intercept = () => null;
  env.respondWith(async (url, init) => {
    const intercepted = intercept(url, init);
    if (intercepted) return intercepted;
    if (url === "/api/sets") return new Response(catalog);
    if (url === "/api/player") return Response.json({ remote: false });
    if (url === "/api/profiles") return Response.json({ remembers: true, profiles: [{ id: "viewer", name: "Viewer" }] });
    if (url.endsWith("/state")) return Response.json(snapshot);
    if (url === "/api/kids") return Response.json({ kids: [] });
    if (init?.method && init.method !== "HEAD") return new Response(null, { status: 204 });
    return new Response(null, { status: 404 });
  });
});

afterEach(async () => {
  env.node("player").close();
  await env.visibility("hidden");
  await settle();
  env.restore();
});

async function start() {
  ({ state } = await import(`${join(bundleDir, "application.js")}?case=${++serial}`));
}

test("startup waits for the chosen profile before drawing its shelves", async () => {
  snapshot = { progress: [{ setId: "First", at: 90, duration: 600, updatedAt: 1 }], watchlist: ["First"] };
  env.location.hash = "#/continue";
  await start();
  expect(env.node("who").textContent).toBe("Viewer");
  expect(env.node("n-continue").textContent).toBe("1");
  expect(env.node("n-watchlist").textContent).toBe("1");
  expect(page()).toContain("First");
});

test.each(["{", "[null]"])("failed catalog construction is retried for the identical response %s", async (invalid) => {
  await start();
  const previous = page();
  catalog = invalid;
  const originalParse = JSON.parse;
  let attempts = 0;
  JSON.parse = (text, reviver) => { if (text === invalid) attempts++; return originalParse(text, reviver); };
  try {
    stream().fire("catalog"); await settle();
    stream().fire("catalog"); await settle();
    expect(page()).toBe(previous);
    expect(attempts).toBe(2);
  } finally { JSON.parse = originalParse; }
  catalog = JSON.stringify([film("Recovered")]);
  stream().fire("catalog"); await settle();
  expect(page()).toContain("Recovered");
});

test("concurrent catalog events coalesce into one follow-up read", async () => {
  await start();
  const next = deferred<Response>();
  let reads = 0;
  intercept = (url) => url === "/api/sets" ? (++reads === 1 ? next.promise : Promise.resolve(new Response(JSON.stringify([film("Latest")])))) : null;
  stream().fire("catalog"); stream().fire("catalog"); stream().fire("open");
  expect(reads).toBe(1);
  next.resolve(new Response(JSON.stringify([film("Intermediate")])));
  await settle();
  expect(reads).toBe(2);
  expect(page()).toContain("Latest");
});

test("catalog updates wait until the player closes before rebuilding the page", async () => {
  await start();
  env.node("player").showModal();
  catalog = JSON.stringify([film("Arrived")]);
  stream().fire("catalog"); await settle();
  expect(page()).toContain("First");
  expect(page()).not.toContain("Arrived");
  env.node("player").close();
  expect(page()).toContain("Arrived");
});

test("hide/show owns one event stream and refreshes the chosen profile", async () => {
  await start();
  expect(env.streams).toHaveLength(1);
  await env.visibility("hidden");
  expect(env.streams[0]?.closed).toBe(true);
  snapshot = { progress: [{ setId: "First", at: 90, duration: 600, updatedAt: 1 }] };
  await env.visibility("visible"); await env.visibility("visible");
  expect(env.streams).toHaveLength(2);
  expect(env.streams.filter((entry) => !entry.closed)).toHaveLength(1);
  expect(env.node("n-continue").textContent).toBe("1");
});

for (const outcome of ["success", "failure"] as const) {
  test(`old search ${outcome} cannot overwrite a different route`, async () => {
    await start();
    const pending = deferred<Response>();
    intercept = (url) => url.startsWith("/api/search?") ? pending.promise : null;
    await env.navigate("#/search/old");
    await env.navigate("#/movies");
    const current = page();
    if (outcome === "success") pending.resolve(Response.json({ hits: [] }));
    else pending.reject(new Error("Unavailable"));
    await settle();
    expect(page()).toBe(current);
  });
}

test("an older search cannot overwrite a newer search", async () => {
  await start();
  const old = deferred<Response>();
  intercept = (url) => url.endsWith("q=old") ? old.promise : url.endsWith("q=new") ? Promise.resolve(Response.json({ hits: [] })) : null;
  await env.navigate("#/search/old"); await env.navigate("#/search/new");
  old.resolve(Response.json({ hits: [] })); await settle();
  expect(page()).toContain("“new”");
});

test("returning to a search does not admit its earlier response", async () => {
  await start();
  const old = deferred<Response>();
  const latest = deferred<Response>();
  let requests = 0;
  intercept = (url) => url.endsWith("q=same") ? (++requests === 1 ? old.promise : latest.promise) : null;
  await env.navigate("#/search/same");
  await env.navigate("#/movies");
  await env.navigate("#/search/same");
  latest.resolve(Response.json({ hits: [film("Latest result")] })); await settle();
  old.resolve(Response.json({ hits: [] })); await settle();
  expect(page()).toContain("Latest result");
});

test("progress and collection mutations update counts and defer the shelf until close", async () => {
  await start();
  await env.navigate("#/collections/list");
  const previous = page();
  env.node("player").showModal();
  state.setProgress("First", 90, 600);
  state.setInCollection("list", "First", true);
  expect(env.node("n-continue").textContent).toBe("1");
  expect(page()).toBe(previous);
  env.node("player").close();
  expect(page()).toContain("First");
  expect(page()).toContain("one title");
});

test("collection picker keeps its editing session through mutation and redraws on close", async () => {
  await start();
  await env.navigate("#/collections/list");
  const trigger = descendants(env.node("main")).find((node) => node.textContent === "Add titles")!;
  trigger.fire("click");
  state.setInCollection("list", "First", true);
  expect(env.node("main").contains(trigger)).toBe(true);
  trigger.fire("click");
  expect(page()).toContain("First");
  expect(page()).toContain("one title");
});

test("remote list-only changes redraw without claiming progress changed", async () => {
  await start();
  await env.navigate("#/watchlist");
  snapshot = { watchlist: ["First"] };
  expect(await state.refreshState()).toBe(false);
  expect(env.node("n-watchlist").textContent).toBe("1");
  expect(page()).toContain("First");
});

test("watchlist and completion changes update the page through state notifications", async () => {
  await start();
  await env.navigate("#/watchlist");
  state.setWatchlisted("First", true);
  expect(page()).toContain("First");
  state.setProgress("First", 90, 600);
  expect(env.node("n-continue").textContent).toBe("1");
  state.clearProgress("First");
  state.setWatched("First", true);
  expect(env.node("n-continue").textContent).toBe("0");
  expect(descendants(env.node("main")).some((node) => node.className === "plate-tick")).toBe(true);
});

test("collection rename and deletion redraw without view-owned invalidation callbacks", async () => {
  await start();
  await env.navigate("#/collections");
  await state.renameCollection("list", "Renamed");
  expect(page()).toContain("Renamed");
  await state.deleteCollection("list");
  expect(page()).not.toContain("Renamed");
  expect(env.node("n-collections").textContent).toBe("0");
});

test("series page owns the season wall, encoded navigation and episode layout", async () => {
  catalog = JSON.stringify([
    { ...film("Episode one"), kind: "ep", show: "A/B", season: 1, episode: 1, showKey: "show-key" },
    { ...film("Episode two"), kind: "ep", show: "A/B", season: 2, episode: 1, showKey: "show-key" },
  ]);
  intercept = (url) => url === "/api/shows/show-key" ? Promise.resolve(Response.json({ overview: "Series description" })) : null;
  await start();
  await env.navigate("#/series/A%2FB");
  expect(page()).toContain("Series description");
  expect(page()).toContain("Season 1");
  const card = descendants(env.node("main")).find((node) => node.className === "card" && textOf(node).includes("Season 2"))!;
  card.fire("click");
  expect(env.location.hash).toBe("#/series/A%2FB/Season%202");
  await env.navigate(env.location.hash);
  expect(page()).toContain("Episode two");
  expect(page()).not.toContain("Episode one");
});

test("course pages preserve nested folders, breadcrumbs and document links", async () => {
  catalog = JSON.stringify([
    { ...film("Lesson"), kind: "tut", show: "Course", path: "Basics/Advanced" },
    { ...film("Workbook"), kind: "doc", show: "Course", path: "Basics", container: "pdf" },
  ]);
  await start();
  await env.navigate("#/tutorials/Course/Basics");
  expect(page()).toContain("one lesson · one document");
  expect(page()).toContain("Workbook");
  const link = descendants(env.node("main")).find((node) => node.className === "row document")!;
  expect(Object.getOwnPropertyDescriptor(link, "href")?.value).toBe("/api/sets/Workbook/stream");
  const folder = descendants(env.node("main")).find((node) => node.className === "row folder")!;
  folder.fire("click");
  expect(env.location.hash).toBe("#/tutorials/Course/Basics/Advanced");
  await env.navigate(env.location.hash);
  expect(page()).toContain("Lesson");
  expect(page()).not.toContain("Workbook");
  await env.navigate("#/tutorials/Course/Absent");
  expect(page()).toContain('has no folder called "Absent"');
});

test("opening a film refreshes its position before the real player resumes it", async () => {
  await start();
  await env.navigate("#/film/First");
  const pending = deferred<Response>();
  intercept = (url) => url.endsWith("/state") ? pending.promise : null;
  descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
  expect(env.node("player").open).toBe(false);
  pending.resolve(Response.json({ progress: [{ setId: "First", at: 90, duration: 600, updatedAt: 1 }] }));
  await settle();
  expect(env.node("player").open).toBe(true);
  expect(env.video.src).toBe("/api/sets/First/stream");
  env.video.duration = 600;
  env.video.fire("loadedmetadata");
  expect(env.video.currentTime).toBe(90);
  await env.video.play();
  env.video.currentTime = 140;
  env.node("player").close();
  expect(state.progressOf("First")?.at).toBe(140);
  expect(page()).toContain("Resume from 2:20");
});

test("a superseded play cannot replace the latest title when refreshes finish in reverse order", async () => {
  catalog = JSON.stringify([film("First"), film("Second")]);
  await start();
  const responses = [deferred<Response>(), deferred<Response>()];
  let reads = 0;
  intercept = (url) => url.endsWith("/state") ? responses[reads++]!.promise : null;
  await env.navigate("#/film/First");
  descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
  await env.navigate("#/film/Second");
  descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
  responses[1]!.resolve(Response.json({}));
  await settle();
  expect(env.video.src).toBe("/api/sets/Second/stream");
  responses[0]!.resolve(Response.json({}));
  await settle();
  expect(env.video.src).toBe("/api/sets/Second/stream");
  expect(env.video.attachments).toEqual(["/api/sets/Second/stream"]);
});

test.each(["close", "pagehide"])("%s invalidates Play next while its state refresh is pending", async (exit) => {
  catalog = JSON.stringify([film("First"), film("Second")]);
  snapshot = { collections: [{ id: "list", name: "My list", items: ["First", "Second"] }] };
  await start();
  await env.navigate("#/collections/list");
  descendants(env.node("main")).find((node) => node.textContent === "Play all")!.fire("click");
  await settle();
  expect(env.video.src).toBe("/api/sets/First/stream");
  const pending = deferred<Response>();
  intercept = (url) => url.endsWith("/state") ? pending.promise : null;
  env.node("play-next").fire("click");
  if (exit === "close") env.node("player").close();
  else env.window.dispatchEvent(new Event("pagehide"));
  const attachments = [...env.video.attachments];
  pending.resolve(Response.json({}));
  await settle();
  expect(env.video.src).toBe("");
  expect(env.video.attachments).toEqual(attachments);
  if (exit === "close") expect(env.node("player").open).toBe(false);
});
