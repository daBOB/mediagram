/** Precedence between the environment and a stored `telegram.json`. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { describe as describeConfig, load } from "../src/config";
import { resolveTelegram } from "../src/settings/resolve-telegram";
import type { TelegramFile } from "../src/settings/telegram-file";

const REQUIRED = {
  MEDIAGRAM_API_ID: "12345",
  MEDIAGRAM_API_HASH: "hash",
  MEDIAGRAM_CHAT_ID: "-1001",
  MEDIAGRAM_CHANNEL_ACCESS_HASH: "99",
  MEDIAGRAM_LIBRARY_DB: "/tmp/does-not-matter/library.db",
};
const TOUCHED = [...Object.keys(REQUIRED), "MEDIAGRAM_SESSION"];

let saved: Record<string, string | undefined>;
beforeEach(() => {
  saved = Object.fromEntries(TOUCHED.map((name) => [name, process.env[name]]));
  for (const name of TOUCHED) delete process.env[name];
  Object.assign(process.env, REQUIRED);
});
afterEach(() => {
  for (const [name, value] of Object.entries(saved)) {
    if (value === undefined) delete process.env[name];
    else process.env[name] = value;
  }
});

describe("session", () => {
  test("without MEDIAGRAM_SESSION, the player still starts, signed out", () => {
    const config = load();
    expect(config.session).toBeNull();
  });

  test("apiHash and a set session are redacted; an absent session is not disguised as one", () => {
    const signedOut = describeConfig(load());
    expect(signedOut.apiHash).toBe("<redacted>");
    expect(signedOut.session).toBeNull();

    process.env.MEDIAGRAM_SESSION = "a-real-session";
    const signedIn = describeConfig(load());
    expect(signedIn.session).toBe("<redacted>");
  });
});

describe("resolveTelegram", () => {
  const file: TelegramFile = {
    apiId: 999,
    apiHash: "file-hash",
    session: "file-session",
    chatId: -1_009_999,
    accessHash: "12345678901234",
    title: "Mediagram",
  };

  test("without a file, the environment stands", () => {
    const config = resolveTelegram(load(), null);
    expect(config.apiId).toBe(12345);
    expect(config.session).toBeNull();
  });

  test("a file overrides every account field, including to null", () => {
    const config = resolveTelegram(load(), file);
    expect(config.apiId).toBe(999);
    expect(config.apiHash).toBe("file-hash");
    expect(config.session).toBe("file-session");
    expect(config.chatId).toBe(-1_009_999);
    expect(config.channelAccessHash).toBe(12345678901234n);

    const signedOut = resolveTelegram(load(), { ...file, session: null });
    expect(signedOut.session).toBeNull();
  });

  test("the resolved config never leaks the file's secrets when described", () => {
    const described = describeConfig(resolveTelegram(load(), file));
    expect(JSON.stringify(described)).not.toContain("file-hash");
    expect(JSON.stringify(described)).not.toContain("file-session");
  });
});
