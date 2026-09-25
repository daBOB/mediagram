/**
 * A viewer who walks away mid-stream.
 *
 * Writing to a socket that is full returns false and the write is finished by
 * a `drain` event — which never arrives if the viewer closed the tab instead.
 * The handler waiting for it stays suspended for the life of the process, so
 * the download it was driving is never cancelled and its `finally` never runs.
 * Every seek and every closed tab on a slow link left one behind.
 */

import { describe, expect, test } from "bun:test";
import type { Database } from "bun:sqlite";
import { EventEmitter } from "node:events";
import { ServerResponse } from "node:http";
import { Socket } from "node:net";

import { startServer, write } from "../src/server";
import type { ByteSource } from "../src/http/stream";
import { emptyIndex } from "./index-fixture";
import { deferred } from "./application-fixture";

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
  readonly cancelled = deferred<void>();

  stream(): ReadableStream<Uint8Array> {
    const chunk = new Uint8Array(256 * 1024);
    return new ReadableStream<Uint8Array>({
      pull: (controller) => void controller.enqueue(chunk),
      cancel: () => {
        this.cancelled.resolve();
      },
    });
  }
}

async function within<T>(promise: Promise<T>, description: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout>;
  try {
    return await Promise.race([promise, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out: ${description}`)), 3000);
    })]);
  } finally {
    clearTimeout(timer!);
  }
}

describe("a write that cannot complete", () => {
  /** A response that is always full and never drains. */
  function stuckResponse() {
    const emitter = new EventEmitter();
    return Object.assign(emitter, { write: () => false }) as unknown as ServerResponse;
  }

  test("a socket that closes settles the write rather than suspending it", async () => {
    const response = stuckResponse();
    const pending = write(response, new Uint8Array(10));
    (response as unknown as EventEmitter).emit("close");
    await expect(within(pending, "closed write")).rejects.toThrow("the reader went away");
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
    const db = index();
    const source = new EndlessSource();
    const server = await startServer({ db, source });
    const socket = new Socket();
    let delivered = 0;
    socket.on("data", (chunk) => { delivered += chunk.length; });
    socket.pause();
    const connected = deferred<void>();
    const blocked = deferred<{ response: ServerResponse; drained: () => boolean }>();
    const originalWrite = ServerResponse.prototype.write;
    const observers = new Map<ServerResponse, () => void>();
    let observed: ServerResponse | undefined;
    // Observe the real socket's return value; bytes and callbacks are unchanged.
    ServerResponse.prototype.write = function (this: ServerResponse, ...args: Parameters<typeof originalWrite>) {
      const flushed = originalWrite.apply(this, args);
      if (!flushed && this.req.url === `/api/sets/${SET}/stream`
        && this.socket?.localPort === server.port) {
        let drained = false;
        const onDrain = () => { drained = true; };
        this.once("drain", onDrain);
        observers.set(this, onDrain);
        setImmediate(() => {
          // Ignore transient buffering that drained in this IO turn. A paused
          // reader eventually leaves a real write waiting for socket capacity.
          if (!drained && !this.destroyed && this.listenerCount("drain") > 1) {
            blocked.resolve({ response: this, drained: () => drained });
          }
        });
      }
      return flushed;
    } as typeof originalWrite;
    socket.once("connect", () => connected.resolve());
    socket.on("error", () => {}); // A deliberate mid-response reset is expected.
    try {
      socket.connect(server.port, "127.0.0.1");
      await within(connected.promise, "TCP connection");
      socket.write(`GET /api/sets/${SET}/stream HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n`);
      const pending = await within(blocked.promise, "real response backpressure");
      observed = pending.response;
      expect(socket.isPaused()).toBe(true);
      expect(delivered).toBe(0);
      expect(pending.drained()).toBe(false);
      expect(observed.listenerCount("drain")).toBeGreaterThan(1);
      const closed = deferred<void>();
      observed.once("close", () => closed.resolve());
      socket.destroy();
      await within(closed.promise, "server response close");
      expect(pending.drained()).toBe(false);
      await within(source.cancelled.promise, "upstream cancellation");
      // Let rejection continuations run, including pump's finally block.
      await new Promise<void>((resolve) => setImmediate(resolve));
      observed.off("drain", observers.get(observed)!);
      expect(observed.listenerCount("drain")).toBe(0);
      expect(observed.listenerCount("close")).toBe(0);
      expect(observed.listenerCount("error")).toBe(0);
    } finally {
      ServerResponse.prototype.write = originalWrite;
      socket.destroy();
      for (const [response, listener] of observers) {
        response.off("drain", listener);
        // Also release a deliberately broken implementation during mutation
        // checks, so a failing test cannot leave its pump suspended.
        response.emit("drain");
      }
      await server.close();
      db.close();
    }
  });
});
