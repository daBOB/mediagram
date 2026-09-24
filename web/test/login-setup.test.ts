import { afterEach, beforeEach, expect, test } from "bun:test";
import { chmod, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Api, helpers } from "teleproto";
import { runLogin, type LoginIo, type LoginOptions } from "../src/login/setup";
import type { LoginClient } from "../src/login/authenticate";

const HASH = "fixture-api-hash";
const SESSION = "fixture-session-secret";
const PASSWORD = "fixture-password";
let root: string;
beforeEach(async () => { root = await mkdtemp(join(tmpdir(), "login-setup-")); });
afterEach(async () => { await rm(root, { recursive: true, force: true }); });

function channel(title = "Library", id = 123, accessHash = "456") {
  return new Api.Channel({
    id: helpers.returnBigInt(id), accessHash: helpers.returnBigInt(accessHash),
    title, photo: new Api.ChatPhotoEmpty(), date: 1,
  });
}

function options(args = ["--stdout"], wanted = "Library"): LoginOptions {
  return { args, settingsPath: join(root, "settings.fixture"), env: {
    MEDIAGRAM_API_ID: "7", MEDIAGRAM_API_HASH: HASH, MEDIAGRAM_CHANNEL: wanted, HOME: root,
  } };
}

function boundary() {
  const calls: string[] = [];
  const stdout: string[] = [];
  const stderr: string[] = [];
  const asked: string[] = [];
  const answers: string[] = [];
  const fail = new Set<string>();
  const dialogs: Array<{ entity: unknown }> = [{ entity: { title: "Library" } }, { entity: channel() }];
  const checkpoint = (name: string) => {
    calls.push(name);
    if (fail.has(name)) throw new Error(`${name} failed with ${HASH}`);
  };
  const client = {
    connect: async () => { checkpoint("connect"); },
    start: async (_auth: Parameters<LoginClient["start"]>[0]) => { checkpoint("phone"); },
    signInUserWithQrCode: async (_credentials: unknown, _auth: Parameters<LoginClient["signInUserWithQrCode"]>[1]) => { checkpoint("qr"); },
    async *iterDialogs() { checkpoint("dialogs"); yield* dialogs; },
    session: { save: () => { checkpoint("save"); return SESSION; } },
    disconnect: async () => { checkpoint("disconnect"); },
    destroy: async () => { checkpoint("destroy"); },
  };
  const io: Partial<LoginIo> = {
    prompts: () => ({
      ask: async (question) => {
        checkpoint("ask"); asked.push(question);
        const answer = answers.shift();
        if (answer === undefined) throw new Error(`unexpected prompt: ${question}`);
        return answer;
      },
      hidden: async (question) => { checkpoint("hidden"); asked.push(question); return PASSWORD; },
      close: () => { checkpoint("close"); },
    }),
    client: (apiId, apiHash) => {
      checkpoint("client");
      expect(apiId).toBe(7); expect(apiHash).toBe(HASH);
      console.log("library banner", apiHash);
      return client as unknown as LoginClient;
    },
    stdout: (text) => { stdout.push(text); },
    stderr: (text) => { stderr.push(text); },
  };
  return { io, client, calls, stdout, stderr, asked, answers, fail, dialogs };
}

test("importing the executable opens no prompt or connection and leaves stdout untouched", async () => {
  const modulePath = new URL("../src/login.ts", import.meta.url).pathname;
  const child = Bun.spawn([process.execPath, "-e", `await import(${JSON.stringify(modulePath)}); console.log('imported')`], {
    cwd: root, env: {}, stdin: "ignore", stdout: "pipe", stderr: "pipe",
  });
  const timeout = setTimeout(() => child.kill(), 2000);
  try {
    const [code, stdout, stderr] = await Promise.all([child.exited, new Response(child.stdout).text(), new Response(child.stderr).text()]);
    expect(code).toBe(0);
    expect(stdout).toBe("imported\n");
    expect(stderr).toBe("");
    expect(await readdir(root)).toEqual([]);
  } finally { clearTimeout(timeout); child.kill(); await child.exited; }
});

test.each(["Library", " 123 ", "-1000000000123"])("matches channel by title, bare ID, or bot API ID: %s", async (wanted) => {
  const fixture = boundary();
  const original = console.log;
  expect(await runLogin(options(["--stdout"], wanted), fixture.io)).toBe(0);
  expect(console.log).toBe(original);
  expect(fixture.calls).toEqual(["client", "connect", "phone", "dialogs", "save", "close", "disconnect", "destroy"]);
  expect(fixture.stdout).toEqual([[
    "MEDIAGRAM_API_ID=7", `MEDIAGRAM_API_HASH=${HASH}`, `MEDIAGRAM_SESSION=${SESSION}`,
    "MEDIAGRAM_CHAT_ID=-1000000000123", "MEDIAGRAM_CHANNEL_ACCESS_HASH=456",
    `MEDIAGRAM_LIBRARY_DB=${root}/.local/share/mediagram/library.db`, "MEDIAGRAM_PLAYER_ADDR=127.0.0.1:8770", "",
  ].join("\n")]);
  expect(fixture.stderr.join("")).toContain("library banner [redacted]");
  expect(fixture.stderr.join("")).not.toContain(HASH);
  expect(fixture.stderr.join("")).not.toContain(SESSION);
  expect(await readdir(root)).toEqual([]);
});

