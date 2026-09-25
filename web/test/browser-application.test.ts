import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test } from "bun:test";
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
  // The search result checked below must be a real catalog title: search
  // results are now kept only when they are in this profile's library.
  catalog = JSON.stringify([film("First"), film("Latest result")]);
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


test("successful list deletion leaves the deleted list after its state notification redraw", async () => {
  await start();
  await env.navigate("#/collections/list");
  Object.assign(env.window, { confirm: () => true });
  descendants(env.node("main")).find((node) => node.textContent === "Delete list")!.fire("click");
  await settle();
  expect(env.location.hash).toBe("#/collections");
  expect(state.collections()).toEqual([]);
});

for (const returnToList of [false, true]) {
  test(`late list deletion does not navigate after ${returnToList ? "leaving and returning" : "leaving"} the list`, async () => {
    await start();
    await env.navigate("#/collections/list");
    Object.assign(env.window, { confirm: () => true });
    const pending = deferred<Response>();
    intercept = (url, init) => url.endsWith("/collections/list") && init?.method === "DELETE" ? pending.promise : null;
    descendants(env.node("main")).find((node) => node.textContent === "Delete list")!.fire("click");
    await env.navigate("#/movies");
    if (returnToList) await env.navigate("#/collections/list");
    const currentHash = env.location.hash;
    pending.resolve(new Response(null, { status: 204 }));
    await settle();
    expect(env.location.hash).toBe(currentHash);
    expect(page()).toContain(returnToList ? "That list is not here any more." : "First");
    expect(state.collections()).toEqual([]);
  });
}

const statusSnapshot = (sets: number) => ({
  catalog: { origin: "package", publishedAt: null, refresh: "current", sets, posters: 2, schema: 6 },
  cache: null, encoder: { name: "libx264", device: null },
  transcodes: { running: 0, capacity: 2, sessions: [], heldBytes: 0, dir: "/tmp/conversions" },
  telegram: { connected: true, failedReads: 0 }, state: { remembered: true, path: "/tmp/state.db" },
  fetchedBytes: 1000, memoryBytes: 2000, uptimeSeconds: 30,
});

test("System route renders its initial status, reports a polling failure and recovers", async () => {
  let reads = 0;
  intercept = (url, init) => {
    if (url !== "/api/status") return null;
    if (init?.method === "HEAD") return Promise.resolve(new Response(null, { status: 204 }));
    reads++;
    return Promise.resolve(reads === 2 ? new Response(null, { status: 503 }) : Response.json(statusSnapshot(reads)));
  };
  await start();
  expect(env.node("nav-system").hidden).toBe(false);
  await env.navigate("#/system");
  expect(page()).toContain("System");
  expect(page()).toContain("1 playable sets, 2 posters");
  env.advance(2000); await settle();
  expect(page()).toContain("Could not read the player's status: the player answered 503");
  env.advance(2000); await settle();
  expect(page()).toContain("3 playable sets, 2 posters");
  expect(page()).not.toContain("Could not read");
  await env.navigate("#/movies");
  env.advance(6000); await settle();
  expect(reads).toBe(3);
});

for (const outcome of ["response", "body", "rejection"] as const) {
  test(`leaving System disposes its poller and isolates a late ${outcome} on return`, async () => {
    await start();
    const pending = deferred<Response>();
    const body = deferred<Uint8Array>();
    const first = outcome === "body"
      ? Promise.resolve(new Response(new ReadableStream({ async start(controller) { controller.enqueue(await body.promise); controller.close(); } })))
      : pending.promise;
    let reads = 0;
    intercept = (url) => url === "/api/status" ? (++reads === 1 ? first : Promise.resolve(Response.json(statusSnapshot(42)))) : null;
    await env.navigate("#/system");
    const oldPanel = descendants(env.node("main")).find((node) => node.className === "status-panel")!;
    expect(oldPanel).toBeDefined();
    const previous = textOf(oldPanel);
    await env.navigate("#/movies");
    env.advance(6000); await settle();
    expect(reads).toBe(1);
    expect(page()).toContain("First");
    await env.navigate("#/system");
    expect(reads).toBe(2);
    expect(page()).toContain("42 playable sets");
    if (outcome === "response") pending.resolve(Response.json(statusSnapshot(999)));
    else if (outcome === "body") body.resolve(new TextEncoder().encode(JSON.stringify(statusSnapshot(999))));
    else pending.reject(new Error("Old request failed"));
    await settle();
    expect(textOf(oldPanel)).toBe(previous);
    expect(page()).toContain("42 playable sets");
    expect(page()).not.toContain("999");
    expect(page()).not.toContain("Old request failed");
    await env.navigate("#/movies");
    env.advance(6000); await settle();
    expect(reads).toBe(2);
  });
}


