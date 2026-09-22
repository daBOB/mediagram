/**
 * How the web player names itself to Telegram.
 *
 * Telegram lists every session of an account by what the client said when it
 * connected, and teleproto's default says only the platform — so this player,
 * the uploader and every phone looked alike in the account's session list.
 * Same shape as the Rust clients' `mediagram-core::connection_params`:
 * `mediagram <surface> · <device>`, and the project's own version.
 */

import { hostname } from "node:os";

import manifest from "../../package.json";

export function deviceModel(surface: string, device: string): string {
  const name = device.trim();
  return name === "" ? `mediagram ${surface}` : `mediagram ${surface} · ${name}`;
}

/** The teleproto client options that carry the name. */
export function sessionName(): { deviceModel: string; appVersion: string } {
  return { deviceModel: deviceModel("web", hostname()), appVersion: manifest.version };
}
