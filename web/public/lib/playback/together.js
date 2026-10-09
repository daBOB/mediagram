/**
 * Watch together, in the player (`docs/mlib-spec.md` §7b). It follows the
 * session this screen is in and sends the viewer's own moves. What to do is
 * decided in `../together/follow.js`; this only reads the player and applies
 * the steps. A change made here because the session moved never goes back
 * out: only `viewer()` sends, and only the player's viewer call paths call it.
 */

import { changeOf, decide, expectedPosition, follow } from "../together/follow.js";
import { acted } from "../together/moves.js";
import {
  heardTogether, onTogether, openForTogether, screen, serverNow, setForTogether, takeJoin, together, togetherNow,
} from "../together-now.js";
import { clockTime } from "../format.js";
import { speedLabel } from "./transport.js";

const DRIFT_CHECK_MS = 2000;
const NOTICE_MS = 2000;
/** A scrub sends one edit, this long after the last movement. */
const SEEK_SETTLE_MS = 300;
/** Below this the picture is loading or buffering. Nobody waits for it, and the next check catches it up. */
const HAVE_FUTURE_DATA = 3;
const NOT_HERE = "That title isn't on this profile";

/**
 * `seekTo` and `setRate` move the player without saying so to anyone;
 * `converting` is whether a seek restarts ffmpeg.
 */
