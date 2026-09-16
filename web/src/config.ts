/**
 * Configuration, from the environment.
 *
 * The session string is the account. It is read once, never logged, never
 * echoed in an error, and never put in an HTTP response.
 */

export interface Config {
  apiId: number;
  apiHash: string;
  /** teleproto `StringSession`. See `mediagram export-session`. */
  session: string;
  /** Bot-API form, as recorded in `parts.chat_id`: `-100…`. */
  chatId: number;
  /**
   * The channel's access hash, exported by `mediagram export-session`.
   *
   * Carried rather than resolved: an access hash is bound to the account, the
   * uploader already holds one that works, and resolving by id costs a round
   * trip on every start.
   */
  channelAccessHash: bigint;
  libraryDb: string;
  hostname: string;
  port: number;
}

function required(name: string): string {
  const value = process.env[name];
  if (value === undefined || value === "") {
    throw new Error(`${name} is not set`);
  }
  return value;
}

export function load(): Config {
  const addr = process.env.MEDIAGRAM_PLAYER_ADDR ?? "127.0.0.1:8770";
  const colon = addr.lastIndexOf(":");
  if (colon < 0) throw new Error(`MEDIAGRAM_PLAYER_ADDR should be host:port, got ${addr}`);

  return {
    apiId: Number(required("MEDIAGRAM_API_ID")),
    apiHash: required("MEDIAGRAM_API_HASH"),
    session: required("MEDIAGRAM_SESSION"),
    chatId: Number(required("MEDIAGRAM_CHAT_ID")),
    channelAccessHash: BigInt(required("MEDIAGRAM_CHANNEL_ACCESS_HASH")),
    libraryDb: required("MEDIAGRAM_LIBRARY_DB"),
    hostname: addr.slice(0, colon) || "127.0.0.1",
    port: Number(addr.slice(colon + 1)),
  };
}

/**
 * Safe to print: everything except the two secrets. Used by startup logging,
 * which must never be the thing that leaks a session.
 */
export function describe(config: Config): Record<string, unknown> {
  return {
    apiId: config.apiId,
    apiHash: "<redacted>",
    session: "<redacted>",
    chatId: config.chatId,
    channelAccessHash: String(config.channelAccessHash),
    libraryDb: config.libraryDb,
    address: `${config.hostname}:${config.port}`,
  };
}
