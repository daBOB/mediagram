/**
 * The one Profiles page, Settings' Profiles tab: You — who is watching, the way
 * to switch, your subtitle language — then everyone in the household, then
 * managing them, then — while there is more than one — the household this
 * device shows, then the owner's Households. A kid sees only You; with nobody
 * chosen, which is how the picker's Manage profiles opens it, there is no You.
 * Seeing the list needs no PIN; changing anything asks who you are and that
 * grown-up's PIN, which the body holds only until the page is left.
 */

import { el } from "../dom.js";
import { href } from "../address.js";
import { mountManage, pill } from "../profile-manage.js";
import * as state from "../watch-state.js";
import { thisHousehold } from "../household.js";
import { joinChoices } from "../household-join.js";
import { HOUSEHOLD_WORDS } from "../household-words.js";
import { householdsPart } from "./settings-households.js";
import { ownerOf } from "../profile-api.js";
import { kidsLimitOf } from "../age-rating.js";
import { dailyLimitWords } from "../daily-limit.js";
import { hidesWords } from "../section-access.js";

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

/** One quiet line for a profile: its role, for a grown-up whether it has a PIN, and the sections it is not shown. */
function roleLine(entry, profiles) {
  if (entry.kids) {
    const owner = profiles.find((other) => other.id === ownerOf(profiles, entry));
    const parts = ["Kid", owner?.name, `FSK ${kidsLimitOf(entry)}`];
    if (entry.dailyLimit != null) parts.push(dailyLimitWords(entry.dailyLimit));
    return [...parts, hidesWords(entry)].filter(Boolean).join(" · ");
  }
  const role = `${entry.owner ? `${HOUSEHOLD_WORDS.owner} · ` : ""}${entry.admin ? "Admin" : "Grown-up"} · ${entry.hasPin ? "PIN set" : "No PIN"}`;
  return [role, hidesWords(entry)].filter(Boolean).join(" · ");
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
 * Draws into `set` which household's profiles this device shows, and a way to
 * another — only while there is more than one. Joining one takes the device
 * off the profile chosen here, so `onJoined` asks who is watching again.
 */
function drawThisDevice(set, promptHost, onJoined) {
  set.textContent = "";
  // Hidden, not just empty: an empty section would still take the panel's gap.
  set.hidden = state.households().length < 2;
  if (set.hidden) return;
  const here = thisHousehold();
  const change = pill(HOUSEHOLD_WORDS.switchHousehold, () => {
    change.hidden = true;
    set.append(joinChoices(promptHost, { except: here?.id, onJoined }));
  });
  set.append(el("h2", "setting-title", HOUSEHOLD_WORDS.thisDevice), el("p", "setting-value", here?.name ?? ""), change);
}

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @param {{ profile: {name: string, kids?: boolean, kidsAge?: number|null}|null,
 *   switchProfile?: () => void, systemVisible?: boolean }} chosen who is watching, if anyone;
 *   `switchProfile` asks again, after switching household too
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the bodies and drops their PINs
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
  const device = el("div", "setting");
  drawThisDevice(device, promptHost, switchProfile);
  /** Who Manage was drawn over, and who runs them: a household change that alters it starts Manage again. */
  const shown = () => state.profiles().map((one) => `${one.id}:${one.admin}`).join();
  let drawnOver = null;
  let stopManage = () => {};
  // A household change can move people out of this household, or add the second one: the page follows.
  const households = householdsPart(promptHost, { profile, switchProfile }, () => {
    drawEveryone(list);
    drawThisDevice(device, promptHost, switchProfile);
    // Manage starts again at who you are only when what it showed may no longer be yours.
    if (shown() !== drawnOver) mount();
  });
  function mount() {
    stopManage();
    drawnOver = shown();
    stopManage = mountManage(manage, { promptHost, onChange: () => {
      drawnOver = shown();
      drawEveryone(list);
      households.redraw();
    } });
  }
  mount();
  box.append(list, manage, device, households.box);
  return { box, stop: () => { stopManage(); households.stop(); } };
}
