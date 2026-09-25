/** Covers `sync`: when a machine sends, and what it takes back. */

import { afterEach, describe, expect, test } from "bun:test";
import { Database } from "bun:sqlite";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { StateSync, type ChannelDocument, type StateChannel } from "../src/state/sync";
import { WatchState } from "../src/state/store";

const dirs: string[] = [];
function machine(name = "André") {
  const dir = mkdtempSync(join(tmpdir(), "mediagram-sync-"));
  dirs.push(dir);
  const path = join(dir, "state.db");
  const state = new WatchState(path);
  return { state, me: state.createProfile(name)!.id, path };
}
afterEach(() => {
  for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true });
});

/** A channel in memory, counting what it was asked to do. */
function fakeChannel(held: ChannelDocument[] = []) {
  let next = 100;
  const puts: Array<{ body: string; messageId: number | null }> = [];
  const channel: StateChannel = {
    list: async () => [...held],
    put: async (body, messageId) => {
      puts.push({ body, messageId });
      if (messageId !== null) {
        const at = held.findIndex((d) => d.messageId === messageId);
        if (at !== -1) held[at] = { ...held[at]!, text: body };
        return messageId;
      }
      const made = { messageId: (next += 1), device: JSON.parse(body).device, text: body };
      held.push(made);
      return made.messageId;
    },
  };
  return { channel, puts, held };
}

describe("a round of sync", () => {
  test("sends this machine's document when it has never written one", () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const { channel, puts } = fakeChannel();

    return new StateSync(state, channel, "laptop").once().then((outcome) => {
      expect(outcome.pushed).toBe(true);
      expect(puts[0]!.messageId).toBeNull();
      expect(JSON.parse(puts[0]!.body).device).toBe("laptop");
    });
  });

  test("edits its own message rather than sending a second", async () => {
    // A device writes one message, for ever. A second would be a second
    // opinion nobody asked for and nothing would ever clean it up.
    const { state, me } = machine();
    const { channel, puts } = fakeChannel();
    const sync = new StateSync(state, channel, "laptop");

    state.setProgress(me, "01A", 100, 1204);
    await sync.once();
    state.setProgress(me, "01A", 200, 1204);
    await sync.once();

    expect(puts).toHaveLength(2);
    expect(puts[0]!.messageId).toBeNull();
    expect(puts[1]!.messageId).toBe(101);
  });

  test("and finds its own message again after a restart", async () => {
    // The id lives on the channel, not in this process.
    const { state, me } = machine();
    state.setProgress(me, "01A", 100, 1204);
    const { channel, puts } = fakeChannel();
    await new StateSync(state, channel, "laptop").once();

    state.setProgress(me, "01A", 300, 1204);
    await new StateSync(state, channel, "laptop").once();

    expect(puts[1]!.messageId).toBe(101);
  });
});

describe("not sending the same thing twice", () => {
  test("a second round with nothing new sends nothing", async () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const { channel, puts } = fakeChannel();
    const sync = new StateSync(state, channel, "laptop");

    await sync.once();
    const again = await sync.once();

    expect(again.pushed).toBe(false);
    expect(puts).toHaveLength(1);
  });

  test("even though every export carries a fresh `writtenAt`", async () => {
    // Left out of the comparison on purpose, or a player left open overnight
    // uploads an identical document every tick for ever.
    const { state } = machine();
    const { channel, puts } = fakeChannel();
    const sync = new StateSync(state, channel, "laptop");

    await sync.once();
    await sync.once();
    await sync.once();

    expect(puts).toHaveLength(1);
  });
});

