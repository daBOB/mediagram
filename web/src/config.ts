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
  /**
   * The index on this machine, or `null` when a package supplies the catalog.
   *
   * Required only in the second case: a player given a package never opens a
   * local index, and demanding a path to a file it will not touch turns a
   * working configuration into a startup failure for no reason.
   */
  libraryDb: string | null;
  /** Where cached chunks live. */
  cacheDir: string;
  /**
   * Ceiling for the chunk cache, in bytes.
   *
   * A media cache with no ceiling fills whatever it is given, and this
   * library is tens of gigabytes. 0 disables caching entirely.
   */
  cacheMaxBytes: number;
  /**
   * Chunks to fetch ahead once reads look sequential. 0 turns it off.
   *
   * Playback walks forward, so without this every chunk is a miss the first
   * time through and each miss is a Telegram round trip. Four chunks is 2 MB
   * of lookahead, comfortably ahead of a 4 Mbit/s stream.
   */
  cacheReadahead: number;
  /** Where HLS segments are written while a transcode runs. */
  transcodeDir: string;
  /**
   * Ceiling for a transcode's output, in bits per second.
   *
   * A 13.9 Mbit/s source does not fit a 25 Mbit/s uplink with room for
   * anything else, so remote viewing transcodes for bitrate as well as for
   * codecs.
   */
  transcodeMaxrate: number;
  /**
   * Where a published package's `latest.json` lives, or `null` to read the
   * index straight off this machine's disk.
   *
   * With it the player needs nothing from the uploader's filesystem: it
   * fetches the catalog, decrypts it, and serves what it finds. See
   * `docs/mlib-package-v1.md`.
   */
  packageUrl: string | null;
  /** 32 bytes, base64. The only thing protecting a published package. */
  packageKey: string | null;
  /** Where decrypted catalogs are kept, one directory per version. */
  catalogDir: string;
  /**
   * Whether `X-Forwarded-For` may be believed.
   *
   * Set it only where a reverse proxy really is in front. Anywhere else the
   * header is whatever the caller chose to send, and believing it would let
   * anyone claim to be on the local network and ask for the original file.
   */
  trustProxy: boolean;
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

/**
 * What a remote link is assumed to carry, in bits per second.
 *
 * Comfortably under a 25 Mbit/s household uplink with room for everything
 * else in the house. It caps a transcode's output and, for the same reason,
 * decides which titles may be played as they are from outside.
 */
export const DEFAULT_MAX_BITRATE = 8_000_000;

export function load(): Config {
  const addr = process.env.MEDIAGRAM_PLAYER_ADDR ?? "127.0.0.1:8770";
  // Both, or neither: a URL without a key cannot open anything, so a
  // half-configured package reads the local index rather than serving nothing.
  const fromPackage =
    Boolean(process.env.MEDIAGRAM_PACKAGE_URL) && Boolean(process.env.MEDIAGRAM_PACKAGE_KEY);
  const colon = addr.lastIndexOf(":");
  if (colon < 0) throw new Error(`MEDIAGRAM_PLAYER_ADDR should be host:port, got ${addr}`);

  return {
    apiId: Number(required("MEDIAGRAM_API_ID")),
    apiHash: required("MEDIAGRAM_API_HASH"),
    session: required("MEDIAGRAM_SESSION"),
    chatId: Number(required("MEDIAGRAM_CHAT_ID")),
    channelAccessHash: BigInt(required("MEDIAGRAM_CHANNEL_ACCESS_HASH")),
    // Read before the package settings below so both are decided together.
    libraryDb: fromPackage
      ? (process.env.MEDIAGRAM_LIBRARY_DB || null)
      : required("MEDIAGRAM_LIBRARY_DB"),
    cacheDir: process.env.MEDIAGRAM_CACHE_DIR ?? `${process.env.HOME}/.cache/mediagram-player`,
    cacheMaxBytes: parseSize(process.env.MEDIAGRAM_CACHE_MAX ?? "8G"),
    cacheReadahead: Number(process.env.MEDIAGRAM_CACHE_READAHEAD ?? "4"),
    transcodeDir: process.env.MEDIAGRAM_TRANSCODE_DIR ?? `${process.env.HOME}/.cache/mediagram-hls`,
    transcodeMaxrate: parseSize(process.env.MEDIAGRAM_TRANSCODE_MAXRATE ?? String(DEFAULT_MAX_BITRATE)),
    packageUrl: process.env.MEDIAGRAM_PACKAGE_URL || null,
    packageKey: process.env.MEDIAGRAM_PACKAGE_KEY || null,
    catalogDir:
      process.env.MEDIAGRAM_CATALOG_DIR ?? `${process.env.HOME}/.cache/mediagram-catalog`,
    trustProxy: /^(1|true|yes)$/i.test(process.env.MEDIAGRAM_TRUST_PROXY ?? ""),
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
    cacheReadahead: config.cacheReadahead,
    transcodeDir: config.transcodeDir,
    transcodeMaxrate: config.transcodeMaxrate,
    trustProxy: config.trustProxy,
    packageUrl: config.packageUrl,
    // The key is the only thing protecting a published package; it never
    // reaches a log, the same way the Telegram session does not.
    packageKey: config.packageKey === null ? null : "<redacted>",
    catalogDir: config.catalogDir,
    address: `${config.hostname}:${config.port}`,
  };
}
