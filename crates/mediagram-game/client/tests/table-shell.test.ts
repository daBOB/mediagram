// Run with `bun test` in crates/mediagram-game/client.
import { expect, spyOn, test } from "bun:test";
import { CHEER_MS, TableShell, type Clock } from "../src/tv/table/table-shell";

/** A clock that moves only when told to. */
function fakeClock() {
  let now = 0, next = 0;
  const timers = new Map<number, { at: number; run: () => void }>();
  const clock: Clock & { advance(ms: number): void } = {
    set: (run, ms) => (timers.set(++next, { at: now + ms, run }), next),
    clear: (t) => void timers.delete(t as number),
    advance(ms) {
      now += ms;
      for (const [id, t] of [...timers]) if (t.at <= now) timers.delete(id) && t.run();
    },
  };
  return clock;
}

/** Stages that load only when the test says so, and remember being freed. */
function fakeStages() {
  const loads: { stage: { disposed: boolean; dispose(): void }; done(): void; fail(e: unknown): void }[] = [];
  const start = () =>
    new Promise<{ dispose(): void }>((ok, fail) => {
      const stage = { disposed: false, dispose: () => void (stage.disposed = true) };
      loads.push({ stage, done: () => ok(stage), fail });
    });
  return { loads, start };
}

const settle = () => new Promise((ok) => setTimeout(ok));
const container = {} as HTMLElement;

function table() {
  const clock = fakeClock(), stages = fakeStages();
  return { clock, stages, shell: new TableShell(stages.start, "test board", clock) };
}

test("the board shows its wait line until the stage is built", async () => {
  const { stages, shell } = table();
  shell.load(container);
  expect(shell.view().state).toBe("loading");
  stages.loads[0].done();
  await settle();
  expect(shell.view().state).toBe("ready");
});

test("a stage built after the board was left is freed at once", async () => {
  const { stages, shell } = table();
  const leave = shell.load(container);
  leave();
  stages.loads[0].done();
  await settle();
  expect(stages.loads[0].stage.disposed).toBe(true);
  expect(shell.view().state).toBe("loading");
});

test("a stage built after the ranking took over is freed at once", async () => {
  const { clock, stages, shell } = table();
  shell.load(container);
  shell.watch(true);
  clock.advance(CHEER_MS);
  stages.loads[0].done();
  await settle();
  expect(stages.loads[0].stage.disposed).toBe(true);
  expect(shell.view().cheered).toBe(true);
});

test("a stage that fails says so, unless the board was left by then", async () => {
  const quiet = spyOn(console, "error").mockImplementation(() => {});
  const { stages, shell } = table();
  shell.load(container)();
  stages.loads[0].fail(new Error("no WebGL"));
  await settle();
  expect(shell.view().state).toBe("loading");
  shell.load(container);
  stages.loads[1].fail(new Error("no WebGL"));
  await settle();
  expect(shell.view().state).toBe("failed");
  quiet.mockRestore();
});

test("OK opens the settings, but not a held OK, another key, or any key once the winner is cheered", () => {
  const { clock, shell } = table();
  shell.press("Enter", true);
  shell.press("ArrowDown", false);
  expect(shell.view().settings).toBe(false);
  shell.press("Enter", false);
  expect(shell.view().settings).toBe(true);
  shell.closeSettings();
  expect(shell.view().settings).toBe(false);
  shell.watch(true);
  clock.advance(CHEER_MS);
  shell.press("Enter", false);
  expect(shell.view().settings).toBe(false);
});

test("the winner is cheered for four seconds, then the stage stops and the settings close", async () => {
  const { clock, stages, shell } = table();
  shell.load(container);
  stages.loads[0].done();
  await settle();
  shell.press("Enter", false);
  shell.watch(true);
  clock.advance(CHEER_MS - 1);
  expect(shell.view().cheered).toBe(false);
  expect(stages.loads[0].stage.disposed).toBe(false);
  clock.advance(1);
  expect(shell.view()).toMatchObject({ cheered: true, settings: false });
  expect(stages.loads[0].stage.disposed).toBe(true);
});

test("a game that is over no longer before its cheer keeps the board", () => {
  const { clock, shell } = table();
  const callOff = shell.watch(true);
  callOff();
  shell.watch(false);
  clock.advance(CHEER_MS);
  expect(shell.view()).toMatchObject({ cheered: false, round: 0 });
});

test("a new game at the same table builds the board again", async () => {
  const { clock, stages, shell } = table();
  const leave = shell.load(container);
  stages.loads[0].done();
  await settle();
  shell.watch(true);
  clock.advance(CHEER_MS);
  shell.watch(false);
  expect(shell.view()).toMatchObject({ cheered: false, round: 1 });
  // what React does when the round changes: leave the old board, build the new one
  leave();
  shell.load(container);
  stages.loads[1].done();
  await settle();
  expect(shell.view().state).toBe("ready");
  expect(stages.loads[1].stage.disposed).toBe(false);
});
