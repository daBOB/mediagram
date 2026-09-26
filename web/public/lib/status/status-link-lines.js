/**
 * The Telegram link and host groups, as sentences.
 *
 * Apart from `status-session-lines.js` for the reason the two server groups
 * are apart in `snapshot.ts`: one is the account's connection to Telegram,
 * the other is this process and the machine it runs on. Neither reaches a
 * video element or a running conversion.
 */

import { humanSize } from "../format.js";

/** `1,284`, not `1284`: the same grouping `toLocaleString` gives everywhere else. */
const grouped = (n) => n.toLocaleString("en");

function latencyPart(p50Ms, p95Ms) {
  if (p50Ms === null && p95Ms === null) return null;
  const typical = p50Ms === null ? "unmeasured" : `${Math.round(p50Ms)} ms typical`;
  return p95Ms === null ? typical : `${typical}, ${Math.round(p95Ms)} ms slow`;
}

function dcLine(dc) {
  const line = [`${grouped(dc.requests)} requests`, humanSize(dc.bytes), latencyPart(dc.p50Ms, dc.p95Ms)]
    .filter(Boolean)
    .join(" · ");
  return dc.errors > 0 ? `${line}, ${dc.errors} failed` : line;
}

/** One row per DC, then the flood and reconnect totals. */
export function linkRows(link) {
  if (link.dcs.length === 0) return [["DC", "nothing requested yet"]];
  return [
    ...link.dcs.map((dc) => [`DC ${dc.dc}`, dcLine(dc)]),
    ["Flood waits", link.flood.count === 0 ? "none" : `${link.flood.count}, ${link.flood.totalSeconds} s waited in all`],
    ["Reconnects", link.reconnects > 0 ? String(link.reconnects) : "none"],
  ];
}

/** `"cache"`, `"conversions"`, or the path itself if it is neither of this player's own. */
function purposeOf(dir, cacheDir, transcodeDir) {
  if (dir === cacheDir) return "cache";
  if (dir === transcodeDir) return "conversions";
  return dir;
}

/** One row per distinct filesystem the cache and conversions live on. */
function diskRows(disks, cacheDir, transcodeDir) {
  return disks.map((disk) => [
    "Disk free",
    `${humanSize(disk.freeBytes)} of ${humanSize(disk.totalBytes)}, ${disk.dirs
      .map((dir) => purposeOf(dir, cacheDir, transcodeDir))
      .join(" and ")}`,
  ]);
}

/**
 * Memory, event-loop lag, free disk and the runtime version.
 *
 * `cacheDir`/`transcodeDir` name which of `host.disks`' raw paths is which,
 * for the sentence; the snapshot itself already shows both paths elsewhere
 * (`cache.dir`, `transcodes.dir`), so naming them again here adds nothing to
 * find out from a reading that could not be had otherwise.
 */
export function hostRows(host, cacheDir, transcodeDir) {
  return [
    ["Memory", `${humanSize(host.rssBytes)} resident, ${humanSize(host.heapBytes)} heap`],
    [
      "Event loop",
      host.loopLagMs === null ? null : `${Math.round(host.loopLagMs.p50)} ms typical, ${Math.round(host.loopLagMs.max)} ms worst`,
    ],
    ...diskRows(host.disks, cacheDir, transcodeDir),
    ["Runtime", `Bun ${host.bun}`],
  ];
}
