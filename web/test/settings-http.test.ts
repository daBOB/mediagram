/**
 * `/api/settings`, driven through its router over the real runtime, account
 * actions and admin gate: who is answered at all, what the lock refuses, and
 * what a change that took effect but could not be saved, or a library that
 * could not be proved, answers and leaves behind.
 *
 * Only Telegram and the catalog follower are faked, at the edges where they
 * would reach the network.
 */

import { afterEach, beforeEach, describe, expect, spyOn, test } from "bun:test";
import { existsSync } from "node:fs";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Api } from "teleproto";
import type { RefreshOutcome } from "../src/application/catalog-follow";
import { FollowedChannel, UpdatesBinding } from "../src/application/telegram-binding";
import type { PlayerRequest, PlayerResponse } from "../src/http/contracts";
import { AdminGate } from "../src/settings/admin-gate";
import { SettingsRuntime } from "../src/settings/context";
import { createSettingsRouter } from "../src/settings/routes";
import { Settings } from "../src/state/settings";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";

const TOKEN = "the-admin-token";
const HOME = "192.168.0.20";
const A = { bare: 111, chatId: -1_000_000_000_111, accessHash: 11n, title: "Channel A" };
const B = { bare: 222, chatId: -1_000_000_000_222, accessHash: 22n, title: "Channel B" };

let dir: string;
let log: ReturnType<typeof spyOn<Console, "log">>;
beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "settings-http-"));
  log = spyOn(console, "log").mockImplementation(() => {});
});
afterEach(async () => {
  log.mockRestore();
  await rm(dir, { recursive: true, force: true });
});

type Route = ReturnType<typeof createSettingsRouter>;

function request(method: string, path: string, over: { body?: unknown; cookie?: string; client?: string } = {}): PlayerRequest {
  return {
    method,
    path: `/api/settings${path}`,
    range: null,
    client: over.client ?? HOME,
    body: over.body === undefined ? null : JSON.stringify(over.body),
    contentType: over.body === undefined ? null : "application/json",
    cookie: over.cookie ?? null,
  };
}

const json = (response: PlayerResponse | null) => JSON.parse(new TextDecoder().decode(response!.body as Uint8Array));

function signedIn(): Telegram {
  const dialog = (channel: typeof A) => ({
    entity: new Api.Channel({
      id: channel.bare as never, title: channel.title, photo: new Api.ChatPhotoEmpty(), date: 0,
      accessHash: channel.accessHash as never,
    }),
  });
  const client = {
    iterDialogs: async function* () { yield dialog(A); yield dialog(B); },
    session: { save: () => "session" },
  };
  return Telegram.withChannel({ client } as unknown as Telegram, A.chatId, A.accessHash);
}

function rig(over: { telegram?: Telegram; telegramFilePath?: string } = {}) {
  const connection = TelegramConnection.fixed(over.telegram ?? null);
  const channel = new FollowedChannel(A.chatId, A.accessHash, A.title);
  const telegramFilePath = over.telegramFilePath ?? join(dir, "telegram.json");
  let outcome: RefreshOutcome = { served: false, reason: "no outcome set" };
  const runtime = new SettingsRuntime(
    {
      connection,
      channel,
      updatesBinding: new UpdatesBinding(connection, channel, "device", null, null, () => () => {}),
      follower: { tryChannel: async () => outcome },
      findIndex: async () => "nothing-pinned" as const,
      settings: new Settings(null),
      cache: null,
      channelCatalogDir: join(dir, "catalogs"),
      telegramFilePath,
    },
    { apiId: 1, apiHash: "a".repeat(32) },
  );
  const route = createSettingsRouter({ gate: new AdminGate(TOKEN), runtime, secure: () => false });
  return { route, connection, channel, telegramFilePath, serve: (next: RefreshOutcome) => { outcome = next; } };
}

async function unlock(route: Route): Promise<string> {
  const response = await route(request("POST", "/unlock", { body: { token: TOKEN } }));
  return response!.headers["set-cookie"]!.split(";")[0]!;
}

