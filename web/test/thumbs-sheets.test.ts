import { afterEach, beforeEach, expect, test } from "bun:test";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { SheetStore } from "../src/thumbs/sheets";
import { spritePlan } from "../public/lib/sprite-plan.js";

let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "mediagram-sheets-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

const PLAN = spritePlan(120)!;
const JPEG = new Uint8Array([0xff, 0xd8, 1, 2, 0xff, 0xd9]);
const options = (isHeld = true) => ({ directory: root, baseUrl: "http://127.0.0.1:8770", isHeld: () => isHeld });

test("unheld media and absent plans never start ffmpeg", async () => {
  let calls = 0;
  const run = async () => { calls++; return 0; };
  expect(await new SheetStore(options(false), run).ensure("SET", PLAN)).toBe(false);
  expect(await new SheetStore(options(), run).ensure("SET", null)).toBe(false);
  expect(calls).toBe(0);
  expect(await readdir(root)).toEqual([]);
});

test("an existing sheet is reused without generation", async () => {
  const store = new SheetStore(options(), async () => { throw new Error("unexpected process"); });
  await writeFile(store.path("SET"), JPEG);
  expect(await store.ensure("SET", PLAN)).toBe(true);
  expect(await store.sizeOf("SET")).toBe(JPEG.length);
  expect(new Uint8Array(await readFile(store.path("SET")))).toEqual(JPEG);
});

test("concurrent requests share generation and publish only after successful exit", async () => {
  let started!: () => void;
  const ready = new Promise<void>((resolve) => { started = resolve; });
  let finish!: (code: number) => void;
  const exit = new Promise<number>((resolve) => { finish = resolve; });
  const commands: string[][] = [];
  const store = new SheetStore(options(), async (args) => {
    commands.push(args);
    await writeFile(args.at(-1)!, JPEG);
    started();
    return exit;
  });
  const making = store.ensure("SET", PLAN);
  try {
    await ready;
    expect(await store.ensure("SET", PLAN)).toBe(false);
    expect(commands).toHaveLength(1);
    expect(commands[0]![commands[0]!.indexOf("-i") + 1]).toBe("http://127.0.0.1:8770/api/sets/SET/cached-stream");
    expect(await store.sizeOf("SET")).toBeNull();
    expect(await readdir(root)).toEqual(["SET.making.jpg"]);
  } finally {
    finish(0);
    await making;
  }
  expect(await making).toBe(true);
  expect(await readdir(root)).toEqual(["SET.jpg"]);
  expect(new Uint8Array(await readFile(store.path("SET")))).toEqual(JPEG);
});

test("failed generation removes the partial file and releases its retry slot", async () => {
  let calls = 0;
  const store = new SheetStore(options(), async (args) => {
    await writeFile(args.at(-1)!, JPEG);
    return ++calls === 1 ? 1 : 0;
  });
  expect(await store.ensure("SET", PLAN)).toBe(false);
  expect(await store.sizeOf("SET")).toBeNull();
  expect(await readdir(root)).toEqual([]);
  expect(await store.ensure("SET", PLAN)).toBe(true);
  expect(calls).toBe(2);
});

test("a process launch error releases its retry slot", async () => {
  let calls = 0;
  const store = new SheetStore(options(), async (args) => {
    if (++calls === 1) throw new Error("process unavailable");
    await writeFile(args.at(-1)!, JPEG);
    return 0;
  });
  expect(await store.ensure("SET", PLAN)).toBe(false);
  expect(await store.ensure("SET", PLAN)).toBe(true);
});

test("a failed held-state lookup is contained and leaves generation retryable", async () => {
  let readable = false;
  let starts = 0;
  const store = new SheetStore({ ...options(), isHeld: () => {
    if (!readable) throw new Error("held state unavailable");
    return true;
  } }, async (args) => {
    starts++;
    await writeFile(args.at(-1)!, JPEG);
    return 0;
  });
  expect(await store.ensure("SET", PLAN)).toBe(false);
  expect(starts).toBe(0);
  readable = true;
  expect(await store.ensure("SET", PLAN)).toBe(true);
  expect(starts).toBe(1);
  await store.stop();
});

test("invalid set IDs cannot reach the filesystem or process", async () => {
  let calls = 0;
  const store = new SheetStore(options(), async () => { calls++; return 0; });
  expect(await store.ensure("../escape", PLAN)).toBe(false);
  expect(calls).toBe(0);
  expect(await readdir(root)).toEqual([]);
});

test("a destination collision is not a published sheet and can be retried", async () => {
  let calls = 0;
  const store = new SheetStore(options(), async (args) => {
    await writeFile(args.at(-1)!, JPEG);
    if (++calls === 1) await mkdir(store.path("SET"));
    return 0;
  });
  expect(await store.ensure("SET", PLAN)).toBe(false);
  expect(await store.sizeOf("SET")).toBeNull();
  expect(await readdir(root)).toEqual(["SET.jpg"]);
  await rm(store.path("SET"), { recursive: true });
  expect(await store.ensure("SET", PLAN)).toBe(true);
  expect(calls).toBe(2);
});
