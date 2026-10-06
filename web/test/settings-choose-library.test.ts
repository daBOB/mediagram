/**
 * Choosing a library in Settings, over the real runtime, follower, connection
 * and server: a channel is proved by serving its index before anything the
 * player follows moves to it, so one without an index leaves the player
 * entirely on the library it had.
 *
 * Only Telegram is faked, at the account's dialog listing and at finding a
 * channel's newest index.
 */

import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Api } from "teleproto";
import { CatalogFollower } from "../src/application/catalog-follow";
import { FollowedChannel, UpdatesBinding } from "../src/application/telegram-binding";
import type { FoundIndex } from "../src/channel-index/find-newest-channel-index";
import type { NoIndex } from "../src/channel-index/pick-newest-index";
import { EXPECTED_SCHEMA } from "../src/catalog";
import { startServer, type RunningServer } from "../src/server";
import { SettingsRuntime } from "../src/settings/context";
import { Settings } from "../src/state/settings";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";
import { library, snapshot } from "./application-fixture";

const A = { bare: 111, chatId: -1_000_000_000_111, accessHash: 11n, title: "Channel A" };
const B = { bare: 222, chatId: -1_000_000_000_222, accessHash: 22n, title: "Channel B" };

let dir: string;
let server: RunningServer | undefined;
let follower: CatalogFollower | undefined;
beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "settings-choose-library-"));
});
afterEach(async () => {
  await follower?.stopFollowing();
  await server?.close();
  follower?.close();
  follower = undefined;
  server = undefined;
  await rm(dir, { recursive: true, force: true });
});

function dialog(channel: typeof A) {
  return {
    entity: new Api.Channel({
      id: channel.bare as never, title: channel.title, photo: new Api.ChatPhotoEmpty(), date: 0,
      accessHash: channel.accessHash as never,
    }),
  };
}

async function titles(): Promise<string[]> {
  const response = await fetch(`http://127.0.0.1:${server!.port}/api/sets`);
  return ((await response.json()) as Array<{ title: string }>).map((set) => set.title);
}

test("a channel without an index leaves the player on its library; one with an index moves everything", async () => {
  const warnings = spyOn(console, "warn").mockImplementation(() => {});
  try {
    const client = {
      iterDialogs: async function* () { yield dialog(A); yield dialog(B); },
      session: { save: () => "session" },
    };
    const following = Telegram.withChannel({ client } as unknown as Telegram, A.chatId, A.accessHash);
    const connection = new TelegramConnection(following);
    const channel = new FollowedChannel(A.chatId, A.accessHash, A.title);
    const indexes = new Map<number, FoundIndex | NoIndex>([[A.bare, snapshot("Before", 100)], [B.bare, "not-an-index"]]);
    const asked: number[] = [];
    const findIndex = async (telegram: Telegram) => {
      const bare = Number(telegram.peer.channelId);
      asked.push(bare);
      return indexes.get(bare)!;
    };
    const listened: number[] = [];

    const before = library("Before");
    server = await startServer({ db: before, source: { stream: () => new ReadableStream<Uint8Array>() } });
    const facts = { catalog: { origin: "channel" as const, publishedAt: 100_000, refresh: null, reason: null, schema: EXPECTED_SCHEMA, sets: 1, posters: 0 } };
    follower = new CatalogFollower({
      db: before, catalog: { dir: null, origin: "channel", publishedAt: 100_000, refresh: "updated", reason: null },
      root: join(dir, "channel"), find: () => connection.ready().then((t) => (t ? findIndex(t) : "nothing-pinned" as const)),
      server, facts, events: { catalogChanged: () => {} },
      fetchPosters: async () => ({ ok: false, reason: "art unavailable" }), posterCount: () => 0,
    });
    const telegramFilePath = join(dir, "telegram.json");
    const runtime = new SettingsRuntime(
      {
        connection, channel, follower, findIndex,
        updatesBinding: new UpdatesBinding(connection, channel, "device", null, follower, (_, { channel: bare }) => {
          listened.push(bare);
          return () => {};
        }),
        settings: new Settings(null),
        cache: null,
        channelCatalogDir: join(dir, "catalogs"),
        telegramFilePath,
      },
      { apiId: 1, apiHash: "a".repeat(32) },
    );
    const listing = await runtime.listLibraries();
    if (!listing.ok) throw new Error(listing.error);
    const handleB = listing.libraries.find((entry) => entry.title === B.title)!.handle;

    expect(await runtime.chooseLibrary(handleB)).toEqual({ ok: false, error: "nothing pinned in the channel is a library index" });
    expect(connection.current()).toBe(following);
    expect(channel).toMatchObject({ chatId: A.chatId, accessHash: A.accessHash, title: A.title });
    expect(listened).toEqual([]);
    expect(existsSync(telegramFilePath)).toBe(false);
    expect(await titles()).toEqual(["Before"]);
    asked.length = 0;
    await follower.refresh();
    expect(asked).toEqual([A.bare]);

    indexes.set(B.bare, snapshot("From B", 5, "01FROMB"));
    expect(await runtime.chooseLibrary(handleB)).toEqual({ ok: true, title: B.title, sets: 1 });
    expect(Number(connection.current()!.peer.channelId)).toBe(B.bare);
    expect(channel).toMatchObject({ chatId: B.chatId, accessHash: B.accessHash, title: B.title });
    expect(listened).toEqual([B.bare]);
    expect(JSON.parse(await readFile(telegramFilePath, "utf8"))).toMatchObject({ chatId: B.chatId, title: B.title });
    expect(await titles()).toEqual(["From B"]);
  } finally {
    warnings.mockRestore();
  }
});
