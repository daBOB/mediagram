/**
 * What one open player tells the server about itself, and the loop that
 * keeps sending it.
 *
 * The System page and the player it describes are usually different
 * devices — a phone looking at what the television is playing — so this
 * cannot be read off a live object the way the on-screen HUD is. It has to
 * travel as a small POST, periodically, while the dialog is open.
 *
 * Kept pure and apart from `player.js`, which is already at this codebase's
 * file size limit: reading the video element and building the body are the
 * same work whichever caller — the HUD or this — asks for it.
 */

import { bufferedAhead } from "./preload-readout.js";
import { sourceBitrate } from "./streaming/adapt-playback.js";

/** How often an open player reports itself. */
export const REPORT_MS = 5000;

/** A picture carried across rather than encoded, tagged HEVC by the source. */
const HEVC_NAMES = new Set(["hevc", "h265"]);

/**
 * The video element's own state, in the shape both the HUD and the report
 * to the server want: the one thing worth reading once rather than twice.
 *
 * @param {HTMLVideoElement} video
 * @param {{starved: boolean, waitingToStart: {mode: string}|null,
 *          held: boolean, watch: {fillRate: () => number|null, health: () => string}}} ctx
 */
export function playbackFields(video, ctx) {
  const quality = video.getVideoPlaybackQuality?.();
  return {
    readyState: video.readyState,
    ahead: bufferedAhead(video.buffered, video.currentTime),
    starved: ctx.starved,
    awaitingStart: ctx.waitingToStart?.mode === "buffered",
    fillRate: ctx.watch.fillRate(),
    health: ctx.watch.health(),
    dropped: quality?.droppedVideoFrames,
    frames: quality?.totalVideoFrames,
    paused: video.paused,
    held: ctx.held,
  };
}

/**
 * What this title is, for the report: which set, how it is reaching the
 * screen, and at what rate.
 *
 * `mode` is `"direct"` for a file the element plays as it is; otherwise
 * `"transcode"` until the server says it was able to copy the picture
 * instead, at which point it is `"copy"`, or `"hevc-copy"` for a copied
 * HEVC picture — fMP4 rather than the ordinary TS, see `SessionSpec`.
 *
 * @param {{setId: string, vcodec?: string, acodec?: string}|null} set
 * @param {{converting: boolean, copiedOutput: boolean, capBits: number|null}} ctx
 */
export function playbackMeta(set, ctx) {
  const vcodec = String(set?.vcodec ?? "").toLowerCase();
  const mode = !ctx.converting
    ? "direct"
    : !ctx.copiedOutput
      ? "transcode"
      : HEVC_NAMES.has(vcodec)
        ? "hevc-copy"
        : "copy";
  return {
    setId: set?.setId ?? "",
    mode,
    videoCodec: set?.vcodec ?? null,
    audioCodec: set?.acodec ?? null,
    bitrateBits: ctx.capBits ?? sourceBitrate(set ?? {}),
  };
}

function clamped(value, maxLength) {
  return typeof value === "string" && value.length > 0 ? value.slice(0, maxLength) : null;
}

function finiteOrNull(value) {
  return Number.isFinite(value) && value >= 0 ? value : null;
}

/**
 * The exact POST body, defensive against whatever the fields above could
 * not measure: the server clamps and validates the same way, but a body
 * that is already sane is one fewer round trip spent on a 400.
 */
export function playbackReport(fields) {
  return {
    viewer: fields.viewer,
    setId: fields.setId,
    title: clamped(fields.title, 200) ?? "",
    mode: fields.mode,
    videoCodec: clamped(fields.videoCodec, 16),
    audioCodec: clamped(fields.audioCodec, 16),
    bitrateBits: finiteOrNull(fields.bitrateBits),
    ahead: finiteOrNull(fields.ahead),
    health: fields.health,
    fillRate: finiteOrNull(fields.fillRate),
    dropped: finiteOrNull(fields.dropped),
    frames: finiteOrNull(fields.frames),
    paused: fields.paused === true,
    held: fields.held === true,
  };
}

const defaultPost = (body) =>
  fetch("/api/status/playback", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });

/** Answers that mean this page will never be accepted here; retrying is pointless. */
const NEVER_ACCEPTED = [403, 404, 405, 415];

/**
 * Posts `read()`'s reading every `intervalMs`, until told to stop or the
 * server says this page is not one it will ever answer.
 *
 * Network errors are ignored until the next tick — a Wi-Fi blip is not a
 * reason to stop reporting for the rest of the session, the same discipline
 * `pollStatus` (`status-lines.js`) follows for the GET side of this page.
 *
 * @param {{read: () => object, post?: (body: object) => Promise<Response>,
 *          intervalMs?: number, schedule?: (fn: () => void, ms: number) => any,
 *          cancel?: (handle: any) => void}} options
 * @returns {() => void} stops the loop; safe to call more than once
 */
export function reportPlayback(options) {
  const { read } = options;
  const post = options.post ?? defaultPost;
  const intervalMs = options.intervalMs ?? REPORT_MS;
  const schedule = options.schedule ?? ((fn, ms) => setInterval(fn, ms));
  const cancel = options.cancel ?? ((handle) => clearInterval(handle));

  let stopped = false;

  async function tick() {
    if (stopped) return;
    try {
      const response = await post(playbackReport(read()));
      if (!stopped && NEVER_ACCEPTED.includes(response.status)) stop();
    } catch {
      // Ignored until the next tick.
    }
  }

  void tick();
  const handle = schedule(() => void tick(), intervalMs);

  function stop() {
    if (stopped) return;
    stopped = true;
    cancel(handle);
  }
  return stop;
}
