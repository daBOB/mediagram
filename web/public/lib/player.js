/**
 * The player dialog: one title at a time, direct or transcoded.
 *
 * Kept apart from the shelves because opening a title is where all the state
 * is. A set played directly is a `src` and nothing else; a set the browser
 * refuses is an hls.js instance and a running ffmpeg on the server, and both
 * have to be torn down when the dialog closes or the server keeps encoding
 * for a viewer who has gone.
 */

import { playbackFor } from "./link.js";
import { playTranscoded } from "./hls-playback.js";
import { sourceBitrate, watchPlayback } from "./adapt-playback.js";
import { clockTime, episodeLabel } from "./format.js";

const dialog = document.getElementById("player");
const video = document.getElementById("video");
const note = document.getElementById("note");
const summaryBox = document.getElementById("summary");
const now = document.getElementById("now");
const jump = document.getElementById("jump");
const jumpTo = document.getElementById("jump-to");
const jumpAt = document.getElementById("jump-at");

/** What the last title left attached, or `null` when it was played directly. */
let detach = null;
/** The set being played, and where in it the current conversion started. */
let playing = null;
let base = 0;
/** The cap the running conversion was given, or `null` while playing direct. */
let capBits = null;

/**
 * Worded to cover both reasons a title is converted. It may be a codec the
 * browser will not decode, or a bitrate the link will not carry, and saying
 * "your browser cannot play this" about the second one is simply untrue.
 */
function noteFor(set) {
  const decision = playbackFor(set);
  return decision.kind === "direct"
    ? null
    : `Converting as you watch: ${decision.reason}.`;
}

/** Attaches whatever subtitle tracks the catalog said this set has. */
function attachSubtitles(set) {
  for (const existing of [...video.querySelectorAll("track")]) existing.remove();
  for (const [index, lang] of (set.subtitles ?? []).entries()) {
    const track = document.createElement("track");
    track.kind = "subtitles";
    track.srclang = lang;
    track.label = lang;
    track.src = `/api/sets/${encodeURIComponent(set.setId)}/subtitles/${lang}.vtt`;
    if (index === 0) track.default = true;
    video.append(track);
  }
}

/** Fetched on open rather than carried in the catalog, which would be large. */
async function showSummary(set) {
  summaryBox.hidden = true;
  summaryBox.textContent = "";
  if (!set.hasSummary) return;
  try {
    const response = await fetch(`/api/sets/${encodeURIComponent(set.setId)}/summary`);
    if (!response.ok) return;
    summaryBox.textContent = await response.text();
    summaryBox.hidden = false;
  } catch {
    /* A missing summary is not worth interrupting playback for. */
  }
}

function stop() {
  video.pause();
  if (detach) {
    detach();
    detach = null;
  }
  video.removeAttribute("src");
  video.load();
}

/**
 * Starts converting `set` from `seconds` and plays the result.
 *
 * Also the seek: a conversion has only encoded as far as it has got, so the
 * video's own scrub bar cannot reach past it. Landing somewhere else in the
 * film means starting the conversion again there, which is what the slider
 * above the note does.
 */
function convert(set, seconds, warning, maxrateBits) {
  stop();
  base = seconds;
  capBits = maxrateBits ?? null;
  watch.begin({ capBits, sourceBits: sourceBitrate(set) });
  // Shown unconditionally: a title that was playing directly has its note
  // hidden, and a switch that explains itself invisibly explains nothing.
  note.textContent = `${warning} Starting at ${clockTime(seconds)}…`;
  note.hidden = false;

  playTranscoded(video, set.setId, {
    seekSeconds: seconds,
    maxrateBits,
    onFatal: (error) => {
      note.textContent = `The conversion stopped: ${error.message}. Pick a position to start it again.`;
    },
  })
    .then((release) => {
      detach = release;
      note.textContent = warning;
      start();
    })
    .catch((error) => {
      note.textContent = `Could not start the conversion: ${error.message}`;
    });
}

/** Where the viewer is in the film, not in the current conversion. */
function filmTime() {
  return base + (Number.isFinite(video.currentTime) ? video.currentTime : 0);
}

function showJump(set) {
  const runtime = Number(set.duration) || 0;
  jump.hidden = runtime === 0;
  if (jump.hidden) return;
  jumpTo.max = String(Math.floor(runtime));
  jumpTo.value = "0";
  jumpAt.textContent = `0:00 / ${clockTime(runtime)}`;
}

function start() {
  video.play().catch(() => {
    /* Autoplay is often blocked; the viewer can press play. */
  });
}

/** Opens the dialog on `set` and starts it playing, whichever way it plays. */
export function openPlayer(set) {
  stop();
  attachSubtitles(set);
  void showSummary(set);
  now.textContent = [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");

  const warning = noteFor(set);
  note.textContent = warning ?? "";
  note.hidden = warning === null;
  playing = set;
  base = 0;
  dialog.showModal();

  if (warning === null) {
    jump.hidden = true;
    capBits = null;
    // Watched too: the original is the stream most likely to be too much for
    // a link, since nothing caps what it was mastered at.
    watch.begin({ capBits: null, sourceBits: sourceBitrate(set) });
    video.src = `/api/sets/${encodeURIComponent(set.setId)}/stream`;
    start();
    return;
  }

  showJump(set);
  convert(set, 0, warning);
}

// The slider only follows playback while the viewer is not holding it.
video.addEventListener("timeupdate", () => {
  if (jump.hidden || document.activeElement === jumpTo) return;
  jumpTo.value = String(Math.floor(filmTime()));
  jumpAt.textContent = `${clockTime(filmTime())} / ${clockTime(Number(jumpTo.max))}`;
});

jumpTo.addEventListener("input", () => {
  jumpAt.textContent = `${clockTime(Number(jumpTo.value))} / ${clockTime(Number(jumpTo.max))}`;
});

// `change`, not `input`: restarting an encode on every pixel of a drag would
// start dozens of them and finish none.
jumpTo.addEventListener("change", () => {
  if (!playing) return;
  // Keeps whatever cap the link was found to need; a seek is not new evidence
  // that the connection got better.
  convert(playing, Number(jumpTo.value), noteFor(playing), capBits ?? undefined);
});

/**
 * The link is losing ground: move to something it can carry.
 *
 * Deliberately keeps the viewer's place — `filmTime()`, not zero — so the
 * interruption costs seconds rather than the film starting again.
 */
const watch = watchPlayback({
  video,
  onSwitch: (targetBits) => {
    if (!playing) return;
    showJump(playing);
    convert(
      playing,
      filmTime(),
      `The connection is slower than this title needs, so it is being converted` +
        ` to ${(targetBits / 1e6).toFixed(1)} Mbit/s.`,
      targetBits,
    );
  },
  onExhausted: () => {
    note.textContent = "This connection is too slow for this title, even at the lowest quality.";
    note.hidden = false;
  },
});

// Closing the tab never fires the dialog's `close`, and a transcode nobody
// released keeps the encoder until the reaper notices.
window.addEventListener("pagehide", stop);

document.getElementById("close").addEventListener("click", () => dialog.close());
dialog.addEventListener("close", () => {
  // Drops the connection so the server stops pulling bytes from Telegram, and
  // the transcode so it stops encoding for nobody.
  stop();
  for (const track of [...video.querySelectorAll("track")]) track.remove();
  summaryBox.hidden = true;
  jump.hidden = true;
  playing = null;
  capBits = null;
  watch.begin({ capBits: null, sourceBits: null });
});
