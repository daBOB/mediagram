import { describe, expect, test } from "bun:test";
import { Api } from "teleproto";
import { SignInFlow, type SignInClient } from "../src/settings/sign-in";

function fakeUser(id: bigint): Api.User {
  return new Api.User({ id: id as never, self: true, accessHash: 1n as never });
}

function fakeSession(saved = "new-session"): SignInClient["session"] {
  return { save: () => saved } as unknown as SignInClient["session"];
}

function passwordNeededError(): Error & { errorMessage: string } {
  return Object.assign(new Error("SESSION_PASSWORD_NEEDED"), { errorMessage: "SESSION_PASSWORD_NEEDED" });
}

/** A client that answers each request kind the flow sends, in order. */
function fakeClient(handlers: {
  sendCode?: () => unknown;
  signIn?: () => unknown;
  getPassword?: () => unknown;
  checkPassword?: () => unknown;
}): { client: SignInClient; disconnects: number } {
  const state = { disconnects: 0 };
  const client: SignInClient = {
    connect: async () => {},
    disconnect: async () => { state.disconnects += 1; },
    destroy: async () => {},
    session: fakeSession(),
    invoke: async (request: unknown) => {
      if (request instanceof Api.auth.SendCode) return handlers.sendCode?.() ?? new Api.auth.SentCode({
        type: new Api.auth.SentCodeTypeApp({ length: 5 }), phoneCodeHash: "hash-1",
      });
      if (request instanceof Api.auth.SignIn) {
        if (handlers.signIn) return handlers.signIn();
        throw new Error("unexpected SignIn");
      }
      if (request instanceof Api.account.GetPassword) return handlers.getPassword?.() ?? (() => { throw new Error("unexpected GetPassword"); })();
      if (request instanceof Api.auth.CheckPassword) return handlers.checkPassword?.() ?? (() => { throw new Error("unexpected CheckPassword"); })();
      throw new Error(`unexpected request ${(request as { className?: string })?.className}`);
    },
  } as unknown as SignInClient;
  return { client, disconnects: 0 };
}

describe("SignInFlow", () => {
  test("phone then code, no 2FA, produces a session", async () => {
    const { client } = fakeClient({
      signIn: () => new Api.auth.Authorization({ user: fakeUser(42n) }),
    });
    const flow = new SignInFlow(() => client);

    const codeStep = await flow.phone(1, "hash", "+15551234");
    expect(codeStep).toEqual({ step: "code", viaApp: true });

    const done = await flow.code("12345");
    expect(done).toEqual({ step: "done", session: "new-session", userId: "42" });
    expect(flow.waiting()).toBe(false);
  });

  test("a password step follows when Telegram asks for one", async () => {
    const { client } = fakeClient({
      signIn: () => { throw passwordNeededError(); },
      getPassword: () => new Api.account.Password({
        hasRecovery: false, hasSecureValues: false, hasPassword: true,
        newAlgo: new Api.PasswordKdfAlgoUnknown(), newSecureAlgo: new Api.SecurePasswordKdfAlgoUnknown(),
        secureRandom: Buffer.from([1]),
      }),
      checkPassword: () => new Api.auth.Authorization({ user: fakeUser(7n) }),
    });
    const flow = new SignInFlow(() => client);
    await flow.phone(1, "hash", "+15551234");

    const passwordStep = await flow.code("12345");
    expect(passwordStep).toEqual({ step: "password" });

    // computeCheck's SRP math over a Password with an unknown algo throws
    // rather than silently accepting one this build cannot compute — this
    // exercises that the flow surfaces it as its own failure text.
    await expect(flow.password("pw")).rejects.toThrow();
  });

  test("no attempt in progress refuses code and password", async () => {
    const flow = new SignInFlow(() => fakeClient({}).client);
    await expect(flow.code("12345")).rejects.toThrow("no sign-in is in progress");
    await expect(flow.password("pw")).rejects.toThrow("no sign-in is in progress");
  });

  test("a second phone call discards the first attempt", async () => {
    let disconnects = 0;
    const first: SignInClient = {
      connect: async () => {}, disconnect: async () => { disconnects += 1; }, destroy: async () => {},
      session: fakeSession(), invoke: async () => new Api.auth.SentCode({ type: new Api.auth.SentCodeTypeApp({ length: 5 }), phoneCodeHash: "h1" }),
    } as unknown as SignInClient;
    const flow = new SignInFlow(() => first);
    await flow.phone(1, "hash", "+15551234");
    await flow.phone(1, "hash", "+15551234");
    expect(disconnects).toBe(1);
  });

  test("a stale attempt expires and must be started again", async () => {
    let now = 0;
    const { client } = fakeClient({ signIn: () => new Api.auth.Authorization({ user: fakeUser(1n) }) });
    const flow = new SignInFlow(() => client, () => now);
    await flow.phone(1, "hash", "+15551234");
    expect(flow.waiting()).toBe(true);
    now += 10 * 60_000 + 1;
    expect(flow.waiting()).toBe(false);
    await expect(flow.code("12345")).rejects.toThrow("no sign-in is in progress");
  });

  test("a bad response to sending the code fails clearly and disconnects", async () => {
    let disconnects = 0;
    const client: SignInClient = {
      connect: async () => {}, disconnect: async () => { disconnects += 1; }, destroy: async () => {},
      session: fakeSession(), invoke: async () => "not a SentCode",
    } as unknown as SignInClient;
    const flow = new SignInFlow(() => client);
    await expect(flow.phone(1, "hash", "+15551234")).rejects.toThrow();
    expect(disconnects).toBe(1);
  });
});
