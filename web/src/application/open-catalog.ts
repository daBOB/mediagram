/** Selecting the startup catalog while keeping filesystem fallback decisions together. */
import type { Config } from "../config";
import { EXPECTED_SCHEMA, OLDEST_READABLE_SCHEMA } from "../catalog";
import { parseKey } from "../package/open";
import { refreshCatalog, type RefreshOptions } from "../package/refresh";
import { refreshFromChannel, type ChannelRefresh } from "../channel-index/refresh-from-channel";
import type { FoundIndex } from "../channel-index/find-newest-channel-index";
import type { NoIndex } from "../channel-index/pick-newest-index";

/**
 * Which catalog is open, and where it came from.
 *
 * A `dir` of `null` means there is no package configured and the index on
 * this machine is the catalog. The rest is what the player is asked about
 * afterwards — by the colophon, which says how old the catalogue is, and by
 * the status route, which says whether the last refresh actually worked.
 */
export interface OpenedCatalog {
  dir: string | null;
  origin: "package" | "channel" | "local";
  /** When the package was built or the snapshot pushed, in milliseconds. `null` for a local index. */
  publishedAt: number | null;
  /** The verdict of this run's refresh, or `null` when none was attempted. */
  refresh: "updated" | "unchanged" | "kept" | null;
  /** Why a refresh was refused, when it was. */
  reason: string | null;
}

/**
 * Refreshes the published catalog, and says which directory to read.
 *
 * A refresh that fails never stops the player: it keeps the catalog it
 * already had, and only a first run with nothing held is fatal.
 */
export async function openCatalog(cfg: Config, find: () => Promise<FoundIndex | NoIndex>, fetch?: RefreshOptions["fetch"]): Promise<OpenedCatalog> {
  if (cfg.packageUrl === null || cfg.packageKey === null) {
    return fromChannel(await refreshFromChannel(cfg.channelIndexDir, find));
  }

  const result = await refreshCatalog({
    baseUrl: cfg.packageUrl,
    key: parseKey(cfg.packageKey),
    root: cfg.catalogDir,
    supportedSchema: [OLDEST_READABLE_SCHEMA, EXPECTED_SCHEMA],
    fetch,
  });
  if (result.status === "kept") {
    console.log(`catalog: ${result.reason}`);
    if (result.dir === null) {
      throw new Error("no catalog: the package could not be read and none was held");
    }
    console.log("catalog: keeping the one already held");
  } else {
    console.log(`catalog: ${result.status} from ${cfg.packageUrl}`);
  }
  return {
    dir: result.dir,
    origin: "package",
    // Seconds in the package, milliseconds everywhere a browser will read it.
    publishedAt: result.identity ? result.identity.created_at * 1000 : null,
    refresh: result.status,
    reason: result.reason ?? null,
  };
}

/**
 * What a channel refresh means for the catalog served.
 *
 * With nothing installed and the channel unreadable, the index on this
 * machine is served, and the status route says why.
 */
function fromChannel(result: ChannelRefresh): OpenedCatalog {
  if (result.kind === "none") {
    console.log(`catalog: ${result.reason}; reading this machine's index`);
    return { dir: null, origin: "local", publishedAt: null, refresh: null, reason: result.reason };
  }
  console.log(
    result.refresh === "kept"
      ? `catalog: ${result.reason}; keeping the channel index pushed at ${result.pushedAt}`
      : `catalog: ${result.refresh} from the channel, pushed at ${result.pushedAt}`,
  );
  return {
    dir: result.dir,
    origin: "channel",
    publishedAt: result.pushedAt * 1000,
    refresh: result.refresh,
    reason: result.reason,
  };
}
