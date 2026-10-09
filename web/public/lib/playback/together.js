/**
 * Watch together, in the player (`docs/mlib-spec.md` §7b). It follows the
 * session this screen is in and sends the viewer's own moves. What to do is
 * decided in `../together/follow.js`; this only reads the player and applies
 * the steps. A change made here because the session moved never goes back
 * out: only `viewer()` sends, and only the player's viewer call paths call it.
 */

import { changeOf, expectedPosition, follow, isLive } from "../together/follow.js";
import { acted } from "../together/moves.js";
import {
  forgetTogether, heardTogether, onTogether, openForTogether, refreshTogether, screen, serverNow, setForTogether, setInTogether,
  takeJoin, together, togetherNow,
} from "../together-now.js";
import { COULD_NOT_START, ENDED, LEFT_NOT_HERE, NOT_HERE, say } from "./together-words.js";
import { togetherView } from "./together-view.js";
import { playerSteps } from "./together-steps.js";

const DRIFT_CHECK_MS = 2000;
/** A scrub sends one edit, this long after the last movement. */
const SEEK_SETTLE_MS = 300;

/** The player's own parts are in `together-steps.js`; the button, label and notice in `together-view.js`. */
export function mountTogether({ video, button, label, note, filmTime, seekTo, setRate, converting }) {
  const player = playerSteps({ video, filmTime, seekTo, setRate, converting });
  const { show, draw } = togetherView({ button, label, note });
  let setId = null;
  let hasNext = false;
  /** The session this screen is in, and that session as this screen follows it. */
  let id = null;
  let current = null;
  /** A title opened because the session moved to it: opening it is not the viewer's move. */
  let expecting = null;
  /** Counts leaving: a start or join answered after this screen left, or its player closed, is too late to enter. */
  let generation = 0;
  let drift = null;
  let settle = null;

  const refresh = () => draw(id, current);

  function apply(drifting = false) {
    const opening = current && player.bring(current, setId, drifting);
    if (opening) openTitle(opening);
  }

  /** Every two seconds: a session gone stale under this screen, as one nobody moved on from does, is over. */
  function check() {
    if (current && !isLive(current, setForTogether(current.set)?.duration ?? null, serverNow())) return over();
    apply(true);
  }

  /** Over, found here rather than heard: out of the tab's view too, and read again, so the server deletes a stale one. */
  function over() {
    const ended = id;
    end(ENDED);
    forgetTogether(ended);
    void refreshTogether();
  }

  /** The session's next title; one this profile may not see (a kids profile's limit) is where this screen leaves. */
  function openTitle(set) {
    if (!setForTogether(set)) return void leave(LEFT_NOT_HERE);
    expecting = set;
    openForTogether(set);
  }

  function end(message) {
    if (message) show(message);
    generation++;
    id = null;
    current = null;
    expecting = null;
    clearInterval(drift);
    clearTimeout(settle);
    drift = null;
    setInTogether(false);
    refresh();
  }

  /** A start or a join, asked of the server; an answer that comes after this screen left takes it out again. */
  async function ask(body) {
    const asked = generation;
    const answer = await together(body);
    if (asked === generation) return answer;
    if (answer.ok) void together({ op: "leave", screen, id: answer.body.id });
    return null;
  }

  /** A start's or a join's answer, heard as the stream would say it: a stale session is over at once. */
  function enter(answer) {
    if (!answer) return;
    if (!answer.ok) return show(answer.status === 404 ? ENDED : answer.body?.reason ?? COULD_NOT_START);
    id = answer.body.id;
    setInTogether(true);
    drift ??= setInterval(check, DRIFT_CHECK_MS);
    heardTogether(answer.body);
  }

  async function start() {
    const answer = await ask({ op: "start", screen, ...player.own(setId) });
    // Another screen started one first: this is a join of theirs.
    if (answer?.status === 409) return joinLive(answer.body);
    enter(answer);
  }

  async function joinLive(live) {
    if (live.session.set === setId) return enter(await ask({ op: "join", screen, id: live.id }));
    if (setForTogether(live.session.set)) openForTogether(live.session.set, live.id);
    else show(NOT_HERE);
  }

  async function leave(message = null) {
    const leaving = id;
    end(message);
    if (leaving !== null) await together({ op: "leave", screen, id: leaving });
  }

  /**
   * The viewer's own move. `settle` holds a scrub's edit until it stops moving.
   * A session the server no longer has is over; one it can't reach Telegram
   * for plays on alone, and says why.
   */
  function viewer(change, { settle: settling = false } = {}) {
    if (id === null || !current) return;
    const moved = acted(current, change, screen, serverNow());
    const session = id;
    current = moved;
    const send = async () => {
      const answer = await together({ op: "act", screen, id: session, change: { ...change, pos: expectedPosition(moved, serverNow()) } });
      if (session !== id) return;
      if (answer.status === 404) over();
      else if (answer.body?.reason) show(answer.body.reason);
    };
    clearTimeout(settle);
    if (settling) settle = setTimeout(send, SEEK_SETTLE_MS);
    else void send();
  }

  onTogether((view, heard) => {
    // A read carries no edit: what it found for this session is the news, and another one newest
    // means this screen leaves it. One that can't reach Telegram leaves it playing on alone until it can.
    if (!heard && id !== null && view.available) {
      if (view.live && view.live.id !== id) return void leave(ENDED);
      heard = { id, session: view.live?.session ?? null };
    }
    if (!heard || heard.id !== id) return refresh();
    // Ended or stale, which `together-now.js` hears as `null`: this screen plays on alone.
    if (heard.session === null) return end(ENDED);
    const before = current;
    // A move given up on comes back as what the channel holds, which the screen that made it follows too.
    current = heard.lost ? heard.session : follow(current, heard.session, screen);
    if (current === before) return;
    const change = before && current.by !== screen ? changeOf(before, current) : null;
    if (change) show(say(change, current));
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
     * Returns where to start it when that is the session's place, for a title opened
     * in a session or to join one, so a conversion starts there rather than twice.
     * @param {{ setId: string }} set @param {{ next?: unknown }} [options]
     * @returns {number | null}
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
      if (joining !== null && id === null) void ask({ op: "join", screen, id: joining }).then(enter);
      refresh();
      const { live } = togetherNow();
      const session = id !== null ? current : live?.id === joining ? live.session : null;
      return session?.set === setId ? expectedPosition(session, serverNow()) : null;
    },
    viewer,
    /** The player closing, or the page going: this screen leaves. */
    clear: () => void leave(),
  };
}
