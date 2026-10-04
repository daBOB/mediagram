/**
 * Managing profiles, PIN and rule checked: every operation's answer, and the
 * order the refusals are checked in.
 */

import { afterEach, describe, expect, test } from "bun:test";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { Profile } from "../src/state/profiles";
import type { ProfileManager, Refused } from "../src/state/profiles-manage";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

function store(): WatchState {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-manage-"));
  dirs.push(dir);
  return new WatchState(join(dir, "state.db"));
}

const profile = (state: WatchState, id: string) => state.profiles().find((one) => one.id === id);

/** The PIN `pin-hash.json` gives for this salt is 1234. */
const PIN_1234 = {
  hash: "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924",
  salt: "00112233445566778899aabbccddeeff",
};

describe("the first profile", () => {
  test("a player with no grown-up makes one, and it runs the household", () => {
    const state = store();
    const first = state.manage().createFirst("André", "1111") as Profile;
    expect(first).toMatchObject({ name: "André", kids: false, admin: true, hasPin: true });
    expect(state.profiles()).toEqual([first]);
    expect(state.manage().unlock(first.id, "1111")).toBeNull();
  });

  test("only while no grown-up exists — kids alone do not count", () => {
    const state = store();
    state.createProfile("TV kids", true);
    expect(state.manage().createFirst("André", "1111")).toMatchObject({ admin: true });
    expect(state.manage().createFirst("Maja", "2222")).toEqual({ reason: "not-allowed" });
  });

  test("a grown-up from before PINs counts: that household claims an admin instead", () => {
    const state = store();
    state.createProfile("Sam");
    expect(state.manage().createFirst("André", "1111")).toEqual({ reason: "not-allowed" });
  });

  test("its name and PIN have to be usable", () => {
    const state = store();
    expect(state.manage().createFirst("  ", "1111")).toEqual({ reason: "invalid" });
    expect(state.manage().createFirst("André", "11")).toEqual({ reason: "invalid" });
    expect(state.manage().createFirst("André", undefined)).toEqual({ reason: "invalid" });
    expect(state.profiles()).toEqual([]);
  });

  test("a player that remembers nothing makes nobody", () => {
    expect(new WatchState(null).manage().createFirst("André", "1111")).toEqual({ reason: "invalid" });
  });
});

describe("claiming the admin", () => {
  test("the first grown-up to claim is the admin, and the PIN given becomes theirs", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    expect(state.manage().claimAdmin(andre, "1111")).toBeNull();
    expect(profile(state, andre)).toMatchObject({ admin: true, hasPin: true });
    expect(state.manage().unlock(andre, "1111")).toBeNull();
  });

  test("there is only ever one", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().claimAdmin(maja, "2222")).toEqual({ reason: "not-allowed" });
    expect(state.manage().claimAdmin(andre, "1111")).toEqual({ reason: "not-allowed" });
    // Refused, so the PIN it offered was not kept either.
    expect(profile(state, maja)).toMatchObject({ admin: false, hasPin: false });
  });

  test("once there is one, a claim is refused before any PIN is compared", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().setPin(maja, "", maja, "2222");
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().claimAdmin(maja, "9999")).toEqual({ reason: "not-allowed" });
  });

  test("a grown-up with a PIN must give it, and a kid cannot claim", () => {
    const state = store();
    const maja = state.createProfile("Maja")!.id;
    const mia = state.createProfile("Mia", true)!.id;
    expect(state.manage().setPin(maja, "", maja, "2222")).toBeNull();
    expect(state.manage().claimAdmin(maja, "9999")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().claimAdmin(mia, "1234")).toEqual({ reason: "not-allowed" });
    expect(state.manage().claimAdmin(maja, "2222")).toBeNull();
  });

  test("the PIN a grown-up without one would take has to be four digits", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    expect(state.manage().claimAdmin(andre, "12345")).toEqual({ reason: "invalid" });
    expect(state.manage().claimAdmin("nobody", "1111")).toEqual({ reason: "not-found" });
  });

  test("an admin another device later called a kid runs nothing, so a grown-up may claim", () => {
    const state = store();
    const andre = (state.manage().createFirst("André", "1111") as Profile).id;
    const maja = state.createProfile("Maja")!.id;
    // Another device's document says André is a kid; `kids` only ever turns on.
    state.importMerged({ profiles: [{ name: "andré", displayName: "André", kids: true, progress: [], watched: [] }] });
    expect(profile(state, andre)).toMatchObject({ kids: true, admin: false, hasPin: false });
    expect(state.manage().unlock(andre, undefined)).toBeNull();
    expect(state.manage().claimAdmin(maja, "2222")).toBeNull();
    expect(profile(state, maja)).toMatchObject({ admin: true });
  });
});

