import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mountPlayerNotes } from "../public/lib/playback/notes/player-notes.js";
import { mountPlayerLibraryMarks } from "../public/lib/playback/player-library-marks.js";
import { mountPlayerHud } from "../public/lib/playback/player-hud.js";
import * as state from "../public/lib/watch-state.js";
import { browserEnvironment, deferred, Node, settle } from "./support/player-environment";

let env: ReturnType<typeof browserEnvironment>;
beforeEach(async () => {
  env = browserEnvironment();
  await state.useProfile(null);
});
afterEach(async () => {
  await settle();
  env.restore();
});

const textOf = (node: Node): string => node.textContent + node.children.map(textOf).join("");

describe("player notes", () => {
  test("lessons open rendered notes, while films offer them through the button", async () => {
    const notes = mountPlayerNotes({ dialog: env.node("player") });
    env.respondWith(async () => new Response("## Notes\n\nA **useful** lesson."));
    await notes.open({ setId: "lesson", hasSummary: true, kind: "tut" });
    expect(textOf(env.node("summary"))).toContain("A useful lesson.");
    expect(env.node("notes-panel").hidden).toBe(false);
    expect(env.node("player").classes.has("with-notes")).toBe(true);
    env.node("notes-close").fire("click");
    expect(env.node("notes").getAttribute("aria-expanded")).toBe("false");
    await notes.open({ setId: "film", hasSummary: true, kind: "movie" });
    expect(env.node("notes-panel").hidden).toBe(true);
    expect(env.node("notes").hidden).toBe(false);
    env.node("notes").fire("click");
    expect(env.node("notes-panel").hidden).toBe(false);
    expect(notes.contains(env.node("summary"))).toBe(false); // Fixture nodes are independent.
    env.node("notes-panel").append(env.node("summary"));
    expect(notes.contains(env.node("summary"))).toBe(true);
    notes.clear();
    expect(env.node("summary").children).toHaveLength(0);
    expect(env.node("notes-panel").hidden).toBe(true);
    expect(env.node("notes").hidden).toBe(true);
    expect(env.node("player").classes.has("with-notes")).toBe(false);
  });

  test("clear cancels a response body in flight and prevents stale rendering", async () => {
    const notes = mountPlayerNotes({ dialog: env.node("player") });
    const body = deferred<string>();
    env.respondWith(async () => ({ ok: true, text: () => body.promise }) as Response);
    const opening = notes.open({ setId: "lesson", hasSummary: true, kind: "tut" });
    await settle();
    const signal = env.requests[0]!.options!.signal!;
    notes.clear();
    expect(signal.aborted).toBe(true);
    body.resolve("Late notes");
    await opening;
    expect(env.node("summary").children).toHaveLength(0);
    expect(env.node("notes-panel").hidden).toBe(true);
  });

  test("reopening the same title owns only the most recent reply", async () => {
    const notes = mountPlayerNotes({ dialog: env.node("player") });
    const old = deferred<Response>();
    let count = 0;
    env.respondWith(async () => (++count === 1 ? old.promise : new Response("Current notes")));
    const title = { setId: "same", hasSummary: true, kind: "movie" };
    const first = notes.open(title);
    await notes.open(title);
    old.resolve(new Response("Obsolete notes"));
    await first;
    expect(textOf(env.node("summary"))).toBe("Current notes");
    notes.clear();
  });

  test("missing notes clear the previous title without disrupting the controls", async () => {
    const notes = mountPlayerNotes({ dialog: env.node("player") });
    env.respondWith(async () => new Response("Previous notes"));
    await notes.open({ setId: "previous", hasSummary: true, kind: "tut" });
    env.respondWith(async () => new Response("", { status: 404 }));
    await notes.open({ setId: "missing", hasSummary: true });
    expect(env.node("summary").children).toHaveLength(0);
    expect(env.node("notes").hidden).toBe(true);
    expect(env.node("notes-panel").hidden).toBe(true);
  });
});

