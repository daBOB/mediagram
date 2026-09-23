import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, readdir, rm } from "node:fs/promises";
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
