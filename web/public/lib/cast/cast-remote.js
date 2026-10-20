/**
 * The remote for a title playing on the TV: it joins the cast session as a
 * remote member, follows it, and sends the viewer's moves. A remote leaving
 * never ends the cast; only Stop does.
 */

import { mountPlayerMenus } from "../playback/player-menus.js";
import { SKIP_SECONDS, SPEEDS, speedLabel } from "../playback/transport.js";
import { acted } from "../together/moves.js";
import { changeOf, expectedPosition, follow } from "../together/follow.js";
import { endingOf } from "../together/cast.js";
import { CAST_WORDS, sayOnRemote } from "../together/cast-words.js";
import { forgetTogether, nextForTogether, onTogether, screen, serverNow, together } from "../together-now.js";
import { drawCard, drawFixed, drawTitle, part } from "./cast-remote-card.js";

const NOTICE_MS = 2000;
const CLOCK_MS = 500;
const MENU_IDS = { dialog: "cast-remote", dock: "cast-remote-dock", menu: "cast-remote-menu" };

let id = null;
let current = null;
let wired = null;
let menus = null;
let clock = null;
let noticeTimer = null;
let unlisten = null;

export const remoteOpen = () => id !== null;

const watchers = new Set();
/** `listener` runs whenever the remote opens or closes; returns the unsubscribe. */
export function onRemote(listener) {
  watchers.add(listener);
  return () => watchers.delete(listener);
}
const changed = () => watchers.forEach((listener) => listener());

const draw = () => current && drawCard(current, serverNow());

/** Says `line` for two seconds in the status note, then `after`. */
function flash(line, after) {
  clearTimeout(noticeTimer);
  part("note").textContent = line;
  noticeTimer = setTimeout(() => { part("note").textContent = ""; after?.(); }, NOTICE_MS);
}

function release() {
  id = null;
  current = null;
  clearInterval(clock);
  menus?.close();
  wired?.abort();
  unlisten?.();
  clock = wired = unlisten = menus = null;
  changed();
}

function shut() {
  release();
  part("note").textContent = "";
  part("root").close();
}

/** The cast is over: say how for two seconds, and close. */
function finish(line) {
  release();
  flash(line, () => part("root").close());
}

/** The server no longer has the session: over here, and out of the tab's view so the bar does not show it. */
function gone() {
  const dead = id;
  finish(CAST_WORDS.stopped);
  forgetTogether(dead);
}

/** The session as it now stands, redrawn whole when it moved to another title. */
function take(next) {
  const moved = current === null || next.set !== current.set;
  current = next;
  if (moved) drawTitle(next);
  draw();
}

/** One move of the viewer's, shown at once and sent. */
function send(change) {
  if (id === null) return;
  const full = { ...change, pos: change.pos ?? expectedPosition(current, serverNow()) };
  const sent = id;
  take(acted(current, full, screen, serverNow()));
  void together({ op: "act", screen, id: sent, change: full }).then((answer) => {
    if (sent !== id) return;
    if (answer.status === 404) gone();
    else if (answer.body?.reason) flash(answer.body.reason);
  });
}

function choose(choice) {
  if (id !== null) void together({ op: "tracks", screen, id, ...choice });
}

function heard(view, news) {
  if (id === null) return;
  // A read is news too, as in the player: what it found for this session, or its absence.
  if (!news && view.available) news = { id, session: view.live?.id === id ? view.live.session : null };
  if (!news || news.id !== id) return;
  // Ended is `null` to the rest of the page; `ended` is the session as it ended, to tell finished from stopped.
  if (news.session === null) return finish(CAST_WORDS[(news.ended && endingOf(news.ended, screen)) || "stopped"]);
  const before = current;
  const next = news.lost ? news.session : follow(current, news.session, screen);
  if (next === before) return;
  const noticed = next.by !== screen ? changeOf(before, next) : null;
  const line = noticed && sayOnRemote(noticed, next);
  if (line) flash(line);
  take(next);
}

function wire(signal) {
  const on = (name, event, run) => part(name).addEventListener(event, run, { signal });
  const jump = (by) => () => send({ pos: Math.max(0, expectedPosition(current, serverNow()) + by) });
  menus = mountPlayerMenus({ ids: MENU_IDS, signal });
  const tracks = (kind) => () => current.cast.tracks[kind].map((t) => ({ value: t.id, label: t.label, current: current.cast[kind] === t.id }));
  menus.list(part("cc"), {
    items: () => [{ value: "", label: "Off", current: current.cast.text === "" }, ...tracks("text")()],
    pick: (text) => choose({ text }),
  });
  menus.list(part("audio"), { items: tracks("audio"), pick: (audio) => choose({ audio }) });
  menus.list(part("speed"), {
    items: () => SPEEDS.map((rate) => ({ value: String(rate), label: speedLabel(rate), current: rate === current.rate })),
    pick: (rate) => send({ rate: Number(rate) }),
  });
  on("play", "click", () => send({ playing: !current.playing }));
  on("back", "click", jump(-SKIP_SECONDS));
  on("forward", "click", jump(SKIP_SECONDS));
  on("seek", "change", () => send({ pos: Number(part("seek").value) }));
  on("next", "click", () => {
    const set = nextForTogether(current.set);
    if (set !== null) send({ set, pos: 0, playing: true });
  });
  on("stop", "click", () => {
    const ending = id;
    shut();
    void together({ op: "end", screen, id: ending });
  });
  on("close", "click", closeRemote);
  // Esc closes an open list first, which the menus' own key handler has already taken.
  on("root", "cancel", (event) => {
    event.preventDefault();
    if (menus.isOpen()) menus.close();
    else closeRemote();
  });
  // Any other way the dialog closes (a second back press cannot be cancelled) leaves the remote just as closed.
  on("root", "close", closeRemote);
}

/**
 * Joins `live` as a remote, shows the remote and follows the session.
 * @param {import("../together-now.js").Live} live
 */
export function openRemote(live) {
  if (id !== null) return;
  clearTimeout(noticeTimer);
  part("note").textContent = "";
  id = live.id;
  wired = new AbortController();
  wire(wired.signal);
  unlisten = onTogether(heard);
  drawFixed();
  take(live.session);
  clock = setInterval(draw, CLOCK_MS);
  part("root").showModal();
  changed();
  const joining = id;
  void together({ op: "join", screen, id: joining, remote: true }).then((answer) => {
    if (joining !== id) return;
    if (answer.status === 404) gone();
    else if (!answer.ok) flash(answer.body?.reason ?? CAST_WORDS.stopped);
    else if (answer.body?.session) take(answer.body.session);
  });
}

/** Leaves the session, which goes on, and closes the remote. */
export function closeRemote() {
  if (id === null) return;
  const leaving = id;
  shut();
  void together({ op: "leave", screen, id: leaving });
}
