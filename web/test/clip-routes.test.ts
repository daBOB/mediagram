/** The clip routes, at the router, over a registry that never runs ffmpeg. */

import { describe, expect, test } from "bun:test";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { WatchState } from "../src/state/store";
import { createRouter } from "../src/routes";
import { ClipJobs, type ClipProcess } from "../src/transcode/clip-jobs";
import type { PlayerRequest } from "../src/http/contracts";
import { emptyIndex } from "./index-fixture";

function harness() {
  const index = emptyIndex();
  index.run(
    `INSERT INTO sets(set_id, kind, title, container, duration, total, part_count, status, created_at, spec_version)
     VALUES ('film1', 'movie', 'A Film', 'mp4', 7200, 10, 1, 'complete', 1700000000, 3)`,
  );
  index.run(`INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
     VALUES ('film1', 0, 0, 10, -1001, 100, 'done')`);
  const state = new WatchState(join(mkdtempSync(join(tmpdir(), "clip-state-")), "state.db"));
  const profile = state.createProfile("Me")!.id;
  const commands: string[][] = [];
  const exits: ((code: number) => void)[] = [];
  const directory = mkdtempSync(join(tmpdir(), "clip-routes-"));
  const jobs = new ClipJobs({
    baseUrl: "http://x", pacedReads: false, directory,
    spawn: (command) => {
      commands.push(command);
      let exit!: (code: number) => void;
      let err!: ReadableStreamDefaultController<Uint8Array>;
      const proc: ClipProcess = {
        stdout: new ReadableStream({ start: (c) => { exits.push((code) => { c.close(); err.close(); exit(code); }); } }),
        stderr: new ReadableStream({ start: (c) => { err = c; } }),
        exited: new Promise<number>((r) => { exit = r; }),
        kill: () => exit(143),
      };
      return proc;
    },
  });
  const route = createRouter({
    db: index, state, clips: jobs,
    source: { stream: () => new ReadableStream<Uint8Array>({ start: (c) => c.close() }) },
  });
  const call = async (method: string, path: string, body?: unknown, extra: Partial<PlayerRequest> = {}) => {
    const response = await route({
      method, path, range: null,
      ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }),
      ...extra,
    });
    return response;
  };
  return { call, profile, jobs, commands, exits };
}

const good = { setId: "film1", from: 10, to: 40, audio: 0 };

describe("clip routes", () => {
  test("starting a clip answers 202 with an id that reports status", async () => {
    const { call, profile } = harness();
    const response = await call("POST", `/api/profiles/${profile}/clips`, good);
    expect(response.status).toBe(202);
    const { id } = JSON.parse(new TextDecoder().decode(response.body as Uint8Array));
    const status = await call("GET", `/api/clips/${id}`);
    expect(JSON.parse(new TextDecoder().decode(status.body as Uint8Array))).toMatchObject({ id, state: "running" });
    expect((await call("GET", "/api/clips/ffffffffffffffff")).status).toBe(404);
  });

  test.each([
    ["from not finite", { ...good, from: "x" }],
    ["negative from", { ...good, from: -1 }],
    ["shorter than a second", { ...good, to: 10.5 }],
    ["longer than 180 s", { ...good, to: 191 }],
    ["fractional audio", { ...good, audio: 0.5 }],
    ["negative audio", { ...good, audio: -1 }],
    ["missing audio", { setId: "film1", from: 10, to: 40 }],
  ])("%s is a 400", async (_name, body) => {
    const { call, profile } = harness();
    expect((await call("POST", `/api/profiles/${profile}/clips`, body)).status).toBe(400);
  });

  test("an unknown set or profile is a 404", async () => {
    const { call, profile } = harness();
    expect((await call("POST", `/api/profiles/${profile}/clips`, { ...good, setId: "nope" })).status).toBe(404);
    expect((await call("POST", "/api/profiles/ghost/clips", good)).status).toBe(404);
  });

  test("a clip while one runs is a 409 with the busy copy", async () => {
    const { call, profile } = harness();
    await call("POST", `/api/profiles/${profile}/clips`, good);
    const response = await call("POST", `/api/profiles/${profile}/clips`, good);
    expect(response.status).toBe(409);
    expect(new TextDecoder().decode(response.body as Uint8Array)).toContain("Another clip is being made. Try again in a moment.");
  });

  test("the file is 404 while running, 403 for another profile, and served when done", async () => {
    const { call, profile, commands, exits } = harness();
    const { id } = JSON.parse(new TextDecoder().decode(
      (await call("POST", `/api/profiles/${profile}/clips`, good)).body as Uint8Array));
    expect((await call("GET", `/api/clips/${id}/file`, undefined, { profile })).status).toBe(404);

    writeFileSync(commands[0]!.at(-1)!, "mp4data");
    exits[0]!(0);
    await new Promise((r) => setTimeout(r, 5));

    expect((await call("GET", `/api/clips/${id}/file`, undefined, { profile: "someone-else" })).status).toBe(403);
    const file = await call("GET", `/api/clips/${id}/file`, undefined, { profile });
    expect(file.status).toBe(200);
    expect(file.headers["content-type"]).toBe("video/mp4");
    expect(file.headers["content-disposition"]).toBe("attachment; filename*=UTF-8''A%20Film%20%E2%80%93%200h00m10s.mp4");
    expect(file.headers["content-length"]).toBe("7");
  });

  test("listing a profile's clips for a set", async () => {
    const { call, profile } = harness();
    await call("POST", `/api/profiles/${profile}/clips`, good);
    const list = await call("GET", `/api/profiles/${profile}/clips`, undefined, { setId: "film1" });
    expect(JSON.parse(new TextDecoder().decode(list.body as Uint8Array))).toHaveLength(1);
  });
});
