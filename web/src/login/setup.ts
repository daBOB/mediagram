/** Credential-producing setup orchestration, with external IO supplied at its boundary. */
import { Api, TelegramClient, sessions } from "teleproto";
import { Logger } from "teleproto/extensions";
import { closeSync, fchmodSync, openSync, writeFileSync } from "node:fs";
import { env as environment } from "node:process";
import { sessionName } from "../telegram/session-name";
import { authenticate, type LoginClient } from "./authenticate";
import { loginPrompts, type LoginPrompts } from "./prompts";

export interface LoginIo {
  prompts(): LoginPrompts;
  client(apiId: number, apiHash: string, logger: Logger): LoginClient;
  stdout(text: string): void;
  stderr(text: string): void;
  writeSettings(path: string, text: string): void;
}

export interface LoginOptions {
  args: string[];
  env: Record<string, string | undefined>;
  settingsPath?: string;
}

const loginIo: LoginIo = {
  prompts: loginPrompts,
  client: (apiId, apiHash, logger) => new TelegramClient(new sessions.StringSession(""), apiId, apiHash, {
    baseLogger: logger, connectionRetries: 3, ...sessionName(),
    // Report flood waits instead of silently sleeping while setup looks stuck.
    floodSleepThreshold: 0,
  }),
  stdout: (text) => { process.stdout.write(text); },
  stderr: (text) => { process.stderr.write(text); },
  writeSettings(path, text) {
    const file = openSync(path, "w", 0o600);
    try {
      // Existing files need their permissions tightened before writing credentials.
      fchmodSync(file, 0o600);
      writeFileSync(file, text);
    } finally { closeSync(file); }
  },
};

/** Returns the CLI exit status after closing prompts and disconnecting/destroying the client. */
export async function runLogin(
  options: LoginOptions = { args: process.argv.slice(2), env: environment },
  overrides: Partial<LoginIo> = {},
): Promise<number> {
  const io = { ...loginIo, ...overrides };
  const { env, args } = options;
  const secrets = new Set<string>([env.MEDIAGRAM_API_HASH ?? ""]);
  const remember = (secret: string) => { secrets.add(secret); return secret; };
  const report = (text: string) => {
    for (const secret of secrets) if (secret) text = text.split(secret).join("[redacted]");
    io.stderr(text);
  };
  const logger = new Logger("error" as never);
  logger.log = (level, message) => {
    if (logger.canSend(level)) report(`[${level}] ${message}\n`);
  };
  let prompts: LoginPrompts | undefined;
  let client: LoginClient | undefined;
  let ready: { title: string; settings: string } | undefined;
  const failures: unknown[] = [];
  const cleanup = async (action: () => unknown) => {
    try { await action(); } catch (error) { failures.push(error); }
  };
  try {
    prompts = io.prompts();
    const apiId = Number(env.MEDIAGRAM_API_ID ?? await prompts.ask("api_id: "));
    const apiHash = remember(env.MEDIAGRAM_API_HASH ?? await prompts.ask("api_hash: "));
    const wantedChannel = env.MEDIAGRAM_CHANNEL ?? await prompts.ask("channel (exact title, or -100… id): ");
    // teleproto prints its banner before a client logger can be installed.
    // Intercept only this synchronous construction; asynchronous diagnostics
    // belong to this client's logger, never a process-wide replacement.
    const originalLog = console.log;
    try {
      console.log = (...values: unknown[]) => report(values.join(" ") + "\n");
      client = io.client(apiId, apiHash, logger);
    } finally { console.log = originalLog; }
    report("\nLogging in. This creates a session separate from the uploader's.\n");
    await authenticate(client, { apiId, apiHash, args, prompts, report, challenge: io.stderr, remember });

    const seen: string[] = [];
    let found: { title: string; chatId: number; accessHash: bigint } | undefined;
    for await (const dialog of client.iterDialogs({})) {
      const channel = dialog.entity;
      if (!(channel instanceof Api.Channel)) continue;
      const bare = BigInt(channel.id.toString());
      const chatId = -1_000_000_000_000 - Number(bare);
      seen.push(channel.title);
      const wanted = wantedChannel.trim();
      if (channel.title === wanted || String(chatId) === wanted || String(bare) === wanted) {
        found = { title: channel.title, chatId, accessHash: BigInt(channel.accessHash?.toString() ?? "0") };
        break;
      }
    }
    if (!found) {
      report(`\nThis account can see no channel called "${wantedChannel}".\n` +
        `Channels it can see: ${seen.length ? seen.join(", ") : "(none)"}\n` +
        "If the player uses a dedicated account, invite it to the channel first.\n");
    } else {
      const session = remember(client.session.save() as unknown as string);
      ready = { title: found.title, settings: [
        `MEDIAGRAM_API_ID=${apiId}`, `MEDIAGRAM_API_HASH=${apiHash}`, `MEDIAGRAM_SESSION=${session}`,
        `MEDIAGRAM_CHAT_ID=${found.chatId}`, `MEDIAGRAM_CHANNEL_ACCESS_HASH=${found.accessHash}`,
        `MEDIAGRAM_LIBRARY_DB=${env.MEDIAGRAM_LIBRARY_DB ?? `${env.HOME}/.local/share/mediagram/library.db`}`,
        `MEDIAGRAM_PLAYER_ADDR=${env.MEDIAGRAM_PLAYER_ADDR ?? "127.0.0.1:8770"}`,
      ].join("\n") + "\n" };
    }
  } catch (error) { failures.push(error); }
  finally {
    await cleanup(() => prompts?.close());
    await cleanup(() => client?.disconnect());
    await cleanup(() => client?.destroy());
  }
  if (failures.length) {
    for (const error of failures) report(`\nLogin failed: ${error instanceof Error ? error.message : String(error)}\n`);
    return 1;
  }
  if (!ready) return 1;
  try {
    report(`\nLogged in, and found "${ready.title}".\n` +
      "The settings include this account's session: treat it as the account itself.\n");
    if (args.includes("--stdout")) io.stdout(ready.settings);
    else {
      io.writeSettings(options.settingsPath ?? ".env", ready.settings);
      report("Written to web/.env (owner-readable only). Now: bun run start\n");
    }
    return 0;
  } catch (error) {
    report(`\nLogin failed: ${error instanceof Error ? error.message : String(error)}\n`);
    return 1;
  }
}