export function mountTogether({ video, button, label, note, filmTime, seekTo, setRate, converting }) {
  let setId = null;
  let hasNext = false;
  /** The session this screen is in, and that session as this screen follows it. */
  let id = null;
  let current = null;
  /** A title opened because the session moved to it: opening it is not the viewer's move. */
  let expecting = null;
  let drift = null;
  let settle = null;
  let hide = null;

  const own = () => ({ set: setId, playing: !video.paused, pos: filmTime(), rate: video.playbackRate });

  /** Text, not `hidden`: a status region has to be on the page already for its change to be read out. */
  function show(text) {
    note.textContent = text;
    clearTimeout(hide);
    hide = setTimeout(() => { note.textContent = ""; }, NOTICE_MS);
  }

  /**
   * Brings the player to the session. A drift check passes over a screen
   * still loading or buffering, and never seeks a conversion: that starts
   * ffmpeg again, which lands it behind by as long as starting takes, every time.
   */
  function apply(drifting = false) {
    if (!current || (drifting && (video.seeking || video.readyState < HAVE_FUTURE_DATA))) return;
    for (const step of decide(own(), current, serverNow())) {
      if (step.kind === "open") return openTitle(step.set);
      if (step.kind === "rate") setRate(step.rate);
      if (step.kind === "seek" && !(drifting && converting())) seekTo(step.pos);
      if (step.kind === "play") void video.play().catch(() => {});
      if (step.kind === "pause") video.pause();
    }
  }

  /** The session's next title; one this profile may not see (a kids profile's limit) is where this screen leaves. */
  function openTitle(set) {
    if (!setForTogether(set)) return void leave("Left watch together: that title isn't on this profile");
    expecting = set;
    openForTogether(set);
  }

  function say({ change, label: who }) {
    const by = who ?? "Another screen";
    if (change === "joined") return `${by} joined`;
    if (change === "left") return `${by} left`;
    if (change === "sought") return `Skipped to ${clockTime(current.pos)} by ${by}`;
    if (change === "rate") return `${speedLabel(current.rate)} by ${by}`;
    if (change === "title") return `Next episode by ${by}`;
    return `${change === "paused" ? "Paused" : "Resumed"} by ${by}`;
  }

  function refresh() {
    const { available, reason, live } = togetherNow();
    const elsewhere = id === null ? live : null;
    const here = elsewhere === null || setForTogether(elsewhere.session.set) !== null;
    button.textContent = id !== null ? "Leave watch together" : elsewhere ? "Join" : "Watch together";
    button.disabled = id === null && (!available || !here);
    button.title = !button.disabled ? "" : available ? NOT_HERE : reason ?? "";
    label.hidden = current === null;
    if (current) label.textContent = `Together · ${current.members.map((m) => m.label).join(", ")}`;
  }

  function end(message) {
    if (message) show(message);
    id = null;
    current = null;
    clearInterval(drift);
    clearTimeout(settle);
    drift = null;
    refresh();
  }

  /** A start's or a join's answer, heard as the stream would say it: a stale session is over at once. */
  function enter(answer) {
    if (!answer.ok) return show(answer.status === 404 ? "Watch together ended" : answer.body?.reason ?? "Watch together couldn't start");
    id = answer.body.id;
    drift ??= setInterval(() => apply(true), DRIFT_CHECK_MS);
    heardTogether(answer.body);
  }

  async function start() {
    const answer = await together({ op: "start", screen, ...own() });
    // Another screen started one first: this is a join of theirs.
    if (answer.status === 409) return joinLive(answer.body);
    enter(answer);
  }

  async function joinLive(live) {
    if (live.session.set === setId) return enter(await together({ op: "join", screen, id: live.id }));
    if (setForTogether(live.session.set)) openForTogether(live.session.set, live.id);
    else show(NOT_HERE);
  }

  async function leave(message = null) {
    const leaving = id;
    end(message);
    if (leaving !== null) await together({ op: "leave", screen, id: leaving });
  }

  /** The viewer's own move. `settle` holds a scrub's edit until it stops moving. */
  function viewer(change, { settle: settling = false } = {}) {
    if (id === null || !current) return;
    const moved = acted(current, change, screen, serverNow());
    const session = id;
    current = moved;
    const send = () => void together({ op: "act", screen, id: session, change: { ...change, pos: expectedPosition(moved, serverNow()) } });
    clearTimeout(settle);
    if (settling) settle = setTimeout(send, SEEK_SETTLE_MS);
    else send();
  }

  onTogether((view, heard) => {
    // A read carries no edit: what it found for this session is the news. One that can't
    // reach Telegram leaves this screen in the session, playing on alone until it can.
    if (!heard && id !== null && view.available) heard = { id, session: view.live?.id === id ? view.live.session : null };
    if (!heard || heard.id !== id) return refresh();
    // Ended or stale, which `together-now.js` hears as `null`: this screen plays on alone.
    if (heard.session === null) return end("Watch together ended");
    const before = current;
    current = follow(current, heard.session, screen);
    if (current === before) return;
    const change = before && current.by !== screen ? changeOf(before, current) : null;
    if (change) show(say(change));
    refresh();
    apply();
  });

  // The end of a film, or of the last episode: over for everyone. With a title next, opening it moves the session instead.
  video.addEventListener("ended", () => {
    if (id === null || hasNext) return;
    const ending = id;
    end(null);
    void together({ op: "end", screen, id: ending });
  });

  button.addEventListener("click", () => {
    const { live } = togetherNow();
    void (id !== null ? leave() : live ? joinLive(live) : start());
  });

  return {
    /**
     * `openPlayer`'s title, as it opens: `next` says whether its end is the session's.
     * @param {{ setId: string }} set @param {{ next?: unknown }} [options]
     */
    open(set, { next = null } = {}) {
      setId = set.setId;
      hasNext = next !== null;
      const followed = expecting === setId;
      expecting = null;
      // A title the session moved to is brought to it once `openPlayer` has attached it; any other is the viewer's move.
      if (id !== null && followed) queueMicrotask(apply);
      else if (id !== null) viewer({ set: setId, pos: 0, playing: true });
      const joining = takeJoin();
      if (joining !== null && id === null) void together({ op: "join", screen, id: joining }).then(enter);
      refresh();
    },
    viewer,
    /** The player closing, or the page going: this screen leaves. */
    clear: () => void leave(),
  };
}
