import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { EXPECTED_SCHEMA } from "../src/catalog";
import { oneAtATime, refreshFromChannel } from "../src/channel-index/refresh-from-channel";
import { emptyIndex } from "./index-fixture";

let root: string;
beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "channel-refresh-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

function found(pushedAt: number) {
  const db = emptyIndex();
  db.run("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  db.run(`INSERT INTO meta VALUES ('schema_version', '${EXPECTED_SCHEMA}')`);
  const bytes = db.serialize();
  db.close();
  return async () => ({
    messageId: 1,
    pushedAt,
    chunks: async function* () {
      yield bytes;
    },
  });
}

describe("what the server serves after asking the channel", () => {
  test("a new snapshot is installed and served", async () => {
    const outcome = await refreshFromChannel(root, found(100));
    expect(outcome).toMatchObject({ kind: "installed", pushedAt: 100, refresh: "updated", reason: null });
  });

  test("the same snapshot again changes nothing", async () => {
    await refreshFromChannel(root, found(100));
    expect(await refreshFromChannel(root, found(100))).toMatchObject({ refresh: "unchanged" });
  });

  test("an unreachable channel keeps serving the snapshot installed before", async () => {
    await refreshFromChannel(root, found(100));
    const outcome = await refreshFromChannel(root, async () => {
      throw new Error("offline");
    });
    expect(outcome).toMatchObject({
      kind: "installed",
      pushedAt: 100,
      refresh: "kept",
      reason: "the channel could not be read: offline",
    });
  });

  test("with nothing ever installed, the caller is told to serve its own index", async () => {
    expect(await refreshFromChannel(root, async () => "nothing-pinned")).toEqual({
      kind: "none",
      reason: "the channel has nothing pinned; run `mediagram push-index` on the uploading machine",
    });
  });

  for (const [description, rejection, message] of [
    ["null", null, "null"],
    ["undefined", undefined, "undefined"],
    ["string", "offline", "offline"],
    ["unprintable object", { toString() { throw new Error("cannot print"); } }, "unprintable rejection"],
  ] as const) {
    for (const installed of [false, true]) {
      test(`${description} discovery rejection preserves ${installed ? "installed" : "local"} fallback`, async () => {
        if (installed) await refreshFromChannel(root, found(100));
        const outcome = await refreshFromChannel(root, async () => { throw rejection; });
        expect(outcome).toMatchObject({
          kind: installed ? "installed" : "none",
          reason: `the channel could not be read: ${message}`,
        });
        if (installed) expect(outcome).toMatchObject({ pushedAt: 100, refresh: "kept" });
      });
    }
  }

  test("a filesystem refusal keeps serving the snapshot installed before", async () => {
    await refreshFromChannel(root, found(100));
    await mkdir(join(root, `.current-${process.pid}`));

    const outcome = await refreshFromChannel(root, found(200));

    expect(outcome).toMatchObject({ kind: "installed", pushedAt: 100, refresh: "kept" });
    expect(outcome.reason).toMatch(/EISDIR/);
  });

  test("an unusable installation path falls back to this machine's own index", async () => {
    const blocked = join(root, "not-a-directory");
    await writeFile(blocked, "file");

    const outcome = await refreshFromChannel(join(blocked, "catalog"), found(100));

    expect(outcome.kind).toBe("none");
    expect(outcome.reason).toMatch(/ENOTDIR/);
  });
});

describe("one install at a time", () => {
  test("a burst during a run collapses into one more run", async () => {
    let runs = 0;
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));
    const run = oneAtATime(async () => {
      runs += 1;
      if (runs === 1) await gate;
    });
    const first = run();
    void run();
    void run();
    release();
    await first;
    // The first run, then exactly one for everything asked during it.
    await run();
    expect(runs).toBe(3);
  });

  test("a failing run does not stop the next one", async () => {
    let runs = 0;
    const run = oneAtATime(async () => {
      runs += 1;
      throw new Error("boom");
    });
    await run();
    await run();
    expect(runs).toBe(2);
  });
});
