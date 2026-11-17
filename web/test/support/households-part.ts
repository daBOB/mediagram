/** The households the owner's view is tested over, and reading and driving it as a viewer would. */

import { profilesPanel } from "../../public/lib/catalog/settings-profiles.js";
import { HOUSEHOLD_WORDS } from "../../public/lib/household-words.js";
import { buttonNamed, byClass, descendants, textOf } from "./browser-application";
import { SPLIT } from "./household-fixture";
import { Node, settle } from "./player-environment";

/** Ole lives in the flat with no PIN; Kai lives at home with one. The attic is empty and nobody runs it. */
const OLE = { id: "ole", name: "Ole", createdAt: 5, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: false, household: "flat" };
const KAI = { id: "kai", name: "Kai", createdAt: 6, kids: false, kidsAge: null, parentId: null, admin: false, hasPin: true, household: "home" };
export const ATTIC = { id: "attic", name: "Attic", adminId: null };
/** andre (the owner) and Lea at home with Kai; Maja runs the flat, with her Tim and Ole. */
export const EVERYONE = [...SPLIT, OLE, KAI];
export const OWNER = EVERYONE[0]!;
export const MAJA = EVERYONE[1]!;

/** The Profiles page as `profile` sees it — the owner's view for the owner, Who's managing? for anyone else — over a host the PIN prompts open on. */
export function part(profile: { id: string; name: string } | null, switchProfile = () => {}) {
  const root = new Node();
  const drawn = profilesPanel(root, { profile, switchProfile });
  root.append(drawn.box);
  return { root, ...drawn };
}

export const rows = (root: Node) => descendants(root).filter((node) => node.classes.has("household-row"));
export const rowOf = (root: Node, name: string) => rows(root).find((row) => row.children[0]!.textContent === name)!;
/** Opens the ⋯ on a household's line, unless it is open already; the menu it opened. */
export function menuOf(row: Node) {
  const more = byClass(row, "more-button");
  if (more.getAttribute("aria-expanded") !== "true") more.fire("click");
  return byClass(row, "household-actions");
}
/** What a household's ⋯ offers, opening it to read. */
export const actionsOf = (row: Node) =>
  descendants(menuOf(row)).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent);
/** A profile's part of the page: its row, then its controls once open. */
export const personOf = (root: Node, name: string) =>
  descendants(root).find((node) => node.classes.has("manage-person") && node.children[0]!.children[0]?.textContent === name)!;
/** Taps `name`'s row, which opens their controls under it, or closes them; their part of the page. */
export function tap(root: Node, name: string) {
  personOf(root, name).children[0]!.fire("click");
  return personOf(root, name);
}
/** `name`'s part of the page with their controls open, tapping their row only if it is not open yet. */
export const opened = (root: Node, name: string) =>
  personOf(root, name).children[0]!.getAttribute("aria-expanded") === "true" ? personOf(root, name) : tap(root, name);
/** Opens the Add someone under `root` — one household's part, or a page with one — unless open already; the form it opened. */
export function addSomeone(root: Node) {
  const add = buttonNamed(root, HOUSEHOLD_WORDS.addSomeone);
  if (add.getAttribute("aria-expanded") !== "true") add.fire("click");
  return descendants(add.parent!).find((node) => node.tagName === "FORM")!;
}
/** Chooses Grown-up or Kid in an Add someone form, as a tap on that choice does. */
export function choose(form: Node, label: string) {
  const choice = descendants(form).find((node) => node.tagName === "INPUT" && node.parent!.children[1]?.textContent === label)!;
  choice.checked = true;
  choice.fire("change");
}
export const prompting = (root: Node) => descendants(root).some((node) => node.classes.has("pin-prompt"));
export const promptTitle = (root: Node) => byClass(root, "pin-prompt").getAttribute("aria-label");
/** The form under `root` that `label`'s button sends. */
const formOf = (root: Node, label: string) => descendants(root).find((node) =>
  node.tagName === "FORM" && descendants(node).some((child) => child.tagName === "BUTTON" && child.textContent === label))!;
export const fieldOf = (root: Node, label: string) =>
  descendants(root).find((node) => ["INPUT", "SELECT"].includes(node.tagName) && node.getAttribute("aria-label") === label)!;
export const optionsOf = (select: Node) => select.children.map((option) => option.textContent);

/** Opens `action`'s form from the household `name`'s ⋯. */
export function open(root: Node, name: string, action: string) {
  buttonNamed(menuOf(rowOf(root, name)), action).fire("click");
  return formOf(rowOf(root, name), action);
}

/** Presses `action` in the household `name`'s ⋯. */
export async function press(root: Node, name: string, action: string) {
  buttonNamed(menuOf(rowOf(root, name)), action).fire("click");
  await settle();
}

async function submit(form: Node) {
  form.fire("submit");
  await settle();
}

/** Opens `action`'s form in the household `name`'s row and sends it, its one field set to `value` if given. */
export async function fill(root: Node, name: string, action: string, value?: string) {
  const form = open(root, name, action);
  if (value !== undefined) descendants(form).find((node) => ["INPUT", "SELECT"].includes(node.tagName))!.value = value;
  await submit(form);
}

/** The create form filled in and sent: the household's name, and its admin — a new grown-up by name, or someone here. */
export async function create(root: Node, name: string, admin: { name: string } | { id: string }) {
  // Create a household opens its form under it; one already open is used as it is.
  if (!descendants(root).some((node) => node.tagName === "FORM" && textOf(node).includes(HOUSEHOLD_WORDS.createAHousehold))) {
    buttonNamed(root, HOUSEHOLD_WORDS.createAHousehold).fire("click");
  }
  const form = formOf(root, HOUSEHOLD_WORDS.createAHousehold);
  fieldOf(form, HOUSEHOLD_WORDS.householdName).value = name;
  const choice = fieldOf(form, HOUSEHOLD_WORDS.admin);
  choice.value = "id" in admin ? admin.id : "";
  choice.fire("change");
  if ("name" in admin) fieldOf(form, HOUSEHOLD_WORDS.newGrownUpName).value = admin.name;
  await submit(form);
}
