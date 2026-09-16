/**
 * One-time setup: log the player backend into Telegram and print its settings.
 *
 * The backend needs a session of its own. Sharing the uploader's is not a
 * style preference — two MTProto clients on one auth key break each other,
 * permanently, until one restarts. Measured, not assumed.
 *
 * Nothing is written to disk here. The settings go to stdout so you can put
 * them where you keep secrets; `web/.env` is gitignored and is the easy
 * choice, since Bun loads it automatically.
 */

import { Api, TelegramClient, sessions } from "teleproto";
import { createInterface } from "node:readline/promises";
import { stderr, stdin } from "node:process";

// Prompts go to stderr so that `bun run login > .env` captures the settings
// and still lets you answer the questions.
const rl = createInterface({ input: stdin, output: stderr });

function ask(question: string): Promise<string> {
  return rl.question(question);
}

/** Reads a line without echoing it, for the 2FA password. */
async function askHidden(question: string): Promise<string> {
  const ETX = "\u0003"; // Ctrl-C
  const DEL = "\u007f"; // backspace
  stderr.write(question);
  const wasRaw = stdin.isRaw ?? false;
  stdin.setRawMode?.(true);
  let value = "";
  for await (const chunk of stdin) {
    const text = chunk.toString();
    if (text === "\r" || text === "\n") break;
    if (text === ETX) {
      stderr.write("\n");
      process.exit(130);
    }
    if (text === DEL) {
      value = value.slice(0, -1);
      continue;
    }
    value += text;
  }
  stdin.setRawMode?.(wasRaw);
  stderr.write("\n");
  return value;
}

const apiId = Number(process.env.MEDIAGRAM_API_ID ?? (await ask("api_id: ")));
const apiHash = process.env.MEDIAGRAM_API_HASH ?? (await ask("api_hash: "));
const wantedChannel =
  process.env.MEDIAGRAM_CHANNEL ?? (await ask("channel (exact title, or -100… id): "));

const client = new TelegramClient(new sessions.StringSession(""), apiId, apiHash, {
  connectionRetries: 3,
});

console.error("\nLogging in. This creates a session separate from the uploader's.\n");

await client.start({
  phoneNumber: () => ask("phone number (e.g. +49…): "),
  phoneCode: () => ask("login code: "),
  password: () => askHidden("2FA password (hidden): "),
  onError: async (error) => {
    console.error(`login failed: ${error.message}`);
    return true;
  },
});

/**
 * The channel's access hash, from the account's own dialogs.
 *
 * An access hash is bound to the account that holds it, so a player logged in
 * as someone else cannot reuse the uploader's. Reading it here means the
 * backend never has to resolve a channel at startup.
 */
let chatId: number | null = null;
let accessHash: bigint | null = null;
let title = "";

for await (const dialog of client.iterDialogs({})) {
  const entity = dialog.entity;
  if (!(entity instanceof Api.Channel)) continue;
  const bare = BigInt(entity.id.toString());
  const botApiId = -1_000_000_000_000 - Number(bare);
  const matches =
    entity.title === wantedChannel ||
    String(botApiId) === wantedChannel.trim() ||
    String(bare) === wantedChannel.trim();
  if (matches) {
    chatId = botApiId;
    accessHash = BigInt(entity.accessHash?.toString() ?? "0");
    title = entity.title;
    break;
  }
}

rl.close();

if (chatId === null || accessHash === null) {
  console.error(
    `\nThis account can see no channel called "${wantedChannel}".\n` +
      "If the player uses a dedicated account, invite it to the channel first.",
  );
  await client.disconnect();
  await client.destroy();
  process.exit(1);
}

const session = client.session.save() as unknown as string;
await client.disconnect();
await client.destroy();

console.error(
  `\nLogged in, and found "${title}".\n` +
    "The line below is this account's session: treat it as the account itself.\n" +
    "Save the block to web/.env (gitignored), then `bun run start`.\n",
);

console.log(`MEDIAGRAM_API_ID=${apiId}`);
console.log(`MEDIAGRAM_API_HASH=${apiHash}`);
console.log(`MEDIAGRAM_SESSION=${session}`);
console.log(`MEDIAGRAM_CHAT_ID=${chatId}`);
console.log(`MEDIAGRAM_CHANNEL_ACCESS_HASH=${accessHash}`);
console.log(
  `MEDIAGRAM_LIBRARY_DB=${process.env.MEDIAGRAM_LIBRARY_DB ?? `${process.env.HOME}/.local/share/mediagram/library.db`}`,
);
console.log(`MEDIAGRAM_PLAYER_ADDR=${process.env.MEDIAGRAM_PLAYER_ADDR ?? "127.0.0.1:8770"}`);
