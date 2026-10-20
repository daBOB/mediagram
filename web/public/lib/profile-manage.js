/**
 * Manage profiles: who there is, a kid's age and daily limits, the sections each is shown, and grown-ups' PINs.
 *
 * Part of the Profiles page (`catalog/settings-profiles.js`), in Settings and
 * over the picker. A device left on a grown-up's profile does not hand a child
 * these controls: the page hides them from kids and asks who you are and the
 * PIN on every visit.
 * A grown-up says who they are and gives their PIN once; it is held while the
 * body is mounted, sent with every change, and dropped when it is torn down.
 *
 * What the panel offers follows the household's rule: the admin adds,
 * removes and resets grown-ups; every grown-up manages their own kids and
 * their own PIN. The server decides all the same — something offered here by
 * mistake is still refused there, and the refusal is shown as it comes.
 */

import { el } from "./dom.js";
import { addForm } from "./profile-add-form.js";
import * as state from "./watch-state.js";
import * as api from "./profile-api.js";
import { kidsLimitOf } from "./age-rating.js";
import { DAILY_LIMIT_WORDS } from "./daily-limit.js";
import { dailyChoice, minutesOf } from "./daily-limit-choice.js";
import { sectionSwitches } from "./section-switches.js";
import { askGrownUp, askPin, newPinTitle, promptsOver, refusalText } from "./pin-prompt.js";

/** What a change reports once the body is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/** The two limits a kid can have. A new kid starts at the stricter; its parent raises it. */
const LIMITS = [6, 12];

export function pill(label, onClick) {
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

/**
 * Draws Manage profiles into `container`.
 * @param {HTMLElement} container
 * @param {{ promptHost: HTMLElement, onChange?: () => void }} options PIN prompts open over
 *   this element; `onChange` runs after each change that went through, once the list is read again
 * @returns {() => void} removes the body and drops the PIN held for the visit
 */
export function mountManage(container, { promptHost, onChange }) {
  const root = el("div");
  const body = el("div");
  const message = el("p", "pin-message");
  message.setAttribute("role", "alert");
  /** `{ actorId, pin }` once a grown-up has said who they are. */
  let session = null;
  let gone = false;
  /** PIN prompts this body opened, closed again if it goes while one is up. */
  const prompts = promptsOver(promptHost);
  const ask = prompts.ask;

  /** Controls by a name that outlives a redraw: the one a change was made with has focus again after it, not the page. */
  const named = new Map();
  let changedWith = null;
  const keep = (name, node) => { named.set(name, node); return node; };

  /** Runs a change with the PIN held, made with the control kept as `name`; once the body is gone nothing is sent. */
  function send(call, name = null) {
    changedWith = name;
    return gone ? Promise.resolve(GONE) : call(session.actorId, session.pin);
  }

  /**
   * Shows how a change went. Every change sends the PIN held here, so a wrong
   * one means it is no longer theirs — ask who they are again rather than
   * spend that profile's tries.
   */
  function report(outcome) {
    if (gone) return;
    message.textContent = outcome.ok ? ""
      : outcome.reason === "wrong-pin" ? "Your PIN is no longer valid. Choose who you are again." : refusalText(outcome);
    if (outcome.reason === "wrong-pin") session = null;
    draw();
    named.get(changedWith)?.focus();
    if (outcome.ok) onChange?.();
  }

  /** Which sections `entry` is shown: a switch sends the whole list with the PIN held, and the panel redraws. */
  const access = (entry) => sectionSwitches(entry,
    async (hidden, key) => report(await send((id, pin) => api.setHiddenSections(id, pin, entry.id, hidden), `${key} ${entry.id}`)),
    (key, node) => keep(`${key} ${entry.id}`, node));

  function draw() {
    body.textContent = "";
    named.clear();
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
        const pin = await ask(() => askGrownUp(promptHost, entry, (given) => (gone ? GONE : api.prove(entry, given)), async (outcome) => {
          if (gone) return;
          message.textContent = refusalText(outcome);
          if (await state.loadProfiles() && !gone) draw();
        }));
        if (pin === null || gone) return;
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
          pill("Remove", () => void removeOne(entry, `Remove ${entry.name}, their kids, and everything they have watched?`)),
          access(entry))),
        addForm("Add a grown-up", [], async (name) => {
          const made = await ask(() => askPin(promptHost, { title: newPinTitle(name), confirm: true, refused: report,
            send: (pin) => send((id, held) => api.create(id, held, { name, kids: false, newPin: pin })) }));
          if (made !== null) report({ ok: true });
        })));
    }
    const newLimit = limitChoice(LIMITS[0], "Age limit for the new kid");
    const newDaily = dailyChoice(null, `${DAILY_LIMIT_WORDS.label} for the new kid`);
    body.append(section("Kids",
      ...kids.map((kid) => {
        const limit = keep(`age ${kid.id}`, limitChoice(kidsLimitOf(kid), `Age limit for ${kid.name}`));
        limit.addEventListener("change", async () =>
          report(await send((id, pin) => api.setKidsAge(id, pin, kid.id, Number(limit.value)), `age ${kid.id}`)));
        const daily = keep(`daily ${kid.id}`, dailyChoice(kid.dailyLimit ?? null, `${DAILY_LIMIT_WORDS.label} for ${kid.name}`));
        daily.addEventListener("change", async () =>
          report(await send((id, pin) => api.setDailyLimit(id, pin, kid.id, minutesOf(daily)), `daily ${kid.id}`)));
        return row(kid.name, limit, daily,
          pill("Remove", () => void removeOne(kid, `Remove ${kid.name} and everything they have watched?`)), access(kid));
      }),
      addForm("Add a kid", [newLimit, newDaily], async (name) =>
        report(await send((id, pin) => api.create(id, pin,
          { name, kids: true, kidsAge: Number(newLimit.value), dailyLimit: minutesOf(newDaily) }))))));
    body.append(section("Your PIN", pill("Change your PIN", () => void newPin(actor))));
  }

  /** A new PIN for `entry`, asked twice. Changing one's own changes the one held here, or the next change would be refused. */
  async function newPin(entry) {
    const own = entry.id === session.actorId;
    const given = await ask(() => askPin(promptHost, { title: own ? "Your new PIN" : `A new PIN for ${entry.name}`, confirm: true,
      refused: report, send: (pin) => send((id, held) => api.setPin(id, held, entry.id, pin)) }));
    if (given === null || gone) return;
    if (own) session.pin = given;
    report({ ok: true });
  }

  async function removeOne(entry, question) {
    if (!window.confirm(question)) return;
    report(await send((id, pin) => api.remove(id, pin, entry.id)));
  }
  root.append(body, message);
  container.append(root);
  draw();
  return () => {
    gone = true;
    session = null;
    prompts.close();
    root.remove();
  };
}