describe("what comes back", () => {
  test("another machine's position arrives", async () => {
    const other = machine();
    other.state.setProgress(other.me, "01FILM", 900, 1204);
    const { channel } = fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]);

    const here = machine();
    const outcome = await new StateSync(here.state, channel, "laptop").once();

    expect(outcome.pulled).toBeGreaterThan(0);
    expect(here.state.snapshot(here.me).progress[0]).toMatchObject({ setId: "01FILM", at: 900 });
  });

  test("another machine's editor's choice arrives, and so does its retirement", async () => {
    const other = machine();
    other.state.setEditorsChoice("01OLD", true);
    const here = machine();
    await new StateSync(here.state, fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]).channel, "laptop").once();
    expect(here.state.editorsChoice()).toBe("01OLD");

    Bun.sleepSync(2);
    other.state.setEditorsChoice("01NEW", true);
    await new StateSync(here.state, fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]).channel, "laptop").once();
    expect(here.state.editorsChoice()).toBe("01NEW");
  });

  test("unpinning after a merge leaves no pick, not an older one", async () => {
    const other = machine();
    const here = machine();
    here.state.setEditorsChoice("01MINE", true);
    Bun.sleepSync(2);
    // Pinned on another device that never heard of this one's pick.
    other.state.setEditorsChoice("01THEIRS", true);
    await new StateSync(here.state, fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]).channel, "laptop").once();
    expect(here.state.editorsChoice()).toBe("01THEIRS");

    here.state.setEditorsChoice("01THEIRS", false);
    expect(here.state.editorsChoice()).toBeNull();
  });

  test("a document that cannot be read is skipped, not fatal", async () => {
    const good = machine();
    good.state.setProgress(good.me, "01FILM", 900, 1204);
    const { channel } = fakeChannel([
      { messageId: 6, device: "junk", text: "{{{ not json" },
      { messageId: 7, device: "desktop", text: JSON.stringify(good.state.exportRecord("desktop")) },
    ]);

    const here = machine();
    await new StateSync(here.state, channel, "laptop").once();

    expect(here.state.snapshot(here.me).progress[0]!.setId).toBe("01FILM");
  });

  test("this machine's own document is not merged back in as a stranger's", async () => {
    // It is already in the merge, from the local database. Parsing it again
    // would be harmless but would hide a device id that never matched.
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const { channel } = fakeChannel();
    const sync = new StateSync(state, channel, "laptop");
    await sync.once();

    const second = await sync.once();
    expect(second.pulled).toBe(0);
  });
});

describe("a channel that cannot be reached", () => {
  test("costs a message and nothing else", async () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const broken: StateChannel = {
      list: async () => {
        throw new Error("no network");
      },
      put: async () => 0,
    };

    const outcome = await new StateSync(state, broken, "laptop").once();

    expect(outcome.failed).toBe("no network");
    expect(outcome.pulled).toBe(0);
    // The player still knows everything it knew.
    expect(state.snapshot(me).progress[0]!.at).toBe(742);
  });

  test("and a send that fails does not pretend to have worked", async () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const half: StateChannel = {
      list: async () => [],
      put: async () => {
        throw new Error("upload refused");
      },
    };

    const outcome = await new StateSync(state, half, "laptop").once();
    expect(outcome.pushed).toBe(false);
    expect(outcome.failed).toBe("upload refused");
  });

  test("so the next round tries again rather than believing it is in sync", async () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    let refuse = true;
    const puts: string[] = [];
    const flaky: StateChannel = {
      list: async () => [],
      put: async (body) => {
        if (refuse) throw new Error("upload refused");
        puts.push(body);
        return 1;
      },
    };
    const sync = new StateSync(state, flaky, "laptop");

    await sync.once();
    refuse = false;
    const second = await sync.once();

    expect(second.pushed).toBe(true);
    expect(puts).toHaveLength(1);
  });

  test.each([
    ["Error", new Error("upload refused"), "upload refused"],
    ["unprintable value", Object.create(null) as unknown, "unprintable rejection"],
  ])("reports committed imports after %s send failures and retries next round", async (_, rejection, message) => {
    const other = machine();
    other.state.setProgress(other.me, "01FILM", 900, 1204);
    const { channel, puts } = fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]);
    let refuse = true;
    const flaky: StateChannel = {
      list: channel.list,
      put: async (body, messageId) => {
        if (refuse) throw rejection;
        return channel.put(body, messageId);
      },
    };
    const here = machine();
    const sync = new StateSync(here.state, flaky, "laptop");

    const first = await sync.once();

    expect(here.state.snapshot(here.me).progress[0]).toMatchObject({ setId: "01FILM", at: 900 });
    expect(first).toEqual({ pulled: 1, pushed: false, failed: message });
    expect(puts).toHaveLength(0);

    refuse = false;
    expect(await sync.once()).toEqual({ pulled: 0, pushed: true });
    expect(puts).toHaveLength(1);
    expect(JSON.parse(puts[0]!.body).profiles[0].progress[0]).toMatchObject({
      setId: "01FILM", at: 900,
    });
    expect(await sync.once()).toEqual({ pulled: 0, pushed: false });
    expect(puts).toHaveLength(1);
  });
});

