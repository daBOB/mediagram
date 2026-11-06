/**
 * Deinterlace: a canvas over the picture that draws it again with the comb
 * filter, for DVD rips whose combing is baked into frames flagged progressive.
 * Browsers never deinterlace, so the filter has to run on our side of the
 * decoder.
 *
 * The canvas sits right above the `<video>` and takes the video's own
 * framing styles, so it lands on exactly the same pixels under every
 * framing. Cues are drawn by the browser *inside* the video box, which the
 * canvas now covers, so while this is on the showing tracks' active cues are
 * drawn in a layer above it.
 *
 * Only runs while on: off hides the canvas and ends the frame loop.
 */

import { createGlRenderer } from "./deinterlace-gl.js";

/** The layout styles the framing control sets on the video, and so the canvas needs too. */
const MIRRORED = ["objectFit", "aspectRatio", "left", "top", "width", "height"];

/**
 * @param {{video: HTMLVideoElement, canvas: HTMLCanvasElement, cues: HTMLElement,
 *   createRenderer?: (canvas: HTMLCanvasElement) => {draw: (video: HTMLVideoElement) => void, clear: () => void}|null}} deps
 */
export function mountDeinterlace({ video, canvas, cues, createRenderer = createGlRenderer }) {
  const renderer = createRenderer(canvas);
  let on = false;
  let pending = null;

  function mirror() {
    for (const name of MIRRORED) canvas.style[name] = video.style[name] ?? "";
  }

  function draw() {
    if (!renderer || video.readyState < 2) return;
    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
    }
    renderer.draw(video);
  }

  function stop() {
    if (pending === null) return;
    if (pending.kind === "video") video.cancelVideoFrameCallback?.(pending.id);
    else cancelAnimationFrame(pending.id);
    pending = null;
  }

  function loop() {
    pending = null;
    if (!on) return;
    draw();
    pending = typeof video.requestVideoFrameCallback === "function"
      ? { kind: "video", id: video.requestVideoFrameCallback(loop) }
      : { kind: "animation", id: requestAnimationFrame(loop) };
  }

  /** Draws what is on screen now and makes sure the loop is running. */
  function kick() {
    if (!on) return;
    if (pending === null) loop();
    else draw();
  }

  function showCues() {
    const lines = [];
    for (const track of video.textTracks) {
      if (track.mode !== "showing") continue;
      for (const cue of track.activeCues ?? []) {
        // A span per cue, so subtitle-style's size and backing rule reaches it;
        // getCueAsHTML keeps the cue's own italics without parsing any markup here.
        const line = document.createElement("span");
        if (typeof cue.getCueAsHTML === "function") line.append(cue.getCueAsHTML());
        else line.textContent = cue.text;
        lines.push(line);
      }
    }
    cues.replaceChildren(...lines.flatMap((line, i) => (i ? [document.createElement("br"), line] : [line])));
    cues.hidden = !on || lines.length === 0;
  }
  const listened = new WeakSet();
  function listenToTracks() {
    for (const track of video.textTracks) {
      if (listened.has(track)) continue;
      listened.add(track);
      track.addEventListener?.("cuechange", showCues);
    }
    showCues();
  }
  video.textTracks?.addEventListener?.("change", listenToTracks);
  video.textTracks?.addEventListener?.("addtrack", listenToTracks);

  // A change of picture size is a new texture size; a new source is a stale frame.
  for (const type of ["resize", "loadeddata", "seeked", "play"]) video.addEventListener(type, kick);
  video.addEventListener("emptied", () => {
    stop();
    renderer?.clear();
  });

  return {
    /** False when the browser has no WebGL; the menu then has no row for it. */
    supported: renderer !== null,
    get on() { return on; },
    /** Copies the video's layout onto the canvas; the framing control calls it after every change. */
    mirror,
    set(next) {
      next = next && renderer !== null;
      if (next === on) return;
      on = next;
      canvas.hidden = !on;
      if (on) {
        mirror();
        listenToTracks();
        kick();
      } else {
        stop();
        renderer?.clear();
        cues.hidden = true;
      }
    },
  };
}