describe("entering a profile", () => {
  test("a kid's opens without a PIN", () => {
    const state = store();
    const mia = state.createProfile("Mia", true)!.id;
    expect(state.manage().unlock(mia, undefined)).toBeNull();
  });

  test("a grown-up's opens with its PIN only", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().unlock(andre, "1112")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock(andre, "1111")).toBeNull();
    // Not four digits is not a malformed request, only a wrong PIN.
    expect(state.manage().unlock(andre, "11a1")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock("nobody", "1111")).toEqual({ reason: "not-found" });
  });

  test("a grown-up from before PINs has none to give yet", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().unlock(sam, "1234")).toEqual({ reason: "no-pin" });
  });

  test("a PIN set on another device opens the profile here", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    state.importMerged({ profiles: [{
      name: "sam", displayName: "Sam", progress: [], watched: [], pin: { ...PIN_1234, updatedAt: 5 },
    }] });
    expect(state.manage().unlock(sam, "1234")).toBeNull();
  });
});

describe("a grown-up's PIN", () => {
  test("a grown-up from before PINs sets its first one with nothing to prove", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    expect(state.manage().setPin(sam, "", sam, "4444")).toBeNull();
    expect(state.manage().unlock(sam, "4444")).toBeNull();
    // From then on, changing it asks for it.
    expect(state.manage().setPin(sam, "", sam, "5555")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().setPin(sam, "4444", sam, "5555")).toBeNull();
    expect(state.manage().unlock(sam, "5555")).toBeNull();
  });

  test("the admin resets another grown-up's; a grown-up cannot reset the admin's", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const maja = state.createProfile("Maja")!.id;
    state.manage().claimAdmin(andre, "1111");
    state.manage().setPin(maja, "", maja, "2222");
    expect(state.manage().setPin(andre, "1111", maja, "3333")).toBeNull();
    expect(state.manage().unlock(maja, "2222")).toEqual({ reason: "wrong-pin" });
    expect(state.manage().unlock(maja, "3333")).toBeNull();
    expect(state.manage().setPin(maja, "3333", andre, "0000")).toEqual({ reason: "not-allowed" });
  });

  test("a kid has none, and a new PIN must be four digits", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    const mia = state.createProfile("Mia", true)!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(state.manage().setPin(andre, "1111", mia, "3333")).toEqual({ reason: "not-allowed" });
    expect(state.manage().setPin(andre, "1111", andre, "33")).toEqual({ reason: "invalid" });
  });

  test("a new PIN outdates one another device's clock stamped, however far ahead", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    const ahead = Date.now() + 60_000;
    state.importMerged({ profiles: [{
      name: "sam", displayName: "Sam", progress: [], watched: [], pin: { ...PIN_1234, updatedAt: ahead },
    }] });
    expect(state.manage().setPin(sam, "1234", sam, "5555")).toBeNull();
    expect(state.exportRecord("x").profiles[0]!.pin!.updatedAt).toBeGreaterThan(ahead);
  });

  test("a new PIN over one stamped at the last safe time stays a time every peer keeps", () => {
    const state = store();
    const sam = state.createProfile("Sam")!.id;
    state.importMerged({ profiles: [{
      name: "sam", displayName: "Sam", progress: [], watched: [],
      pin: { ...PIN_1234, updatedAt: Number.MAX_SAFE_INTEGER },
    }] });
    expect(state.manage().setPin(sam, "1234", sam, "5555")).toBeNull();
    expect(state.exportRecord("x").profiles[0]!.pin!.updatedAt).toBe(Number.MAX_SAFE_INTEGER);
  });

  test("is never told to the page", () => {
    const state = store();
    const andre = state.createProfile("André")!.id;
    state.manage().claimAdmin(andre, "1111");
    expect(JSON.stringify(state.profiles())).not.toMatch(/[0-9a-f]{32}/);
  });
});

