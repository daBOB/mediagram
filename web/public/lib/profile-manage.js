/**
 * Manage profiles: who there is, a kid's age and daily limits, the sections each is shown, and grown-ups' PINs.
 *
 * Part of the Profiles page (`catalog/settings-profiles.js`), in Settings and
 * over the picker. A device left on a grown-up's profile does not hand a child
 * these controls: the page hides them from kids and asks Who's managing?
 * (`profile-manage-who.js`) and that PIN on every visit — but for the owner,
 * whose view of every household (`profile-manage-owner.js`) opens at once when
 * watching as them, asking their PIN at the first change. A PIN is given once,
 * held while the body is mounted, sent with every change, and dropped when it
 * is torn down.
 *
 * What the panel offers follows the household's rule: the admin adds, removes and resets grown-ups;
 * every grown-up manages their own kids and their own PIN; the owner, everyone in every household.
 * The server decides all the same — something offered here by mistake is still
 * refused there, and the refusal is shown as it comes.
 */

import { el } from "./dom.js";
import * as state from "./watch-state.js";
import * as api from "./profile-api.js";
import { HOUSEHOLD_WORDS } from "./household-words.js";
import { byHousehold } from "./profile-manage-households.js";
import { manageRows } from "./profile-manage-rows.js";
import { people } from "./profile-manage-person.js";
import { addSomeone } from "./profile-add-someone.js";
import { ownerView } from "./profile-manage-owner.js";
import { whoIsManaging } from "./profile-manage-who.js";
import { askPin, newPinTitle, promptsOver, refusalText } from "./pin-prompt.js";

/** What a change reports once the body is gone: nothing was sent. */
const GONE = { ok: false, reason: "not-found" };

/**
 * Draws Manage profiles into `container`.
 * @param {HTMLElement} container
 * @param {{ promptHost: HTMLElement, onChange?: () => void,
 *   chosen?: { profile?: {id: string}|null, switchProfile?: () => void } }} options PIN prompts open over
 *   `promptHost`; `onChange` runs after each change that went through, once the list is read again;
 *   `chosen` is who is watching, the owner's view at once for the owner
 * @returns {{ stop: () => void, onOwnerView: (listener: (showing: boolean) => void) => void }}
 *   `stop` removes the body and drops the PIN held; `onOwnerView`'s listener hears at once, and on every
 *   change, whether the owner's view shows
 */
