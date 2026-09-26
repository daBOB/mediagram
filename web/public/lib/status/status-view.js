/**
 * What the player is doing, for whoever is standing in front of it.
 *
 * The terminal this process started in prints all of this, and is usually on
 * another machine, in another room, or gone. This is the same reading, put
 * where the person watching can see it.
 *
 * Set as rows of label and figure in the page's own faces rather than as a
 * monospace dashboard: this catalogue is a printed thing, and a telemetry
 * grid dropped into it would read as somebody else's tool bolted on.
 *
 * Everything that decides *what a reading says* lives in `status-lines.js`,
 * which can be tested without a browser. What is left here is where the nodes
 * go.
 */

import { el } from "../dom.js";
import { humanSize } from "../format.js";
import { cacheReadsLine, ofBudget, pollStatus, refreshLine, throughput, uptime } from "./status-lines.js";
import { hostRows, linkRows } from "./status-link-lines.js";
import { playbackRows, transcodeRows } from "./status-session-lines.js";

/** Where the catalogue comes from, in the words the Source row uses. */
const SOURCES = { package: "published package", channel: "the channel's index" };

function block(heading, rows) {
  const section = el("section", "status-block");
  section.append(el("h3", null, heading));
  const list = el("dl", "status-list");
  for (const [label, value] of rows) {
    // A row with nothing to say is left out rather than shown empty: a label
    // beside a blank is a reading that failed, and none of these did.
    if (value === null || value === undefined || value === "") continue;
    const line = el("div", "status-row");
    line.append(el("dt", null, label), el("dd", null, value));
    list.append(line);
  }
  section.append(list);
  return section;
}

function cacheRows(cache) {
  if (cache === null) return [["Cache", "disabled — every byte is fetched"]];
  return [
    ["Held", ofBudget(cache.heldBytes, cache.budget)],
    ["Reads", cacheReadsLine(cache)],
    ["Evicted", cache.evicted > 0 ? `${cache.evicted} chunks` : "nothing yet"],
    ["Readahead", `${cache.readahead} chunks`],
    ["Directory", cache.dir],
  ];
}

/**
 * What is crossing the wire, now and in total.
 *
 * The rate needs the reading before this one, which is why the previous
 * snapshot is threaded through rather than held inside the renderer: a render
 * is a pure function of what it is given, and the poller is what has a past.
 */
function upstreamRows(snapshot, previous) {
  const rate = throughput(
    previous ? { bytes: previous.fetchedBytes, at: previous.readAt } : null,
    { bytes: snapshot.fetchedBytes, at: snapshot.readAt },
  );
  return [
    ["Now", rate],
    ["Since starting", humanSize(snapshot.fetchedBytes)],
    [
      "Failed reads",
      snapshot.telegram.failedReads > 0 ? String(snapshot.telegram.failedReads) : "none",
    ],
  ];
}

/**
 * Renders a snapshot into `root`, replacing whatever was there.
 *
 * @param {HTMLElement} root
 * @param {object} snapshot what `/api/status` answered
 */
export function renderStatus(root, snapshot, previous = null) {
  const { catalog, cache, encoder, transcodes, telegram, link, playback, state, host } = snapshot;
  const panel = el("div", "status");

  panel.append(
    block("Catalogue", [
      ["Source", SOURCES[catalog.origin] ?? "this machine"],
      ["Refresh", refreshLine(catalog)],
      ["Holds", `${catalog.sets} playable sets, ${catalog.posters} posters`],
      ["Schema", `v${catalog.schema}`],
    ]),
    block("Cache", cacheRows(cache)),
    block("Upstream", upstreamRows(snapshot, previous)),
    block("Telegram link", linkRows(link)),
    block("Watching now", playbackRows(playback)),
    block("Conversion", [
      ["Encoder", encoder.device ? `${encoder.name} on ${encoder.device}` : encoder.name],
      ...transcodeRows(transcodes, encoder.name),
    ]),
    block("This player", [
      [
        "Telegram",
        telegram.connected === null
          ? "cannot say"
          : telegram.connected
            ? "connected"
            : "disconnected",
      ],
      ["Watch state", state.remembered ? state.path : "not remembered"],
      ...hostRows(host, cache?.dir ?? null, transcodes.dir),
      ["Uptime", uptime(snapshot.uptimeSeconds)],
    ]),
  );

  // Replaced in one go rather than emptied and refilled: every two seconds,
  // the second way leaves a blank frame the eye catches as a flicker.
  root.textContent = "";
  root.append(panel);
}

/**
 * Fills `root` and keeps it filled. Returns the function that stops it.
 *
 * The caller must call that on leaving. Nothing here stops on its own,
 * because the panel cannot tell being navigated away from apart from being
 * briefly off-screen — the router can.
 */
export function watchStatus(root, fetchStatus = () => fetch("/api/status")) {
  // The one thing the panel remembers. A rate is two readings and the time
  // between them, and only the thing doing the polling has the earlier one.
  let previous = null;

  return pollStatus({
    fetchStatus,
    onSnapshot: (snapshot) => {
      // Stamped on arrival rather than taken from the server: the interval
      // that matters is between the readings this page actually received.
      const stamped = { ...snapshot, readAt: Date.now() };
      renderStatus(root, stamped, previous);
      previous = stamped;
    },
    onError: (error) => {
      root.textContent = "";
      root.append(el("p", "error", `Could not read the player's status: ${error.message}`));
    },
  });
}
