/**
 * The owner's inline forms on the Households part: a household's new name,
 * a choice of grown-up — a new admin, someone to move in — and a new
 * household with its admin. Each only gathers what was typed or chosen; the
 * part asks the PINs and sends it.
 */

import { el } from "../dom.js";
import { nameField } from "../profile-add-form.js";
import { HOUSEHOLD_WORDS } from "../household-words.js";

/** `fields`, and the button `label` that sends them, drawn as Manage's add forms are. */
function inline(label, fields, submit) {
  const form = el("form", "who-new");
  const send = el("button", "who-create", label);
  send.type = "submit";
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    void submit();
  });
  form.append(...fields, send);
  return form;
}

/** A household's name, starting from `current`. */
function householdName(current = "") {
  const name = nameField(HOUSEHOLD_WORDS.householdName, HOUSEHOLD_WORDS.householdName);
  name.value = current;
  return name;
}

/** A native choice of `people`, by name, the first one chosen. */
function personChoice(label, people) {
  const choice = el("select");
  choice.setAttribute("aria-label", label);
  for (const one of people) {
    const option = el("option", null, one.name);
    option.value = one.id;
    choice.append(option);
  }
  choice.value = people[0]?.id ?? "";
  return choice;
}

/** A new name for the household now called `current`. */
export function renameForm(current, submit) {
  const name = householdName(current);
  return inline(HOUSEHOLD_WORDS.rename, [name], () => name.value.trim() && submit(name.value.trim()));
}

/** One of `people`, chosen under `label`, which is also what sends them. */
export function personForm(label, people, submit) {
  const choice = personChoice(label, people);
  return inline(label, [choice], () => submit(people.find((one) => one.id === choice.value)));
}

/**
 * A new household: its name, then its admin — a new grown-up, named here, or
 * one of `movers`. `submit` gets the name, and `{ name }` or the grown-up.
 */
export function createForm(movers, submit) {
  const name = householdName();
  const admin = personChoice(HOUSEHOLD_WORDS.admin, [{ id: "", name: HOUSEHOLD_WORDS.aNewGrownUp }, ...movers]);
  const labelled = el("label", "household-admin", HOUSEHOLD_WORDS.admin);
  labelled.append(admin);
  const grownUp = nameField(HOUSEHOLD_WORDS.newGrownUpName, HOUSEHOLD_WORDS.newGrownUpName);
  admin.addEventListener("change", () => { grownUp.hidden = admin.value !== ""; });
  return inline(HOUSEHOLD_WORDS.createAHousehold, [name, labelled, grownUp], () => {
    const household = name.value.trim();
    const named = grownUp.value.trim();
    if (household === "" || (admin.value === "" && named === "")) return;
    return submit(household, admin.value === "" ? { name: named } : movers.find((one) => one.id === admin.value));
  });
}
