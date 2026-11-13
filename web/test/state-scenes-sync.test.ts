/**
 * Marked scenes on the watch state and the sync record: the store, the
 * merge, hostile parsing, and the two routes.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { createStateRouter } from "../src/state/routes";
import { mergeStates } from "../src/state/merge";
import type { SceneRow } from "../src/state/scenes-record";
import { parseRecord, SYNC_FORMAT, type SyncRecord } from "../src/state/sync-record";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
const tempPath = () => {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-scenes-sync-"));
  dirs.push(dir);
  return join(dir, "state.db");
};
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

const record = (device: string, scenes: SceneRow[]): SyncRecord => ({
  format: SYNC_FORMAT,
  device,
  writtenAt: 1,
  profiles: [{ name: "André", progress: [], watched: [], scenes }],
});
const row = (updatedAt: number, removed = false): SceneRow =>
  ({ id: "s1", setId: "01A", at: 61.5, updatedAt, ...(removed ? { removed: true as const } : {}) });

describe("scene marks in the store", () => {
  test("marking a scene shows it in the snapshot, newest first", async () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    const first = state.markScene(me, "01A", 61.5)!;
    await Bun.sleep(3);
    const second = state.markScene(me, "01A", 3600)!;

    const scenes = state.snapshot(me).scenes;
    expect(scenes.map((s) => s.id)).toEqual([second, first]);
    expect(first).not.toBe(second);
    expect(first).toHaveLength(16);
    expect(scenes.map((s) => s.at)).toEqual([3600, 61.5]);
    state.close();
  });

  test("refuses an unknown profile and a bad second", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    expect(state.markScene("nobody", "01A", 1)).toBeNull();
    expect(state.markScene(me, "01A", -1)).toBeNull();
    expect(state.markScene(me, "01A", Number.NaN)).toBeNull();
    state.close();
  });

  test("removing a scene leaves a tombstone that the record carries", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    const id = state.markScene(me, "01A", 10)!;
    const before = Date.now();
    expect(state.removeScene(me, id)).toBe(true);
    expect(state.removeScene(me, id)).toBe(false);

    expect(state.snapshot(me).scenes).toEqual([]);
    const sent = state.exportRecord("laptop").profiles[0]!.scenes!.find((r) => r.id === id)!;
    expect(sent).toMatchObject({ id, setId: "01A", at: 10, removed: true });
    expect(sent.updatedAt).toBeGreaterThanOrEqual(before);
    state.close();
  });

  test("a scene marked on another device arrives through importMerged", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    state.importMerged(mergeStates([record("phone", [row(1000)])]));

    expect(state.snapshot(me).scenes).toEqual([{ id: "s1", setId: "01A", at: 61.5, markedAt: 1000 }]);
    state.close();
  });

  test("a later removal elsewhere takes it off here", () => {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    state.importMerged(mergeStates([record("phone", [row(1000)])]));
    state.importMerged(mergeStates([record("phone", [row(1000)]), record("tv", [row(2000, true)])]));

    expect(state.snapshot(me).scenes).toEqual([]);
    expect(state.exportRecord("laptop").profiles[0]!.scenes).toEqual([row(2000, true)]);
    state.close();
  });
});

describe("merging and parsing scene rows", () => {
  test("merge keeps the later of two edits to one id, removal included", () => {
    const removedLater = mergeStates([record("a", [row(1000)]), record("b", [row(2000, true)])]);
    expect(removedLater.profiles[0]!.scenes).toEqual([row(2000, true)]);
    const markedLater = mergeStates([record("a", [row(3000)]), record("b", [row(2000, true)])]);
    expect(markedLater.profiles[0]!.scenes).toEqual([row(3000)]);
  });

  test("parseRecord drops a bad scene row and keeps the rest", () => {
    const text = (scenes?: unknown[]) =>
      JSON.stringify({ format: SYNC_FORMAT, device: "d", writtenAt: 1, profiles: [{ name: "A", progress: [], watched: [], scenes }] });
    const good = { id: "s1", setId: "01A", at: 0.25, updatedAt: 5 };
    const parsed = parseRecord(text([
      { id: "x", setId: "01A", updatedAt: 5 },
      { id: "x", setId: "01A", at: -1, updatedAt: 5 },
      { id: 5, setId: "01A", at: 1, updatedAt: 5 },
      { id: "x", setId: "", at: 1, updatedAt: 5 },
      good,
    ]));
    expect(parsed!.profiles[0]!.scenes).toEqual([good]);
    expect(parseRecord(text())!.profiles[0]!.scenes).toBeUndefined();
  });
});

describe("scene routes", () => {
  function route() {
    const state = new WatchState(tempPath());
    const me = state.createProfile("André")!.id;
    const handle = createStateRouter({ state, isPlayable: (setId) => setId === "01A" });
    const call = (path: string, method: string, body?: unknown) =>
      handle({ method, path, range: null, ...(body === undefined ? {} : { body: JSON.stringify(body), contentType: "application/json" }) })!;
    return { state, me, call };
  }

  test("POST scenes returns the id; 404 for an unknown profile or set; 400 for at = \"x\"", async () => {
    const { state, me, call } = route();
    const made = call(`/api/profiles/${me}/scenes`, "POST", { setId: "01A", at: 12.345 });
    expect(made.status).toBe(201);
    const { id } = JSON.parse(await new Response(made.body).text());
    expect(state.snapshot(me).scenes[0]).toMatchObject({ id, at: 12.345 });

    expect(call(`/api/profiles/nobody/scenes`, "POST", { setId: "01A", at: 1 }).status).toBe(404);
    expect(call(`/api/profiles/${me}/scenes`, "POST", { setId: "01Z", at: 1 }).status).toBe(404);
    expect(call(`/api/profiles/${me}/scenes`, "POST", { setId: "01A", at: "x" }).status).toBe(400);
    state.close();
  });

  test("DELETE removes a live mark; an unknown id is 404", () => {
    const { state, me, call } = route();
    const id = state.markScene(me, "01A", 5)!;
    expect(call(`/api/profiles/${me}/scenes/${id}`, "DELETE").status).toBe(204);
    expect(call(`/api/profiles/${me}/scenes/${id}`, "DELETE").status).toBe(404);
    expect(call(`/api/profiles/${me}/scenes/nope`, "DELETE").status).toBe(404);
    state.close();
  });
});