async function waitForProfilePicker() {
  for (let turn = 0; turn < 100 && !descendants(env.document.body).some((node) => node.className === "who"); turn++) {
    await Bun.sleep(1);
  }
  expect(descendants(env.document.body).some((node) => node.className === "who")).toBe(true);
}

async function finishProfilePicker(starting: Promise<void>) {
  // Also release startup when a pre-fix assertion fails: use the real creation
  // and selection callbacks if the old picker has no discovery retry button.
  if (descendants(env.document.body).some((node) => node.className === "who")) {
    intercept = (url, init) => url === "/api/profiles" && init?.method === "POST"
      ? Promise.resolve(Response.json({ id: "viewer", name: "Viewer", createdAt: 1 })) : null;
    const retry = descendants(env.document.body).find((node) => node.textContent === "Retry profiles");
    if (retry) retry.fire("click");
    else if (!descendants(env.document.body).some((node) => node.className === "who-name" && node.textContent === "Viewer")) {
      descendants(env.document.body).find((node) => node.className === "who-tile who-add")!.fire("click");
      await settle();
      const form = descendants(env.document.body).find((node) => node.className === "who-new")!;
      const name = descendants(form).find((node) => node.tagName === "INPUT") as unknown as { value: string };
      name.value = "Viewer";
      form.fire("submit");
    }
    await settle();
    const tile = descendants(env.document.body).find((node) => node.className === "who-tile" && textOf(node).includes("Viewer"));
    if (!tile) throw new Error("Recovered profile tile missing");
    tile.fire("click");
  }
  await starting;
}

for (const failure of ["HTTP", "network", "JSON"] as const) {
  test(`remembered profile state ${failure} failure blocks empty shelves until retry succeeds`, async () => {
    env.location.hash = "#/continue";
    snapshot = { progress: [{ setId: "First", at: 90, duration: 600, updatedAt: 1 }] };
    intercept = (url) => {
      if (!url.endsWith("/state")) return null;
      if (failure === "network") return Promise.reject(new Error("offline"));
      return Promise.resolve(failure === "HTTP" ? new Response(null, { status: 503 }) : new Response("{"));
    };
    const starting = start();
    try {
      await waitForProfilePicker();
      expect(textOf(env.document.body)).toContain("Could not load this profile");
      expect(page()).not.toContain("Nothing started yet");
      const tile = descendants(env.document.body).find((node) => node.className === "who-tile")!;
      tile.fire("click");
      await settle();
      expect(textOf(env.document.body)).toContain("Could not load this profile");
      expect(page()).not.toContain("Nothing started yet");
    } finally {
      await finishProfilePicker(starting);
    }
    expect(env.node("who").textContent).toBe("Viewer");
    expect(env.node("n-continue").textContent).toBe("1");
    expect(page()).toContain("First");
  });
}

for (const failure of ["HTTP", "network", "JSON"] as const) {
  test(`startup profile ${failure} failure shows a retry and recovers without an empty-profile claim`, async () => {
    intercept = (url) => {
      if (url !== "/api/profiles") return null;
      if (failure === "network") return Promise.reject(new Error("offline"));
      return Promise.resolve(failure === "HTTP" ? new Response(null, { status: 503 }) : new Response("{"));
    };
    const starting = start();
    await waitForProfilePicker();
    try {
      expect(textOf(env.document.body)).toContain("Could not load profiles.");
      expect(textOf(env.document.body)).not.toContain("New profile");
      expect(textOf(env.document.body)).not.toContain("cannot save anything");
      const retry = descendants(env.document.body).find((node) => node.textContent === "Retry profiles")!;
      retry.fire("click");
      await settle();
      expect(textOf(env.document.body)).toContain("Could not load profiles.");
      expect(descendants(env.document.body).find((node) => node.textContent === "Retry profiles")?.disabled).toBe(false);
      expect(page()).not.toContain("Could not load the catalog");
    } finally {
      await finishProfilePicker(starting);
    }
    expect(state.profileId()).toBe("viewer");
    expect(env.node("who").textContent).toBe("Viewer");
    expect(page()).toContain("First");
  });
}

test.each([true, false])("successful empty profile discovery (remembers=%s) offers creation without a retry error", async (remembers) => {
  intercept = (url) => url === "/api/profiles" ? Promise.resolve(Response.json({ remembers, profiles: [] })) : null;
  const starting = start();
  await waitForProfilePicker();
  try {
    expect(textOf(env.document.body)).toContain("New profile");
    expect(textOf(env.document.body)).not.toContain("Could not load profiles.");
    expect(textOf(env.document.body)).not.toContain("Retry profiles");
    expect(textOf(env.document.body).includes("cannot save anything")).toBe(!remembers);
  } finally {
    await finishProfilePicker(starting);
  }
});

