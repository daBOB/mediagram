/**
 * Manage profiles: who there is, a kid's age limit, and grown-ups' PINs.
 *
 * Opened from the picker only — entering a profile never opens it, so a
 * device left on a grown-up's profile does not hand a child these controls.
 * A grown-up says who they are and gives their PIN once; it is held in this
 * panel while it is open, sent with every change, and dropped when it closes.
 *
 * What the panel offers follows the household's rule: the admin adds,
 * removes and resets grown-ups; every grown-up manages their own kids and
 * their own PIN. The server decides all the same — something offered here by
 * mistake is still refused there, and the refusal is shown as it comes.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import * as api from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { askGrownUp, askPin, refusalText } from "./pin-prompt.js";

/** The two limits a kid can have. A new kid starts at the stricter; its parent raises it. */
const LIMITS = [6, 12];

function pill(label, onClick) {
  const button = el("button", "pill pill-line", label);
  button.type = "button";
  button.addEventListener("click", onClick);
  return button;
}

/** A native choice between the two limits. */
function limitChoice(value, label) {
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

function section(title, ...nodes) {
  const box = el("section", "manage-section");
  box.append(el("h2", null, title), ...nodes);
  return box;
}

function row(name, ...controls) {
  const line = el("div", "manage-row");
  line.append(el("span", null, name), ...controls);
  return line;
}

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

/**
 * Covers `parent` — the picker — with the panel.
 * @param {HTMLElement} parent
 * @param {() => void} onClose called once the panel is gone
 */
export function openManage(parent, onClose) {
  const screen = el("div", "who who-manage");
  const card = el("div", "who-card");
  const body = el("div");
  const message = el("p", "pin-message");
  message.setAttribute("role", "alert");
  /** `{ actorId, pin }` once a grown-up has said who they are. */
  let session = null;

  const done = pill("Done", close);

  function close() {
    session = null;
    document.removeEventListener("keydown", onKey);
    screen.remove();
    onClose();
  }

  // Escape closes the panel as Done does, as Back does on Android — unless a
  // PIN prompt lies over it (a second child of the screen), which goes first.
  function onKey(event) {
    if (event.key === "Escape" && screen.childElementCount === 1) close();
  }

  /**
   * Shows how a change went. Every change sends the PIN held here, so a wrong
   * one means it is no longer theirs — ask who they are again rather than
   * spend that profile's tries.
   */
  function report(outcome) {
    message.textContent = outcome.ok ? ""
      : outcome.reason === "wrong-pin" ? "Your PIN is no longer valid. Choose who you are again." : refusalText(outcome);
    if (outcome.reason === "wrong-pin") session = null;
    draw();
  }

  function draw() {
    body.textContent = "";
    const view = session === null ? null : api.manageable(state.profiles(), session.actorId);
    if (view?.actor) drawRole(view);
    else drawWho();
  }

  function drawWho() {
    session = null;
    body.append(el("h1", null, "Manage profiles"), el("p", "who-note", "Who are you?"));
    const choices = el("div", "who-ask");
    for (const entry of state.profiles().filter((profile) => !profile.kids)) {
      choices.append(pill(entry.name, async () => {
        // A first PIN refused is news from another player: read who is here again.
        const pin = await askGrownUp(screen, entry, (given) => api.prove(entry, given), async (outcome) => {
          message.textContent = refusalText(outcome);
          if (await state.loadProfiles()) draw();
        });
        if (pin === null) return;
        session = { actorId: entry.id, pin };
        message.textContent = "";
        draw();
      }));
    }
    body.append(choices);
  }

  function drawRole({ actor, grownUps, kids }) {
    body.append(el("h1", null, "Manage profiles"), el("p", "who-note", `As ${actor.name}`));
    if (actor.admin) {
      body.append(section("Grown-ups",
        ...grownUps.map((entry) => row(entry.name,
          pill("Reset PIN", () => void newPin(entry)),
          pill("Remove", () => void removeOne(entry, `Remove ${entry.name}, their kids, and everything they have watched?`)))),
        addForm("Add a grown-up", [], async (name) => {
          const made = await askPin(screen, { title: `A PIN for ${name}`, confirm: true, refused: report,
            send: (pin) => api.create(session.actorId, session.pin, { name, kids: false, newPin: pin }) });
          if (made !== null) report({ ok: true });
        })));
    }
    const newLimit = limitChoice(LIMITS[0], "Age limit for the new kid");
    body.append(section("Kids",
      ...kids.map((kid) => {
        const limit = limitChoice(kidsLimitOf(kid), `Age limit for ${kid.name}`);
        limit.addEventListener("change", async () =>
          report(await api.setKidsAge(session.actorId, session.pin, kid.id, Number(limit.value))));
        return row(kid.name, limit,
          pill("Remove", () => void removeOne(kid, `Remove ${kid.name} and everything they have watched?`)));
      }),
      addForm("Add a kid", [newLimit], async (name) =>
        report(await api.create(session.actorId, session.pin, { name, kids: true, kidsAge: Number(newLimit.value) })))));
    body.append(section("Your PIN", pill("Change your PIN", () => void newPin(actor))));
  }

  /** A new PIN for `entry`, asked twice. Changing one's own changes the one held here, or the next change would be refused. */
  async function newPin(entry) {
    const own = entry.id === session.actorId;
    const given = await askPin(screen, { title: own ? "Your new PIN" : `A new PIN for ${entry.name}`, confirm: true,
      refused: report, send: (pin) => api.setPin(session.actorId, session.pin, entry.id, pin) });
    if (given === null) return;
    if (own) session.pin = given;
    report({ ok: true });
  }

  async function removeOne(entry, question) {
    if (!window.confirm(question)) return;
    report(await api.remove(session.actorId, session.pin, entry.id));
  }

  card.append(body, message, done);
  screen.append(card);
  parent.append(screen);
  document.addEventListener("keydown", onKey);
  draw();
}
