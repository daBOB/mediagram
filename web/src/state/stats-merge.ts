/**
 * Merging viewing stats: per viewer, the newest row per (title, device) and
 * per (day, device), ties broken the way every other kept row's are.
 *
 * Nothing is added up here. Two devices' rows for one title stay two rows,
 * and the same row arriving in two documents — one device's own and another
 * passing it on — stays one: totals are sums over devices, taken later by
 * `stats-summary.ts`, so a merge can never count anything twice.
 */

import { normalName, type SyncRecord } from "./sync-record";
import type { DayStatRow, StatsRows, TitleStatRow } from "./stats-record";
import { keep, type Held } from "./tie-break";

interface Viewer {
  titles: Map<string, Held<TitleStatRow>>;
  days: Map<string, Held<DayStatRow>>;
}

/** Each viewer's merged stats by normalised name; a key is omitted when it has no rows. */
export function mergeStats(records: SyncRecord[]): Map<string, StatsRows> {
  const viewers = new Map<string, Viewer>();
  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;
      let held = viewers.get(name);
      if (held === undefined) {
        held = { titles: new Map(), days: new Map() };
        viewers.set(name, held);
      }
      // JSON for the key: a set id and a device id may hold any text.
      for (const row of profile.titleStats ?? []) keep(held.titles, JSON.stringify([row.setId, row.device]), row, device);
      for (const row of profile.dayStats ?? []) keep(held.days, JSON.stringify([row.day, row.device]), row, device);
    }
  }
  const merged = new Map<string, StatsRows>();
  for (const [name, held] of viewers) {
    const titleStats = [...held.titles.values()].map((one) => one.row);
    const dayStats = [...held.days.values()].map((one) => one.row);
    merged.set(name, {
      ...(titleStats.length > 0 ? { titleStats } : {}),
      ...(dayStats.length > 0 ? { dayStats } : {}),
    });
  }
  return merged;
}
