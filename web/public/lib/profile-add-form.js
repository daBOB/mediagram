import { el } from "./dom.js";

/** A name, whatever `extra` asks beside it, and the button that adds. The picker's first-profile form is one too. */
export function addForm(label, extra, submit) {
  const form = el("form", "who-new");
  const name = el("input");
  name.maxLength = 120;
  name.placeholder = "Name";
  name.setAttribute("aria-label", `${label}: name`);
  const add = el("button", "who-create", label);
  add.type = "submit";
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (name.value.trim() !== "") void submit(name.value.trim());
  });
  form.append(name, ...extra, add);
  return form;
}
