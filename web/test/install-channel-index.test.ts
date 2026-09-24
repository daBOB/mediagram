import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, readFile, readdir, readlink, rename, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Database } from "bun:sqlite";
import {
  MAX_INDEX_BYTES,
  currentDir,
  installChannelIndex,
  installedPushedAt,
} from "../src/channel-index/install-channel-index";
import { emptyIndex } from "./index-fixture";
import { EXPECTED_SCHEMA } from "../src/catalog";

let root: string;
beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "channel-index-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

/** A snapshot's bytes, delivered in pieces the way a download is. */
function snapshot(bytes: Uint8Array, piece = 4096): () => AsyncIterable<Uint8Array> {
  return async function* () {
    for (let at = 0; at < bytes.length; at += piece) yield bytes.subarray(at, at + piece);
  };
}

/** An index as `push-index` sends one: the tables, and the schema it is at. */
function library(): Uint8Array {
  const db = emptyIndex();
  db.run("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
  db.run(`INSERT INTO meta VALUES ('schema_version', '${EXPECTED_SCHEMA}')`);
  const bytes = db.serialize();
  db.close();
  return bytes;
}

describe("installing a channel's index", () => {
  test("a library becomes the live catalog, named by when it was pushed", async () => {
    const outcome = await installChannelIndex(root, 1_789_946_371, snapshot(library()));
    expect(outcome.status).toBe("updated");
    expect(await installedPushedAt(root)).toBe(1_789_946_371);
    const db = new Database(join(currentDir(root), "library.db"), { readonly: true });
    expect(db.query("SELECT count(*) AS n FROM sets").get()).toEqual({ n: 0 });
    db.close();
  });

  test("something that is not a library never replaces one that works", async () => {
    await installChannelIndex(root, 100, snapshot(library()));
    const outcome = await installChannelIndex(root, 200, snapshot(new TextEncoder().encode("not a database")));
    expect(outcome.status).toBe("kept");
    expect(await installedPushedAt(root)).toBe(100);
    // Nothing half-staged is left behind to be mistaken for a version.
    expect((await readdir(root)).sort()).toEqual(["current", "v-100"]);
  });

  test("a snapshot no newer than the live one is not even downloaded", async () => {
    await installChannelIndex(root, 200, snapshot(library()));
    let started = false;
    const outcome = await installChannelIndex(root, 200, () => {
      started = true;
      return snapshot(library())();
    });
    expect(outcome.status).toBe("unchanged");
    expect(started).toBe(false);
    expect((await installChannelIndex(root, 150, snapshot(library()))).status).toBe("unchanged");
  });

  test("a newer snapshot replaces the older version", async () => {
    await installChannelIndex(root, 100, snapshot(library()));
    await installChannelIndex(root, 200, snapshot(library()));
    expect(await installedPushedAt(root)).toBe(200);
    expect((await readdir(root)).sort()).toEqual(["current", "v-200"]);
  });

  test("an occupied version name gets a suffix without losing its freshness", async () => {
    await installChannelIndex(root, 100, snapshot(library()));
    await symlink("missing-version", join(root, "v-200"));

    const outcome = await installChannelIndex(root, 200, snapshot(library()));

    expect(outcome.status).toBe("updated");
    expect(await readlink(join(root, "current"))).toBe("v-200-1");
    expect(await installedPushedAt(root)).toBe(200);
    const unchanged = await installChannelIndex(root, 200, () => {
      throw new Error("a repeated snapshot must not download");
    });
    expect(unchanged.status).toBe("unchanged");
    expect((await readdir(root)).sort()).toEqual(["current", "v-200-1"]);
  });

  test("a document past the ceiling is refused before it fills the disk", async () => {
    const huge = async function* () {
      const block = new Uint8Array(64 * 1024 * 1024);
      for (let sent = 0; sent <= MAX_INDEX_BYTES; sent += block.length) yield block;
    };
    const outcome = await installChannelIndex(root, 300, huge);
    expect(outcome).toEqual({ status: "kept", reason: `the pinned index is larger than ${MAX_INDEX_BYTES} bytes` });
    expect(await installedPushedAt(root)).toBeNull();
  });
});

describe("filesystem failures while installing a channel snapshot", () => {
  test("database-open failures retain their cause and the installed catalog", async () => {
    const bytes = library();
    await installChannelIndex(root, 100, snapshot(bytes));
    const replaced = async function* () {
      yield bytes;
      const path = join(root, `incoming-200-${process.pid}`, "library.db");
      await rm(path);
      await mkdir(path);
    };
    const outcome = await installChannelIndex(root, 200, replaced);
    expect(outcome.status).toBe("kept");
    if (outcome.status === "kept") expect(outcome.reason).toMatch(/could not be opened as a library: .+/);
    expect(await installedPushedAt(root)).toBe(100);
    expect(await readFile(join(currentDir(root), "library.db"))).toEqual(Buffer.from(bytes));
  });

  test("a file in the catalog path is a kept outcome before download", async () => {
    const blocked = join(root, "not-a-directory");
    await writeFile(blocked, "file");
    let downloaded = false;

    const outcome = await installChannelIndex(join(blocked, "catalog"), 100, () => {
      downloaded = true;
      return snapshot(library())();
    });

    expect(outcome.status).toBe("kept");
    if (outcome.status === "kept") expect(outcome.reason).toMatch(/ENOTDIR/);
    expect(downloaded).toBe(false);
  });

  test("a refused pointer staging operation leaves the installed library readable", async () => {
    const bytes = library();
    await installChannelIndex(root, 100, snapshot(bytes));
    await mkdir(join(root, `.current-${process.pid}`));

    const outcome = await installChannelIndex(root, 200, snapshot(bytes));

    expect(outcome.status).toBe("kept");
    if (outcome.status === "kept") expect(outcome.reason).toMatch(/EISDIR/);
    expect(await installedPushedAt(root)).toBe(100);
    expect(await readFile(join(currentDir(root), "library.db"))).toEqual(Buffer.from(bytes));
    expect(await readdir(root)).not.toContain("v-200");
  });

  test("failed cleanup cannot replace the download's original reason", async () => {
    const catalog = join(root, "catalog");
    const moved = join(root, "moved-catalog");
    const bytes = library();
    await installChannelIndex(catalog, 100, snapshot(bytes));
    const interrupted = async function* () {
      yield bytes;
      await rename(catalog, moved);
      await writeFile(catalog, "the path is now a file");
      throw new Error("download interrupted");
    };

    const outcome = await installChannelIndex(catalog, 200, interrupted);

    expect(outcome).toEqual({ status: "kept", reason: "download interrupted" });
    expect(await readFile(join(moved, "current", "library.db"))).toEqual(Buffer.from(bytes));
  });
});