test("the Movies shelf is drawn a page at a time, with its page in the address", async () => {
  catalog = JSON.stringify(Array.from({ length: 50 }, (_, index) => film(`Film ${String(index + 1).padStart(2, "0")}`)));
  await start();
  expect(page()).toContain("50 films · page 1 of 2");
  expect(page()).toContain("Film 48");
  expect(page()).not.toContain("Film 49");
  const pager = descendants(env.node("main")).find((node) => node.className === "pager")!;
  expect(descendants(pager).filter((node) => node.tagName === "A").map((node) => (node as unknown as { href: string }).href))
    .toEqual(["#/movies/page/2", "#/movies/page/2"]);

  const scrolled = env.scrolls.length;
  await env.navigate("#/movies/page/2");
  expect(page()).toContain("Film 50");
  expect(page()).not.toContain("Film 48");
  expect(env.scrolls.length).toBe(scrolled + 1);

  // A page past the end is the last one; a catalog redraw keeps the viewer's place.
  await env.navigate("#/movies/page/9");
  expect(page()).toContain("page 2 of 2");
  stream().fire("catalog"); await settle();
  expect(env.scrolls.length).toBe(scrolled + 1);
});

describe("kids profiles", () => {
  const rated = (setId: string, fsk: string | null) => ({ ...film(setId), fsk });
  const profiles = [
    { id: "viewer", name: "Viewer", createdAt: 1, kids: true },
    { id: "adult", name: "Adult", createdAt: 2, kids: false },
  ];
  beforeEach(() => {
    catalog = JSON.stringify([rated("Family", "6"), rated("Grown", "16"), rated("Unknown", null), rated("Marked", null)]);
    intercept = (url) => {
      if (url === "/api/profiles") return Promise.resolve(Response.json({ remembers: true, profiles }));
      if (url === "/api/kids") return Promise.resolve(Response.json({ kids: ["Marked"] }));
      if (url.startsWith("/api/search")) {
        return Promise.resolve(Response.json({ query: "x", hits: [rated("Family", "6"), rated("Grown", "16")] }));
      }
      return null;
    };
  });

  test("a kids profile's shelves hold only what is rated for kids or marked by hand", async () => {
    await start();
    expect(env.node("n-movies").textContent).toBe("2");
    await env.navigate("#/movies");
    expect(page()).toContain("Family");
    expect(page()).toContain("Marked");
    expect(page()).not.toContain("Grown");
    expect(page()).not.toContain("Unknown");
  });

  test("search on a kids profile drops hits outside its library", async () => {
    await start();
    await env.navigate("#/search/x");
    expect(page()).toContain("Family");
    expect(page()).not.toContain("Grown");
  });

  test("a catalog refresh on a kids profile is filtered too", async () => {
    await start();
    catalog = JSON.stringify([rated("Family", "6"), rated("Grown", "16"), rated("Later", "18"), rated("Also", "0")]);
    stream().fire("catalog");
    await settle();
    expect(env.node("n-movies").textContent).toBe("2");
  });

  test("choosing an adult profile brings the whole library back without fetching it again", async () => {
    await start();
    const fetched = env.requests.filter((request) => request.url === "/api/sets").length;
    env.node("who").fire("click");
    await settle();
    const adult = descendants(env.document.body)
      .find((node) => node.className === "who-tile" && textOf(node).includes("Adult"))!;
    adult.fire("click");
    await settle();
    expect(env.node("who").textContent).toBe("Adult");
    expect(env.node("n-movies").textContent).toBe("4");
    expect(env.requests.filter((request) => request.url === "/api/sets").length).toBe(fetched);
  });

  test("an empty kids library says what it is waiting for", async () => {
    catalog = JSON.stringify([rated("Grown", "16")]);
    await start();
    await env.navigate("#/movies");
    expect(page()).toContain("Nothing rated FSK 12 or under yet.");
  });

  test("a kids profile is offered no way to change the household's editor's choice", async () => {
    await start();
    await env.navigate("#/film/Family");
    const pins = descendants(env.node("main")).filter((node) => node.className.startsWith("pin-control"));
    expect(pins).toEqual([]);
    expect(descendants(env.node("main")).some((node) => node.className === "film-play")).toBe(true);
  });

  test("the player offers no Kids mark on a kids profile", async () => {
    await start();
    await env.navigate("#/film/Family");
    descendants(env.node("main")).find((node) => node.className === "film-play")!.fire("click");
    await settle();
    expect(env.node("kids").hidden).toBe(true);
  });

  test("a new profile can be made a kids profile, and its tile says so", async () => {
    let posted: unknown = null;
    const base = intercept;
    intercept = (url, init) => {
      if (url === "/api/profiles" && init?.method === "POST") {
        posted = JSON.parse(String(init.body));
        return Promise.resolve(Response.json({ id: "mia", name: "Mia", createdAt: 3, kids: true }, { status: 201 }));
      }
      return base(url, init);
    };
    await start();
    env.node("who").fire("click");
    await settle();
    descendants(env.document.body).find((node) => node.className.includes("who-add"))!.fire("click");
    await settle();
    const form = descendants(env.document.body).find((node) => node.className === "who-new")!;
    const [name, kids] = descendants(form).filter((node) => node.tagName === "INPUT") as unknown as
      [{ value: string; checked: boolean }, { value: string; checked: boolean }];
    name.value = "Mia";
    kids.checked = true;
    form.fire("submit");
    await settle();
    expect(posted).toEqual({ name: "Mia", kids: true });
    const tile = descendants(env.document.body)
      .find((node) => node.className === "who-tile" && textOf(node).includes("Mia"))!;
    expect(textOf(tile)).toContain("Kids");
  });
});

