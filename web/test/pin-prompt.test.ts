/** The one PIN dialog: what it checks before sending, what it says after, and how it gives up. */

import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { askGrownUp, askPin, pinProblem, refusalText } from "../public/lib/pin-prompt.js";
import { answerPin, buttonNamed, byClass, descendants, textOf } from "./support/browser-application";
import { browserEnvironment, Node } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(() => { env = browserEnvironment(); });
afterEach(() => env.restore());

const fields = (root: Node) => descendants(root).filter((node) => node.tagName === "INPUT");
const open = (root: Node) => descendants(root).some((node) => node.className.split(" ").includes("pin-prompt"));
type Outcome = { ok: boolean; reason?: string | null; retryAfter?: number };

describe("what is said before and after sending", () => {
  test("a PIN is four digits, and a new one must be typed the same twice", () => {
    expect(pinProblem("1234", "", false)).toBeNull();
    for (const typed of ["", "123", "12345", "12a4", " 1234"]) expect(pinProblem(typed, "", false)).toBe("A PIN is four digits.");
    expect(pinProblem("1234", "1243", true)).toBe("The two PINs are not the same.");
    expect(pinProblem("1234", "1234", true)).toBeNull();
  });

  test("each refusal in words, the wait in seconds", () => {
    expect(refusalText({ reason: "wrong-pin" })).toBe("Wrong PIN.");
    expect(refusalText({ reason: "wait", retryAfter: 42 })).toBe("Too many wrong PINs. Try again in 42 s.");
    expect(refusalText({ reason: "not-allowed" })).toBe("That is not allowed.");
    expect(refusalText({ reason: "no-pin" })).toBe("This profile has no PIN yet. Choose it again to set one.");
    expect(refusalText({ reason: "not-found" })).toBe("That profile is not here any more.");
    expect(refusalText({ reason: "invalid" })).toBe("That was not accepted. Check the name and the PIN.");
    expect(refusalText({ reason: "name-taken" })).toBe("A profile with that name already exists.");
    expect(refusalText({ reason: null })).toBe("That did not go through. Please try again.");
  });
});

describe("the dialog", () => {
  test("a masked, numeric field the browser is asked not to fill in", () => {
    const root = new Node();
    void askPin(root, { title: "andre’s PIN", send: async () => ({ ok: true }) });
    expect(byClass(root, "pin-prompt").open).toBe(true);
    expect(fields(root)).toHaveLength(1);
    const field = fields(root)[0]!;
    expect(field.getAttribute("type")).toBe("password");
    expect(field.getAttribute("inputmode")).toBe("numeric");
    expect(field.getAttribute("autocomplete")).toBe("off");
    expect(field.getAttribute("maxlength")).toBe("4");
    expect(textOf(root)).toContain("andre’s PIN");
  });

  test("a PIN of the wrong shape is said at once and not sent", async () => {
    const root = new Node();
    const sent: string[] = [];
    void askPin(root, { title: "PIN", send: async (pin: string) => { sent.push(pin); return { ok: true }; } });
    await answerPin(root, "12");
    expect(textOf(byClass(root, "pin-message"))).toBe("A PIN is four digits.");
    expect(sent).toEqual([]);
  });

  test("a refusal is shown, the field emptied, another try allowed", async () => {
    const root = new Node();
    const answers: Outcome[] = [{ ok: false, reason: "wrong-pin" }, { ok: true }];
    const asked = askPin(root, { title: "PIN", send: async () => answers.shift()! });
    await answerPin(root, "1111");
    expect(textOf(byClass(root, "pin-message"))).toBe("Wrong PIN.");
    expect(fields(root)[0]!.value).toBe("");
    expect(open(root)).toBe(true);
    await answerPin(root, "1234");
    expect(await asked).toBe("1234");
    expect(open(root)).toBe(false);
  });

  test("a waiting player's answer is shown in seconds", async () => {
    const root = new Node();
    void askPin(root, { title: "PIN", send: async () => ({ ok: false, reason: "wait", retryAfter: 60 }) });
    await answerPin(root, "1234");
    expect(textOf(byClass(root, "pin-message"))).toBe("Too many wrong PINs. Try again in 60 s.");
  });

  test("Cancel gives up", async () => {
    const root = new Node();
    const asked = askPin(root, { title: "PIN", send: async () => ({ ok: true }) });
    buttonNamed(root, "Cancel").fire("click");
    expect(await asked).toBeNull();
    expect(root.children).toHaveLength(0);
  });

  test("setting a PIN asks twice; two different ones are not sent", async () => {
    const root = new Node();
    const sent: string[] = [];
    const asked = askPin(root, { title: "New", confirm: true, send: async (pin: string) => { sent.push(pin); return { ok: true }; } });
    expect(fields(root)).toHaveLength(2);
    await answerPin(root, "1234", "4321");
    expect(sent).toEqual([]);
    await answerPin(root, "1234", "1234");
    expect(await asked).toBe("1234");
    expect(sent).toEqual(["1234"]);
  });

  test("a PIN being set is sent once: a refusal ends the dialog and is handed back", async () => {
    // Typed twice and four digits: no other new PIN would fare better, and a
    // refusal of the current PIN sent beside it would only spend more tries.
    const root = new Node();
    const sent: string[] = [];
    const refused: Outcome[] = [];
    const asked = askPin(root, {
      title: "New", confirm: true, refused: (outcome: Outcome) => { refused.push(outcome); },
      send: async (pin: string) => { sent.push(pin); return { ok: false, reason: "wrong-pin" }; },
    });
    await answerPin(root, "1234", "1234");
    expect(await asked).toBeNull();
    expect(open(root)).toBe(false);
    expect(sent).toEqual(["1234"]);
    expect(refused).toEqual([{ ok: false, reason: "wrong-pin" }]);
  });

  test("a grown-up with a PIN is asked it; one without chooses one", () => {
    const root = new Node();
    void askGrownUp(root, { name: "andre", hasPin: true }, async () => ({ ok: true }));
    expect(fields(root)).toHaveLength(1);
    expect(textOf(root)).toContain("andre’s PIN");
    const other = new Node();
    void askGrownUp(other, { name: "Maja", hasPin: false }, async () => ({ ok: true }));
    expect(fields(other)).toHaveLength(2);
    expect(textOf(other)).toContain("Choose a PIN for Maja");
  });
});
