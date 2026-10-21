/**
 * The statistics' download rows — network, on disk and next — from a
 * `/api/sets/:id/download` answer, worded as the Android overlay words them
 * (`android/feature/player/src/main/kotlin/PlaybackStatRows.kt`), so a viewer
 * who uses both reads one set of sentences.
 */

import { humanSize } from "../format.js";

/** A rate in bits per second, `9.4 Mbps`, rounded as `bitrateLabel` rounds one. */
function mbps(bits) {
  const value = bits / 1e6;
  return `${value < 10 ? value.toFixed(1) : String(Math.round(value))} Mbps`;
}

/**
 * What is arriving for playback now: `14 Mbps · 2 in flight`, or `idle` once
 * nothing is being fetched — said in a word, since a zero rate reads like a stall.
 */
export function networkLine(traffic) {
  const parts = [];
  if (traffic.foregroundBitsPerSecond > 0) parts.push(mbps(traffic.foregroundBitsPerSecond));
  if (traffic.foregroundInFlight > 0) parts.push(`${traffic.foregroundInFlight} in flight`);
  return parts.length > 0 ? parts.join(" · ") : "idle";
}

/** How much of the title is on the server's disk; rounded down, so missing chunks never read 100%. */
export function onDiskLine(heldBytes, totalBytes) {
  if (!(totalBytes > 0)) return null;
  return `${humanSize(heldBytes)} of ${humanSize(totalBytes)} · ${Math.floor((heldBytes * 100) / totalBytes)}%`;
}

/** The episode the series preload is taking in: `Pilot · 34% · waiting for playback`, or its rate. */
export function nextLine(next, traffic) {
  if (!next) return null;
  const percent = next.totalBytes > 0 ? Math.floor((next.heldBytes * 100) / next.totalBytes) : 0;
  const state = traffic.backgroundWaiting
    ? "waiting for playback"
    : traffic.backgroundBitsPerSecond > 0
      ? mbps(traffic.backgroundBitsPerSecond)
      : null;
  return [next.title, `${percent}%`, state].filter(Boolean).join(" · ");
}

/** The rows, in the overlay's order; none before the first answer arrives. */
export function downloadRows(facts) {
  if (!facts) return [];
  return [
    ["network", networkLine(facts.traffic)],
    ["on disk", onDiskLine(facts.onDiskBytes, facts.totalBytes)],
    ["next", nextLine(facts.next, facts.traffic)],
  ];
}
