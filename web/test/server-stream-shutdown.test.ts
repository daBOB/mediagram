import { expect, spyOn, test } from "bun:test";
import { shutdownFor } from "../src/application/lifecycle";
import { startServer } from "../src/server";
import { TelegramSource } from "../src/telegram/source";
import { TelegramConnection } from "../src/telegram/connection";
import { deferred, library, telegramBoundary } from "./application-fixture";

async function within<T>(promise: Promise<T>, description = "cleanup"): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([promise, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error(`stream shutdown timed out: ${description}`)), 2000);
    })]);
  } finally { clearTimeout(timer); }
}

const turn = () => new Promise<void>((resolve) => setImmediate(resolve));

test.each([null, new Error("upstream cleanup refused"), undefined])("application shutdown awaits HTTP pull and generator cleanup (%s)", async (failure) => {
  const rejectCleanup = failure !== null;
  const db = library("Streaming");
  db.run("UPDATE parts SET chat_id = 1");
  const pulling = deferred<void>();
  const finishPull = deferred<void>();
  const cleaning = deferred<void>();
  const finishCleanup = deferred<void>();
  const order: string[] = [];
  const errors = spyOn(console, "error").mockImplementation(() => {});
  const telegram = telegramBoundary(order);
  telegram.partMedia = async () => ({}) as never;
  telegram.client.iterDownload = (async function* () {
    try {
      yield new Uint8Array([1]);
      pulling.resolve();
      await finishPull.promise;
      yield new Uint8Array([2]);
    } finally {
      cleaning.resolve();
      await finishCleanup.promise;
      order.push("upstream-cleaned");
      if (rejectCleanup) throw failure;
    }
  }) as never;
  const server = await startServer({ db, source: new TelegramSource(TelegramConnection.fixed(telegram)) });
  const controller = new AbortController();
  const request = fetch(`${server.baseUrl}/api/sets/01SET/stream`, { signal: controller.signal })
    .then((response) => response.arrayBuffer()).catch(() => {});
  const stop = shutdownFor({ timers: [], server, telegram });
  let stopped: Promise<void> | undefined;
  let completed = false;
  try {
    await within(pulling.promise, "upstream pull");
    stopped = stop().then(() => { completed = true; });
    await within(request, "HTTP disconnection");
    await turn();
    expect(completed).toBe(false);
    expect(order).toEqual([]);
    expect(server.close()).toBe(server.close());
    finishPull.resolve();
    await within(cleaning.promise, "upstream cleanup");
    await turn();
    expect(completed).toBe(false);
    expect(order).toEqual([]);
    finishCleanup.resolve();
    await within(stopped);
    expect(order).toEqual(["upstream-cleaned", "disconnect"]);
    expect(errors.mock.calls).toEqual(rejectCleanup ? [["stream cancellation failed", failure]] : []);
  } finally {
    finishPull.resolve();
    finishCleanup.resolve();
    controller.abort();
    await within(request);
    await within(stopped ?? stop());
    await within(server.close());
    db.close();
    errors.mockRestore();
  }
}, 7000);

test.each([new Error("source read failed"), undefined, NaN])("an errored stream reports its original failure only once (%s)", async (failure) => {
  const db = library("Failed stream");
  db.run("UPDATE parts SET chat_id = 1");
  const errors = spyOn(console, "error").mockImplementation(() => {});
  const server = await startServer({
    db,
    source: { stream: () => new ReadableStream<Uint8Array>({ start(controller) { controller.error(failure); } }) },
  });
  const controller = new AbortController();
  const request = fetch(`${server.baseUrl}/api/sets/01SET/stream`, { signal: controller.signal })
    .then((response) => response.arrayBuffer()).catch(() => {});
  try {
    await within(request, "errored HTTP response");
    await within(server.close());
    expect(errors.mock.calls).toEqual([["stream aborted mid-body", failure]]);
  } finally {
    controller.abort();
    await within(request);
    await within(server.close());
    db.close();
    errors.mockRestore();
  }
}, 7000);

test("close awaits a body produced by routing after the socket was already closed", async () => {
  const db = library("Delayed routing");
  const routing = deferred<void>();
  const finishRoute = deferred<void>();
  const cancelling = deferred<void>();
  const finishCancellation = deferred<void>();
  const server = await startServer({
    db,
    source: { stream: () => { throw new Error("unexpected media route"); } },
    status: async () => {
      routing.resolve();
      await finishRoute.promise;
      return {
        status: 200, headers: { "content-length": "10" },
        body: new ReadableStream<Uint8Array>({ cancel: async () => {
          cancelling.resolve();
          await finishCancellation.promise;
        } }),
      };
    },
  });
  const controller = new AbortController();
  const request = fetch(`${server.baseUrl}/status`, { signal: controller.signal }).catch(() => {});
  let stopped: Promise<void> | undefined;
  let completed = false;
  try {
    await within(routing.promise, "route entry");
    stopped = server.close().then(() => { completed = true; });
    await within(request, "HTTP disconnection");
    expect(completed).toBe(false);
    finishRoute.resolve();
    await within(cancelling.promise, "late body cancellation");
    await turn();
    expect(completed).toBe(false);
    finishCancellation.resolve();
    await within(stopped);
  } finally {
    finishRoute.resolve();
    finishCancellation.resolve();
    controller.abort();
    await within(request);
    await within(stopped ?? server.close());
    db.close();
  }
}, 7000);
