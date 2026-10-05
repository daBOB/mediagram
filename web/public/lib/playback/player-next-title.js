/**
 * The run around the open title: the next-title offer with its countdown,
 * cancellation and speculative work, and the card's ⏮ and ⏭ — every way of
 * moving along a run opens through `openInRun`.
 */
import { episodeLabel } from "../format.js";
import { playbackFor } from "../link.js";
import { resumeAt } from "../resume-point.js";
import * as state from "../watch-state.js";
import { warmTranscode } from "./streaming/hls-playback.js";
import { COUNTDOWN_SECONDS, upNextPhase } from "./up-next.js";

const PRELOAD_BYTES = 8 * 1024 * 1024;

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("./player.js").PlayerOptions} PlayerOptions
 * @param {{showControls: () => void, openTitle: (set: CatalogSet, options: PlayerOptions) => void}} options
 */
export function mountPlayerNextTitle({ showControls, openTitle }) {
  const button = document.getElementById("play-next");
  const back = document.getElementById("previous");
  const panel = document.getElementById("up-next");
  const title = document.getElementById("up-next-title");
  const timing = document.getElementById("up-next-in");
  const cancelled = new Set();
  let playing = null;
  let next = null;
  let previous = null;
  let inRun = false;
  let onOpenNext = null;
  let countdown = null;
  let shownPhase = null;
  let preloaded = null;
  let warm = null;

  const titleLine = (set) => [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");

  function dropWarm() {
    warm?.controller.abort();
    warm = null;
  }

  function hide() {
    clearInterval(countdown);
    countdown = null;
    shownPhase = null;
    panel.hidden = true;
  }

  /**
   * A title with no run hides both steps; in a run, the end with nothing past
   * it is disabled instead, so the transport does not shift under a finger.
   */
  function offer() {
    for (const [control, target] of [[back, previous], [button, next]]) {
      control.hidden = !inRun;
      control.disabled = target === null;
      control.title = target === null ? "" : titleLine(target);
    }
  }

  /** @param {CatalogSet} set @param {PlayerOptions} [options] */
  function open(set, options = {}) {
    hide();
    // The pending source must join before we release its warm watcher.
    if (warm?.setId !== set.setId) dropWarm();
    playing = set;
    next = options.next ?? null;
    previous = options.previous ?? null;
    // Saying what is next already says there is a run.
    inRun = options.inRun ?? (next !== null || previous !== null);
    onOpenNext = options.onOpenNext ?? null;
    preloaded = null;
    offer();
  }

  function clear() {
    hide();
    dropWarm();
    playing = next = previous = onOpenNext = preloaded = null;
    inRun = false;
    offer();
  }

  /** Called after the source joins, or after its startup has failed. */
  function releaseWarm(setId) {
    if (warm?.setId === setId) dropWarm();
  }

  function warmNext() {
    if (!next || warm?.setId === next.setId || playbackFor(next).kind === "direct") return;
    dropWarm();
    const operation = { setId: next.setId, controller: new AbortController() };
    warm = operation;
    // Match the initial source: resume offset and first audio stream.
    void warmTranscode(next.setId, {
      seekSeconds: resumeAt(state.progressOf(next.setId)) ?? 0,
      signal: operation.controller.signal,
    }).then((release) => {
      if (warm !== operation) release();
    }).catch(() => {
      if (warm === operation) warm = null;
      // A failed convenience warm is reported only if actual playback fails.
    });
  }

  /** Warm the shared chunk cache once, only when current playback has headroom. */
  function preload(ahead) {
    if (!next || preloaded === next.setId || ahead < 30) return;
    preloaded = next.setId;
    void fetch(`/api/sets/${encodeURIComponent(next.setId)}/stream`, {
      headers: { range: `bytes=0-${PRELOAD_BYTES - 1}` },
    }).then((response) => response.body?.cancel()).catch(() => {});
  }

  /**
   * Opens `set` the way the run's next title opens, for ⏮, ⏭ and the
   * countdown alike, so a step back is never a different kind of open.
   * @param {CatalogSet|null} set @param {"buffered"|"asap"} [how]
   */
  function openInRun(set, how = "asap") {
    hide();
    if (set) (onOpenNext ?? openTitle)(set, { autoplay: how });
  }

  function update({ runtime, at, ended }) {
    const phase = upNextPhase({
      hasNext: next !== null && playing !== null,
      cancelled: playing !== null && cancelled.has(playing.setId),
      remainingSeconds: runtime > 0 ? runtime - at : null,
      ended,
    });
    if (phase === shownPhase) return;
    hide();
    shownPhase = phase;
    if (phase === "hidden") {
      if (warm?.setId !== playing?.setId) dropWarm();
      return;
    }
    title.textContent = titleLine(next);
    panel.hidden = false;
    showControls();
    if (phase === "waiting") {
      timing.textContent = "when this ends";
      warmNext();
      return;
    }
    let left = COUNTDOWN_SECONDS;
    timing.textContent = `starting in ${left}…`;
    countdown = setInterval(() => {
      left -= 1;
      timing.textContent = `starting in ${left}…`;
      if (left <= 0) openInRun(next, "buffered");
    }, 1000);
  }

  document.getElementById("up-next-play").addEventListener("click", () => openInRun(next));
  button.addEventListener("click", () => openInRun(next));
  back.addEventListener("click", () => openInRun(previous));
  document.getElementById("up-next-cancel").addEventListener("click", () => {
    if (playing) cancelled.add(playing.setId);
    hide();
    dropWarm();
  });
  return { open, update, preload, releaseWarm, clear, openInRun };
}
