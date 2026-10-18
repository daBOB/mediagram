/**
 * The remote for a title playing on the TV: it joins the cast session as a
 * remote member, follows it, and sends the viewer's moves. A remote leaving
 * never ends the cast; only Stop does.
 */

import { acted } from "../together/moves.js";
import { changeOf, expectedPosition, follow } from "../together/follow.js";
import { endingOf } from "../together/cast.js";
import { CAST_WORDS, sayOnRemote } from "../together/cast-words.js";
import { SPEEDS, speedLabel } from "../playback/transport.js";
import { nextForTogether, onTogether, screen, serverNow, together } from "../together-now.js";
import { drawCard, drawMenu, drawTitle, part } from "./cast-remote-card.js";

const NOTICE_MS = 2000;
const CLOCK_MS = 500;
const JUMP_SECONDS = 10;

let id = null;
let current = null;
let wired = null;
let clock = null;
let noticeTimer = null;
let unlisten = null;

export const remoteOpen = () => id !== null;

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
  wired?.abort();
  unlisten?.();
  clock = wired = unlisten = null;
}

function shut() {
  release();
  drawMenu([]);
  part("note").textContent = "";
  part("root").close();
}

/** The cast is over: say how for two seconds, and close. */
function finish(line) {
  release();
  drawMenu([]);
  flash(line, () => part("root").close());
}

/** One move of the viewer's, shown at once and sent; a session the server no longer has is over. */
function send(change) {
  if (id === null) return;
  const full = { ...change, pos: change.pos ?? expectedPosition(current, serverNow()) };
  const sent = id;
  current = acted(current, full, screen, serverNow());
  draw();
  void together({ op: "act", screen, id: sent, change: full }).then((answer) => {
    if (sent !== id) return;
    if (answer.status === 404) finish(CAST_WORDS.stopped);
    else if (answer.body?.reason) flash(answer.body.reason);
  });
}

function choose(choice) {
  if (id !== null) void together({ op: "tracks", screen, id, ...choice });
}

function showTracks() {
  const { tracks, text, audio } = current.cast;
  drawMenu([
    { label: "Off", current: text === "", pick: () => choose({ text: "" }) },
    ...tracks.text.map((t) => ({ label: t.label, current: text === t.id, pick: () => choose({ text: t.id }) })),
    ...tracks.audio.map((t) => ({ label: `Audio · ${t.label}`, current: audio === t.id, pick: () => choose({ audio: t.id }) })),
  ]);
}

const showSpeeds = () => drawMenu(SPEEDS.map((rate) => ({
  label: speedLabel(rate), current: rate === current.rate, pick: () => send({ rate }),
})));

const toggle = (show) => () => (part("menu").hidden ? show() : drawMenu([]));

function heard(_view, news) {
  if (id === null || !news || news.id !== id) return;
  // Ended is `null` to the rest of the page; `ended` is the session as it ended, to tell finished from stopped.
  if (news.session === null) return finish(CAST_WORDS[(news.ended && endingOf(news.ended, screen)) || "stopped"]);
  const before = current;
  current = news.lost ? news.session : follow(current, news.session, screen);
  const ending = endingOf(current, screen);
  if (ending) return finish(CAST_WORDS[ending]);
  if (current === before) return;
  const noticed = current.by !== screen ? changeOf(before, current) : null;
  const line = noticed && sayOnRemote(noticed, current);
  if (line) flash(line);
  draw();
}

function wire(signal) {
  const on = (name, event, run) => part(name).addEventListener(event, run, { signal });
  const jump = (by) => () => send({ pos: Math.max(0, expectedPosition(current, serverNow()) + by) });
  on("play", "click", () => send({ playing: !current.playing }));
  on("back", "click", jump(-JUMP_SECONDS));
  on("forward", "click", jump(JUMP_SECONDS));
  on("seek", "change", () => send({ pos: Number(part("seek").value) }));
  on("speed", "click", toggle(showSpeeds));
  on("tracks", "click", toggle(showTracks));
  on("next", "click", () => {
    const set = nextForTogether(current.set);
    if (set !== null) send({ set, pos: 0, playing: true });
  });
  on("stop", "click", () => {
    const ending = id;
    shut();
    void together({ op: "end", screen, id: ending });
  });
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
  current = live.session;
  drawMenu([]);
  wired = new AbortController();
  wire(wired.signal);
  unlisten = onTogether(heard);
  drawTitle(current);
  draw();
  clock = setInterval(draw, CLOCK_MS);
  part("root").showModal();
  const joining = id;
  void together({ op: "join", screen, id: joining, remote: true }).then((answer) => {
    if (joining !== id) return;
    if (answer.status === 404) finish(CAST_WORDS.stopped);
    else if (!answer.ok) flash(answer.body?.reason ?? CAST_WORDS.stopped);
    else if (answer.body?.session) { current = answer.body.session; draw(); }
  });
}

/** Leaves the session, which goes on, and closes the remote. */
export function closeRemote() {
  if (id === null) return;
  const leaving = id;
  shut();
  void together({ op: "leave", screen, id: leaving });
}
