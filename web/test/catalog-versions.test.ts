import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { mkdir, mkdtemp, readlink, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { cleanupCatalogDirectory, removeOtherVersions, swapCurrent } from "../src/package/catalog-versions";

let root: string;
beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), "catalog-versions-"));
});
afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

describe("catalog version cleanup", () => {
  test("a published current version survives removing obsolete and incoming directories", async () => {
    for (const name of ["v-100", "v-200", "incoming-300", "unrelated"]) await mkdir(join(root, name));
    await swapCurrent(root, "v-200");

    await removeOtherVersions(root, "v-200");

    expect(await readlink(join(root, "current"))).toBe("v-200");
    expect((await readdir(root)).sort()).toEqual(["current", "unrelated", "v-200"]);
  });

  test("a cleanup path that became a file is diagnosed without rejecting", async () => {
    const blocked = join(root, "not-a-directory");
    await writeFile(blocked, "file");
    const warning = spyOn(console, "warn").mockImplementation(() => {});
    try {
      await removeOtherVersions(join(blocked, "catalog"), "v-200");

      expect(warning).toHaveBeenCalledWith("catalog cleanup failed:", expect.objectContaining({ code: "ENOTDIR" }));
    } finally {
      warning.mockRestore();
    }
  });

  test("a directory removal failure is diagnosed without disturbing the published pointer", async () => {
    await mkdir(join(root, "v-200"));
    await swapCurrent(root, "v-200");
    const blocked = join(root, "not-a-directory");
    await writeFile(blocked, "file");
    const warning = spyOn(console, "warn").mockImplementation(() => {});
    try {
      await cleanupCatalogDirectory(join(blocked, "obsolete"));

      expect(warning).toHaveBeenCalledWith("catalog cleanup failed:", expect.objectContaining({ code: "ENOTDIR" }));
      expect(await readlink(join(root, "current"))).toBe("v-200");
    } finally {
      warning.mockRestore();
    }
  });
});
