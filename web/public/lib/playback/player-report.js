/**
 * The Report sheet: what is wrong with the open title, sent with what the
 * player knows about how it was playing — where the viewer was, how the
 * picture reached the screen, and the last error, if there was one.
 *
 * One send per title: after it the button reads as done until another title
 * opens, so one sitting cannot send a stream of notifications. A send that
 * fails leaves the sheet open to try again; nothing is queued.
 */

import { el } from "../dom.js";
import { REPORT_REASONS, REPORT_WORDS } from "../report/report-text.js";
import * as state from "../watch-state.js";

const SEND_TIMEOUT_MS = 8000;
// The route refuses a whole report whose error is longer than this.
const ERROR_MAX = 300;
/** `playbackMeta`'s modes, in the words a report uses. */
const PLAYED = { direct: "direct", transcode: "converted", copy: "copied", "hevc-copy": "copied" };
const MEDIA_ERRORS = ["", "MEDIA_ERR_ABORTED", "MEDIA_ERR_NETWORK", "MEDIA_ERR_DECODE", "MEDIA_ERR_SRC_NOT_SUPPORTED"];

/** A `MediaError` as a report states it: its code's name, and the browser's message when it gave one. */
export function mediaError(error) {
  const name = MEDIA_ERRORS[error.code] || `MEDIA_ERR_${error.code}`;
  return error.message ? `${name}: ${error.message}` : name;
}

/**
 * @param {{dialog: HTMLElement, facts: () => {title: string, position: number, mode: string, held: boolean}}} options
 *   `facts` is asked as Send is pressed, so the report says where the viewer is then.
 */
export function mountPlayerReport({ dialog, facts }) {
  const button = document.getElementById("report");
  const panel = document.getElementById("report-panel");
  const note = document.getElementById("report-note");
  const said = document.getElementById("report-said");
  const send = document.getElementById("report-send");
  let set = null;
  let reason = null;
  let lastError = null;
  /** Which open a reply belongs to: `open` and `clear` move it on, so a late answer marks nothing. */
  let opened = 0;
  /** A send is in flight: one press, one message, however the viewer clicks meanwhile. */
  let sending = false;

  button.textContent = REPORT_WORDS.button;
  document.getElementById("report-heading").textContent = REPORT_WORDS.heading;
  document.getElementById("report-note-label").textContent = REPORT_WORDS.note;
  document.getElementById("report-cancel").textContent = REPORT_WORDS.cancel;
  send.textContent = REPORT_WORDS.send;
  const choices = REPORT_REASONS.map(({ id, words }) => {
    const input = Object.assign(el("input"), { type: "radio", name: "report-reason", value: id });
    input.addEventListener("change", () => {
      reason = id;
      send.disabled = sending;
    });
    const label = el("label", "report-reason");
    label.append(input, words);
    document.getElementById("report-reasons").append(label);
    return input;
  });

  /** Each showing starts with nothing chosen, so a reason is always one the viewer picked. */
  function show(visible) {
    panel.hidden = !visible;
    button.setAttribute("aria-expanded", String(visible));
    if (!visible) return;
    reason = null;
    for (const input of choices) input.checked = false;
    note.value = "";
    said.textContent = "";
    send.disabled = true;
    choices[0]?.focus();
  }

  function dismiss() {
    show(false);
    button.focus();
  }

  async function submit() {
    if (set === null || reason === null || sending) return;
    const ticket = opened;
    const { title, position, mode, held } = facts();
    sending = true;
    // Not reopened mid-send either: a fresh sheet would offer a Send of its own.
    button.disabled = true;
    send.disabled = true;
    said.textContent = "";
    let sent = false;
    try {
      const response = await fetch("/api/report", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          set: set.setId, reason, title, note: note.value, position,
          played: PLAYED[mode] ?? null, held, error: lastError, profileId: state.profileId(),
        }),
        signal: AbortSignal.timeout(SEND_TIMEOUT_MS),
      });
      sent = response.status === 204;
    } catch {
      // Unreachable or too slow reads the same as a refusal.
    }
    if (ticket !== opened) return;
    sending = false;
    if (sent) {
      show(false);
      button.textContent = REPORT_WORDS.sent;
      // The focused Send just went away; the dialog keeps the player's keys.
      dialog.focus();
      return;
    }
    button.disabled = false;
    said.textContent = REPORT_WORDS.failed;
    send.disabled = false;
    send.focus();
  }

  button.addEventListener("click", () => show(panel.hidden));
  document.getElementById("report-cancel").addEventListener("click", dismiss);
  send.addEventListener("click", () => void submit());
  dialog.addEventListener("keydown", (event) => {
    // Taken, so the dialog does not close on the same key: Esc leaves the sheet, not the film.
    if (event.key !== "Escape" || event.defaultPrevented || panel.hidden) return;
    event.preventDefault();
    dismiss();
  });

  return {
    /** A new title: nothing reported, nothing gone wrong yet. */
    open(next) {
      opened++;
      sending = false;
      set = next;
      lastError = null;
      button.textContent = REPORT_WORDS.button;
      button.disabled = false;
      show(false);
    },
    clear() {
      opened++;
      sending = false;
      set = null;
      lastError = null;
      show(false);
    },
    /** The last thing that went wrong with this title, for the next report. */
    failed(error) {
      lastError = String(error).slice(0, ERROR_MAX);
    },
  };
}
