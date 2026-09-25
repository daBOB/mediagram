import { afterEach, describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";

import { startServer, type RunningServer } from "../src/server";
import { emptyIndex } from "./index-fixture";

let db: Database | null = null;
const servers: RunningServer[] = [];

afterEach(async () => {
  for (const server of servers.splice(0)) await server.close();
  db?.close();
  db = null;
});

describe("starting the listener", () => {
  test.each([
    ["127.0.0.1", "127.0.0.1"],
    ["127.0.0.2", "127.0.0.2"],
    ["0.0.0.0", "127.0.0.1"],
    ["::1", "[::1]"],
    ["::", "[::1]"],
  ])("%s advertises its reachable bound endpoint when the OS chooses the port", async (hostname, host) => {
    db = emptyIndex();
    const server = await startServer({
      db, hostname, port: 0,
      source: { stream: () => new ReadableStream<Uint8Array>() },
    });
    servers.push(server);

    expect(server.port).toBeGreaterThan(0);
    expect(server.baseUrl).toBe(`http://${host}:${server.port}`);
    const response = await fetch(`${server.baseUrl}/api/sets`);
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual([]);
  });

  test("an occupied loopback port rejects with the original bind error", async () => {
    db = emptyIndex();
    const options = {
      db,
      hostname: "127.0.0.1",
      source: { stream: () => new ReadableStream<Uint8Array>() },
    };
    const occupied = await startServer(options);
    servers.push(occupied);

    const attempt = startServer({ ...options, port: occupied.port }).then((server) => {
      servers.push(server);
      return server;
    });

    await expect(attempt).rejects.toMatchObject({ code: "EADDRINUSE", syscall: "listen" });
  }, 1000);
});
