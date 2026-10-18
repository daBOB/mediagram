/**
 * Pressing Play on TV: offers the title to the TV, says "Asking the TV…"
 * while it answers, and then closes, asks whether to replace what the TV
 * is playing, or says why nothing was cast. The server holds the offer
 * while it waits for the TV, so one request is the whole wait.
 */

import { el } from "../dom.js";
import { screen, together, UNREACHABLE } from "../together-now.js";
import { CAST_WORDS, busyQuestion, refusedLine } from "../together/cast-words.js";

/** Shows `line` with `buttons` and resolves to the label of the one pressed. */
function show(line, buttons) {
  const dialog = document.getElementById("cast-ask");
  document.getElementById("cast-ask-status").textContent = line;
  const row = document.getElementById("cast-ask-actions");
  row.replaceChildren();
  if (!dialog.open) dialog.showModal();
  return new Promise((resolve) => {
    for (const label of buttons) {
      const button = el("button", "ghost", label);
      button.type = "button";
      button.setAttribute("aria-label", label);
      button.addEventListener("click", () => resolve(label));
      row.append(button);
    }
    // Escape is a Cancel, or an OK: either way the viewer is done with it.
    dialog.addEventListener("cancel", (event) => { event.preventDefault(); resolve(buttons.at(-1) ?? null); }, { once: true });
  });
}

const OK = "OK";
const close = () => {
  document.getElementById("cast-ask").close();
  document.getElementById("cast-ask-actions").replaceChildren();
};

/** Says `line` with one OK, then closes. Resolves `null`: nothing was cast. */
async function tell(line) {
  await show(line, [OK]);
  close();
  return null;
}

/**
 * @param {{ set: string, playing: boolean, pos: number, rate: number }} own where this tab is
 * @param {string} profile
 * @returns {Promise<import("../together-now.js").Live | null>}
 */
export async function askTv(own, profile) {
  const body = { screen, profile, ...own };
  show(CAST_WORDS.asking, []);
  let move = { op: "offer", ...body };
  for (;;) {
    const { status, body: reply } = await together(move);
    // Offline (0), Telegram down (503) or a refused request: the server's reason, else the unreachable line.
    if (status !== 200) return tell(reply?.reason ?? UNREACHABLE);
    if (reply.answer === "on") { close(); return reply.live; }
    if (reply.answer === "refused") return tell(refusedLine(reply.note));
    if (reply.answer === "none") return tell(CAST_WORDS.noAnswer);
    // Busy: the TV's session is in `live`, and `note` names its title.
    const id = reply.live.id;
    const chosen = await show(busyQuestion(reply.note), [CAST_WORDS.cancel, CAST_WORDS.replace]);
    if (chosen !== CAST_WORDS.replace) {
      await together({ op: "cancel", id });
      close();
      return null;
    }
    show(CAST_WORDS.asking, []);
    move = { op: "replace", ...body, id };
  }
}
