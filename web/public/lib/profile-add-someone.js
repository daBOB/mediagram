/**
 * Add someone: the one way into a household on Manage profiles — a quiet
 * button that opens one form in place: Grown-up or Kid where both may be
 * added, a name, and for a kid the age limit, the daily limit and, with more
 * than one grown-up to choose from, who they belong to, its admin to start
 * with. A grown-up is asked their first PIN twice, after the manager's. A
 * refusal is said under the form, which stays open as typed.
 */

import { el } from "./dom.js";
import * as api from "./profile-api.js";
import { HOUSEHOLD_WORDS } from "./household-words.js";
import { DAILY_LIMIT_WORDS } from "./daily-limit.js";
import { dailyChoice, minutesOf } from "./daily-limit-choice.js";
import { LIMITS, limitChoice, nameField } from "./profile-add-form.js";
import { personChoice } from "./catalog/household-forms.js";
import { line, message, opener } from "./profile-manage-households.js";

/** Tells apart the forms' Grown-up and Kid choices, which go by name. */
let forms = 0;

/** Grown-up or Kid, a grown-up to start with: two native choices drawn as one control. `choose` hears whether a kid is chosen. */
function kindChoice(choose) {
  const group = el("div", "add-kind");
  group.setAttribute("role", "radiogroup");
  group.setAttribute("aria-label", HOUSEHOLD_WORDS.addSomeone);
  const name = `add-kind-${++forms}`;
  for (const [kid, label] of [[false, HOUSEHOLD_WORDS.grownUp], [true, HOUSEHOLD_WORDS.kid]]) {
    const option = el("label", "add-kind-option");
    const radio = el("input");
    radio.type = "radio";
    radio.name = name;
    radio.checked = !kid;
    // Focus goes with the choice, so the option ringed is the one chosen.
    radio.addEventListener("change", () => {
      choose(kid);
      radio.focus();
    });
    option.append(radio, el("span", null, label));
    group.append(option);
  }
  return group;
}

/**
 * @param {{ grownUp: boolean, kid: boolean, parents?: Array<{id: string, name: string, admin?: boolean}>,
 *   household?: string, householdName?: string, act: (said: HTMLElement, call: Function, opts?: object) => Promise<void>,
 *   keep: (name: string, node: HTMLElement) => HTMLElement }} options what may be added; `parents`, the grown-ups
 *   a new kid may belong to — none where the manager's own kids are added; `household`, where a grown-up goes,
 *   the manager's own when none; `householdName` names the button where there is one per household; `act`
 *   sends the add, saying a refusal in the place it is given; `keep` names the button, which has focus again
 *   once an add went through
 * @returns {HTMLElement}
 */
export function addSomeone({ grownUp, kid, parents = [], household, householdName, act, keep }) {
  const named = `add ${household ?? ""}`;
  /** The form, drawn each time Add someone opens. */
  function build() {
    const said = message();
    const form = el("form", "add-form");
    const name = nameField("Name", "");
    const send = el("button", "who-create");
    send.type = "submit";
    const kidPart = el("div", "add-kid");
    const age = limitChoice(LIMITS[0], "Age limit for the new kid");
    const daily = dailyChoice(null, `${DAILY_LIMIT_WORDS.label} for the new kid`);
    const parent = personChoice(HOUSEHOLD_WORDS.belongsTo, parents);
    parent.value = (parents.find((one) => one.admin) ?? parents[0])?.id ?? "";
    kidPart.append(line("Age limit", age), line(DAILY_LIMIT_WORDS.label, daily),
      ...(parents.length > 1 ? [line(HOUSEHOLD_WORDS.belongsTo, parent)] : []));
    let kids = false;
    const choose = (isKid) => {
      kids = isKid;
      send.textContent = isKid ? "Add a kid" : "Add a grown-up";
      kidPart.hidden = !isKid;
    };
    choose(!grownUp);
    form.append(...(grownUp && kid ? [kindChoice(choose)] : []), line("Name", name), ...(kid ? [kidPart] : []), send);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const typed = name.value.trim();
      if (typed === "") return;
      said.textContent = "";
      if (kids) {
        const someone = { name: typed, kids: true, kidsAge: Number(age.value), dailyLimit: minutesOf(daily), ...(parents.length > 0 && { parentId: parent.value }) };
        void act(said, (actor, pin) => api.create(actor, pin, someone), { name: named });
      } else {
        void act(said, (actor, pin, newPin) => api.create(actor, pin, { name: typed, kids: false, newPin, ...(household && { household }) }),
          { newPinFor: typed, name: named });
      }
    });
    return [form, said];
  }
  return opener(HOUSEHOLD_WORDS.addSomeone, build, (node) => keep(named, node),
    householdName && `${HOUSEHOLD_WORDS.addSomeoneTo} ${householdName}`);
}
