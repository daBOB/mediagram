import { afterEach, describe, expect, test } from "bun:test";
import { chmod, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { readTelegramFile, writeTelegramFile, type TelegramFile } from "../src/settings/telegram-file";

const dirs: string[] = [];
async function tempDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), "telegram-file-"));
  dirs.push(dir);
  return dir;
}
afterEach(async () => {
  for (const dir of dirs.splice(0)) await rm(dir, { recursive: true, force: true });
});

const value: TelegramFile = {
  apiId: 123,
  apiHash: "abc",
  session: "sess",
  chatId: -1_001_234,
  accessHash: "9876543210",
  title: "Mediagram",
};

describe("telegram.json", () => {
  test("a missing file reads as absent", async () => {
    const dir = await tempDir();
    expect(await readTelegramFile(join(dir, "telegram.json"))).toBeNull();
  });

  test("a value round-trips, including a null session", async () => {
    const dir = await tempDir();
    const path = join(dir, "sub", "telegram.json");
    await writeTelegramFile(path, value);
    expect(await readTelegramFile(path)).toEqual(value);

    await writeTelegramFile(path, { ...value, session: null });
    expect((await readTelegramFile(path))?.session).toBeNull();
  });

  test("the file is written 0600 and its directory 0700", async () => {
    const dir = await tempDir();
    const path = join(dir, "priv", "telegram.json");
    await writeTelegramFile(path, value);
    expect((await stat(path)).mode & 0o777).toBe(0o600);
    expect((await stat(join(dir, "priv"))).mode & 0o777).toBe(0o700);
  });

  test("a write replaces the old file atomically, leaving no temp file behind", async () => {
    const dir = await tempDir();
    const path = join(dir, "telegram.json");
    await writeTelegramFile(path, value);
    await writeTelegramFile(path, { ...value, title: "Other" });
    const entries = await import("node:fs/promises").then((m) => m.readdir(dir));
    expect(entries).toEqual(["telegram.json"]);
    expect((await readTelegramFile(path))?.title).toBe("Other");
  });

  test("a malformed file is treated as absent rather than thrown", async () => {
    const dir = await tempDir();
    const path = join(dir, "telegram.json");
    await writeFile(path, "not json");
    expect(await readTelegramFile(path)).toBeNull();

    await writeFile(path, JSON.stringify({ apiId: 1 }));
    expect(await readTelegramFile(path)).toBeNull();
  });

  test("an unreadable file is treated as absent", async () => {
    const dir = await tempDir();
    const path = join(dir, "telegram.json");
    await writeTelegramFile(path, value);
    await chmod(path, 0o000);
    try {
      // Root can read anything regardless of mode; this assertion only holds
      // when the permission actually blocks the read.
      const isRoot = process.getuid?.() === 0;
      if (!isRoot) expect(await readTelegramFile(path)).toBeNull();
    } finally {
      await chmod(path, 0o600);
    }
  });
});
