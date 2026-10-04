/** Covers the wait after wrong PINs, on a clock the test moves; `pin-wait.json` pins the rest for the core. */

import { describe, expect, test } from "bun:test";
import { MAX_WRONG_PINS, PinWait, WAIT_MS } from "../src/state/profiles-wait";

function clock() {
  let now = 1_000_000;
  return { now: () => now, pass: (ms: number) => { now += ms; } };
}

const fail = (wait: PinWait, id: string, times: number) => {
  for (let wrong = 0; wrong < times; wrong++) wait.failed(id);
};

describe("the wrong-PIN wait", () => {
  test("is kept per profile: five wrong PINs make that profile wait, nobody else", () => {
    const wait = new PinWait(clock().now);
    fail(wait, "andre", MAX_WRONG_PINS - 1);
    expect(wait.secondsLeft("andre")).toBe(0);
    wait.failed("andre");
    expect(wait.secondsLeft("andre")).toBe(WAIT_MS / 1000);
    expect(wait.secondsLeft("maja")).toBe(0);
  });

  test("a right PIN for a profile whose PIN is known cannot wash out the guesses at another's", () => {
    // Four guesses at the admin, one right answer for one's own profile, over
    // and over: with one count for the whole player that never waited.
    const wait = new PinWait(clock().now);
    for (let round = 0; round < 3; round++) {
      fail(wait, "andre", MAX_WRONG_PINS - 1);
      wait.succeeded("maja");
    }
    expect(wait.secondsLeft("andre")).toBe(WAIT_MS / 1000);
  });

  test("a right PIN wipes that profile's count", () => {
    const wait = new PinWait(clock().now);
    fail(wait, "andre", MAX_WRONG_PINS - 1);
    wait.succeeded("andre");
    fail(wait, "andre", MAX_WRONG_PINS - 1);
    expect(wait.secondsLeft("andre")).toBe(0);
  });

  test("when the wait runs out the count starts again from nothing", () => {
    const time = clock();
    const wait = new PinWait(time.now);
    fail(wait, "andre", MAX_WRONG_PINS);
    time.pass(WAIT_MS - 1);
    expect(wait.secondsLeft("andre")).toBe(1);
    time.pass(1);
    expect(wait.secondsLeft("andre")).toBe(0);
    fail(wait, "andre", MAX_WRONG_PINS - 1);
    expect(wait.secondsLeft("andre")).toBe(0);
  });
});