export function mountManage(container, { promptHost, onChange, chosen = {} }) {
  const root = el("div");
  const body = el("div");
  const message = el("p", "pin-message");
  message.setAttribute("role", "alert");
  const owner = () => state.everyone().find((one) => one.owner === true && !one.kids);
  /** Who is managing, said only when that is not who is watching. */
  const asNote = (actor) => (actor.id === chosen.profile?.id ? [] : [el("p", "who-note", `As ${actor.name}`)]);
  /** `{ actorId, pin }` once a grown-up has said who they are; watching as the owner, theirs with the PIN still to ask. */
  let session = chosen.profile?.id != null && chosen.profile.id === owner()?.id ? { actorId: chosen.profile.id, pin: null } : null;
  let gone = false;
  let showing = false;
  const listeners = [];
  /** PIN prompts this body opened, closed again if it goes while one is up. */
  const prompts = promptsOver(promptHost);
  const ask = prompts.ask;

  /** Controls by a name that outlives a redraw: the one a change was made with has focus again after it, not the page. */
  const named = new Map();
  let changedWith = null;
  const keep = (name, node) => { named.set(name, node); return node; };
  /** Who is open on the page: one person at a time, still open after a redraw. */
  const person = people(keep);

  /** Whether a PIN is held, the owner's asked now when none is yet; false when none was given, or the body is gone. */
  async function pinHeld() {
    const asked = session;
    if (!gone && asked?.pin === null) {
      const given = await ask(() => askPin(promptHost, { title: HOUSEHOLD_WORDS.ownersPin,
        send: (pin) => (gone ? Promise.resolve(GONE) : api.unlock(asked.actorId, pin)) }));
      if (session === asked && given !== null) asked.pin = given;
    }
    return !gone && session?.pin != null;
  }

  /** Runs a change with the PIN held, made with the control kept as `name`; `null` when no PIN was given, and once the body is gone nothing is sent. */
  async function send(call, name = null) {
    changedWith = name;
    if (!(await pinHeld())) return gone ? GONE : null;
    return call(session.actorId, session.pin);
  }

  /**
   * Shows how a change went; `null` is one never sent, the controls drawn back
   * as they were. Every change sends the PIN held here, so a wrong one means it
   * is no longer theirs: the owner is asked it again at the next change, anyone
   * else who they are, rather than spend that profile's tries.
   */
  function report(outcome) {
    if (gone) return;
    const wrong = outcome?.reason === "wrong-pin";
    message.textContent = outcome === null || outcome.ok ? ""
      : wrong && !showing ? "Your PIN is no longer valid. Choose who you are again." : refusalText(outcome);
    if (wrong && showing) session.pin = null;
    else if (wrong) session = null;
    draw();
    named.get(changedWith)?.focus();
    if (outcome?.ok) onChange?.();
  }

  /**
   * Runs `call(actorId, pin, newPin)` from a form, saying a refusal in `said`,
   * beside it, which keeps what was typed — but for a PIN that is no longer
   * theirs, from anyone but the owner, who is asked who they are again.
   * `newPinFor` names a grown-up being added, or about to run a household with
   * no PIN yet: theirs is asked twice, after the manager's. A taken name is the
   * household's when `named()` says so. Once it went through, the control kept
   * as `name` has focus.
   */
  async function act(said, call, { newPinFor = null, named: household = () => false, name = null } = {}) {
    changedWith = name;
    if (!(await pinHeld())) return;
    const run = (next) => (gone ? Promise.resolve(GONE) : call(session.actorId, session.pin, next));
    const refused = (outcome) => {
      if (gone) return;
      if (outcome.reason === "wrong-pin" && !showing) return report(outcome);
      if (outcome.reason === "wrong-pin" && session !== null) session.pin = null;
      said.textContent = outcome.reason === "name-taken" && household() ? HOUSEHOLD_WORDS.householdNameTaken : refusalText(outcome);
    };
    if (newPinFor === null) {
      const outcome = await run();
      return outcome.ok ? report(outcome) : refused(outcome);
    }
    const given = await ask(() => askPin(promptHost, { title: newPinTitle(newPinFor), confirm: true, send: run, refused }));
    if (given !== null) report({ ok: true });
  }

  function draw() {
    body.textContent = "";
    named.clear();
    const boss = owner();
    const asOwner = session !== null && session.actorId === boss?.id;
    if (asOwner) {
      body.append(el("h1", null, "Manage profiles"), ...asNote(boss),
        ...ownerView(boss, { draws, person, act, keep, switchProfile: chosen.switchProfile ?? (() => {}) }));
    } else {
      const view = session === null || !state.profiles().some((one) => one.id === session.actorId) ? null : api.manageable(state.everyone(), session.actorId);
      if (view?.actor) drawRole(view);
      else drawWho();
    }
    if (asOwner === showing) return;
    showing = asOwner;
    listeners.forEach((listener) => listener(showing));
  }

  function drawWho() {
    session = null;
    body.append(whoIsManaging({ promptHost, ask, gone: () => gone,
      chosen: (entry, pin) => {
        session = { actorId: entry.id, pin };
        message.textContent = "";
        draw();
      },
      // A first PIN refused is news from another player: read who is here again.
      refused: async (outcome) => {
        if (gone) return;
        message.textContent = refusalText(outcome);
        if (await state.loadProfiles() && !gone) draw();
      } }));
  }
  /** What opens under a person's row: one's own, a grown-up's, a kid's. */
  const draws = manageRows({ send, report, keep, newPin });

  /** Anyone but the owner: their own household's people as the owner's view draws them, unnamed, with Add someone. */
  function drawRole(view) {
    const add = addSomeone({ grownUp: view.actor.admin === true, kid: true, act, keep });
    body.append(el("h1", null, "Manage profiles"), ...asNote(view.actor),
      byHousehold(view, state.profiles(), { person, draws, add }));
  }

  /** A new PIN for `entry`, asked twice — the owner's own first, if not held yet — then focus on their row. Changing one's own changes the one held here, or the next change would be refused. */
  async function newPin(entry) {
    if (session?.pin === null && !(await pinHeld())) return;
    const own = entry.id === session.actorId;
    const given = await ask(() => askPin(promptHost, { title: own ? "Your new PIN" : `A new PIN for ${entry.name}`, confirm: true,
      refused: report, send: (pin) => send((id, held) => api.setPin(id, held, entry.id, pin), `person ${entry.id}`) }));
    if (given === null || gone) return;
    if (own) session.pin = given;
    report({ ok: true });
  }
  root.append(body, message);
  container.append(root);
  draw();
  // A change made elsewhere on the page — a household renamed, someone moved — is drawn here too.
  window.addEventListener("mediagram:profiles", draw);
  return {
    stop() {
      gone = true;
      window.removeEventListener("mediagram:profiles", draw);
      session = null;
      prompts.close();
      root.remove();
    },
    onOwnerView: (listener) => { listeners.push(listener); listener(showing); },
  };
}