describe("who is answered", () => {
  test("off this household's network every path is a 404, even an unlock with the right token", async () => {
    const { route } = rig();
    const cookie = await unlock(route);
    const outside = "203.0.113.7";

    const unlocking = await route(request("POST", "/unlock", { body: { token: TOKEN }, client: outside }));
    expect(unlocking?.status).toBe(404);
    expect(unlocking?.headers["set-cookie"]).toBeUndefined();
    expect((await route(request("GET", "", { cookie, client: outside })))?.status).toBe(404);
  });

  test("a wrong token is 401, and an address that keeps guessing is 429 even with the right one", async () => {
    const { route } = rig();
    for (let attempt = 0; attempt < 5; attempt++) {
      expect((await route(request("POST", "/unlock", { body: { token: "guess" } })))?.status).toBe(401);
    }
    expect((await route(request("POST", "/unlock", { body: { token: TOKEN } })))?.status).toBe(429);
    // The limit is the guessing address's own.
    expect((await route(request("POST", "/unlock", { body: { token: TOKEN }, client: "192.168.0.21" })))?.status).toBe(200);
  });

  test("past unlock and lock, nothing is answered without the admin cookie", async () => {
    const { route } = rig({ telegram: signedIn() });
    const cookie = await unlock(route);
    await route(request("POST", "/lock", { cookie }));

    for (const [method, path, body] of [
      ["GET", "", undefined],
      ["GET", "/libraries", undefined],
      ["PUT", "/telegram/app", { apiId: 2, apiHash: "b".repeat(32) }],
      ["POST", "/telegram/sign-out", {}],
    ] as const) {
      for (const sent of [undefined, cookie]) {
        const response = await route(request(method, path, { body, cookie: sent }));
        expect(response?.status).toBe(401);
        expect(json(response)).toEqual({ locked: true });
      }
    }
  });
});

describe("what a change answers and leaves behind", () => {
  test("app credentials that took effect but could not be saved answer 400, and are live", async () => {
    // A regular file where the account file's directory should be.
    await writeFile(join(dir, "blocker"), "");
    const { route } = rig({ telegramFilePath: join(dir, "blocker", "telegram.json") });
    const cookie = await unlock(route);

    const response = await route(request("PUT", "/telegram/app", { body: { apiId: 2, apiHash: "b".repeat(32) }, cookie }));
    expect(response?.status).toBe(400);
    expect(json(response).error).toStartWith("the change took effect but could not be saved: ");
    expect(json(await route(request("GET", "", { cookie }))).telegram.app).toEqual({ apiId: 2, apiHashSet: true });
  });

  test("a library that cannot be served answers why and moves nothing; one that is served moves and is saved", async () => {
    const telegram = signedIn();
    const { route, connection, channel, telegramFilePath, serve } = rig({ telegram });
    const cookie = await unlock(route);
    const listing = json(await route(request("GET", "/libraries", { cookie })));
    const handle = listing.libraries.find((entry: { title: string }) => entry.title === B.title).handle;

    serve({ served: false, reason: "nothing pinned in the channel is a library index" });
    const refused = await route(request("POST", "/library", { body: { handle }, cookie }));
    expect(refused?.status).toBe(400);
    expect(json(refused)).toEqual({ error: "nothing pinned in the channel is a library index" });
    expect(connection.current()).toBe(telegram);
    expect(channel).toMatchObject({ chatId: A.chatId, accessHash: A.accessHash, title: A.title });
    expect(existsSync(telegramFilePath)).toBe(false);

    serve({ served: true, sets: 3 });
    const moved = await route(request("POST", "/library", { body: { handle }, cookie }));
    expect(moved?.status).toBe(200);
    expect(json(moved)).toEqual({ title: B.title, sets: 3 });
    expect(Number(connection.current()!.peer.channelId)).toBe(B.bare);
    expect(channel).toMatchObject({ chatId: B.chatId, accessHash: B.accessHash, title: B.title });
    expect(JSON.parse(await readFile(telegramFilePath, "utf8"))).toMatchObject({ chatId: B.chatId, title: B.title });
  });

  test("an answered write leaves one line naming it, and never its body", async () => {
    const { route } = rig();
    const cookie = await unlock(route);
    await route(request("GET", "", { cookie }));

    const lines = log.mock.calls.map((call) => String(call[0]));
    expect(lines).toEqual([`settings: POST /api/settings/unlock from ${HOME} -> 200`]);
    expect(lines.join("\n")).not.toContain(TOKEN);
  });
});
