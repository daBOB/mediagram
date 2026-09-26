/**
 * The Telegram section of Settings: read-only rows, the sign-in stepper, and
 * the entry points into the three secondary actions in `settings-telegram-forms.js`.
 *
 * `refresh` is the one thing every action calls when it succeeds: the whole
 * section re-fetches `/api/settings` and redraws, so the rows shown are
 * always the server's own answer rather than a client guess at what changed.
 */

import { el } from "./dom.js";
import { signInCode, signInPassword, signInPhone } from "./settings-api.js";
import { renderAppForm, renderLibraryPicker, renderSignOutConfirm } from "./settings-telegram-forms.js";

function row(label, value) {
  const line = el("div", "status-row");
  line.append(el("dt", null, label), el("dd", null, value));
  return line;
}

function renderSignedIn(root, view, refresh) {
  const { telegram } = view;
  const rows = el("dl", "status-list");
  rows.append(
    row("Account", telegram.account ? [telegram.account.name, telegram.account.username && `@${telegram.account.username}`].filter(Boolean).join(" ") || "—" : "—"),
    row("Library", telegram.library?.title ?? "none chosen"),
    row("Datacenter", telegram.dc !== null ? `DC ${telegram.dc}` : "—"),
    row("Session", telegram.connected === null ? "live, cannot say if connected" : telegram.connected ? "live, connected" : "live, not connected"),
  );
  root.append(rows);

  // One open at a time: choosing a different action replaces whatever the
  // last one left rather than stacking beneath it.
  const detail = el("div", "settings-detail");
  const openDetail = (render) => {
    detail.textContent = "";
    render(detail, refresh);
  };

  const actions = el("div", "settings-actions");
  const libraryButton = el("button", "settings-button", "Change library");
  libraryButton.type = "button";
  libraryButton.onclick = () => openDetail(renderLibraryPicker);

  const appButton = el("button", "settings-button", "Application id/hash");
  appButton.type = "button";
  appButton.onclick = () => openDetail((container, done) => renderAppForm(container, telegram.app, done));

  const signOutButton = el("button", "settings-button danger", "Sign out");
  signOutButton.type = "button";
  signOutButton.onclick = () => renderSignOutConfirm(root, refresh);

  actions.append(libraryButton, appButton, signOutButton);
  root.append(actions, detail);
}

function renderSignedOut(root, refresh) {
  const form = el("form", "settings-form");
  const phoneField = el("input");
  phoneField.type = "tel";
  phoneField.placeholder = "+15551234567";
  phoneField.autocomplete = "off";
  const submit = el("button", "settings-button", "Sign in");
  submit.type = "submit";
  const message = el("p", "settings-message");
  form.append(el("label", null, "Phone number"), phoneField, submit, message);

  form.onsubmit = async (event) => {
    event.preventDefault();
    message.textContent = "";
    const result = await signInPhone(phoneField.value.trim());
    if (!result.ok) {
      message.append(el("p", "error", result.error));
      return;
    }
    renderSignInStep(root, result, refresh);
  };
  root.append(form);
}

function renderSignInStep(root, step, refresh) {
  root.textContent = "";
  if (step.step !== "code" && step.step !== "password") {
    void refresh();
    return;
  }
  const form = el("form", "settings-form");
  const field = el("input");
  field.type = step.step === "code" ? "text" : "password";
  field.autocomplete = step.step === "code" ? "one-time-code" : "current-password";
  const submit = el("button", "settings-button", "Continue");
  submit.type = "submit";
  const message = el("p", "settings-message");
  form.append(
    el("p", null, step.step === "code"
      ? (step.viaApp ? "Telegram sent the code to the app on another device." : "Telegram sent the code by SMS.")
      : "This account has a password."),
    field, submit, message,
  );
  form.onsubmit = async (event) => {
    event.preventDefault();
    const answer = step.step === "code" ? await signInCode(field.value.trim()) : await signInPassword(field.value);
    field.value = "";
    if (!answer.ok) {
      message.textContent = "";
      message.append(el("p", "error", answer.error));
      return;
    }
    if (answer.step === "code" || answer.step === "password") return renderSignInStep(root, answer, refresh);
    await refresh();
  };
  root.append(form);
}

/** Renders the whole Telegram section into `root`, replacing what was there. */
export function renderTelegramSection(root, view, refresh) {
  const section = el("section", "status-block");
  section.append(el("h3", null, "Telegram"));
  if (view.telegram.signedIn) renderSignedIn(section, view, refresh);
  else renderSignedOut(section, refresh);
  root.append(section);
}
