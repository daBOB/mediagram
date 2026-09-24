/** Route setup through Telegram's QR or phone-code authentication. */
import type { TelegramClient } from "teleproto";
import type { LoginPrompts } from "./prompts";

export type LoginClient = Pick<TelegramClient,
  "connect" | "signInUserWithQrCode" | "start" | "iterDialogs" | "session" | "disconnect" | "destroy">;

interface Authentication {
  apiId: number;
  apiHash: string;
  args: string[];
  prompts: LoginPrompts;
  report(text: string): void;
  challenge(text: string): void;
  remember(secret: string): string;
}

export async function authenticate(client: LoginClient, auth: Authentication): Promise<void> {
  const { prompts, report, remember } = auth;
  const password = async () => remember(await prompts.hidden("2FA password (hidden): "));
  const onError = async (error: Error) => {
    report(`\n  Telegram said: ${error.message}\n`);
    // A truthy answer aborts with AUTH_USER_CANCEL and discards the useful error.
    return false;
  };
  // QR login does not connect itself; connecting also covers the phone path.
  await client.connect();
  if (auth.args.includes("--qr")) {
    await client.signInUserWithQrCode({ apiId: auth.apiId, apiHash: auth.apiHash }, {
      qrCode: async ({ token, expires }) => {
        const value = remember(token.toString("base64"));
        const seconds = Math.max(0, expires - Math.floor(Date.now() / 1000));
        // The approval challenge is intentional secret output, separate from diagnostics.
        auth.challenge(
          "\n  Approve this login from the uploader's session, in another terminal:\n\n" +
          `    cargo run -q -p mediagram -- accept-login ${value}\n\n` +
          `  The token expires in about ${seconds}s; a fresh one is printed if it lapses.\n`,
        );
      },
      password, onError,
    });
    return;
  }

  const forceSMS = auth.args.includes("--sms");
  await client.start({
    phoneNumber: async () => {
      for (;;) {
        const phone = remember((await prompts.ask("phone number, international format (+<country><number>): ")).trim());
        if (/^\+[\d()\s-]{6,}$/.test(phone)) return phone;
        report("  Needs to start with + and your country code, with the national\n" +
          "  leading 0 dropped. It is the number this Telegram account is\n" +
          "  registered with — your Telegram settings show it in this form.\n");
      }
    },
    phoneCode: async (isCodeViaApp?: boolean) => {
      report(isCodeViaApp
        ? "\n  Telegram sent the code to the app, not by SMS: look in the\n" +
          '  "Telegram" service chat on a device where you are signed in.\n' +
          "  Re-run with `--sms` if you need a text message instead.\n"
        : `\n  Telegram sent the code by ${forceSMS ? "SMS" : "SMS or a call"}.\n`);
      return remember(await prompts.ask("login code: "));
    },
    password, forceSMS, onError,
  });
}
