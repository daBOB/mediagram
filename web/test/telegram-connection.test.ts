/**
 * Swapping the one Telegram client this process holds: restarts run one at a
 * time with the old client disconnected before the new one opens, a read
 * parked on a restart sees the client it produced, a failed open puts back
 * what was live, and every change of client moves the generation on.
 */

import { expect, test } from "bun:test";
import type { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";

function fake(name: string, log: string[] = []): Telegram {
  return {
    name,
    disconnect: async () => {
      log.push(`disconnect ${name}`);
    },
  } as never;
}

/** A promise settled from outside, standing in for an open still on the wire. */
function pending<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((settle) => {
    resolve = settle;
  });
  return { promise, resolve };
}

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

test("a second restart waits for the first, and each disconnects before it opens", async () => {
  const log: string[] = [];
  const connection = new TelegramConnection(fake("a", log));
  const b = fake("b", log);
  const c = fake("c", log);
  const openB = pending<Telegram>();

  const first = connection.restart(() => {
    log.push("open b");
    return openB.promise.then((telegram) => {
      log.push("b opened");
      return telegram;
    });
  });
  const second = connection.restart(async () => {
    log.push("open c");
    return c;
  });
  await settle();
  expect(log).toEqual(["disconnect a", "open b"]);

  openB.resolve(b);
  expect(await first).toBe(b);
  expect(await second).toBe(c);
  expect(log).toEqual(["disconnect a", "open b", "b opened", "disconnect b", "open c"]);
  expect(connection.current()).toBe(c);
});

test("a read during an open is parked, and is handed the new client rather than the old one", async () => {
  const connection = new TelegramConnection(fake("a"));
  const b = fake("b");
  const openB = pending<Telegram>();
  const restarting = connection.restart(() => openB.promise);
  await settle();

  const reading = connection.ready();
  expect(connection.current()).toBeNull();
  openB.resolve(b);
  expect(await reading).toBe(b);
  expect(await restarting).toBe(b);
});

test("an open that fails puts back the client that was live, and still fails the restart", async () => {
  const connection = new TelegramConnection(fake("a"));
  const back = fake("a again");
  const failure = new Error("API_ID_INVALID");

  const restart = connection.restart(
    async () => {
      throw failure;
    },
    async () => back,
  );
  await expect(restart).rejects.toBe(failure);
  expect(connection.current()).toBe(back);
  expect(await connection.ready()).toBe(back);
});

test("the generation moves on a channel switch, a restart that worked, and one that failed", async () => {
  const connection = new TelegramConnection(fake("a"));
  expect(connection.generation).toBe(0);

  const pointed = fake("a, another channel");
  connection.withChannel(() => pointed);
  expect(connection.current()).toBe(pointed);
  expect(connection.generation).toBe(1);

  await connection.restart(async () => fake("b"));
  expect(connection.generation).toBe(2);

  await connection
    .restart(async () => {
      throw new Error("AUTH_KEY_UNREGISTERED");
    })
    .catch(() => {});
  expect(connection.generation).toBe(3);
});
