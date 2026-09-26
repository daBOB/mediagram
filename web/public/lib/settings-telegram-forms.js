/**
 * The three secondary actions off the Telegram section: choosing a library,
 * editing the application id/hash, and confirming a sign-out. Split out of
 * `settings-telegram.js` to keep it under this project's line ceiling.
 */

import { el } from "./dom.js";
import { chooseLibrary, listLibraries, setAppCredentials, signOutTelegram } from "./settings-api.js";

function errorLine(text) {
  return el("p", "error settings-error", text);
}

export function renderLibraryPicker(root, refresh) {
  const panel = el("div", "settings-panel");
  panel.append(el("p", null, "Reading the account's channels…"));
  root.append(panel);

  void listLibraries().then((result) => {
    panel.textContent = "";
    if (!result.ok) {
      panel.append(errorLine(result.error));
      return;
    }
    if (result.libraries.length === 0) {
      panel.append(el("p", null, "This account is not in any channel."));
      return;
    }
    const list = el("ul", "settings-library-list");
    for (const library of result.libraries) {
      const item = el("li", null);
      const button = el("button", "settings-button", library.title + (library.current ? " (current)" : ""));
      button.type = "button";
      button.disabled = library.current;
      button.onclick = async () => {
        panel.textContent = "";
        panel.append(el("p", null, "Reading the channel's index…"));
        const chosen = await chooseLibrary(library.handle);
        if (!chosen.ok) {
          panel.textContent = "";
          panel.append(errorLine(chosen.error));
          return;
        }
        await refresh();
      };
      item.append(button);
      list.append(item);
    }
    panel.append(list);
  });
}

export function renderAppForm(root, app, refresh) {
  const form = el("form", "settings-form");
  const idField = el("input");
  idField.type = "text";
  idField.value = String(app.apiId);
  idField.autocomplete = "off";

  const hashField = el("input");
  hashField.type = "text";
  hashField.placeholder = app.apiHashSet ? "set — leave blank to keep it" : "application hash";
  hashField.autocomplete = "off";

  const submit = el("button", "settings-button", "Save");
  submit.type = "submit";
  const message = el("p", "settings-message");

  form.append(el("label", null, "Application id"), idField, el("label", null, "Application hash"), hashField, submit, message);
  form.onsubmit = async (event) => {
    event.preventDefault();
    message.textContent = "Reconnecting…";
    const result = await setAppCredentials(Number(idField.value), hashField.value.trim());
    if (!result.ok) {
      message.textContent = "";
      message.append(errorLine(result.error));
      return;
    }
    await refresh();
  };
  root.append(form);
}

export function renderSignOutConfirm(root, refresh) {
  const dialog = el("dialog", "settings-dialog");
  dialog.append(
    el("p", null, "Sign out of this account? Uncached titles stop playing until you sign in again."),
  );
  const confirm = el("button", "settings-button danger", "Sign out");
  confirm.type = "button";
  const cancel = el("button", "settings-button", "Cancel");
  cancel.type = "button";
  cancel.onclick = () => dialog.close();
  confirm.onclick = async () => {
    await signOutTelegram();
    dialog.close();
    await refresh();
  };
  const actions = el("div", "settings-actions");
  actions.append(cancel, confirm);
  dialog.append(actions);
  root.append(dialog);
  dialog.showModal();
}
