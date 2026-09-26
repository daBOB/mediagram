/**
 * `#/settings`: the locked form, then the Telegram and Cache sections.
 *
 * Same idiom as the System page (`status-view.js`): plain rows in the page's
 * own faces, mounted and unmounted like any other route. Unlike System,
 * this one gates itself behind a token, so the first draw always asks
 * `/api/settings` and branches on `locked` before showing anything.
 */

import { el } from "./dom.js";
import { humanSize } from "./format.js";
import { renderTelegramSection } from "./settings-telegram.js";
import { formatSizeInput, lockSettings, parseSizeInput, readSettings, setCacheBudget, unlockSettings } from "./settings-api.js";

function renderLocked(root, onUnlocked) {
  const form = el("form", "settings-form settings-locked");
  const field = el("input");
  field.type = "password";
  field.placeholder = "admin token";
  field.autocomplete = "off";
  const submit = el("button", "settings-button", "Unlock");
  submit.type = "submit";
  const message = el("p", "settings-message");
  form.append(el("p", null, "Settings is locked."), field, submit, message);

  form.onsubmit = async (event) => {
    event.preventDefault();
    const result = await unlockSettings(field.value);
    field.value = "";
    if (!result.ok) {
      message.textContent = result.status === 429 ? "Too many attempts; wait a minute." : "That token does not match.";
      return;
    }
    onUnlocked();
  };
  root.append(form);
}

function cacheSection(root, view, refresh) {
  const section = el("section", "status-block");
  section.append(el("h3", null, "Cache"));
  if (!view.cache.enabled) {
    section.append(el("p", null, "Caching is off — every byte is fetched."));
    root.append(section);
    return;
  }

  const rows = el("dl", "status-list");
  const held = view.cache.heldBytes;
  rows.append((() => {
    const line = el("div", "status-row");
    line.append(el("dt", null, "Held"), el("dd", null, `${humanSize(held ?? 0)} of ${humanSize(view.cache.budget)}`));
    return line;
  })());
  section.append(rows);

  const form = el("form", "settings-form");
  const field = el("input");
  field.type = "text";
  field.value = formatSizeInput(view.cache.budget);
  field.autocomplete = "off";
  const submit = el("button", "settings-button", "Save");
  submit.type = "submit";
  const message = el("p", "settings-message");
  form.append(el("label", null, `Size (minimum ${humanSize(view.cache.min)})`), field, submit, message);

  form.onsubmit = async (event) => {
    event.preventDefault();
    const bytes = parseSizeInput(field.value);
    if (bytes === null) {
      message.textContent = "";
      message.append(el("p", "error", "Not a size — try 8G or 512M."));
      return;
    }
    message.textContent = "Freeing space…";
    const result = await setCacheBudget(bytes);
    message.textContent = "";
    if (!result.ok) {
      message.append(el("p", "error", result.error));
      return;
    }
    await refresh();
  };
  section.append(form);
  root.append(section);
}

/** Renders `#/settings` into `main`. Returns the function that tears it down. */
export function viewSettings(main) {
  let stopped = false;
  const panel = el("div", "status");
  // Outside the grid `panel` lays its sections in: a lone button as a grid
  // item would stretch to fill a whole column-sized cell.
  const footer = el("div", "settings-actions");
  main.append(panel, footer);

  async function draw() {
    const result = await readSettings();
    if (stopped) return;
    panel.textContent = "";
    footer.textContent = "";
    if (result.status === 404) {
      panel.append(el("p", "error", "Settings is only reachable from this household's own network."));
      return;
    }
    if (result.locked) {
      renderLocked(panel, () => void draw());
      return;
    }
    renderTelegramSection(panel, result, draw);
    cacheSection(panel, result, draw);

    const lock = el("button", "settings-button", "Lock");
    lock.type = "button";
    lock.onclick = async () => {
      await lockSettings();
      await draw();
    };
    footer.append(lock);
  }

  void draw();
  return () => { stopped = true; };
}