describe("a local import that fails", () => {
  test("refuses to publish when local storage is unavailable", async () => {
    const other = machine();
    other.state.setProgress(other.me, "01FILM", 900, 1204);
    const { channel, puts } = fakeChannel([
      { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
    ]);
    const state = new WatchState(null);

    expect(await new StateSync(state, channel, "laptop").once()).toEqual({
      pulled: 0,
      pushed: false,
      failed: "watch state database is unavailable",
    });
    expect(puts).toHaveLength(0);
    expect(state.profiles()).toEqual([]);
  });

  test.each(["profiles", "progress", "watched", "collection_items"])(
    "rolls back every change and sends nothing when writing %s fails",
    async (table) => {
      const other = machine("Sam");
      other.state.setKids("01KID", true);
      other.state.setProgress(other.me, "01FILM", 900, 1204);
      other.state.setWatched(other.me, "01DONE", true);
      other.state.setWatchlisted(other.me, "01LATER", true);
      const collection = other.state.createCollection(other.me, "Sunday")!;
      other.state.addToCollection(other.me, collection.id, "01FILM");
      const { channel, puts } = fakeChannel([
        { messageId: 7, device: "desktop", text: JSON.stringify(other.state.exportRecord("desktop")) },
      ]);
      const here = machine();
      here.state.setProgress(here.me, "01MINE", 120, 1204);
      const before = here.state.snapshot(here.me);
      const profiles = here.state.profiles();
      const db = new Database(here.path);
      try {
        // Abort a real SQLite write after Kids has been imported.
        db.exec(`CREATE TRIGGER refuse_import BEFORE INSERT ON ${table}
          BEGIN SELECT RAISE(ABORT, 'local import refused'); END`);
        const sync = new StateSync(here.state, channel, "laptop");

        expect(await sync.once()).toEqual({ pulled: 0, pushed: false, failed: "local import refused" });
        expect(puts).toHaveLength(0);
        expect(here.state.profiles()).toEqual(profiles);
        expect(here.state.snapshot(here.me)).toEqual(before);
        expect(here.state.kids()).toEqual([]);

        db.exec("DROP TRIGGER refuse_import");
        expect(await sync.once()).toEqual({ pulled: 6, pushed: true });
        expect(puts).toHaveLength(1);
        const sam = here.state.profiles().find((profile) => profile.name === "Sam")!;
        expect(here.state.snapshot(sam.id)).toMatchObject({
          progress: [{ setId: "01FILM", at: 900 }],
          watched: [{ setId: "01DONE" }],
          watchlist: ["01LATER"],
          collections: [{ id: collection.id, items: ["01FILM"] }],
        });
        expect(here.state.kids()).toEqual(["01KID"]);
      } finally {
        db.close();
      }
    },
  );
});

describe("two rounds asked for at once", () => {
  test("run one after the other, so a first send happens once", async () => {
    const { state, me } = machine();
    state.setProgress(me, "01A", 742, 1204);
    const { channel, puts } = fakeChannel();
    // Hold every listing until released, so the two rounds would overlap if
    // nothing kept them apart — the timer and a pushed update, say.
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));
    const slow: StateChannel = { list: async () => (await gate, channel.list()), put: channel.put };
    const sync = new StateSync(state, slow, "laptop");

    const first = sync.once();
    const second = sync.once();
    release();
    await Promise.all([first, second]);

    expect(puts.map((p) => p.messageId)).toEqual([null]);
  });
});
