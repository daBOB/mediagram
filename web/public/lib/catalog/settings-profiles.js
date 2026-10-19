/**
 * Settings' Profiles tab: everyone in the household at a glance, then the same
 * management as the picker's, inline. Seeing the list needs no PIN; changing
 * anything asks who you are and that grown-up's PIN, which the body holds only
 * until the tab or the page is left.
 */

import { el } from "../dom.js";
import { mountManage } from "../profile-manage.js";
import * as state from "../watch-state.js";
import { ownerOf } from "../profile-api.js";
import { kidsLimitOf } from "../age-rating.js";
import { dailyLimitWords } from "../daily-limit.js";

/** One quiet line for a profile: its role, and for a grown-up whether it has a PIN. */
function roleLine(entry, profiles) {
  if (entry.kids) {
    const owner = profiles.find((other) => other.id === ownerOf(profiles, entry));
    const parts = ["Kid", owner?.name, `FSK ${kidsLimitOf(entry)}`];
    if (entry.dailyLimit != null) parts.push(dailyLimitWords(entry.dailyLimit));
    return parts.filter(Boolean).join(" · ");
  }
  return `${entry.admin ? "Admin" : "Grown-up"} · ${entry.hasPin ? "PIN set" : "No PIN"}`;
}

/** Draws the household into `set`, read afresh from the watch state. */
function drawEveryone(set) {
  const profiles = state.profiles();
  set.textContent = "";
  set.append(el("h2", "setting-title", "Everyone here"));
  for (const entry of [...profiles.filter((p) => !p.kids), ...profiles.filter((p) => p.kids)]) {
    const line = el("div", "manage-row everyone-row");
    line.append(el("span", null, entry.name), el("span", "setting-value", roleLine(entry, profiles)));
    set.append(line);
  }
}

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the body and drops the PIN
 */
export function profilesPanel(promptHost) {
  const box = el("div", "settings-panel");
  const list = el("div", "setting");
  drawEveryone(list);
  const manage = el("div", "setting");
  box.append(list, manage);
  const stop = mountManage(manage, {
    promptHost,
    onChange: () => drawEveryone(list),
  });
  return { box, stop };
}
