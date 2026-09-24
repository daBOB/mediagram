import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { startPlayer } from "../src/index";
import { startServer, type RunningServer } from "../src/server";
import { configIn, library, snapshot, telegramBoundary } from "./application-fixture";

let root: string;
const stops: Array<() => Promise<void>> = [];
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "application-startup-")); });
afterEach(async () => {
  for (const stop of stops.splice(0).reverse()) await stop();
  await rm(root, { recursive: true, force: true });
});

async function titles(server: RunningServer): Promise<string[]> {
  const response = await fetch(`http://127.0.0.1:${server.port}/api/sets`);
  return ((await response.json()) as Array<{ title: string }>).map((set) => set.title);
}

describe("the production application startup", () => {
  test("catches an index published after the initial read and before subscription", async () => {
    const order: string[] = [];
    let newest = snapshot("Before", 100);
    const player = await startPlayer(configIn(root), {
      connect: async () => { order.push("connect"); return telegramBoundary(order); },
      findIndex: async () => { order.push(`read-${newest.pushedAt}`); return newest; },
      detectEncoder: async () => {
        // No callback exists yet: this update cannot set a missed-event flag.
        newest = snapshot("After", 200);
        return { kind: "software", name: "libx264" };
      },
      listen: () => { order.push("subscribe"); return () => { order.push("unsubscribe"); }; },
      fetchPosters: async () => ({ ok: false, reason: "art disabled in this test" }),
    });
    stops.push(player.stop);
    await player.ready;

    expect(await titles(player.server)).toEqual(["After"]);
    expect(order.slice(0, 4)).toEqual(["connect", "read-100", "subscribe", "read-200"]);
    await player.stop();
    expect(order.slice(-2)).toEqual(["unsubscribe", "disconnect"]);
  });

  test("an unavailable channel serves the local SQLite catalog through the real listener", async () => {
    const db = library("Local");
    await writeFile(join(root, "library.db"), db.serialize());
    db.close();
    const order: string[] = [];
    const player = await startPlayer(configIn(root), {
      connect: async () => telegramBoundary(order), findIndex: async () => "nothing-pinned",
      detectEncoder: async () => ({ kind: "software", name: "libx264" }),
      listen: () => () => { order.push("unsubscribe"); },
      fetchPosters: async () => { throw new Error("local catalogs do not fetch channel art"); },
    });
    stops.push(player.stop);
    await player.ready;
    expect(await titles(player.server)).toEqual(["Local"]);
    const facts = await (await fetch(`http://127.0.0.1:${player.server.port}/api/player`)).json();
    expect(facts).toMatchObject({ catalog: { origin: "local" } });
  });

  test("a configured package failure disconnects before exposing a listener or falling back to a channel", async () => {
    const order: string[] = [];
    const config = { ...configIn(root), packageUrl: "https://catalog.test", packageKey: Buffer.alloc(32).toString("base64") };
    await expect(startPlayer(config, {
      connect: async () => telegramBoundary(order),
      findIndex: async () => { throw new Error("must not read the channel"); },
      detectEncoder: async () => { throw new Error("must not probe the encoder"); },
      fetch: async () => new Response(null, { status: 503 }),
    })).rejects.toThrow(/no catalog/);
    expect(order).toEqual(["disconnect"]);
  });

  test("a refused listener bind cleans the subscription and connection without closing the occupied listener", async () => {
    const db = library("Occupied");
    const occupied = await startServer({ db, source: { stream: () => new ReadableStream<Uint8Array>() } });
    stops.push(async () => { await occupied.close(); db.close(); });
    const order: string[] = [];
    await expect(startPlayer({ ...configIn(root), port: occupied.port }, {
      connect: async () => telegramBoundary(order), findIndex: async () => snapshot("Candidate", 100),
      detectEncoder: async () => ({ kind: "software", name: "libx264" }),
      listen: () => { order.push("subscribe"); return () => { order.push("unsubscribe"); }; },
    })).rejects.toMatchObject({ code: "EADDRINUSE" });
    expect(order).toEqual(["subscribe", "unsubscribe", "disconnect"]);
    expect(await titles(occupied)).toEqual(["Occupied"]);
  });
});
