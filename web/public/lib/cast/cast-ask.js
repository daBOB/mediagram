/**
 * Pressing Play on TV: offers the title to the TV, says "Asking the TV…"
 * while it answers, and then closes, asks whether to replace what the TV
 * is playing, or says why nothing was cast. The server holds the offer
 * while it waits for the TV, so one request is the whole wait; the page
 * gives up a little after the server would have.
 */

import { el } from "../dom.js";
import { screen, together, UNREACHABLE } from "../together-now.js";
import { CAST_ANSWER_MS } from "../together/cast.js";
import { CAST_WORDS, busyQuestion, refusedLine } from "../together/cast-words.js";

/** What the server's own deadline leaves for the round trip. */
const MARGIN_MS = 3000;
const OK = "OK";

let pending = null;
/** Resolves the buttons now showing as if the last were pressed; `null` while none are. */
let dismiss = null;

const dialog = () => document.getElementById("cast-ask");

/** Shows `line` with `buttons` and resolves to the label of the one pressed. */
function show(line, buttons) {
  document.getElementById("cast-ask-status").textContent = line;
  const row = document.getElementById("cast-ask-actions");
  row.replaceChildren();
  dismiss = null;
  if (!dialog().open) dialog().showModal();
  return new Promise((resolve) => {
    const choose = (label) => { dismiss = null; resolve(label); };
    for (const label of buttons) {
      const button = el("button", "ghost", label);
      button.type = "button";
      button.setAttribute("aria-label", label);
      button.addEventListener("click", () => choose(label));
      row.append(button);
    }
    if (buttons.length) dismiss = () => choose(buttons.at(-1));
  });
}

/** Escape is a Cancel or an OK once there is a button to press, and nothing while the TV is being asked. */
function onCancel(event) {
  event.preventDefault();
  dismiss?.();
}

/** The reply to `move`, or `null` when none came in time. */
function post(move) {
  let timer;
  const late = new Promise((resolve) => { timer = setTimeout(() => resolve(null), CAST_ANSWER_MS + MARGIN_MS); });
  return Promise.race([together(move), late]).finally(() => clearTimeout(timer));
}

/** Says `line` with one OK. Resolves `null`: nothing was cast. */
async function tell(line) {
  await show(line, [OK]);
  return null;
}

async function ask(own, profile) {
  const body = { screen, profile, ...own };
  show(CAST_WORDS.asking, []);
  let move = { op: "offer", ...body };
  for (;;) {
    const result = await post(move);
    if (!result) return tell(CAST_WORDS.noAnswer);
    const { status, body: reply } = result;
    // Offline (0), Telegram down (503) or a refused request: the server's reason, else the unreachable line.
    if (status !== 200) return tell(reply?.reason ?? UNREACHABLE);
    if (reply?.answer === "none") return tell(CAST_WORDS.noAnswer);
    if (reply?.answer === "refused") return tell(refusedLine(reply.note));
    // `live.id` is the offer's own message, which replace and cancel act on.
    if (reply?.answer === "on" && reply.live) return reply.live;
    const id = reply?.answer === "busy" ? reply.live?.id : undefined;
    if (id === undefined) return tell(UNREACHABLE);
    const chosen = await show(busyQuestion(reply.note), [CAST_WORDS.cancel, CAST_WORDS.replace]);
    if (chosen !== CAST_WORDS.replace) {
      await together({ op: "cancel", id });
      return null;
    }
    show(CAST_WORDS.asking, []);
    move = { op: "replace", ...body, id };
  }
}

/**
 * Asks the TV to play `own.set`; a second call while one is open returns the
 * first's answer and sends nothing.
 * @param {{ set: string, playing: boolean, pos: number, rate: number }} own where this tab is
 * @param {string} profile
 * @returns {Promise<import("../together-now.js").Live | null>}
 */
export function askTv(own, profile) {
  if (pending) return pending;
  const box = dialog();
  box.setAttribute("aria-label", CAST_WORDS.playOnTv);
  box.addEventListener("cancel", onCancel);
  pending = ask(own, profile).finally(() => {
    box.removeEventListener("cancel", onCancel);
    dismiss = null;
    pending = null;
    box.close();
  });
  return pending;
}
