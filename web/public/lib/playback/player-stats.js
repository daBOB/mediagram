/**
 * The stats overlay behind ⓘ: what the browser can say about the picture,
 * the sound and the buffer, under the row names the Android player uses.
 *
 * A row nothing here can know is left out rather than shown as a guess — a
 * dash or a zero would read as a measurement. Android's "reads" row counts
 * Telegram fetches, which a browser never sees, so it is never here.
 */

import { el } from "../dom.js";
import { bitrateLabel, clockTime, hdrLabel } from "../format.js";
import { languageLabel } from "../language-label.js";
import { channelLabel } from "./audio-chooser.js";
import { downloadRows } from "./download-rows.js";
import { preloadReadout } from "./preload-readout.js";

/** How often the open panel asks the server for its download rows. */
const DOWNLOAD_POLL_MS = 2000;

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("../../../src/catalog/audio-tracks").AudioTrack} AudioTrack
 * @param {{fields: ReturnType<typeof import("./playback-report.js").playbackFields>,
 *   set: CatalogSet|null, audio: AudioTrack|null, size: {width: number, height: number},
 *   download?: object|null}} at `download` is the server's `/api/sets/:id/download` answer
 * @returns {{name: string, value: string}[]}
 */
export function statsRows({ fields, set, audio, size, download = null }) {
  // The element's own size is the picture actually decoded; the index's
  // quality is only what the file was labelled, so it is the fallback.
  const resolution = size.width > 0 && size.height > 0 ? `${size.width}×${size.height}` : set?.quality;
  const sound = audio
    ? [audio.codec, channelLabel(audio.channels), languageLabel(audio.lang, null)]
    : [set?.acodec];
  const dropped = Number(fields.dropped);
  const rows = [
    ["video", [resolution, set?.vcodec, set ? hdrLabel(set) : null, set ? bitrateLabel(set) : null]],
    ["audio", sound],
    ["buffer", [fields.held ? "cached" : `${clockTime(fields.ahead)} ahead`]],
    ...downloadRows(download).map(([name, value]) => [name, [value]]),
    // The readout that used to sit under the bar. Its dropped count has its own row here.
    ["cache", [preloadReadout({ ...fields, dropped: 0 })]],
    ["dropped", [dropped > 0 ? `${dropped} frames` : null]],
  ];
  return rows
    .map(([name, parts]) => ({ name, value: parts.filter(Boolean).join(" ") }))
    .filter((row) => row.value !== "");
}

/**
 * The ⓘ toggle and the overlay. `draw` is called as often as the player
 * refreshes anything; it builds nothing while the overlay is shut.
 * @param {{video: HTMLVideoElement}} deps
 */
export function mountPlayerStats({ video }) {
  const button = document.getElementById("stats-toggle");
  const panel = document.getElementById("stats-panel");
  const bar = document.getElementById("top-bar");
  let last = null;
  /** The server's download rows, and the title they are about; see `poll`. */
  let download = null;
  let poller = null;

  /**
   * Below the top bar as tall as it is now: at phone width its marks wrap onto
   * a second row, and a fixed offset would put the overlay over the way out.
   */
  function place() {
    const top = `${Math.round(bar.getBoundingClientRect().height)}px`;
    if (panel.style.top !== top) panel.style.top = top;
  }

  function draw(fields, set, audio) {
    last = { fields, set, audio };
    if (panel.hidden) return;
    place();
    const size = { width: video.videoWidth || 0, height: video.videoHeight || 0 };
    const facts = download !== null && download.setId === set?.setId ? download.facts : null;
    panel.replaceChildren(...statsRows({ ...last, size, download: facts }).flatMap(({ name, value }) => [el("dt", null, name), el("dd", null, value)]));
  }

  /**
   * Asks the server what it is downloading for this title. Only while the
   * panel is open; a failed ask leaves the rows as they were until the next.
   */
  function poll() {
    const setId = last?.set?.setId;
    if (panel.hidden || !setId || !video.currentSrc) return;
    fetch(`/api/sets/${encodeURIComponent(setId)}/download`)
      .then((response) => (response.ok ? response.json() : null))
      .then((facts) => {
        if (!facts) return;
        download = { setId, facts };
        if (last) draw(last.fields, last.set, last.audio);
      })
      .catch(() => {});
  }

  button.addEventListener("click", () => {
    panel.hidden = !panel.hidden;
    button.setAttribute("aria-pressed", String(!panel.hidden));
    clearInterval(poller);
    poller = panel.hidden ? null : setInterval(poll, DOWNLOAD_POLL_MS);
    poll();
    if (last) draw(last.fields, last.set, last.audio);
    else if (!panel.hidden) place();
  });
  window.addEventListener("resize", () => {
    if (!panel.hidden) place();
  });

  return { draw };
}
