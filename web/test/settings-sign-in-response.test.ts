/**
 * A finished sign-in, driven through the settings router over the real
 * runtime: the page is told it is done, and the session string the server
 * signed in with — the account's credential — never leaves the server.
 *
 * Only Telegram is faked, at the two calls that would reach it: the auth
 * exchange that produces the session, and opening a client on it.
 */

import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { ChannelState, UpdatesBinding } from "../src/application/telegram-binding";
import type { PlayerRequest, PlayerResponse } from "../src/http/contracts";
import { AdminGate } from "../src/settings/admin-gate";
import { SettingsRuntime } from "../src/settings/context";
import { createSettingsRouter } from "../src/settings/routes";
import { SignInFlow } from "../src/settings/sign-in";
import { Settings } from "../src/state/settings";
import type { StartupFacts } from "../src/status/facts";
import { Telegram } from "../src/telegram/client";
import { TelegramConnection } from "../src/telegram/connection";

const SESSION = "1BAAOMTQ5LjE1NC4xNjcuNTEAUAFAKESESSIONSTRINGFORTHISTEST";
const CLIENT = "192.168.0.20";

let dir: string;
beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "settings-sign-in-"));
});
afterEach(async () => {
  await rm(dir, { recursive: true, force: true });
});

function post(path: string, body: unknown, cookie: string | null = null): PlayerRequest {
  return {
    method: "POST",
    path: `/api/settings${path}`,
    range: null,
    client: CLIENT,
    body: JSON.stringify(body),
    contentType: "application/json",
    cookie,
  };
}

const text = (response: PlayerResponse | null) => new TextDecoder().decode(response?.body as Uint8Array);

test.each([
  ["code", { code: "12345" }],
  ["password", { password: "hunter2" }],
] as const)("finishing sign-in at the %s step answers only that it is done", async (step, body) => {
  const finished = spyOn(SignInFlow.prototype, step).mockResolvedValue({ step: "done", session: SESSION, userId: "42" });
  const opened: (string | null)[] = [];
  const open = spyOn(Telegram, "open").mockImplementation(async (config) => {
    opened.push(config.session);
    return { client: { session: { save: () => config.session } }, disconnect: async () => {} } as unknown as Telegram;
  });

  try {
    const telegramFilePath = join(dir, "telegram.json");
    const connection = new TelegramConnection(null);
    const channel = new ChannelState(-1001234567890, 99n, "Library");
    const runtime = new SettingsRuntime(
      {
        connection,
        channel,
        updatesBinding: new UpdatesBinding(connection, channel, "device", null, null, () => () => {}),
        follower: { refresh: async () => {}, retarget: () => {} },
        // Signing in never reads what startup found.
        facts: {} as StartupFacts,
        settings: new Settings(null),
        cache: null,
        channelCatalogDir: dir,
        telegramFilePath,
      },
      { apiId: 1, apiHash: "a".repeat(32) },
    );
    const route = createSettingsRouter({ gate: new AdminGate("token"), runtime, secure: () => false });

    const unlocked = await route(post("/unlock", { token: "token" }));
    const cookie = unlocked!.headers["set-cookie"]!.split(";")[0]!;
    const response = await route(post(`/telegram/sign-in/${step}`, body, cookie));

    expect(response?.status).toBe(200);
    expect(text(response)).not.toContain(SESSION);
    expect(JSON.parse(text(response))).toEqual({ step: "done" });
    expect(finished).toHaveBeenCalledTimes(1);

    // The server itself still signs in with it and keeps it.
    expect(opened).toEqual([SESSION]);
    expect(connection.current()).not.toBeNull();
    expect(JSON.parse(await readFile(telegramFilePath, "utf8")).session).toBe(SESSION);
  } finally {
    finished.mockRestore();
    open.mockRestore();
  }
});