test("QR takes precedence over SMS, prints the approval challenge, and uses hidden 2FA", async () => {
  const fixture = boundary();
  const token = Buffer.from("fixture-login-token");
  fixture.client.signInUserWithQrCode = async (credentials, auth) => {
    fixture.calls.push("qr");
    expect(credentials).toEqual({ apiId: 7, apiHash: HASH });
    if (!auth.qrCode) throw new Error("QR callback missing");
    await auth.qrCode({ token, expires: Math.floor(Date.now() / 1000) + 60 });
    expect(await auth.password?.()).toBe(PASSWORD);
    expect(await auth.onError(new Error(`retry ${PASSWORD} ${token.toString("base64")}`))).toBe(false);
  };
  expect(await runLogin(options(["--qr", "--sms", "--stdout"]), fixture.io)).toBe(0);
  expect(fixture.calls).not.toContain("phone");
  expect(fixture.asked).toEqual(["2FA password (hidden): "]);
  expect(fixture.stderr.join("")).toContain(`accept-login ${token.toString("base64")}`);
  expect(fixture.stderr.join("")).toContain("Telegram said: retry [redacted] [redacted]");
  expect(fixture.stdout.join("")).not.toContain("accept-login");
});

test.each([
  { args: ["--stdout"], viaApp: true, note: "Telegram sent the code to the app", sms: false },
  { args: ["--sms", "--stdout"], viaApp: false, note: "Telegram sent the code by SMS.", sms: true },
  { args: ["--stdout"], viaApp: false, note: "Telegram sent the code by SMS or a call.", sms: false },
])("phone routing and delivery guidance: %j", async ({ args, viaApp, note, sms }) => {
  const fixture = boundary();
  fixture.answers.push("invalid", "+49 (123) 456789", "12345");
  fixture.client.start = async (auth) => {
    fixture.calls.push("phone");
    if (!auth || !("phoneNumber" in auth)) throw new Error("phone flow requires user authentication");
    expect(auth.forceSMS).toBe(sms);
    expect(await (auth.phoneNumber as () => Promise<string>)()).toBe("+49 (123) 456789");
    expect(await auth.phoneCode(viaApp)).toBe("12345");
    expect(await auth.password?.()).toBe(PASSWORD);
    expect(await auth.onError(new Error(`retry ${HASH} ${PASSWORD} 12345`))).toBe(false);
  };
  expect(await runLogin(options([...args]), fixture.io)).toBe(0);
  expect(fixture.calls).not.toContain("qr");
  expect(fixture.stderr.join("")).toContain("Needs to start with +");
  expect(fixture.stderr.join("")).toContain(note);
  expect(fixture.stderr.join("")).toContain("Telegram said: retry [redacted] [redacted] [redacted]");
});

test("missing settings are prompted and configured media paths are preserved", async () => {
  const fixture = boundary();
  fixture.answers.push("7", HASH, "Library");
  const config = { ...options(), env: { MEDIAGRAM_LIBRARY_DB: "/fixture/library.db", MEDIAGRAM_PLAYER_ADDR: "0.0.0.0:1234" } };
  expect(await runLogin(config, fixture.io)).toBe(0);
  expect(fixture.asked).toEqual(["api_id: ", "api_hash: ", "channel (exact title, or -100… id): "]);
  expect(fixture.stdout.join("")).toContain("MEDIAGRAM_LIBRARY_DB=/fixture/library.db\nMEDIAGRAM_PLAYER_ADDR=0.0.0.0:1234\n");
});

test("a missing channel emits no credentials and closes both prompt and client", async () => {
  const fixture = boundary();
  const original = console.log;
  expect(await runLogin(options(["--stdout"], "Missing"), fixture.io)).toBe(1);
  expect(console.log).toBe(original);
  expect(fixture.calls).not.toContain("save");
  expect(fixture.calls.slice(-3)).toEqual(["close", "disconnect", "destroy"]);
  expect(fixture.stdout).toEqual([]);
  expect(fixture.stderr.join("")).toContain("Channels it can see: Library");
});

test.each(["client", "connect", "phone", "qr", "dialogs", "save", "close", "disconnect", "destroy"])(
  "failure at %s restores console output and cleans every acquired resource", async (stage) => {
    const fixture = boundary();
    fixture.fail.add(stage);
    const original = console.log;
    expect(await runLogin(options(stage === "qr" ? ["--qr", "--stdout"] : ["--stdout"]), fixture.io)).toBe(1);
    expect(console.log).toBe(original);
    expect(fixture.calls).toContain("close");
    if (stage !== "client") expect(fixture.calls.slice(-3)).toEqual(["close", "disconnect", "destroy"]);
    expect(fixture.stdout).toEqual([]);
    expect(fixture.stderr.join("")).toContain(`${stage} failed with [redacted]`);
    expect(fixture.stderr.join("")).not.toContain(HASH);
  },
);

