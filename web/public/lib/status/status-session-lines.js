/**
 * The conversion and playback groups, as sentences.
 *
 * Both describe a session in progress — a conversion running on the server,
 * a title playing on some device — which is why they share one module and
 * `status-link-lines.js` does not.
 */

import { clockTime, humanSize } from "../format.js";

const MODE_WORDS = { direct: "direct", copy: "copy", "hevc-copy": "HEVC copy", transcode: "transcode" };

/** The word for a mode everywhere it appears as one word rather than a clause. */
export function modeLabel(mode) {
  return MODE_WORDS[mode] ?? mode;
}

/** `"keeping up"`, `"falling behind"` or `"starving"`, from the buffer verdict. */
export function healthWord(health) {
  return health === "behind" ? "falling behind" : health === "starving" ? "starving" : "keeping up";
}

/** `5 re-encodes, 3 copies, 2 HEVC copies`, or `none` since this process started. */
export function startedLine(started) {
  const parts = [
    started.encode > 0 ? `${started.encode} re-encode${started.encode === 1 ? "" : "s"}` : null,
    started.copy > 0 ? `${started.copy} cop${started.copy === 1 ? "y" : "ies"}` : null,
    started.hevcCopy > 0 ? `${started.hevcCopy} HEVC cop${started.hevcCopy === 1 ? "y" : "ies"}` : null,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(", ") : "none";
}

/** One conversion, by what it is doing to the picture and how it is going. */
function sessionLine(session, encoderName) {
  const doing = [
    session.mode === "encode" ? `re-encode with ${encoderName}` : modeLabel(session.mode),
    session.speed === null ? null : `${session.speed.toFixed(1)}× realtime`,
    `${session.segments} segment${session.segments === 1 ? "" : "s"}`,
    session.cpuPercent === null ? null : `${Math.round(session.cpuPercent)}% CPU`,
  ]
    .filter(Boolean)
    .join(" · ");
  return `${doing}, ${session.watchers} watching`;
}

/**
 * Every running conversion, then what has run since this process started and
 * what they have written to disk. `encoderName` is `facts.encoder.name`: a
 * re-encode's own row names it, and the session carries only its mode.
 */
export function transcodeRows(transcodes, encoderName) {
  const running =
    transcodes.running === 0
      ? [["Running", `none, of ${transcodes.capacity} allowed`]]
      : [
          ["Running", `${transcodes.running} of ${transcodes.capacity}`],
          ...transcodes.sessions.map((session, index) => [`Conversion ${index + 1}`, sessionLine(session, encoderName)]),
        ];
  return [
    ...running,
    ["Since starting", transcodes.started ? startedLine(transcodes.started) : null],
    ["On disk", transcodes.heldBytes === null ? null : humanSize(transcodes.heldBytes)],
    ["Directory", transcodes.dir ?? null],
  ];
}

/** One open player's own reading, labelled by what it is watching. */
function playbackLine(entry) {
  const codecs = [entry.videoCodec, entry.audioCodec].filter(Boolean).join(" / ") || null;
  const line = [
    codecs,
    modeLabel(entry.mode),
    Number.isFinite(entry.bitrateBits) && entry.bitrateBits > 0 ? `${(entry.bitrateBits / 1e6).toFixed(1)} Mbps` : null,
    Number.isFinite(entry.ahead) && entry.ahead > 0 ? `${clockTime(entry.ahead)} ahead` : null,
    healthWord(entry.health),
    entry.dropped !== null && entry.frames !== null
      ? `${entry.dropped.toLocaleString("en")} dropped of ${entry.frames.toLocaleString("en")}`
      : null,
    entry.paused ? "paused" : null,
    entry.held ? "cached" : null,
    `from ${entry.from}`,
  ]
    .filter(Boolean)
    .join(" · ");
  return line;
}

/** One row per device currently watching, or a single row saying nobody is. */
export function playbackRows(playback) {
  if (playback.length === 0) return [["Watching", "nobody"]];
  return playback.map((entry, index) => [entry.title || entry.setId || `Viewer ${index + 1}`, playbackLine(entry)]);
}
