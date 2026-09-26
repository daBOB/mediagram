import { describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { diskFree } from "../src/status/disk-free";

describe("disk free", () => {
  test("reports free and total bytes for a directory that exists", async () => {
    const dir = await mkdtemp(join(tmpdir(), "mediagram-disk-free-"));
    try {
      const [row] = await diskFree([dir]);
      expect(row?.dirs).toEqual([dir]);
      expect(row?.freeBytes).toBeGreaterThan(0);
      expect(row?.totalBytes).toBeGreaterThan(0);
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });

  test("dedupes the same directory given twice, rather than double-counting it", async () => {
    const dir = await mkdtemp(join(tmpdir(), "mediagram-disk-free-"));
    try {
      const rows = await diskFree([dir, dir]);
      expect(rows).toHaveLength(1);
      expect(rows[0]?.dirs).toEqual([dir]);
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });

  test("merges two distinct directories that share a device into one row", async () => {
    const root = await mkdtemp(join(tmpdir(), "mediagram-disk-free-"));
    const cache = join(root, "cache");
    const transcode = join(root, "transcode");
    await Promise.all([mkdir(cache), mkdir(transcode)]);
    try {
      // Both are subdirectories of `root`, so they share its device.
      const rows = await diskFree([cache, transcode]);
      expect(rows).toHaveLength(1);
      expect(rows[0]?.dirs).toEqual([cache, transcode]);
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  test("leaves out a directory that does not exist rather than failing", async () => {
    const rows = await diskFree(["/does/not/exist/mediagram-status-probe"]);
    expect(rows).toEqual([]);
  });

  test("keeps directories that exist alongside one that is missing", async () => {
    const dir = await mkdtemp(join(tmpdir(), "mediagram-disk-free-"));
    try {
      const rows = await diskFree([dir, "/does/not/exist/mediagram-status-probe"]);
      expect(rows).toHaveLength(1);
      expect(rows[0]?.dirs).toEqual([dir]);
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });
});