/** André the admin (PIN 1111), Maja a parent (2222), her kid Mia (12), André's kid Leo (6). */
function household() {
  const state = store();
  const manage = state.manage();
  const andre = made(manage.createFirst("André", "1111"));
  const maja = made(manage.createGrownUp(andre, "1111", "Maja", "2222"));
  const mia = made(manage.createKid(maja, "2222", "Mia", 12));
  const leo = made(manage.createKid(andre, "1111", "Leo", 6));
  return { state, manage, andre, maja, mia, leo };
}

function made(result: Refused | Profile): string {
  if ("reason" in result) throw new Error(`refused: ${result.reason}`);
  return result.id;
}

/** Five wrong PINs: the player waits a minute from here. */
function startWait(manage: ProfileManager, id: string): void {
  for (let wrong = 0; wrong < 5; wrong++) manage.unlock(id, "0000");
}

describe("adding to the household", () => {
  test("the admin adds a grown-up with a first PIN, and a grown-up adds its own kid", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(profile(state, maja)).toMatchObject({ name: "Maja", kids: false, hasPin: true, admin: false });
    expect(manage.unlock(maja, "2222")).toBeNull();
    expect(profile(state, mia)).toMatchObject({ kids: true, kidsAge: 12, parentId: maja, hasPin: false });
    expect(profile(state, leo)).toMatchObject({ kids: true, kidsAge: 6, parentId: andre });
  });

  test("a chosen limit is dated, so a default synced in later cannot undo it", () => {
    const { state } = household();
    const leo = state.exportRecord("laptop").profiles.find((one) => one.name === "Leo")!;
    expect(leo.kidsAge!.age).toBe(6);
    expect(leo.kidsAge!.updatedAt).toBeGreaterThan(0);
    expect(leo.parent).toBe("André");
  });

  test("only the admin adds a grown-up", () => {
    const { manage, maja } = household();
    expect(manage.createGrownUp(maja, "2222", "Ben", "3333")).toEqual({ reason: "not-allowed" });
  });

  test("a kid adds nobody", () => {
    const { manage, mia } = household();
    expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
  });

  test("a name, a new PIN and a limit have to be usable, before anything else is asked", () => {
    const { manage, andre } = household();
    expect(manage.createGrownUp(andre, "1111", "   ", "3333")).toEqual({ reason: "invalid" });
    expect(manage.createGrownUp(andre, "1111", "Ben", "33")).toEqual({ reason: "invalid" });
    expect(manage.createGrownUp(andre, "1111", "Ben", undefined)).toEqual({ reason: "invalid" });
    expect(manage.createKid(andre, "1111", "Zoe", 9)).toEqual({ reason: "invalid" });
    expect(manage.createKid(andre, "1111", "Zoe", "6")).toEqual({ reason: "invalid" });
    expect(manage.createKid(andre, "1111", "Zoe", undefined)).toEqual({ reason: "invalid" });
    expect(manage.createKid("nobody", "1111", "Zoe", 9)).toEqual({ reason: "invalid" });
  });
});

describe("a name already here", () => {
  test("is refused for a kid, a grown-up and a first profile, however it is spelled", () => {
    const { state, manage, andre, maja } = household();
    expect(manage.createKid(maja, "2222", "ANDRÉ ", 6)).toEqual({ reason: "name-taken" });
    expect(manage.createGrownUp(andre, "1111", "  maja", "3333")).toEqual({ reason: "name-taken" });
    expect(manage.createKid(andre, "1111", "leo", 6)).toEqual({ reason: "name-taken" });
    expect(state.profiles()).toHaveLength(4);
    const kidsOnly = store();
    kidsOnly.createProfile("Andre", true);
    expect(kidsOnly.manage().createFirst("andre", "1111")).toEqual({ reason: "name-taken" });
    expect(kidsOnly.manage().createFirst("André", "1111")).toMatchObject({ admin: true });
  });

  test("so a kid can never take the admin's name, and with it the admin's place", () => {
    // Sync knows a viewer by its name and only ever turns `kids` on: a kid
    // called "andré" would have made the admin a kid on every device.
    const { state, manage, andre, maja } = household();
    expect(manage.createKid(maja, "2222", "andré", 12)).toEqual({ reason: "name-taken" });
    expect(state.profiles().find((one) => one.id === andre)).toMatchObject({ kids: false, admin: true });
  });

  test("is said right after a name that cannot be used: before nobody there, and before the wait", () => {
    const { manage, maja } = household();
    startWait(manage, maja);
    expect(manage.createKid(maja, "2222", "  ", 6)).toEqual({ reason: "invalid" });
    expect(manage.createKid("nobody", "0000", "Maja", 6)).toEqual({ reason: "name-taken" });
    expect(manage.createKid(maja, "2222", "Maja", 6)).toEqual({ reason: "name-taken" });
  });
});

