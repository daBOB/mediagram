import { expect, spyOn, test } from "bun:test";
import { ServerResponse } from "node:http";
import { createConnection } from "node:net";
import { TelegramSource } from "../src/telegram/source";
import { TelegramConnection } from "../src/telegram/connection";
import { startServer } from "../src/server";
import { deferred, library, telegramBoundary } from "./application-fixture";

const tick = () => new Promise<void>((done) => setImmediate(done));

function sourceFor(iterate: () => AsyncGenerator<Uint8Array, void, unknown>) {
  const telegram = telegramBoundary([]);
  telegram.partMedia = async () => ({}) as never;
  telegram.client.iterDownload = iterate as never;
  return new TelegramSource(TelegramConnection.fixed(telegram));
}

function stream(iterate: () => AsyncGenerator<Uint8Array, void, unknown>) {
  const source = sourceFor(iterate);
  const body = source.stream(
    [{ span: { idx: 0, off: 0, len: 100 }, chatId: 1, messageId: 1 }],
    [{ partIdx: 0, offset: 0, headDrop: 0, take: 100 }], "SET",
  );
  return { source, reader: body.getReader() };
}

test("cancellation waits for asynchronous upstream iterator cleanup", async () => {
  const started = deferred<void>();
  const cleaning = deferred<void>();
  const finish = deferred<void>();
  let closed = false;
  const { source, reader } = stream(async function* () {
    try {
      started.resolve();
      yield new Uint8Array([1]);
    } finally {
      cleaning.resolve();
      await finish.promise;
      closed = true;
    }
  });
  await started.promise;
  await tick(); // Leave the first chunk queued, so no next pull is pending.
  let settled = false;
  const cancelled = reader.cancel().then(() => { settled = true; });
  try {
    await cleaning.promise;
    await tick();
    expect(settled).toBe(false);
    expect(closed).toBe(false);
  } finally { finish.resolve(); await cancelled; }
  expect(closed).toBe(true);
  expect(source.stats().failedReads).toBe(0);
});

test.each(["success", "cleanup after yield", "cleanup during pull"])(
  "cancelling an in-flight pull awaits and propagates %s", async (outcome) => {
    const pulling = deferred<void>();
    const next = deferred<void>();
    const cleaning = deferred<void>();
    const finish = deferred<void>();
    const cleanupError = new Error("iterator cleanup refused");
    let closed = false;
    const { source, reader } = stream(async function* () {
      try {
        yield new Uint8Array([1]);
        pulling.resolve();
        await next.promise;
        if (outcome === "cleanup during pull") return;
        yield new Uint8Array([2]);
      } finally {
        cleaning.resolve();
        await finish.promise;
        closed = true;
        if (outcome !== "success") throw cleanupError;
      }
    });
    expect((await reader.read()).value).toEqual(new Uint8Array([1]));
    await pulling.promise;
    let settled = false;
    const cancelled = reader.cancel().then(
      () => { settled = true; return { failure: undefined }; },
      (failure: unknown) => { settled = true; return { failure }; },
    );
    try {
      await tick();
      expect(settled).toBe(false);
      next.resolve();
      await cleaning.promise;
      await tick();
      expect(settled).toBe(false);
      expect(closed).toBe(false);
    } finally {
      next.resolve();
      finish.resolve();
      await cancelled;
      await tick();
    }
    expect((await cancelled).failure).toBe(outcome === "success" ? undefined : cleanupError);
    expect(closed).toBe(true);
    expect(source.stats().failedReads).toBe(0);
    expect(await reader.read()).toEqual({ done: true, value: undefined });
  },
);

test("HTTP abandonment owns a rejected upstream cleanup through reader cancellation", async () => {
  const next = deferred<void>();
  const cleaning = deferred<void>();
  const finish = deferred<void>();
  const closed = deferred<void>();
  const source = sourceFor(async function* () {
    try {
      yield new Uint8Array([1]);
      await next.promise;
      yield new Uint8Array([2]);
    } finally {
      cleaning.resolve();
      await finish.promise;
      closed.resolve();
      throw new Error("cleanup after disconnected HTTP reader");
    }
  });
  const db = library("Streaming");
  db.run("UPDATE parts SET chat_id = 1");
  const server = await startServer({ db, source });
  const received = deferred<void>();
  const abandoned = deferred<void>();
  const originalWrite = ServerResponse.prototype.write;
  let observed: ServerResponse | undefined;
  const write = spyOn(ServerResponse.prototype, "write").mockImplementation(function (this: ServerResponse, ...args: Parameters<typeof originalWrite>) {
    if (!observed && this.socket?.localPort === server.port) {
      observed = this;
      this.once("close", () => abandoned.resolve());
    }
    return originalWrite.apply(this, args);
  } as typeof originalWrite);
  const socket = createConnection({ host: "127.0.0.1", port: server.port });
  socket.on("error", () => {}); // Deliberate mid-response reset.
  socket.once("data", () => received.resolve());
  try {
    socket.write("GET /api/sets/01SET/stream HTTP/1.1\r\nHost: localhost\r\n\r\n");
    await received.promise;
    socket.destroy();
    await abandoned.promise;
    next.resolve();
    await cleaning.promise;
    finish.resolve();
    await closed.promise;
    await tick(); // A detached cleanup rejection would fail the runner here.
    expect(source.stats().failedReads).toBe(0);
  } finally {
    socket.destroy();
    next.resolve();
    finish.resolve();
    await server.close();
    write.mockRestore();
    db.close();
  }
});
