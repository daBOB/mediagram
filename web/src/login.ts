/**
 * One-time setup: log the player backend into Telegram and print its settings.
 *
 * The backend needs a session of its own. Sharing the uploader's is not a
 * style preference — two MTProto clients on one auth key break each other,
 * permanently, until one restarts. Measured, not assumed.
 *
 * Nothing is written to disk here. The settings go to stdout so you can put
 * them where you keep secrets; `web/.env` is gitignored and is the easy
 * choice, since Bun loads it automatically. Everything else goes to stderr,
 * so `bun run login > .env` captures the settings and still lets you answer.
 *
 * Two ways in:
 *
 * `--qr` (recommended) exports a login token that an already-authorized
 * session approves, exactly as a phone approves a scanned QR code. Nothing is
 * delivered by Telegram and nothing has to be read anywhere:
 * `mediagram accept-login <token>` does the approving.
 *
 * Without it, the phone-code flow. That path is at Telegram's mercy: it will
 * issue a fresh `phone_code_hash` while silently declining to deliver the
 * code, and it offers no SMS fallback on this account (`next_type` absent), so
 * `--sms` may do nothing at all.
 */

import { Api, TelegramClient, sessions } from "teleproto";
import { createInterface } from "node:readline/promises";
import { stderr, stdin } from "node:process";

const forceSMS = process.argv.includes("--sms");
const useQr = process.argv.includes("--qr");

// Prompts go to stderr so that `bun run login > .env` captures the settings
// and still lets you answer the questions.
const rl = createInterface({ input: stdin, output: stderr, terminal: true });

function ask(question: string): Promise<string> {
  return rl.question(question);
}

/**
 * Reads a line without echoing it.
 *
 * Done by silencing readline's own echo rather than by reading `stdin`
 * directly: a second reader on the same descriptor competes with the
 * interface above, and the loser hangs.
 */
async function askHidden(question: string): Promise<string> {
  const muted = rl as unknown as { _writeToOutput?: (text: string) => void };
  const original = muted._writeToOutput;
  muted._writeToOutput = (text: string) => {
    // Keep the prompt itself visible; swallow the characters typed after it.
    if (text.includes(question)) stderr.write(question);
  };
  try {
    return await rl.question(question);
  } finally {
    muted._writeToOutput = original;
    stderr.write("\n");
  }
}

/**
 * teleproto's `parsePhone` returns undefined for a number that does not begin
 * with `+`, and the login then fails somewhere less obvious. Ask again here.
 * Spaces, hyphens and parentheses are stripped for us, so only the `+` and
 * the country code actually matter.
 */
async function askPhone(): Promise<string> {
  for (;;) {
    const phone = (
      await ask("phone number, international format (+<country><number>): ")
    ).trim();
    if (/^\+[\d()\s-]{6,}$/.test(phone)) return phone;
    stderr.write(
      "  Needs to start with + and your country code, with the national\n" +
        "  leading 0 dropped. It is the number this Telegram account is\n" +
        "  registered with — your Telegram settings show it in this form.\n",
    );
  }
}

const apiId = Number(process.env.MEDIAGRAM_API_ID ?? (await ask("api_id: ")));
const apiHash = process.env.MEDIAGRAM_API_HASH ?? (await ask("api_hash: "));
const wantedChannel =
  process.env.MEDIAGRAM_CHANNEL ??
  (await ask("channel (exact title, or -100… id): "));

const client = new TelegramClient(
  new sessions.StringSession(""),
  apiId,
  apiHash,
  {
    connectionRetries: 3,
    // Surface flood waits instead of sleeping through them. teleproto otherwise
    // sleeps inside the request loop for any wait up to a minute and never
    // raises it, which looks exactly like "I asked for a code and nothing
    // happened".
    floodSleepThreshold: 0,
  },
);

stderr.write(
  "\nLogging in. This creates a session separate from the uploader's.\n",
);

const password = () => askHidden("2FA password (hidden): ");

