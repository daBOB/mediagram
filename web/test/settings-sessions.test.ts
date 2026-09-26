import { describe, expect, test } from "bun:test";
import { Api } from "teleproto";
import { listSessions, revokeSession } from "../src/settings/sessions";
import type { Telegram } from "../src/telegram/client";

function authorization(overrides: Partial<ConstructorParameters<typeof Api.Authorization>[0]> = {}) {
  return new Api.Authorization({
    current: false, hash: 5n as never, deviceModel: "mediagram web · host", platform: "Linux",
    systemVersion: "1", apiId: 1, appName: "mediagram", appVersion: "1.0",
    dateCreated: 1, dateActive: 2, ip: "1.2.3.4", country: "DE", region: "",
    ...overrides,
  } as never);
}

function fakeTelegram(overrides: Partial<{ invoke: Telegram["client"]["invoke"] }> = {}): Telegram {
  return { client: { invoke: overrides.invoke ?? (async () => {}) } } as unknown as Telegram;
}

describe("listSessions", () => {
  test("shapes the account's authorizations for this app", async () => {
    const telegram = fakeTelegram({
      invoke: (async () => new Api.account.Authorizations({
        authorizationTtlDays: 1,
        authorizations: [authorization({ apiId: 1, hash: 5n as never })],
      })) as never,
    });
    const rows = await listSessions(telegram, 1);
    expect(rows).toEqual([{
      id: "5", device: "mediagram web · host", platform: "Linux", app: "mediagram", appVersion: "1.0",
      location: "DE", lastActive: 2, created: 1, current: false, unconfirmed: false,
    }]);
  });
});

describe("revokeSession", () => {
  test("refuses the current session without a request", async () => {
    let called = false;
    const telegram = fakeTelegram({ invoke: (async () => { called = true; }) as never });
    const result = await revokeSession(telegram, "0");
    expect(result.ok).toBe(false);
    expect(called).toBe(false);
  });

  test("succeeds when Telegram accepts the revoke", async () => {
    const telegram = fakeTelegram({ invoke: (async () => true) as never });
    expect(await revokeSession(telegram, "123")).toEqual({ ok: true });
  });

  test("a session already gone still counts as revoked", async () => {
    const telegram = fakeTelegram({
      invoke: (async () => { throw Object.assign(new Error("HASH_INVALID"), { errorMessage: "HASH_INVALID" }); }) as never,
    });
    expect(await revokeSession(telegram, "123")).toEqual({ ok: true });
  });

  test("the 24-hour guard reads as a sentence, not a raw RPC name", async () => {
    const telegram = fakeTelegram({
      invoke: (async () => {
        throw Object.assign(new Error("x"), { errorMessage: "FRESH_RESET_AUTHORISATION_FORBIDDEN" });
      }) as never,
    });
    const result = await revokeSession(telegram, "123");
    expect(result).toEqual({
      ok: false,
      error: "This device signed in less than a day ago; Telegram allows removing other sessions after 24 hours.",
    });
  });

  test("a malformed id is refused before any request", async () => {
    let called = false;
    const telegram = fakeTelegram({ invoke: (async () => { called = true; }) as never });
    const result = await revokeSession(telegram, "not-a-number");
    expect(result.ok).toBe(false);
    expect(called).toBe(false);
  });
});
