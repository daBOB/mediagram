/**
 * The one Profiles page, Settings' Profiles tab: You — who is watching, the way
 * to switch, your subtitle language — then everyone in the household, then
 * managing them. A kid sees only You; with nobody chosen, which is how the
 * picker's Manage profiles opens it, there is no You. Seeing the list needs no
 * PIN; changing anything asks who you are and that grown-up's PIN, which the
 * body holds only until the page is left.
 */

import { el } from "../dom.js";
import { href } from "../address.js";
import { mountManage } from "../profile-manage.js";
import * as state from "../watch-state.js";
import { ownerOf } from "../profile-api.js";
import { kidsLimitOf } from "../age-rating.js";
import { dailyLimitWords } from "../daily-limit.js";

/** The page's name: its Settings tab, and its heading over the picker. */
export const PROFILES = "Profiles";

const SUBTITLE_LANGUAGES = [["off", "Off"], ["de", "German"], ["en", "English"]];

/** Who is watching and the way to switch, their subtitles, and for a local viewer the System link. */
function you(profile, switchProfile, systemVisible) {
  const set = el("div", "setting");
  set.append(el("h2", "setting-title", "Who is watching"));
  set.append(el("p", "setting-value", `${profile.name}${profile.kids ? ` · Kids · FSK ${kidsLimitOf(profile)}` : ""}`));
  const change = el("button", "pill pill-line", "Switch profile");
  change.type = "button";
  change.addEventListener("click", switchProfile);
  set.append(change);
  const parts = [set, subtitlePreference()];
  if (systemVisible) {
    const system = el("a", "dept-all", "System and playback status →");
    system.href = href({ page: "system" });
    parts.push(system);
  }
  return parts;
}

/**
 * The profile-wide subtitle language: the fallback `subtitle-choice.js` uses
 * once a show has no remembered choice of its own. Stored under the fixed
 * scope `"profile"`, unlike every other preference here, which is per show.
 */
function subtitlePreference() {
  const set = el("div", "setting");
  set.append(el("h2", "setting-title", "Subtitles"));
  const select = el("select");
  for (const [value, label] of SUBTITLE_LANGUAGES) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.append(option);
  }
  select.value = state.preferenceOf("profile", "subtitle") ?? "off";
  select.addEventListener("change", () => state.setPreference("profile", "subtitle", select.value));
  set.append(select, el("p", "setting-value", "Forced subtitles still appear when a film switches language."));
  return set;
}

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
 * @param {{ profile: {name: string, kids?: boolean, kidsAge?: number|null}|null,
 *   switchProfile?: () => void, systemVisible?: boolean }} chosen who is watching, if anyone
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the body and drops the PIN
 */
export function profilesPanel(promptHost, { profile, switchProfile, systemVisible = false }) {
  const box = el("div", "settings-panel");
  if (profile) box.append(...you(profile, switchProfile, systemVisible));
  // A kid manages nothing, so is shown nobody else either.
  if (profile?.kids) return { box, stop: () => {} };
  const list = el("div", "setting");
  drawEveryone(list);
  // Scoped faces (appearance.css) make the shared body read as settings sections.
  const manage = el("div", "setting manage-inline");
  box.append(list, manage);
  const stop = mountManage(manage, {
    promptHost,
    onChange: () => drawEveryone(list),
  });
  return { box, stop };
}
