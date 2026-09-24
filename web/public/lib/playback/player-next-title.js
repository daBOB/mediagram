/** The next-title offer owns its countdown, cancellation and speculative work. */
import { episodeLabel } from "../format.js";
import { playbackFor } from "../link.js";
import { resumeAt } from "../resume-point.js";
import * as state from "../watch-state.js";
import { warmTranscode } from "./streaming/hls-playback.js";
import { COUNTDOWN_SECONDS, upNextPhase } from "./up-next.js";

const PRELOAD_BYTES = 8 * 1024 * 1024;

export function mountPlayerNextTitle({ showControls, openTitle }) {
  const button = document.getElementById("play-next");
  const panel = document.getElementById("up-next");
  const title = document.getElementById("up-next-title");
  const timing = document.getElementById("up-next-in");
  const cancelled = new Set();
  let playing = null;
  let next = null;
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

  function offer() {
    button.hidden = next === null;
    button.title = next === null ? "" : titleLine(next);
  }

  function open(set, options = {}) {
    hide();
    // The pending source must join before we release its warm watcher.
    if (warm?.setId !== set.setId) dropWarm();
    playing = set;
    next = options.next ?? null;
    onOpenNext = options.onOpenNext ?? null;
    preloaded = null;
    offer();
  }

  function clear() {
    hide();
    dropWarm();
    playing = next = onOpenNext = preloaded = null;
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

  /** @param {"buffered"|"asap"} how */
  function playNext(how) {
    const following = next;
    hide();
    if (following) (onOpenNext ?? openTitle)(following, { autoplay: how });
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
      if (left <= 0) playNext("buffered");
    }, 1000);
  }

  document.getElementById("up-next-play").addEventListener("click", () => playNext("asap"));
  button.addEventListener("click", () => playNext("asap"));
  document.getElementById("up-next-cancel").addEventListener("click", () => {
    if (playing) cancelled.add(playing.setId);
    hide();
    dropWarm();
  });
  return { open, update, preload, releaseWarm, clear };
}
