/** The daily-limit select Manage profiles draws beside the age limit. */

import { el } from "./dom.js";
import { DAILY_LIMIT_CHOICES } from "./daily-limit.js";

/** A native choice of daily watching limit; `value` is minutes, or `null` for none. */
export function dailyChoice(value, label) {
  const choice = el("select");
  choice.setAttribute("aria-label", label);
  for (const { minutes, label: text } of DAILY_LIMIT_CHOICES) {
    const option = el("option", null, text);
    option.value = minutes === null ? "" : String(minutes);
    choice.append(option);
  }
  // A limit set elsewhere that is not on the menu still shows as itself.
  if (value !== null && !DAILY_LIMIT_CHOICES.some(({ minutes }) => minutes === value)) {
    const odd = el("option", null, `${value} min`);
    odd.value = String(value);
    choice.append(odd);
  }
  choice.value = value === null ? "" : String(value);
  return choice;
}

/** What a daily choice says: minutes, or `null` for none. */
export const minutesOf = (choice) => (choice.value === "" ? null : Number(choice.value));