describe("removing", () => {
  test("the admin removes a grown-up, and its kids go with it", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(manage.remove(andre, "1111", maja)).toBeNull();
    const left = state.profiles().map((one) => one.id);
    expect(left).toContain(andre);
    expect(left).toContain(leo);
    expect(left).not.toContain(maja);
    expect(left).not.toContain(mia);
  });

  test("the admin cannot be removed, by anyone", () => {
    const { manage, andre, maja } = household();
    expect(manage.remove(andre, "1111", andre)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", andre)).toEqual({ reason: "not-allowed" });
  });

  test("a parent removes its own kid, and nobody else's", () => {
    const { state, manage, andre, maja, mia, leo } = household();
    expect(manage.remove(andre, "1111", mia)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", leo)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(maja, "2222", mia)).toBeNull();
    expect(profile(state, mia)).toBeUndefined();
  });

  test("a kid from before parents is the admin's", () => {
    const { state, manage, andre, maja } = household();
    const tvKids = state.createProfile("TV kids", true)!.id;
    expect(manage.remove(maja, "2222", tvKids)).toEqual({ reason: "not-allowed" });
    expect(manage.remove(andre, "1111", tvKids)).toBeNull();
  });

  test("removing a kid takes only that kid, even one another kid names as parent", () => {
    const { state, manage, andre } = household();
    // Sync sets a parent by name, so a kid can end up naming another kid.
    state.importMerged({ profiles: [
      { name: "tim", displayName: "Tim", kids: true, progress: [], watched: [] },
      { name: "odd", displayName: "Odd", kids: true, parent: "tim", progress: [], watched: [] },
    ] });
    const [tim, odd] = ["Tim", "Odd"].map((name) => state.profiles().find((one) => one.name === name)!);
    expect(odd!.parentId).toBe(tim!.id);
    expect(manage.remove(andre, "1111", tim!.id)).toBeNull();
    expect(profile(state, odd!.id)).toBeDefined();
  });

  test("somebody not there is not found", () => {
    const { manage, andre } = household();
    expect(manage.remove(andre, "1111", "nobody")).toEqual({ reason: "not-found" });
    expect(manage.remove("nobody", "1111", andre)).toEqual({ reason: "not-found" });
  });
});

describe("a kid's limit", () => {
  test("is its parent's to set, and nobody else's", () => {
    const { state, manage, andre, maja, mia } = household();
    expect(manage.setKidsAge(maja, "2222", mia, 6)).toBeNull();
    expect(profile(state, mia)!.kidsAge).toBe(6);
    expect(manage.setKidsAge(andre, "1111", mia, 12)).toEqual({ reason: "not-allowed" });
    expect(manage.setKidsAge(maja, "2222", mia, 9)).toEqual({ reason: "invalid" });
    expect(manage.setKidsAge(maja, "2222", maja, 6)).toEqual({ reason: "not-allowed" });
  });

  test("a change outdates one another device's clock stamped, however far ahead", () => {
    const { state, manage, maja, mia } = household();
    const ahead = Date.now() + 60_000;
    state.importMerged({ profiles: [{
      name: "mia", displayName: "Mia", kids: true, kidsAge: { age: 12, updatedAt: ahead }, progress: [], watched: [],
    }] });
    expect(manage.setKidsAge(maja, "2222", mia, 6)).toBeNull();
    const limit = state.exportRecord("x").profiles.find((one) => one.name === "Mia")!.kidsAge!;
    expect(limit.age).toBe(6);
    expect(limit.updatedAt).toBeGreaterThan(ahead);
  });

  test("a change over one stamped at the last safe time stays a time every peer keeps", () => {
    const { state, manage, maja, mia } = household();
    state.importMerged({ profiles: [{
      name: "mia", displayName: "Mia", kids: true, progress: [], watched: [],
      kidsAge: { age: 12, updatedAt: Number.MAX_SAFE_INTEGER },
    }] });
    expect(manage.setKidsAge(maja, "2222", mia, 6)).toBeNull();
    const limit = state.exportRecord("x").profiles.find((one) => one.name === "Mia")!.kidsAge!;
    expect(limit).toEqual({ age: 6, updatedAt: Number.MAX_SAFE_INTEGER });
  });
});

describe("wrong PINs", () => {
  test("five in a row for one profile, and even its right PIN waits", () => {
    const { manage, andre, leo } = household();
    for (const pin of ["0000", "0001", "0002", "0003", "0004"]) {
      expect(manage.unlock(andre, pin)).toEqual({ reason: "wrong-pin" });
    }
    const waiting = manage.unlock(andre, "1111") as Refused;
    expect(waiting.reason).toBe("wait");
    expect(waiting.retryAfter).toBeGreaterThan(0);
    expect(waiting.retryAfter).toBeLessThanOrEqual(60);
    // Every call that compares that profile's PIN waits, not only entering.
    expect(manage.setKidsAge(andre, "1111", leo, 12)).toMatchObject({ reason: "wait" });
  });

  test("the wait is that profile's; another grown-up's PIN is still compared", () => {
    const { manage, andre, maja } = household();
    startWait(manage, andre);
    expect(manage.unlock(maja, "2222")).toBeNull();
  });

  test("a right PIN for one's own profile does not wash out the guesses at another's", () => {
    const { manage, andre, maja } = household();
    for (let wrong = 0; wrong < 4; wrong++) expect(manage.unlock(andre, "0000")).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(maja, "2222")).toBeNull();
    expect(manage.unlock(andre, "0000")).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(andre, "1111")).toMatchObject({ reason: "wait" });
  });

  test("the count is the player's, not one manager's", () => {
    const { state, andre } = household();
    startWait(state.manage(), andre);
    expect(state.manage().unlock(andre, "1111")).toMatchObject({ reason: "wait" });
  });

  test("a PIN-less grown-up acting while another profile waits is told it has none", () => {
    const { state, manage, andre } = household();
    const sam = state.createProfile("Sam")!.id;
    startWait(manage, andre);
    expect(manage.createKid(sam, "1234", "Zoe", 6)).toEqual({ reason: "no-pin" });
  });

  test("a current PIN that is not four digits is simply wrong, and counts", () => {
    const { manage, andre } = household();
    for (const pin of ["12", "abcd", "", "11111"]) expect(manage.unlock(andre, pin)).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(andre, undefined)).toEqual({ reason: "wrong-pin" });
    expect(manage.unlock(andre, "1111")).toMatchObject({ reason: "wait" });
  });

  test("bad input, somebody not there, and what no PIN could allow are said before the wait", () => {
    const { manage, andre, maja, mia } = household();
    startWait(manage, maja);
    expect(manage.setKidsAge(andre, "1111", mia, 9)).toEqual({ reason: "invalid" });
    expect(manage.remove("nobody", "1111", mia)).toEqual({ reason: "not-found" });
    expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
    expect(manage.claimAdmin(maja, "2222")).toEqual({ reason: "not-allowed" });
    expect(manage.createFirst("Zoe", "3333")).toEqual({ reason: "not-allowed" });
    // The rule comes after the PIN, so this one waits.
    expect(manage.remove(maja, "2222", andre)).toMatchObject({ reason: "wait" });
  });

  test("only a call about to compare a PIN waits", () => {
    const { state, manage, maja, mia } = household();
    const sam = state.createProfile("Sam")!.id;
    startWait(manage, maja);
    expect(manage.unlock(mia, undefined)).toBeNull();
    expect(manage.unlock(sam, "1234")).toEqual({ reason: "no-pin" });
    expect(manage.setPin(sam, "", sam, "4444")).toBeNull();
  });

  test("a right PIN wipes the count", () => {
    const { manage, andre } = household();
    for (let wrong = 0; wrong < 4; wrong++) manage.unlock(andre, "0000");
    expect(manage.unlock(andre, "1111")).toBeNull();
    for (let wrong = 0; wrong < 4; wrong++) manage.unlock(andre, "0000");
    expect(manage.unlock(andre, "1111")).toBeNull();
  });

  test("a kid proves nothing, so it adds nothing to the count", () => {
    const { manage, andre, mia } = household();
    for (let tries = 0; tries < 5; tries++) {
      expect(manage.createKid(mia, "0000", "Zoe", 6)).toEqual({ reason: "not-allowed" });
    }
    expect(manage.unlock(andre, "1111")).toBeNull();
  });

  test("a wrong PIN is said before what the rule would have said", () => {
    const { manage, andre, maja } = household();
    expect(manage.remove(maja, "0000", andre)).toEqual({ reason: "wrong-pin" });
  });
});
