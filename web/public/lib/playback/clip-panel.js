/**
 * The Clip panel: the A–B loop with its ends in view, and the export of it.
 *
 * The range *is* the loop (`transport.loop()`), so the panel is another way
 * to set the keys' `a` and `b`, and a viewer on a phone can do all of it by
 * touch. While both ends are set the loop plays, which is the preview.
 *
 * Closing the panel does not cancel an export: the job is the server's and
 * the panel only follows it. What it follows is `clip-flow.js`'s decision.
 */

import { clockTime } from "../format.js";
import * as state from "../watch-state.js";
import { buildClipPanel, clipLink, downloadClip } from "./clip-panel-view.js";
import { clipProblem, nextClipStep, presetAround } from "./clip-flow.js";

const POLL_MS = 1000;

/**
 * @param {{button: HTMLElement, menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   transport: {loop: () => {from: number|null, to: number|null}, setLoop: (range: {from: number, to: number}) => void},
 *   filmTime: () => number, runtime: () => number, audioTrack: () => number}} deps
 */
export function mountClipPanel({ button, menus, transport, filmTime, runtime, audioTrack }) {
  const { panel, start, end, length, exportButton, said, ready } = buildClipPanel({
    move: (which, by) => move(which, by),
  });

  let setId = null;
  let range = { from: 0, to: 0 };
  /** A clip opened from a marked scene counts as clip mode with the panel shut. */
  let fromScene = false;
  let job = null;
  let finished = new Map();
  const downloaded = new Set();
  let timer = 0;
  let starting = false;
  let problem = null;
  /** Why the last export did not start. */
  let message = "";

  const isOpen = () => !panel.hidden;

  function apply(next) {
    range = { from: Math.max(0, Math.round(next.from)), to: Math.round(next.to) };
    transport.setLoop(range);
    // `setLoop` may have sorted a reversed pair.
    range = { from: transport.loop().from ?? range.from, to: transport.loop().to ?? range.to };
    render();
  }

  /** `by === null` is "Set to now". */
  function move(which, by) {
    apply({ ...range, [which]: by === null ? filmTime() : range[which] + by });
  }

  function render() {
    start.time.textContent = clockTime(range.from);
    end.time.textContent = clockTime(range.to);
    problem = clipProblem(range.from, range.to);
    const seconds = Math.round(range.to - range.from);
    length.textContent = problem ?? `${seconds} s · plays as a loop`;
    length.classList.toggle("clip-problem", problem !== null);
    const running = job?.state === "running" || starting;
    exportButton.disabled = problem !== null || running || state.profileId() === null;
    if (running) said.textContent = `Making the clip… ${Math.round((job?.progress ?? 0) * 100)}%`;
    else if (job?.state === "failed") said.textContent = job.error ?? "The clip could not be made";
    else said.textContent = message;
    if (job?.state === "done") finished.set(job.id, job);
    ready.replaceChildren(...[...finished.values()].map((clip) => {
      return clipLink(clip, () => downloaded.add(clip.id));
    }));
  }

  /** Asks the job's state, then decides what it means. */
  async function poll() {
    clearTimeout(timer);
    if (job === null) return;
    const asked = job.id;
    try {
      const response = await fetch(`/api/clips/${encodeURIComponent(asked)}`);
      if (response.status === 404 && job?.id === asked) job = null;
      else if (response.ok && job?.id === asked) job = await response.json();
    } catch { /* The next poll asks again. */ }
    render();
    follow();
  }

  function follow() {
    clearTimeout(timer);
    const step = nextClipStep(job, { visible: !document.hidden, downloaded });
    if (step === "download") { downloadClip(job); downloaded.add(job.id); render(); } else if (step === "poll") timer = setTimeout(poll, POLL_MS);
  }

  async function startExport() {
    const profile = state.profileId();
    if (profile === null || problem !== null) return;
    starting = true;
    message = "";
    render();
    try {
      const response = await fetch(`/api/profiles/${encodeURIComponent(profile)}/clips`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ setId, from: range.from, to: range.to, audio: audioTrack() }),
      });
      if (response.status === 202) {
        job = { id: (await response.json()).id, state: "running", progress: 0 };
      } else {
        const body = response.status === 409 ? await response.json().catch(() => null) : null;
        message = body?.reason ?? "Couldn't start the clip";
      }
    } catch { message = "Couldn't start the clip"; }
    starting = false;
    render();
    if (job?.state === "running") void poll();
  }

  /** The profile's running and finished clips for this title, so a closed tab's work is found again. */
  async function recall() {
    const profile = state.profileId();
    if (profile === null) return;
    const asked = setId;
    try {
      const response = await fetch(`/api/profiles/${encodeURIComponent(profile)}/clips?setId=${encodeURIComponent(asked)}`);
      if (!response.ok || asked !== setId) return;
      for (const clip of await response.json()) {
        if (clip.state === "done") finished.set(clip.id, clip);
        else if (clip.state === "running" && job === null) job = clip;
      }
    } catch { return; }
    render();
    follow();
  }

  function show() {
    const loop = transport.loop();
    const set = loop.from !== null && loop.to !== null;
    apply(set ? loop : presetAround(filmTime(), runtime() || null));
    menus.panel(button, panel);
    void recall();
  }

  function toggle() {
    if (isOpen()) menus.close()?.focus();
    else show();
  }

  button.addEventListener("click", toggle);
  exportButton.addEventListener("click", () => void startExport());
  document.addEventListener("visibilitychange", () => {
    if (document.hidden || job === null) return;
    if (job.state === "running") void poll(); else follow();
  });

  return {
    toggle,
    /** Whether playback should leave no trace: the panel is up, or a marked scene opened this title. */
    active: () => isOpen() || fromScene,
    /** After `a` or `b`: the panel follows the loop, and keeps its range when a key clears it. */
    refresh: () => {
      if (!isOpen()) return;
      const loop = transport.loop();
      apply({ from: loop.from ?? range.from, to: loop.to ?? range.to });
    },
    /** A new title: forget the last one's range and jobs; a marked scene opens ready to preview. */
    open(set, options = {}) {
      clearTimeout(timer);
      setId = set.setId;
      job = null;
      message = "";
      finished = new Map();
      fromScene = options.clip !== undefined;
      if (isOpen()) menus.close();
      if (fromScene) { apply(options.clip); menus.panel(button, panel); void recall(); }
    },
  };
}
