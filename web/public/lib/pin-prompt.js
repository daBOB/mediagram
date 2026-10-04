/**
 * The one PIN dialog, for the picker and for Manage profiles.
 *
 * Four digits, in a masked field the browser is asked not to fill in or
 * remember. A PIN keeps a child out of a grown-up's profile and out of the
 * household's management; it is not a login and does not pretend to be one —
 * the kids filter runs in this browser, where developer tools reach it. This
 * checks only the shape, so a mistyped PIN is said at once; whether it is the
 * right one is the server's to say, which also makes the whole player wait
 * after five wrong ones.
 *
 * Its own PIN, not the Settings admin token: that one guards the library's
 * Telegram account, this one only who may manage profiles.
 */

import { el } from "./dom.js";

const FOUR_DIGITS = /^[0-9]{4}$/;

/** What is wrong with what was typed, or `null` when it can be sent. */
export function pinProblem(pin, again, confirm = false) {
  if (!FOUR_DIGITS.test(pin)) return "A PIN is four digits.";
  if (confirm && pin !== again) return "The two PINs are not the same.";
  return null;
}

const REFUSALS = {
  invalid: "That was not accepted. Check the name and the PIN.",
  "name-taken": "A profile with that name already exists.",
  "not-found": "That profile is not here any more.",
  "no-pin": "This profile has no PIN yet. Choose it again to set one.",
  "wrong-pin": "Wrong PIN.",
  // One wording for every refusal by the rule: a manage action outside the
  // viewer's role, and a first profile when a grown-up has arrived meanwhile.
  "not-allowed": "That is not allowed.",
};

/**
 * What to tell the viewer about an outcome from `profile-api.js` that did not go through.
 * @param {{ reason?: string|null, retryAfter?: number }} outcome
 */
export function refusalText({ reason, retryAfter }) {
  if (reason === "wait") return `Too many wrong PINs. Try again in ${retryAfter} s.`;
  return REFUSALS[reason] ?? "That did not go through. Please try again.";
}

function pinField(label) {
  const field = el("input");
  field.setAttribute("type", "password");
  field.setAttribute("maxlength", "4");
  field.setAttribute("inputmode", "numeric");
  field.setAttribute("autocomplete", "off");
  field.setAttribute("aria-label", label);
  return field;
}

/**
 * Asks for a PIN until `send` accepts one or the viewer gives up.
 * @param {HTMLElement} parent where the dialog is attached; it removes itself
 * @param {{ title: string, confirm?: boolean,
 *   send: (pin: string) => Promise<{ ok: boolean, reason?: string|null, retryAfter?: number }>,
 *   refused?: (outcome: { ok: boolean, reason?: string|null, retryAfter?: number }) => void }} options
 *   `confirm` asks twice, for a PIN being set — which is sent once: a refusal
 *   ends the dialog and goes to `refused`, the caller's to tell
 * @returns {Promise<string|null>} the PIN `send` accepted, or `null`
 */
export function askPin(parent, { title, confirm = false, send, refused = () => {} }) {
  return new Promise((resolve) => {
    const dialog = el("dialog", "settings-dialog pin-prompt");
    dialog.setAttribute("aria-label", title);
    const form = el("form");
    const pin = pinField(confirm ? "New PIN" : "PIN");
    const again = pinField("The new PIN again");
    const message = el("p", "pin-message");
    message.setAttribute("role", "alert");
    const cancel = el("button", "pill pill-line", "Cancel");
    cancel.type = "button";
    const ok = el("button", "pill pill-solid", "OK");
    ok.type = "submit";
    let accepted = null;

    cancel.addEventListener("click", () => dialog.close());
    // Escape closes a modal dialog as well; either way the answer is what was accepted.
    dialog.addEventListener("close", () => {
      dialog.remove();
      resolve(accepted);
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (ok.disabled) return;
      const problem = pinProblem(pin.value, again.value, confirm);
      if (problem) {
        message.textContent = problem;
        return;
      }
      ok.disabled = true;
      const outcome = await send(pin.value);
      ok.disabled = false;
      if (outcome.ok) {
        accepted = pin.value;
        dialog.close();
        return;
      }
      // A new PIN was typed twice and has the right shape, so no other would
      // fare better: what was refused is something else — a current PIN
      // sent beside it, a name — and asking again would only spend tries.
      if (confirm) {
        refused(outcome);
        dialog.close();
        return;
      }
      message.textContent = refusalText(outcome);
      pin.value = "";
      again.value = "";
      pin.focus();
    });

    const actions = el("div", "settings-actions");
    actions.append(cancel, ok);
    form.append(el("h2", null, title), pin, ...(confirm ? [again] : []), message, actions);
    dialog.append(form);
    parent.append(dialog);
    dialog.showModal();
    pin.focus();
  });
}

/**
 * A grown-up's PIN — or, for one from before PINs, a new one, asked twice —
 * handed to `send`; a new one refused goes to `refused`, as `askPin` says.
 */
export function askGrownUp(parent, entry, send, refused) {
  return askPin(parent, entry.hasPin
    ? { title: `${entry.name}’s PIN`, send }
    : { title: `Choose a PIN for ${entry.name}`, confirm: true, send, refused });
}
