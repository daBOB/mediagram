/**
 * What opens under a person's row on Manage: one's own PIN, a grown-up's PIN,
 * sections and removal, a kid's age and daily limits, sections and removal —
 * each the household's rule offers — laid out as a short list: what a line
 * sets, then its control, and the actions last. Every change is sent with the
 * PIN `mountManage` holds, and reported there.
 */

import { el } from "./dom.js";
import { limitChoice } from "./profile-add-form.js";
import * as api from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { DAILY_LIMIT_WORDS } from "./daily-limit.js";
import { dailyChoice, minutesOf } from "./daily-limit-choice.js";
import { sectionSwitches } from "./section-switches.js";
import { line, pill } from "./profile-manage-households.js";

/** An open part's actions, last; removing someone sits apart, at the end. */
function actions(...buttons) {
  const one = el("div", "manage-actions");
  one.append(...buttons);
  return one;
}

/**
 * @param {{ send: (call: (actorId: string, pin: string) => Promise<object>, name?: string|null) => Promise<object|null>,
 *   report: (outcome: object|null) => void, keep: (name: string, node: HTMLElement) => HTMLElement,
 *   newPin: (entry: object) => Promise<void> }} tools `send` runs a change with the PIN held, made with
 *   the control kept as `name`; `keep` names a control so it has focus again after the redraw
 */
export function manageRows({ send, report, keep, newPin }) {
  /** Which sections `entry` is shown: a switch sends the whole list with the PIN held, and the panel redraws. */
  const access = (entry) => sectionSwitches(entry,
    async (hidden, key) => report(await send((id, pin) => api.setHiddenSections(id, pin, entry.id, hidden), `${key} ${entry.id}`)),
    (key, node) => keep(`${key} ${entry.id}`, node));

  /** Removing `entry`, once `question` is answered yes. */
  function remove(entry, question) {
    const button = el("button", "manage-remove", "Remove");
    button.type = "button";
    button.addEventListener("click", async () => {
      if (window.confirm(question)) report(await send((id, pin) => api.remove(id, pin, entry.id)));
    });
    return button;
  }

  return {
    you: (entry) => [actions(pill("Change your PIN", () => void newPin(entry)))],
    grownUp: (entry) => [access(entry), actions(pill("Reset PIN", () => void newPin(entry)),
      remove(entry, `Remove ${entry.name}, their kids, and everything they have watched?`))],
    kid(kid) {
      const limit = keep(`age ${kid.id}`, limitChoice(kidsLimitOf(kid), `Age limit for ${kid.name}`));
      limit.addEventListener("change", async () =>
        report(await send((id, pin) => api.setKidsAge(id, pin, kid.id, Number(limit.value)), `age ${kid.id}`)));
      const daily = keep(`daily ${kid.id}`, dailyChoice(kid.dailyLimit ?? null, `${DAILY_LIMIT_WORDS.label} for ${kid.name}`));
      daily.addEventListener("change", async () =>
        report(await send((id, pin) => api.setDailyLimit(id, pin, kid.id, minutesOf(daily)), `daily ${kid.id}`)));
      return [line("Age limit", limit), line(DAILY_LIMIT_WORDS.label, daily), access(kid),
        actions(remove(kid, `Remove ${kid.name} and everything they have watched?`))];
    },
  };
}
