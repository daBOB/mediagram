/**
 * Managing profiles: who may add, remove, enter and re-PIN whom.
 *
 * Every operation answers in one order: input that cannot be used (a name, a
 * new PIN, an age); a name another profile here already answers to; somebody
 * not there; what no PIN could make allowed — a
 * kid acting, a second admin, a first profile beside grown-ups; then, only
 * when a PIN is about to be compared, the wrong-PIN wait; a grown-up with no
 * PIN yet; a wrong PIN; and last the rule in `profiles-rules.ts`. So the page
 * can say exactly what went wrong, and a guess without the PIN learns nothing
 * about who may do what.
 *
 * Not a login, and not meant as one. A PIN keeps a child from tapping into a
 * grown-up's profile; the state API has no sessions and a kid's catalog is
 * filtered in the browser, so developer tools or `curl` get past it. What is
 * enforced here is that no management action happens unless the PIN and the
 * rule both agree.
 */

import type { Database } from "bun:sqlite";
import { cleanName, insertProfile, profileRows, toProfile, writeKidsAge, writePin, type Profile, type ProfileRow } from "./profiles";
import { newPin, pinMatches, validPin } from "./profiles-pin";
import { allowed, nameTaken, type Action } from "./profiles-rules";
import type { PinWait } from "./profiles-wait";

/** Why an operation was refused: the strings an HTTP refusal carries. */
export type Refusal = "invalid" | "name-taken" | "not-found" | "wait" | "no-pin" | "wrong-pin" | "not-allowed";

export interface Refused {
  reason: Refusal;
  /** Whole seconds until a PIN is compared again; only with `wait`. */
  retryAfter?: number;
}

/** `null` is done. */
export type Outcome = Refused | null;

const refuse = (reason: Refusal): Refused => ({ reason });
const validAge = (age: unknown): age is 6 | 12 => age === 6 || age === 12;
const isKid = (row: ProfileRow): boolean => row.kids !== 0;

export class ProfileManager {
  constructor(
    private readonly db: Database | null,
    private readonly wait: PinWait,
  ) {}

  /**
   * The first grown-up on a player that has none — the only way a fresh
   * install gets anyone at all. It runs the household from the start; a
   * device that later hears of an older claim hands the role over by the
   * earliest-claim rule the merge already applies.
   */
  createFirst(name: unknown, next: unknown): Refused | Profile {
    if (!validPin(next)) return refuse("invalid");
    const unusable = this.unusable(name);
    if (unusable) return unusable;
    if (profileRows(this.db).some((row) => !isKid(row))) return refuse("not-allowed");
    return insertProfile(this.db, name, { pin: newPin(next), admin: true }) ?? refuse("invalid");
  }

  /** A grown-up, with its first PIN. The admin's to add. */
  createGrownUp(actorId: string, pin: unknown, name: unknown, next: unknown): Refused | Profile {
    if (!validPin(next)) return refuse("invalid");
    const refused = this.unusable(name) ?? this.check(actorId, pin, "create-grown-up", null);
    return refused ?? insertProfile(this.db, name, { pin: newPin(next) }) ?? refuse("invalid");
  }

  /** A kid, belonging to whichever grown-up adds it, with its own limit. */
  createKid(actorId: string, pin: unknown, name: unknown, kidsAge: unknown): Refused | Profile {
    if (!validAge(kidsAge)) return refuse("invalid");
    const refused = this.unusable(name) ?? this.check(actorId, pin, "create-kid", null);
    return refused ?? insertProfile(this.db, name, { kids: true, kidsAge, parentId: actorId }) ?? refuse("invalid");
  }

  /** Removing a grown-up takes its kids with it; removing a kid takes only it. */
  remove(actorId: string, pin: unknown, id: string): Outcome {
    const refused = this.check(actorId, pin, "remove", id);
    if (refused) return refused;
    const db = this.db!;
    const grownUp = !isKid(this.row(id)!);
    // By hand, not by a foreign key: `parent_id` deliberately is not one, so
    // a kid whose parent is gone here but known elsewhere stays readable.
    db.transaction(() => {
      if (grownUp) db.query("DELETE FROM profiles WHERE parent_id = ?1 AND kids = 1").run(id);
      db.query("DELETE FROM profiles WHERE id = ?1").run(id);
    })();
    return null;
  }

