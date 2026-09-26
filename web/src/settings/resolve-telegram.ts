/**
 * File over environment for the account this player is bound to.
 *
 * A key present in `telegram.json` — even `session: null` — is this
 * account's answer, and the environment stays only the bootstrap for a
 * player that has never opened Settings. The poster directory a channel
 * library falls back to is unaffected: it is derived from `libraryDb`, not
 * from either source here.
 */

import type { Config } from "../config";
import type { TelegramFile } from "./telegram-file";

export function resolveTelegram(base: Config, file: TelegramFile | null): Config {
  if (file === null) return base;
  return {
    ...base,
    apiId: file.apiId,
    apiHash: file.apiHash,
    session: file.session,
    chatId: file.chatId,
    channelAccessHash: BigInt(file.accessHash),
  };
}