test("a failed initial prompt is closed without constructing a client", async () => {
  const fixture = boundary();
  fixture.fail.add("ask");
  const { env, ...config } = options();
  delete env.MEDIAGRAM_API_ID;
  const original = console.log;
  expect(await runLogin({ ...config, env }, fixture.io)).toBe(1);
  expect(console.log).toBe(original);
  expect(fixture.calls).toEqual(["ask", "close"]);
  expect(fixture.stdout).toEqual([]);
});

test.each([false, true])("file output writes only settings with mode 0600 (existing=%s)", async (existing) => {
  const fixture = boundary();
  const config = options([]);
  if (existing) {
    await writeFile(config.settingsPath!, "old contents");
    await chmod(config.settingsPath!, 0o644);
  }
  expect(await runLogin(config, fixture.io)).toBe(0);
  expect((await stat(config.settingsPath!)).mode & 0o777).toBe(0o600);
  const written = await readFile(config.settingsPath!, "utf8");
  expect(written.split("\n").filter(Boolean)).toHaveLength(7);
  expect(written).toContain(`MEDIAGRAM_SESSION=${SESSION}\n`);
  expect(written).not.toContain("library banner");
  expect(fixture.stdout).toEqual([]);
  expect(fixture.stderr.join("")).not.toContain(SESSION);
});

test("a failed settings write follows client cleanup and never prints credentials as a fallback", async () => {
  const fixture = boundary();
  const original = console.log;
  expect(await runLogin({ ...options([]), settingsPath: root }, fixture.io)).toBe(1);
  expect(console.log).toBe(original);
  expect(fixture.calls.slice(-3)).toEqual(["close", "disconnect", "destroy"]);
  expect(fixture.stdout).toEqual([]);
  expect(fixture.stderr.join("")).not.toContain(SESSION);
});

test("pending authentication leaves unrelated console output alone", async () => {
  const fixture = boundary();
  const entered = Promise.withResolvers<void>();
  const release = Promise.withResolvers<void>();
  const original = console.log;
  fixture.client.connect = async () => { entered.resolve(); await release.promise; };
  const running = runLogin(options(), fixture.io);
  try {
    await entered.promise;
    expect(console.log).toBe(original);
  } finally {
    release.resolve();
    await running;
    console.log = original;
  }
});

test("asynchronous client diagnostics use the scoped redacting logger", async () => {
  const fixture = boundary();
  const construct = fixture.io.client!;
  const original = console.log;
  fixture.io.client = (apiId, apiHash, logger) => {
    const client = construct(apiId, apiHash, logger);
    fixture.client.connect = async () => {
      await Promise.resolve();
      expect(console.log).toBe(original);
      logger.error(`diagnostic ${HASH}`);
      logger.info("filtered detail");
    };
    return client;
  };

  expect(await runLogin(options(), fixture.io)).toBe(0);
  expect(fixture.stderr.join("")).toContain("[error] diagnostic [redacted]");
  expect(fixture.stderr.join("")).not.toContain("filtered detail");
  expect(fixture.stdout.join("")).not.toContain("diagnostic");
  expect(console.log).toBe(original);
});

test.each([false, true])("concurrent login runs restore the original logger (second finishes first: %s)", async (secondFirst) => {
  const first = boundary();
  const second = boundary();
  const a = Promise.withResolvers<void>();
  const b = Promise.withResolvers<void>();
  const original = console.log;
  first.client.connect = async () => { await a.promise; };
  second.client.connect = async () => { await b.promise; };
  const firstRun = runLogin(options(), first.io);
  const secondRun = runLogin(options(), second.io);
  try {
    const earlier = secondFirst ? { release: b, run: secondRun } : { release: a, run: firstRun };
    const later = secondFirst ? { release: a, run: firstRun } : { release: b, run: secondRun };
    earlier.release.resolve();
    expect(await earlier.run).toBe(0);
    later.release.resolve();
    expect(await later.run).toBe(0);
    expect(console.log).toBe(original);
    expect(first.stderr.join("")).not.toContain(HASH);
    expect(second.stderr.join("")).not.toContain(HASH);
  } finally {
    a.resolve(); b.resolve();
    await Promise.all([firstRun, secondRun]);
    console.log = original;
  }
});

test("constructor failure restores the logger before asynchronous cleanup", async () => {
  const fixture = boundary();
  const entered = Promise.withResolvers<void>();
  const release = Promise.withResolvers<void>();
  const original = console.log;
  const prompts = fixture.io.prompts!();
  fixture.io.prompts = () => ({ ...prompts, close: async () => { entered.resolve(); await release.promise; } });
  fixture.fail.add("client");
  const running = runLogin(options(), fixture.io);
  try {
    await entered.promise;
    expect(console.log).toBe(original);
  } finally {
    release.resolve();
    expect(await running).toBe(1);
    console.log = original;
  }
});
