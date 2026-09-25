/**
 * One-time setup for the player's own Telegram session.
 *
 * Sharing the uploader's auth key between two MTProto clients breaks their
 * sessions. Setup creates a separate session and resolves the channel's
 * account-specific access hash from this account's dialogs.
 *
 * By default credentials are written to `.env`, owner-readable only. With
 * `--stdout`, only the settings go to stdout for piping to a secret store.
 * Prompts, diagnostics, and the explicit QR approval challenge use stderr.
 *
 * `--qr` prints a token to approve with `mediagram accept-login <token>` from
 * an existing session. Otherwise setup asks for a phone number and login code;
 * `--sms` requests SMS, although Telegram may still choose in-app delivery.
 * Importing this module neither opens stdin nor connects to Telegram.
 */
import { runLogin } from "./login/setup";

export { runLogin };

if (import.meta.main) process.exitCode = await runLogin();
