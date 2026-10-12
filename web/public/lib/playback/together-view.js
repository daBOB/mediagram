/**
 * What watch together shows in the player: the tool button, the label in the
 * top bar and the two-second notice. `together.js` decides; this draws.
 */

import { setForTogether, togetherNow } from "../together-now.js";
import { NOT_HERE } from "./together-words.js";

const NOTICE_MS = 2000;

export function togetherView({ button, label, note }) {
  let hide = null;
  return {
    /** Text, not `hidden`: a status region has to be on the page already for its change to be read out. */
    show(text) {
      note.textContent = text;
      clearTimeout(hide);
      hide = setTimeout(() => { note.textContent = ""; }, NOTICE_MS);
    },

    /** `id` is the session this screen is in and `current` that session as it follows it; both `null` out of one. */
    draw(id, current) {
      const { available, reason, live } = togetherNow();
      const elsewhere = id === null ? live : null;
      const here = elsewhere === null || setForTogether(elsewhere.session.set) !== null;
      button.textContent = id !== null ? "Leave watch together" : elsewhere ? "Join" : "Watch together";
      button.disabled = id === null && (!available || !here);
      button.title = !button.disabled ? "" : available ? NOT_HERE : reason ?? "";
      label.hidden = current === null;
      if (current) label.textContent = `Together · ${current.members.map((m) => m.label).join(", ")}`;
    },
  };
}
