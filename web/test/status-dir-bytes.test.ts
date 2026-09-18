import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { dirBytes } from "../src/status/dir-bytes";

let root: string;

beforeAll(async () => {
  root = await mkdtemp(join(tmpdir(), "mediagram-dir-"));
  await writeFile(join(root, "a.ts"), new Uint8Array(100));
  await mkdir(join(root, "session"), { recursive: true });
  await writeFile(join(root, "session", "b.ts"), new Uint8Array(250));
  await writeFile(join(root, "session", "playlist.m3u8"), new Uint8Array(30));
});
afterAll(async () => rm(root, { recursive: true, force: true }));

describe("measuring a directory", () => {
  test("adds up every file beneath it, however deep", () => {
    // A transcode writes segments into a directory per session.
    return expect(dirBytes(root)).resolves.toBe(380);
  });

  test("answers zero for a directory that is not there", () => {
    // The transcode directory does not exist until the first conversion.
    return expect(dirBytes(join(root, "nothing"))).resolves.toBe(0);
  });

  test("counts an empty directory as nothing rather than failing", async () => {
    const empty = join(root, "empty");
    await mkdir(empty, { recursive: true });
    expect(await dirBytes(empty)).toBe(0);
  });
});
