/**
 * The sentences the status panel is made of, and the loop that retakes them.
 *
 * Apart from `status-view.js` for the reason `buffer-health.js` is apart from
 * `adapt-playback.js`: what a reading *says* and where its nodes *go* fail
 * differently, and only the first can be tested without a browser. Everything
 * here is a string or a stop function, and none of it touches the document.
 */

import { humanSize } from "./format.js";
import { catalogueAge } from "./colophon.js";

/** How often the reading is retaken while the panel is open. */
export const POLL_MS = 2000;

/** `7.0 GB of 20.0 GB (35%)`, or just the amount when there is no budget. */
export function ofBudget(held, budget) {
  if (!Number.isFinite(held)) {
    return Number.isFinite(budget) && budget > 0 ? `unmeasured of ${humanSize(budget)}` : null;
  }
  if (!Number.isFinite(budget) || budget <= 0) return humanSize(held);
  return `${humanSize(held)} of ${humanSize(budget)} (${Math.round((held / budget) * 100)}%)`;
}

/** `1h 12m`, which is how long a person says a process has been up. */
export function uptime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return null;
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

/**
 * What the last refresh did, said as a sentence rather than a status word.
 *
 * The `kept` case is the only reading on this page that is a warning. A
 * player serving a catalogue it could not refresh looks exactly like one
 * serving a current one, and nothing else here would say otherwise.
 */
export function refreshLine(catalog, now = Date.now()) {
  if (catalog.origin !== "package") return "read from this machine";
  const age = catalogueAge(catalog.publishedAt ?? null, now);
  if (catalog.refresh === "kept") {
    const reason = catalog.reason ?? "no reason given";
    return `refresh refused — ${reason}${age ? `, still serving the one ${age}` : ""}`;
  }
  return [age, catalog.refresh === "updated" ? "refreshed just now" : "already current"]
    .filter(Boolean)
    .join(" · ");
}

/**
 * Bytes per second between two readings, said as `12.4 Mbps`.
 *
 * Derived here rather than reported by the server, because the server does
 * not know how often it is being asked. An average since startup is a
 * different number and not the one anyone watching a stall wants: a player up
 * for six hours that is fetching hard right now would report a comfortable
 * average and a starving present.
 *
 * `null` until there are two readings to subtract, and on any reading that
 * went backwards — which is what a restarted player looks like.
 */
export function throughput(previous, current) {
  if (!previous || !current) return null;
  const seconds = (current.at - previous.at) / 1000;
  if (!(seconds > 0)) return null;
  const gained = current.bytes - previous.bytes;
  if (!Number.isFinite(gained) || gained < 0) return null;
  if (gained === 0) return "idle";

  const mbps = (gained * 8) / seconds / 1e6;
  return `${mbps < 10 ? mbps.toFixed(1) : String(Math.round(mbps))} Mbps`;
}

/** How the reads went, or that there have not been any. */
export function cacheReadsLine(cache) {
  if (cache.hitRate === null) return "nothing read yet";
  return `${Math.round(cache.hitRate * 100)}% from disk (${cache.hits} hits, ${cache.misses} misses)`;
}

/** The conversions, as label-and-value pairs. */
export function transcodeRows(transcodes) {
  const running =
    transcodes.running === 0
      ? [["Running", `none, of ${transcodes.capacity} allowed`]]
      : [
          ["Running", `${transcodes.running} of ${transcodes.capacity}`],
          ...transcodes.sessions.map((session, index) => [
            `Conversion ${index + 1}`,
            `${(session.maxrateBits / 1e6).toFixed(1)} Mbps from ${Math.round(session.seekSeconds / 60)}m` +
              `, ${session.watchers} watching`,
          ]),
        ];

  // Segments outlive the conversion that wrote them until the reaper gets to
  // them, so this is not zero just because nothing is running — and it has no
  // budget, unlike the cache, which is the reason to show it at all.
  return [
    ...running,
    ["On disk", transcodes.heldBytes === null ? null : humanSize(transcodes.heldBytes)],
    ["Directory", transcodes.dir ?? null],
  ];
}

/**
 * Asks for a reading, over and over, until told to stop.
 *
 * Returns the stop function, and stopping must be complete: a reply that
 * arrives after it is dropped rather than rendered. Without that, leaving the
 * panel while a request is in flight paints a reading into a view that is no
 * longer there.
 *
 * @param {{fetchStatus: () => Promise<Response>,
 *          onSnapshot: (snapshot: object) => void,
 *          onError: (error: Error) => void,
 *          intervalMs?: number,
 *          schedule?: (fn: () => void, ms: number) => any,
 *          cancel?: (handle: any) => void}} options
 */
export function pollStatus(options) {
  const { fetchStatus, onSnapshot, onError } = options;
  const intervalMs = options.intervalMs ?? POLL_MS;
  const schedule = options.schedule ?? ((fn, ms) => setInterval(fn, ms));
  const cancel = options.cancel ?? ((handle) => clearInterval(handle));

  let stopped = false;

  async function tick() {
    try {
      const response = await fetchStatus();
      if (stopped) return;
      if (!response.ok) throw new Error(`the player answered ${response.status}`);
      const snapshot = await response.json();
      if (stopped) return;
      onSnapshot(snapshot);
    } catch (error) {
      if (stopped) return;
      onError(error instanceof Error ? error : new Error(String(error)));
    }
  }

  void tick();
  const handle = schedule(() => void tick(), intervalMs);

  return function stop() {
    stopped = true;
    cancel(handle);
  };
}
