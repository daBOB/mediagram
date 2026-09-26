import { afterEach, describe, expect, test } from "bun:test";
import { chmod, mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { AdminGate, cookieValue, resolveAdminToken } from "../src/settings/admin-gate";

const dirs: string[] = [];
async function tempDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), "admin-gate-"));
  dirs.push(dir);
  return dir;
}
afterEach(async () => {
  for (const dir of dirs.splice(0)) await rm(dir, { recursive: true, force: true });
});

describe("resolveAdminToken", () => {
  test("an env token wins and nothing is written to disk", async () => {
    const dir = await tempDir();
    const path = join(dir, "admin-token");
    const token = await resolveAdminToken({ MEDIAGRAM_ADMIN_TOKEN: "from-env" }, path);
    expect(token).toBe("from-env");
    await expect(readFile(path)).rejects.toThrow();
  });

  test("without one, a token is created 0600 and reused on the next call", async () => {
    const dir = await tempDir();
    const path = join(dir, "admin-token");
    const first = await resolveAdminToken({}, path);
    expect(first.length).toBeGreaterThan(20);
    expect((await stat(path)).mode & 0o777).toBe(0o600);

    const second = await resolveAdminToken({}, path);
    expect(second).toBe(first);
  });
});

describe("cookieValue", () => {
  test("reads the named cookie out of a header with several", () => {
    expect(cookieValue("a=1; mediagram_admin=abc; b=2", "mediagram_admin")).toBe("abc");
    expect(cookieValue(null, "mediagram_admin")).toBeNull();
    expect(cookieValue("a=1", "mediagram_admin")).toBeNull();
  });
});

describe("AdminGate", () => {
  test("the right token unlocks; the cookie then reads as unlocked", () => {
    const gate = new AdminGate("secret");
    const result = gate.unlock("secret", "10.0.0.5", false);
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error("unreachable");
    const cookie = result.setCookie.split(";")[0]!;
    expect(gate.isUnlocked(cookie)).toBe(true);
    expect(cookie).not.toContain("Secure");
    expect(result.setCookie).toContain("HttpOnly");
    expect(result.setCookie).toContain("SameSite=Strict");
    expect(result.setCookie).toContain("Path=/api/settings");
  });

  test("secure is only set when asked", () => {
    const gate = new AdminGate("secret");
    const result = gate.unlock("secret", "10.0.0.5", true);
    if (!result.ok) throw new Error("unreachable");
    expect(result.setCookie).toContain("Secure");
  });

  test("the wrong token never unlocks", () => {
    const gate = new AdminGate("secret");
    const result = gate.unlock("wrong", "10.0.0.5", false);
    expect(result).toEqual({ ok: false, limited: false });
    expect(gate.isUnlocked(null)).toBe(false);
  });

  test("a cookie naming no session is locked", () => {
    const gate = new AdminGate("secret");
    expect(gate.isUnlocked("mediagram_admin=nonsense")).toBe(false);
  });

  test("a session expires after its ttl", () => {
    let now = 0;
    const gate = new AdminGate("secret", () => now);
    const result = gate.unlock("secret", "10.0.0.5", false);
    if (!result.ok) throw new Error("unreachable");
    const cookie = result.setCookie.split(";")[0]!;
    expect(gate.isUnlocked(cookie)).toBe(true);
    now += 12 * 60 * 60 * 1000 + 1;
    expect(gate.isUnlocked(cookie)).toBe(false);
  });

  test("lock ends the session", () => {
    const gate = new AdminGate("secret");
    const result = gate.unlock("secret", "10.0.0.5", false);
    if (!result.ok) throw new Error("unreachable");
    const cookie = result.setCookie.split(";")[0]!;
    gate.lock(cookie, false);
    expect(gate.isUnlocked(cookie)).toBe(false);
  });

  test("five wrong guesses in a window lock out a sixth, even a correct one", () => {
    let now = 0;
    const gate = new AdminGate("secret", () => now);
    for (let i = 0; i < 5; i++) {
      expect(gate.unlock("wrong", "1.2.3.4", false)).toEqual({ ok: false, limited: false });
    }
    expect(gate.unlock("secret", "1.2.3.4", false)).toEqual({ ok: false, limited: true });
  });

  test("the limit is per address", () => {
    let now = 0;
    const gate = new AdminGate("secret", () => now);
    for (let i = 0; i < 5; i++) gate.unlock("wrong", "1.2.3.4", false);
    expect(gate.unlock("secret", "5.6.7.8", false).ok).toBe(true);
  });

  test("the window clears after it passes", () => {
    let now = 0;
    const gate = new AdminGate("secret", () => now);
    for (let i = 0; i < 5; i++) gate.unlock("wrong", "1.2.3.4", false);
    now += 60_001;
    expect(gate.unlock("secret", "1.2.3.4", false).ok).toBe(true);
  });
});
