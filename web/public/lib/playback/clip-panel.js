/**
 * The Clip panel: the A–B loop with its ends in view, and the export of it.
 *
 * The range *is* the loop (`transport.loop()`), so the panel is another way
 * to set the keys' `a` and `b`, and a viewer on a phone can do all of it by
 * touch. While both ends are set the loop plays, which is the preview.
 *
 * That loop lasts only while the panel is open: closing it puts back the loop
 * the viewer had before, usually none. Left behind, it would turn a phone's
 * seek bar into a way back to the start of the scene. While the panel is open
 * the card beneath keeps working, and "Pick again" drops the loop so the
 * viewer can seek anywhere and set both ends there.
 *
 * Closing the panel does not cancel an export: the job is the server's and
 * the panel only follows it. What it follows is `clip-flow.js`'s decision.
 */

import { clockTime } from "../format.js";
import * as state from "../watch-state.js";
import { NO_LOOP } from "./ab-loop.js";
import { buildClipPanel, clipLink, downloadClip } from "./clip-panel-view.js";
import { clipProblem, nextClipStep, presetAround } from "./clip-flow.js";

const POLL_MS = 1000;

/**
 * @param {{button: HTMLElement, menus: ReturnType<typeof import("./player-menus.js").mountPlayerMenus>,
 *   transport: {loop: () => {from: number|null, to: number|null}, setLoop: (range: {from: number|null, to: number|null}) => void},
 *   filmTime: () => number, runtime: () => number, audioTrack: () => number}} deps
 */
export function mountClipPanel({ button, menus, transport, filmTime, runtime, audioTrack }) {
  const { panel, start, end, length, exportButton, said, progress, ready, card } = buildClipPanel({
    move: (which, by) => move(which, by),
    pickAgain: () => apply(NO_LOOP),
  });

  let setId = null;
  /** The loop as the panel set it; an end is `null` while the viewer picks it. */
  let range = { ...NO_LOOP };
  /** The viewer's own loop from before the panel opened, put back when it closes. */
  let before = { ...NO_LOOP };
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

  /** Ends kept to the frame they were set on: a clip starts where the viewer chose, not at the nearest second. */
  function apply(next) {
    const at = (end) => (end === null ? null : Math.max(0, end));
    transport.setLoop({ from: at(next.from), to: at(next.to) });
    // `setLoop` may have sorted a reversed pair.
    range = transport.loop();
    render();
  }

  /** `by === null` is "Set to now"; a nudge needs an end to move. */
  function move(which, by) {
    if (by !== null && range[which] === null) return;
    apply({ ...range, [which]: by === null ? filmTime() : range[which] + by });
  }

  function render() {
    for (const [row, at] of [[start, range.from], [end, range.to]]) {
      row.time.textContent = at === null ? "–:––" : clockTime(at);
      for (const nudge of row.nudges) nudge.disabled = at === null;
    }
    problem = clipProblem(range.from, range.to);
    const seconds = Math.round(range.to - range.from);
    length.textContent = problem ?? `${seconds} s · plays as a loop`;
    length.classList.toggle("clip-problem", problem !== null);
    const running = job?.state === "running" || starting;
    exportButton.disabled = problem !== null || running || state.profileId() === null;
    const news = running ? "Making the clip…"
      : job?.state === "failed" ? job.error ?? "The clip could not be made"
        : job?.state === "done" ? "The clip is ready" : message;
    // Only a change is written: a live region rewritten is read out again.
    if (said.textContent !== news) said.textContent = news;
    progress.textContent = running ? `${Math.round((job?.progress ?? 0) * 100)}%` : "";
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

  /** Opens on `preset`, else on the viewer's loop, else on a stretch around the playhead. */
  function show(preset) {
    before = transport.loop();
    const set = before.from !== null && before.to !== null;
    apply(preset ?? (set ? before : presetAround(filmTime(), runtime() || null)));
    menus.panel(button, panel, { closed: () => transport.setLoop(before), alongside: card });
    void recall();
  }

  function toggle() {
    if (isOpen()) menus.close()?.focus();
    else show(null);
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
    /** `a` and `b` while the panel is up set that end to now; `false` leaves them to the loop. */
    key: (end) => {
      if (!isOpen()) return false;
      move(end, null);
      return true;
    },
    /** A new title: forget the last one's range and jobs; a marked scene opens ready to preview. */
    open(set, options = {}) {
      clearTimeout(timer);
      setId = set.setId;
      job = null;
      message = "";
      finished = new Map();
      fromScene = options.clip !== undefined;
      // The last title's loop is not this one's to get back.
      before = { ...NO_LOOP };
      if (isOpen()) menus.close();
      // Closing it frees playback as any close does, but the title still saves nothing until it is left.
      if (fromScene) show(options.clip);
    },
  };
}
