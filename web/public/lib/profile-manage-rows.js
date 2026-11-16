/**
 * Manage's rows and add forms: one's own, a grown-up's, a kid's — each with
 * the controls the household's rule offers and, when given, the line that
 * says who it is — and adding a grown-up or a kid in one's own household.
 * Every change is sent with the PIN `mountManage` holds, and reported there.
 */

import { addForm, LIMITS, limitChoice } from "./profile-add-form.js";
import * as api from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { DAILY_LIMIT_WORDS } from "./daily-limit.js";
import { dailyChoice, minutesOf } from "./daily-limit-choice.js";
import { sectionSwitches } from "./section-switches.js";
import { newPinTitle, askPin } from "./pin-prompt.js";
import { pill, row } from "./profile-manage-households.js";

/**
 * @param {{ promptHost: HTMLElement, ask: (open: () => Promise<string|null>) => Promise<string|null>,
 *   send: (call: (actorId: string, pin: string) => Promise<object>, name?: string|null) => Promise<object|null>,
 *   report: (outcome: object|null) => void, keep: (name: string, node: HTMLElement) => HTMLElement,
 *   newPin: (entry: object) => Promise<void> }} tools `send` runs a change with the PIN held, made with
 *   the control kept as `name`; `keep` names a control so it has focus again after the redraw
 */
export function manageRows({ promptHost, ask, send, report, keep, newPin }) {
  /** Which sections `entry` is shown: a switch sends the whole list with the PIN held, and the panel redraws. */
  const access = (entry) => sectionSwitches(entry,
    async (hidden, key) => report(await send((id, pin) => api.setHiddenSections(id, pin, entry.id, hidden), `${key} ${entry.id}`)),
    (key, node) => keep(`${key} ${entry.id}`, node));

  async function removeOne(entry, question) {
    if (!window.confirm(question)) return;
    report(await send((id, pin) => api.remove(id, pin, entry.id)));
  }

  return {
    you: (entry, line) => row(entry.name, line, pill("Change your PIN", () => void newPin(entry))),
    grownUp: (entry, line) => row(entry.name, line,
      pill("Reset PIN", () => void newPin(entry)),
      pill("Remove", () => void removeOne(entry, `Remove ${entry.name}, their kids, and everything they have watched?`)),
      access(entry)),
    kid(kid, line) {
      const limit = keep(`age ${kid.id}`, limitChoice(kidsLimitOf(kid), `Age limit for ${kid.name}`));
      limit.addEventListener("change", async () =>
        report(await send((id, pin) => api.setKidsAge(id, pin, kid.id, Number(limit.value)), `age ${kid.id}`)));
      const daily = keep(`daily ${kid.id}`, dailyChoice(kid.dailyLimit ?? null, `${DAILY_LIMIT_WORDS.label} for ${kid.name}`));
      daily.addEventListener("change", async () =>
        report(await send((id, pin) => api.setDailyLimit(id, pin, kid.id, minutesOf(daily)), `daily ${kid.id}`)));
      return row(kid.name, line, limit, daily,
        pill("Remove", () => void removeOne(kid, `Remove ${kid.name} and everything they have watched?`)), access(kid));
    },
    addGrownUp: () => addForm("Add a grown-up", [], async (name) => {
      const made = await ask(() => askPin(promptHost, { title: newPinTitle(name), confirm: true, refused: report,
        send: (pin) => send((id, held) => api.create(id, held, { name, kids: false, newPin: pin })) }));
      if (made !== null) report({ ok: true });
    }),
    addKid() {
      const newLimit = limitChoice(LIMITS[0], "Age limit for the new kid");
      const newDaily = dailyChoice(null, `${DAILY_LIMIT_WORDS.label} for the new kid`);
      return addForm("Add a kid", [newLimit, newDaily], async (name) =>
        report(await send((id, pin) => api.create(id, pin,
          { name, kids: true, kidsAge: Number(newLimit.value), dailyLimit: minutesOf(newDaily) }))));
    },
  };
}
