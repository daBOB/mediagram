import { Database } from "bun:sqlite";
import { expect, test } from "bun:test";
import { watch } from "node:fs";
import { mkdtemp, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRouter } from "../src/routes";
import { SheetStore } from "../src/thumbs/sheets";
import type { PlayerResponse } from "../src/http/contracts";
import { deferred, library } from "./application-fixture";

const SET = "01THUMBNAIL";
const JPEG = new Uint8Array([0xff, 0xd8, 1, 2, 0xff, 0xd9]);

async function within<T>(promise: Promise<T>, description: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout>;
  try {
    return await Promise.race([promise, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out: ${description}`)), 2000);
    })]);
  } finally {
    clearTimeout(timer!);
  }
}

async function fixture() {
  const directory = await mkdtemp(join(tmpdir(), "mediagram-thumbnail-route-"));
  const seed = library("Thumbnail handoff", SET);
  seed.run("UPDATE sets SET duration = 120");
  const dbPath = join(directory, "library.db");
  await writeFile(dbPath, seed.serialize());
  seed.close();
  const db = new Database(dbPath);
  const started = deferred<string[]>();
  const exit = deferred<number>();
  const heldChecks: string[] = [];
  const commands: string[][] = [];
  const sheets = new SheetStore({
    directory,
    baseUrl: "http://127.0.0.1:8770",
    isHeld: (setId) => { heldChecks.push(setId); return true; },
  }, async (args) => {
    commands.push(args);
    await writeFile(args.at(-1)!, JPEG);
    started.resolve(args);
    return exit.promise;
  });
  const route = createRouter({
    db,
    thumbs: sheets,
    source: { stream: () => { throw new Error("thumbnail requests must not read remote media"); } },
  });
  const request = (method = "GET", setId = SET) =>
    within(route({ method, path: `/api/sets/${setId}/thumbs.jpg`, range: null }), "thumbnail response");
  return {
    directory, sheets, started, exit, heldChecks, commands, request,
    async close() {
      exit.resolve(-1);
      await sheets.stop();
      db.close();
      await rm(directory, { recursive: true, force: true });
    },
  };
}

/** Observe real publication/removal, without replacing SheetStore methods. */
function renamed(directory: string, name: string) {
  const changed = deferred<void>();
  const watcher = watch(directory, (event, filename) => {
    if (event === "rename" && filename === name) changed.resolve();
  });
  return { promise: changed.promise, close: () => watcher.close() };
}

function expectMissing(response: PlayerResponse) {
  expect(response.status).toBe(404);
  expect(response.headers["content-length"]).toBe("0");
  expect(response.body).toBeNull();
}

test("missing previews return while ffmpeg is pending, then GET and HEAD frame the published JPEG", async () => {
  const f = await fixture();
  const published = renamed(f.directory, `${SET}.jpg`);
  try {
    // The exit promise remains unresolved throughout these requests. Awaiting
    // generation in the route would hit the failure guard instead of returning.
    expectMissing(await f.request());
    const args = await within(f.started.promise, "ffmpeg start");
    expect(args[args.indexOf("-i") + 1]).toBe(`http://127.0.0.1:8770/api/sets/${SET}/stream`);
    expect(args.at(-1)).toBe(join(f.directory, `${SET}.making.jpg`));
    expect(await f.sheets.sizeOf(SET)).toBeNull();
    expect((await readdir(f.directory)).filter((name) => name.endsWith(".jpg")))
      .toEqual([`${SET}.making.jpg`]);
    expectMissing(await f.request());
    expectMissing(await f.request("HEAD"));
    expect(f.commands).toHaveLength(1);
    expect(f.heldChecks.every((id) => id === SET)).toBe(true);

    f.exit.resolve(0);
    await within(published.promise, "atomic sheet publication");
    const image = await f.request();
    expect(image.status).toBe(200);
    expect(image.headers["content-type"]).toBe("image/jpeg");
    expect(image.headers["content-length"]).toBe(String(JPEG.length));
    expect(image.headers["cache-control"]).toBe("public, max-age=86400");
    expect(image.body).toEqual(JPEG);
    const head = await f.request("HEAD");
    expect(head.status).toBe(200);
    expect(head.headers).toEqual(image.headers);
    expect(head.body).toBeNull();
    expect(f.commands).toHaveLength(1);
    expect((await readdir(f.directory)).filter((name) => name.endsWith(".jpg")))
      .toEqual([`${SET}.jpg`]);
  } finally {
    published.close();
    await f.close();
  }
});

test("an unsuccessful ffmpeg exit never turns its partial JPEG into a route response", async () => {
  const f = await fixture();
  try {
    expectMissing(await f.request());
    await within(f.started.promise, "ffmpeg start");
    const removed = renamed(f.directory, `${SET}.making.jpg`);
    try {
      f.exit.resolve(1);
      await within(removed.promise, "failed output cleanup");
    } finally {
      removed.close();
    }
    expect(await f.sheets.sizeOf(SET)).toBeNull();
    expect((await readdir(f.directory)).filter((name) => name.endsWith(".jpg"))).toEqual([]);
    expectMissing(await f.request());
  } finally {
    await f.close();
  }
});

test("an unknown catalog set neither starts generation nor serves a leftover sheet", async () => {
  const f = await fixture();
  try {
    expectMissing(await f.request("GET", "UNKNOWN"));
    await writeFile(f.sheets.path("UNKNOWN"), JPEG);
    expectMissing(await f.request("GET", "UNKNOWN"));
    expectMissing(await f.request("HEAD", "UNKNOWN"));
    await f.sheets.stop();
    expect(f.heldChecks).toEqual([]);
    expect(f.commands).toEqual([]);
  } finally {
    await f.close();
  }
});
