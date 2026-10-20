/** The households the owner's part is tested over, and reading and driving the part as a viewer would. */

import { householdsPart } from "../../public/lib/catalog/settings-households.js";
import { HOUSEHOLD_WORDS } from "../../public/lib/household-words.js";
import { buttonNamed, byClass, descendants } from "./browser-application";
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

/** The part as `profile` sees it, over a host the PIN prompts open on. */
export function part(profile: { id: string } | null, switchProfile = () => {}) {
  const root = new Node();
  const drawn = householdsPart(root, { profile, switchProfile });
  root.append(drawn.box);
  return { root, ...drawn };
}

export const rows = (root: Node) => descendants(root).filter((node) => node.classes.has("household-row"));
export const rowOf = (root: Node, name: string) => rows(root).find((row) => row.children[0]!.textContent === name)!;
export const actionsOf = (row: Node) =>
  descendants(byClass(row, "household-actions")).filter((node) => node.tagName === "BUTTON").map((node) => node.textContent);
export const prompting = (root: Node) => descendants(root).some((node) => node.classes.has("pin-prompt"));
export const promptTitle = (root: Node) => byClass(root, "pin-prompt").getAttribute("aria-label");
/** The form under `root` that `label`'s button sends. */
const formOf = (root: Node, label: string) => descendants(root).find((node) =>
  node.tagName === "FORM" && descendants(node).some((child) => child.tagName === "BUTTON" && child.textContent === label))!;
export const fieldOf = (root: Node, label: string) =>
  descendants(root).find((node) => ["INPUT", "SELECT"].includes(node.tagName) && node.getAttribute("aria-label") === label)!;
export const optionsOf = (select: Node) => select.children.map((option) => option.textContent);

/** Opens `action`'s form in the household `name`'s row. */
export function open(root: Node, name: string, action: string) {
  buttonNamed(rowOf(root, name), action).fire("click");
  return formOf(rowOf(root, name), action);
}

/** Presses `action` in the household `name`'s row. */
export async function press(root: Node, name: string, action: string) {
  buttonNamed(rowOf(root, name), action).fire("click");
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
  const form = formOf(root, HOUSEHOLD_WORDS.createAHousehold);
  fieldOf(form, HOUSEHOLD_WORDS.householdName).value = name;
  const choice = fieldOf(form, HOUSEHOLD_WORDS.admin);
  choice.value = "id" in admin ? admin.id : "";
  choice.fire("change");
  if ("name" in admin) fieldOf(form, `${HOUSEHOLD_WORDS.aNewGrownUp}: name`).value = admin.name;
  await submit(form);
}
