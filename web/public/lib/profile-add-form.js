import { el } from "./dom.js";

/** A field for a name, as long as the server keeps one, that says `placeholder` until something is typed. */
export function nameField(label, placeholder = "Name") {
  const name = el("input");
  name.maxLength = 120;
  name.placeholder = placeholder;
  name.setAttribute("aria-label", label);
  return name;
}

/** A name, whatever `extra` asks beside it, and the button that adds. The picker's first-profile form is one too. */
export function addForm(label, extra, submit) {
  const form = el("form", "who-new");
  const name = nameField(`${label}: name`);
  const add = el("button", "who-create", label);
  add.type = "submit";
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (name.value.trim() !== "") void submit(name.value.trim());
  });
  form.append(name, ...extra, add);
  return form;
}

/** The two limits a kid can have. A new kid starts at the stricter; its parent raises it. */
export const LIMITS = [6, 12];

/** A native choice between the two limits. */
export function limitChoice(value, label) {
  const choice = el("select");
  choice.setAttribute("aria-label", label);
  for (const age of LIMITS) {
    const option = el("option", null, `FSK ${age}`);
    option.value = String(age);
    choice.append(option);
  }
  choice.value = String(value);
  return choice;
}