test("Featured suggests unwatched films and opens the one chosen after leaving its history entry", async () => {
  const withPoster = (setId: string) => ({ ...film(setId), poster: `tmdb-movie-${setId}` });
  catalog = JSON.stringify([withPoster("Seen"), withPoster("Unseen")]);
  snapshot = { progress: [], watched: [{ setId: "Seen", finishedAt: 1 }], collections: [] };
  await start();
  const button = descendants(env.node("main")).find((node) => node.tagName === "BUTTON" && node.textContent === "Featured")!;
  button.fire("click"); await settle();

  const reel = env.node("featured");
  expect(reel.open).toBe(true);
  expect(env.history.state).toEqual({ featured: true });
  expect(textOf(reel)).toContain("Unseen");
  expect(textOf(reel)).not.toContain("Seen·");
  expect(textOf(reel)).toContain("Featured · 1 / 1");

  descendants(reel).find((node) => node.className === "featured-details")!.fire("click"); await settle();
  expect(reel.open).toBe(false);
  expect(env.history.state).toBeNull();
  expect(env.location.hash).toBe("#/film/Unseen");
});

test("a bare address opens home once, in place, rather than navigating there", async () => {
  env.location.hash = "";
  let changes = 0;
  env.window.addEventListener("hashchange", () => { changes++; });
  await start();
  expect(env.location.hash).toBe("#/home");
  // Replaced, so no second route and no history entry for the bare address.
  expect(changes).toBe(0);
  expect(env.history.state).toBeNull();
});

describe("the magazine home page", () => {
  const featured = (setId: string, over: Record<string, unknown> = {}) => ({
    ...film(setId), poster: `tmdb-movie-${setId}`, backdrop: `tmdb-movie-${setId}-bg`, rating: 7, popularity: 5, ...over,
  });

  test("leads its features with the household's pinned editor's choice", async () => {
    catalog = JSON.stringify([featured("Pinned", { rating: 5 }), featured("Acclaimed", { rating: 9 }), featured("Popular", { popularity: 80 })]);
    intercept = (url) => (url === "/api/editors-choice" ? Response.json({ setId: "Pinned" }) as never : null);
    env.location.hash = "#/home";
    await start();
    const features = descendants(env.node("main")).filter((node) => node.className.startsWith("feature feature-"));
    expect(features.map((node) => node.className)).toEqual(["feature feature-editor", "feature feature-trending", "feature feature-staff"]);
    expect(textOf(features[0]!)).toContain("Pinned");
    expect((features[0] as unknown as { href: string }).href).toBe("#/film/Pinned");
    // A grown-up profile is offered the pin on a title's page.
    await env.navigate("#/film/Acclaimed");
    expect(descendants(env.node("main")).some((node) => node.className.startsWith("pin-control"))).toBe(true);
  });

  test("draws without a pin, or when the pin cannot be asked for", async () => {
    catalog = JSON.stringify([featured("One"), featured("Two"), featured("Three")]);
    env.location.hash = "#/home";
    await start();
    const kinds = descendants(env.node("main")).filter((node) => node.className.startsWith("feature feature-"))
      .map((node) => node.className);
    expect(kinds).not.toContain("feature feature-editor");
    expect(kinds.length).toBeGreaterThan(0);
  });
});