if (useQr) {
  await client.signInUserWithQrCode(
    { apiId, apiHash },
    {
      /**
       * The token is what an existing session approves. Printed base64 rather
       * than drawn as a QR block, because the approver here is a command on
       * this machine rather than a phone camera.
       */
      qrCode: async ({ token, expires }) => {
        const seconds = Math.max(0, expires - Math.floor(Date.now() / 1000));
        stderr.write(
          "\n  Approve this login from the uploader's session, in another terminal:\n\n" +
            `    cargo run -q -p mediagram -- accept-login ${token.toString("base64")}\n\n` +
            `  The token expires in about ${seconds}s; a fresh one is printed if it lapses.\n`,
        );
      },
      password,
      onError: async (error: Error) => {
        stderr.write(`\n  Telegram said: ${error.message}\n`);
        return false;
      },
    },
  );
} else {
  await client.start({
    phoneNumber: askPhone,

    /**
     * `isCodeViaApp` is teleproto reporting where Telegram actually sent the
     * code. Saying so beats guessing: an account signed in elsewhere gets the
     * code in Telegram itself, and people wait for an SMS that is never coming.
     */
    phoneCode: async (isCodeViaApp?: boolean) => {
      stderr.write(
        isCodeViaApp
          ? "\n  Telegram sent the code to the app, not by SMS: look in the\n" +
              '  "Telegram" service chat on a device where you are signed in.\n' +
              "  Re-run with `--sms` if you need a text message instead.\n"
          : `\n  Telegram sent the code by ${forceSMS ? "SMS" : "SMS or a call"}.\n`,
      );
      return ask("login code: ");
    },

    password,
    forceSMS,

    /**
     * Returning a truthy value makes teleproto abort with a bare
     * "AUTH_USER_CANCEL", discarding what actually went wrong. Report the real
     * message and let it ask again — a mistyped code should cost one retry, not
     * the whole login.
     */
    onError: async (error: Error) => {
      stderr.write(`\n  Telegram said: ${error.message}\n`);
      return false;
    },
  });
}

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
const seen: string[] = [];

for await (const dialog of client.iterDialogs({})) {
  const entity = dialog.entity;
  if (!(entity instanceof Api.Channel)) continue;
  const bare = BigInt(entity.id.toString());
  const botApiId = -1_000_000_000_000 - Number(bare);
  seen.push(entity.title);
  const wanted = wantedChannel.trim();
  if (
    entity.title === wanted ||
    String(botApiId) === wanted ||
    String(bare) === wanted
  ) {
    chatId = botApiId;
    accessHash = BigInt(entity.accessHash?.toString() ?? "0");
    title = entity.title;
    break;
  }
}

rl.close();

if (chatId === null || accessHash === null) {
  stderr.write(
    `\nThis account can see no channel called "${wantedChannel}".\n` +
      `Channels it can see: ${seen.length ? seen.join(", ") : "(none)"}\n` +
      "If the player uses a dedicated account, invite it to the channel first.\n",
  );
  await client.disconnect();
  await client.destroy();
  process.exit(1);
}

const session = client.session.save() as unknown as string;
await client.disconnect();
await client.destroy();

stderr.write(
  `\nLogged in, and found "${title}".\n` +
    "The line below is this account's session: treat it as the account itself.\n" +
    "Save the block to web/.env (gitignored), then `bun run start`.\n\n",
);

console.log(`MEDIAGRAM_API_ID=${apiId}`);
console.log(`MEDIAGRAM_API_HASH=${apiHash}`);
console.log(`MEDIAGRAM_SESSION=${session}`);
console.log(`MEDIAGRAM_CHAT_ID=${chatId}`);
console.log(`MEDIAGRAM_CHANNEL_ACCESS_HASH=${accessHash}`);
console.log(
  `MEDIAGRAM_LIBRARY_DB=${process.env.MEDIAGRAM_LIBRARY_DB ?? `${process.env.HOME}/.local/share/mediagram/library.db`}`,
);
console.log(
  `MEDIAGRAM_PLAYER_ADDR=${process.env.MEDIAGRAM_PLAYER_ADDR ?? "127.0.0.1:8770"}`,
);
