/**
 * The one Profiles page, Settings' Profiles tab: You — who is watching, the way
 * to switch, your subtitle language — then managing the household, which asks
 * Who's managing? with the picker's tiles. Watching as the owner, the owner's
 * view of every household opens at once instead, with — while there is more
 * than one — the household this device shows, which any grown-up in Settings
 * sees too. A kid sees only You; with nobody
 * chosen, which is how the picker's Manage profiles opens it, there is no You.
 * Changing anything asks that grown-up's PIN — the owner's only at the first
 * change — which the body holds only until the page is left.
 */

import { el } from "../dom.js";
import { initialOf } from "../profile-tiles.js";
import { mountManage } from "../profile-manage.js";
import * as state from "../watch-state.js";
import { thisHousehold } from "../household.js";
import { joinChoices } from "../household-join.js";
import { HOUSEHOLD_WORDS } from "../household-words.js";
import { kidsLimitOf } from "../age-rating.js";

/** The page's name: its Settings tab. */
export const PROFILES = "Profiles";

const SUBTITLE_LANGUAGES = [["off", "Off"], ["de", "German"], ["en", "English"]];

/** Who someone is, as the rows under Manage profiles say it, without the PIN. */
const roleOf = (profile) => (profile.kids ? `${HOUSEHOLD_WORDS.kid} · FSK ${kidsLimitOf(profile)}`
  : [profile.owner && HOUSEHOLD_WORDS.owner, profile.admin ? HOUSEHOLD_WORDS.admin : HOUSEHOLD_WORDS.grownUp].filter(Boolean).join(" · "));

/**
 * You: who is watching, drawn as the people under Manage profiles are — the
 * initial, the name, who they are — with the way to switch at the row's end,
 * then their subtitles on a row of the same columns.
 */
function you(profile, switchProfile) {
  const set = el("section", "setting you");
  set.append(el("h2", "setting-title", "You"));
  const me = el("div", "manage-row");
  me.dataset.initial = initialOf(profile.name);
  const change = el("button", "pill pill-line", "Switch profile");
  change.type = "button";
  change.addEventListener("click", switchProfile);
  me.append(el("span", "manage-name", profile.name), el("span", "setting-value", roleOf(profile)), change);
  set.append(me, subtitlePreference());
  return set;
}

/**
 * The profile-wide subtitle language: the fallback `subtitle-choice.js` uses
 * once a show has no remembered choice of its own. Stored under the fixed
 * scope `"profile"`, unlike every other preference here, which is per show.
 */
function subtitlePreference() {
  const row = el("div", "setting-row");
  const select = el("select");
  select.id = "profile-subtitles";
  for (const [value, label] of SUBTITLE_LANGUAGES) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.append(option);
  }
  select.value = state.preferenceOf("profile", "subtitle") ?? "off";
  select.addEventListener("change", () => state.setPreference("profile", "subtitle", select.value));
  const label = el("label", "manage-name", "Subtitles");
  label.htmlFor = select.id;
  row.append(label, el("span", "setting-value", "Forced subtitles still appear when a film switches language."), select);
  return row;
}

/**
 * Draws into `set`, one quiet line at the page's end, which household's
 * profiles this device shows, and a way to another — when `shown`, and while
 * there is more than one. Joining one takes the device off the profile chosen
 * here, so `onJoined` asks who is watching again.
 */
function drawThisDevice(set, promptHost, onJoined, shown) {
  set.textContent = "";
  // Hidden, not just empty: an empty section would still take the panel's gap.
  set.hidden = !shown || state.households().length < 2;
  if (set.hidden) return;
  const here = thisHousehold();
  const change = el("button", "quiet device-switch", HOUSEHOLD_WORDS.switchHousehold);
  change.type = "button";
  change.addEventListener("click", () => {
    change.hidden = true;
    set.append(joinChoices(promptHost, { except: here?.id, onJoined }));
  });
  set.append(el("span", "device-label", HOUSEHOLD_WORDS.thisDevice), el("span", "setting-value", here?.name ?? ""), change);
}

/**
 * @param {HTMLElement} promptHost PIN prompts open over this element
 * @param {{ profile: {name: string, kids?: boolean, kidsAge?: number|null, owner?: boolean, admin?: boolean}|null,
 *   switchProfile?: () => void }} chosen who is watching, if anyone;
 *   `switchProfile` asks again, after switching household too
 * @returns {{ box: HTMLElement, stop: () => void }} `stop` removes the bodies and drops their PINs
 */
export function profilesPanel(promptHost, { profile, switchProfile }) {
  const box = el("div", "settings-panel");
  if (profile) box.append(you(profile, switchProfile));
  // A kid manages nothing, so is shown nobody else either.
  if (profile?.kids) return { box, stop: () => {} };
  // Scoped faces (appearance.css) make the shared body read as settings sections.
  const manage = el("div", "setting manage-inline");
  const device = el("div", "setting device-line");
  let owning = false;
  // Over the picker, nobody chosen, Who's managing? stands alone until the owner's view opens; in Settings every grown-up has it.
  const drawDevice = () => drawThisDevice(device, promptHost, switchProfile, profile != null || owning);
  // A change can add the second household: the page follows.
  const managed = mountManage(manage, { promptHost, chosen: { profile, switchProfile }, onChange: drawDevice });
  managed.onOwnerView((showing) => {
    owning = showing;
    drawDevice();
  });
  box.append(manage, device);
  return { box, stop: managed.stop };
}
