/**
 * A viewer who walks away mid-stream.
 *
 * Writing to a socket that is full returns false and the write is finished by
 * a `drain` event — which never arrives if the viewer closed the tab instead.
 * The handler waiting for it stays suspended for the life of the process, so
 * the download it was driving is never cancelled and its `finally` never runs.
 * Every seek and every closed tab on a slow link left one behind.
 */

import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { EventEmitter } from "node:events";
import type { ServerResponse } from "node:http";

import { startServer, write, type RunningServer } from "../src/server";
import type { ByteSource } from "../src/routes";
import { emptyIndex } from "./index-fixture";

const SET = "01SET0000000000000000009";
const SIZE = 64 * 1024 * 1024;

function index(): Database {
  const db = emptyIndex();
  db.run(
    `INSERT INTO sets(set_id, kind, title, year, container, vcodec, acodec,
                      duration, total, part_count, status, created_at, spec_version)
     VALUES (?, 'movie', 'Big', 1999, 'mp4', 'h264', 'aac', 3600, ?, 1, 'complete', 1700000000, 3)`,
    [SET, SIZE],
  );
  db.run(
    `INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
     VALUES (?, 0, 0, ?, -1001, 100, 'done')`,
    [SET, SIZE],
  );
  return db;
}

/** Produces bytes forever, and says whether it was cancelled. */
class EndlessSource implements ByteSource {
  cancelled = false;

  stream(): ReadableStream<Uint8Array> {
    const chunk = new Uint8Array(256 * 1024);
    return new ReadableStream<Uint8Array>({
      pull: (controller) => void controller.enqueue(chunk),
      cancel: () => {
        this.cancelled = true;
      },
    });
  }
}

let server: RunningServer;
const source = new EndlessSource();

beforeAll(async () => {
  server = await startServer({ db: index(), source });
});
afterAll(async () => {
  await server.close();
});

describe("a write that cannot complete", () => {
  /** A response that is always full and never drains. */
  function stuckResponse() {
    const emitter = new EventEmitter();
    return Object.assign(emitter, { write: () => false }) as unknown as ServerResponse;
  }

  test("a socket that closes settles the write rather than suspending it", async () => {
    const response = stuckResponse();
    const pending = write(response, new Uint8Array(10));
    let settled = false;
    void pending.then(
      () => (settled = true),
      () => (settled = true),
    );

    (response as unknown as EventEmitter).emit("close");
    await Bun.sleep(10);

    expect(settled).toBe(true);
  });

  test("an errored socket settles it too", async () => {
    const response = stuckResponse();
    const pending = write(response, new Uint8Array(10));

    (response as unknown as EventEmitter).emit("error", new Error("reset"));

    await expect(pending).rejects.toThrow();
  });

  test("a drain resolves it, and nothing is left listening", async () => {
    const response = stuckResponse();
    const emitter = response as unknown as EventEmitter;
    const pending = write(response, new Uint8Array(10));

    emitter.emit("drain");
    await pending;

    // Left attached, these accumulate one set per chunk of every stream.
    expect(emitter.listenerCount("close")).toBe(0);
    expect(emitter.listenerCount("error")).toBe(0);
    expect(emitter.listenerCount("drain")).toBe(0);
  });
});

describe("a reader that goes away mid-body", () => {
  test("the download is cancelled rather than paid for", async () => {
    // Reads nothing, so the socket fills and the server is waiting on drain.
    const socket = await Bun.connect({
      hostname: "127.0.0.1",
      port: server.port,
      socket: { data: () => {}, open: () => {}, error: () => {} },
    });
    socket.write(
      `GET /api/sets/${SET}/stream HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n`,
    );

    await Bun.sleep(300);
    socket.terminate();

    // The cancel only runs if the pending write settles when the socket dies.
    for (let waited = 0; waited < 3000 && !source.cancelled; waited += 50) {
      await Bun.sleep(50);
    }
    expect(source.cancelled).toBe(true);
  });
});