  /** A kid's limit, set by the grown-up it belongs to. */
  setKidsAge(actorId: string, pin: unknown, id: string, age: unknown): Outcome {
    if (!validAge(age)) return refuse("invalid");
    const refused = this.check(actorId, pin, "set-kids-age", id);
    if (refused) return refused;
    writeKidsAge(this.db!, id, age);
    return null;
  }

  /** Entering a profile from the picker. A kid's needs no PIN. */
  unlock(id: string, pin: unknown): Outcome {
    const target = this.row(id);
    if (target === undefined) return refuse("not-found");
    return isKid(target) ? null : this.prove(target, pin);
  }

  /**
   * Makes `id` the household's admin — once, while this player knows of
   * none. A grown-up with a PIN gives it; one without takes the PIN given,
   * which is why that one has to be a PIN at all.
   */
  claimAdmin(id: string, pin: unknown): Outcome {
    const rows = profileRows(this.db);
    const target = rows.find((row) => row.id === id);
    if (target !== undefined && !isKid(target) && target.pinHash === null && !validPin(pin)) {
      return refuse("invalid");
    }
    if (target === undefined) return refuse("not-found");
    // A claim a kid's row still holds is no admin: see `toProfile`.
    if (isKid(target) || rows.some((row) => toProfile(row).admin)) return refuse("not-allowed");
    if (target.pinHash !== null) {
      const refused = this.prove(target, pin);
      if (refused) return refused;
    }
    const db = this.db!;
    db.transaction(() => {
      if (target.pinHash === null && validPin(pin)) writePin(db, id, pin);
      db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1").run(id, Date.now());
    })();
    return null;
  }

  /** A grown-up's PIN: its own, or — for the admin — anyone's. */
  setPin(actorId: string, pin: unknown, id: string, next: unknown): Outcome {
    if (!validPin(next)) return refuse("invalid");
    const actor = this.row(actorId);
    // A grown-up from before PINs sets its first one with nothing to prove:
    // until it has one, its profile is as open as every profile was.
    const first = actorId === id && actor !== undefined && !isKid(actor) && actor.pinHash === null;
    const refused = this.check(actorId, pin, "set-pin", id, first);
    if (refused) return refused;
    writePin(this.db!, id, next);
    return null;
  }

  /**
   * A new profile's name: one that cannot be stored, or one another profile
   * here already answers to — which sync would read as the same viewer.
   */
  private unusable(name: unknown): Refused | null {
    if (cleanName(name) === null) return refuse("invalid");
    return nameTaken(profileRows(this.db).map((row) => row.name), name) ? refuse("name-taken") : null;
  }

  private row(id: string): ProfileRow | undefined {
    return profileRows(this.db).find((row) => row.id === id);
  }

  /**
   * Somebody there, a grown-up acting, its PIN unless `unproven`, then the
   * rule. A player that cannot remember has no rows, so nothing gets past.
   */
  private check(actorId: string, pin: unknown, action: Action, targetId: string | null, unproven = false): Outcome {
    const rows = profileRows(this.db);
    const actor = rows.find((row) => row.id === actorId);
    if (actor === undefined || (targetId !== null && !rows.some((row) => row.id === targetId))) {
      return refuse("not-found");
    }
    // A kid manages nothing, and has no PIN to prove otherwise with.
    if (isKid(actor)) return refuse("not-allowed");
    if (!unproven) {
      const refused = this.prove(actor, pin);
      if (refused) return refused;
    }
    // A `Profile` is the rule's view of a role, with a kid never the admin.
    return allowed(rows.map(toProfile), actorId, action, targetId ?? "") ? null : refuse("not-allowed");
  }

  /**
   * A grown-up's PIN: none yet; or — only now that one is about to be
   * compared — that profile's wait, then the comparison and the count it
   * keeps. A PIN that is not four digits is compared like any other, and is wrong.
   */
  private prove(row: ProfileRow, pin: unknown): Outcome {
    if (row.pinHash === null || row.pinSalt === null) return refuse("no-pin");
    const left = this.wait.secondsLeft(row.id);
    if (left > 0) return { reason: "wait", retryAfter: left };
    if (!validPin(pin) || !pinMatches(row.pinHash, row.pinSalt, pin)) {
      this.wait.failed(row.id);
      return refuse("wrong-pin");
    }
    this.wait.succeeded(row.id);
    return null;
  }
}
