import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { chmod, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fetchPostersForIndex } from "../src/channel-index/fetch-posters-for-index";

let dir: string;
beforeAll(async () => {
  dir = await mkdtemp(join(tmpdir(), "posters-cmd-"));
});
afterAll(async () => {
  await rm(dir, { recursive: true, force: true });
});

/** A stand-in for `mediagram` that records its arguments and answers as told. */
async function fakeCommand(script: string): Promise<string> {
  const path = join(dir, `fake-${Math.random().toString(36).slice(2)}`);
  await writeFile(path, `#!/bin/sh\n${script}\n`);
  await chmod(path, 0o755);
  return path;
}

describe("fetching posters for a snapshot", () => {
  test("runs `posters --index <snapshot>` and reports its summary line", async () => {
    const log = join(dir, "args");
    const command = await fakeCommand(`echo "$@" > ${log}; echo working; echo "3 poster(s) in x: 1 fetched, 2 already held"`);
    const outcome = await fetchPostersForIndex(command, "/snap/library.db");
    expect(outcome).toEqual({ ok: true, said: "3 poster(s) in x: 1 fetched, 2 already held" });
    expect((await Bun.file(log).text()).trim()).toBe("posters --index /snap/library.db");
  });

  test("a failing run says why, in its own words", async () => {
    const command = await fakeCommand(`echo "no TMDB key configured" >&2; exit 1`);
    expect(await fetchPostersForIndex(command, "/snap/library.db")).toEqual({
      ok: false,
      reason: `\`${command} posters\` exited 1: no TMDB key configured`,
    });
  });

  test("a machine without the command is told so, not thrown at", async () => {
    const outcome = await fetchPostersForIndex(join(dir, "no-such-command"), "/snap/library.db");
    expect(outcome.ok).toBe(false);
  });
});
