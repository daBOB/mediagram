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
  /** Where cached chunks live. */
  cacheDir: string;
  /**
   * Ceiling for the chunk cache, in bytes.
   *
   * A media cache with no ceiling fills whatever it is given, and this
   * library is tens of gigabytes. 0 disables caching entirely.
   */
  cacheMaxBytes: number;
  hostname: string;
  port: number;
}

/**
 * A size with an optional unit: `8G`, `512M`, `1024`. Plain bytes without one.
 *
 * Spelled out rather than taking raw bytes because a quota is a number a
 * person sets by hand, and "8589934592" invites a typo that silently becomes
 * a different order of magnitude.
 */
export function parseSize(text: string): number {
  const match = /^(\d+(?:\.\d+)?)\s*([KMGT])?B?$/i.exec(text.trim());
  if (!match) throw new Error(`not a size: ${text}`);
  const scale = { k: 1024, m: 1024 ** 2, g: 1024 ** 3, t: 1024 ** 4 }[
    (match[2] ?? "").toLowerCase()
  ];
  return Math.floor(Number(match[1]) * (scale ?? 1));
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
    cacheDir: process.env.MEDIAGRAM_CACHE_DIR ?? `${process.env.HOME}/.cache/mediagram-player`,
    cacheMaxBytes: parseSize(process.env.MEDIAGRAM_CACHE_MAX ?? "8G"),
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
    cacheDir: config.cacheDir,
    cacheMaxBytes: config.cacheMaxBytes,
    address: `${config.hostname}:${config.port}`,
  };
}