describe("player library marks", () => {
  test("watchlist controls follow the current title and emit state-owned changes once", () => {
    const marks = mountPlayerLibraryMarks();
    let changed = 0;
    const unsubscribe = state.subscribeChanges(() => {
      changed++;
    });
    try {
      marks.open({ setId: "first-mark" });
      env.node("watchlist").fire("click");
      expect(state.isWatchlisted("first-mark")).toBe(true);
      expect(env.node("watchlist").textContent).toBe("On the list");
      expect(changed).toBe(1);
      marks.open({ setId: "second-mark" });
      expect(env.node("watchlist").getAttribute("aria-pressed")).toBe("false");
      env.node("watchlist").fire("click");
      expect(state.isWatchlisted("second-mark")).toBe(true);
      expect(changed).toBe(2);
      marks.clear();
      env.node("watchlist").fire("click");
      expect(changed).toBe(2);
      expect(state.isWatchlisted("second-mark")).toBe(true);
    } finally {
      unsubscribe();
    }
  });

  test("ratings decide kids membership while unrated titles remain editable", () => {
    const marks = mountPlayerLibraryMarks();
    marks.open({ setId: "rated-safe", fsk: "6" });
    expect(env.node("kids").textContent).toBe("For kids · FSK 6");
    expect(env.node("kids").disabled).toBe(true);
    env.node("kids").fire("click");
    expect(state.isKids("rated-safe")).toBe(false);
    marks.open({ setId: "rated-adult", fsk: "18" });
    expect(env.node("kids").textContent).toBe("FSK 18 · not for kids");
    env.node("kids").fire("click");
    expect(state.isKids("rated-adult")).toBe(false);
    marks.open({ setId: "unrated-editable" });
    expect(env.node("kids").disabled).toBe(false);
    env.node("kids").fire("click");
    expect(state.isKids("unrated-editable")).toBe(true);
    marks.clear();
    env.node("kids").fire("click");
    expect(state.isKids("unrated-editable")).toBe(true);
    state.setKids("unrated-editable", false);
  });

  test("adding to a chosen collection uses the currently open title", async () => {
    env.respondWith(async () =>
      Response.json({ collections: [{ id: "weekend", name: "Weekend", items: [] }] }),
    );
    await state.useProfile("viewer");
    const marks = mountPlayerLibraryMarks();
    const prompts: string[] = [];
    Object.assign(env.window, {
      prompt: (message: string) => {
        prompts.push(message);
        return "1";
      },
    });
    marks.open({ setId: "old" });
    marks.open({ setId: "chosen" });
    env.node("add-to").fire("click");
    expect(prompts).toHaveLength(1);
    expect(prompts[0]).toContain("1. Weekend");
    const write = env.requests.find(({ url }) => url.endsWith("/collections/weekend/items/chosen"));
    expect(write?.options?.method).toBe("PUT");
    marks.clear();
    env.node("add-to").fire("click");
    expect(prompts).toHaveLength(1);
  });
});

describe("player HUD", () => {
  test("resting resumes after activity and clearing prevents a timer from surviving close", async () => {
    const dialog = env.node("player");
    const hud = mountPlayerHud({ dialog, video: env.video });
    hud.open();
    await env.video.play();
    env.advance(2600);
    expect(dialog.classes.has("resting")).toBe(true);
    dialog.fire("pointermove");
    expect(dialog.classes.has("resting")).toBe(false);
    env.advance(2000);
    hud.clear();
    env.advance(5000);
    expect(dialog.classes.has("resting")).toBe(false);
    dialog.fire("pointermove");
    env.advance(5000);
    expect(dialog.classes.has("resting")).toBe(false);
    expect(env.timers.size).toBe(0);
  });

  test("pause and focused controls hold the HUD open but video focus does not", async () => {
    const dialog = env.node("player");
    const hud = mountPlayerHud({ dialog, video: env.video });
    hud.open();
    env.advance(5000);
    expect(dialog.classes.has("resting")).toBe(false);
    dialog.append(env.node("play-pause"));
    env.document.activeElement = env.node("play-pause");
    await env.video.play();
    env.advance(5000);
    expect(dialog.classes.has("resting")).toBe(false);
    env.document.activeElement = env.video;
    dialog.fire("focusout");
    env.advance(2600);
    expect(dialog.classes.has("resting")).toBe(true);
    env.video.pause();
    expect(dialog.classes.has("resting")).toBe(false);
    hud.clear();
  });
});
