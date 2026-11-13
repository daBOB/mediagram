// What the page hands the iPad app's native drawing board, and when.

import { InkLog } from "../ink/ink-log";
import type { BoardAct } from "../protocol/BoardAct";
import type { BoardPlayer } from "../protocol/BoardPlayer";
import type { BoardScreen } from "../protocol/BoardScreen";
import type { ClientMsg } from "../protocol/ClientMsg";
import type { Clock } from "../protocol/Clock";
import type { DrawOp } from "../protocol/DrawOp";
import { hideBoard, showBoard } from "./app-bridge";

/** The clock on the app's own wall time: it runs until `ends_at_epoch_ms`, or stands at `left_ms` while that is null. */
export type NativeClock = { ends_at_epoch_ms: number | null; left_ms: number; total_ms: number };
export type NativeScreen =
  | { kind: "handover"; turn: number; drawer: BoardPlayer }
  | { kind: "drawing"; turn: number; drawer: BoardPlayer; word: string; clock: NativeClock }
  | {
      kind: "picking";
      turn: number;
      drawer: BoardPlayer;
      word: string;
      clock: NativeClock;
      guessers: BoardPlayer[];
      can_go_back: boolean;
    };
/** `ops`: the whole drawing of `screen.turn` as the box holds it. `resync`: replace the board's own drawing with it. */
export type NativeBoardShow = { screen: NativeScreen; ops: DrawOp[]; resync: boolean };

function nativeClock(clock: Clock, serverNow: number, dateNow: number): NativeClock {
  const { ends_at, left_ms, total_ms } = clock;
  const ends_at_epoch_ms = ends_at === null ? null : Math.round(dateNow + (ends_at - serverNow));
  return { ends_at_epoch_ms, left_ms, total_ms };
}

/** What the app shows for `screen`, or null when the page draws it (wait). */
export function nativeShow(
  screen: BoardScreen,
  ops: DrawOp[],
  resync: boolean,
  serverNow: number,
  dateNow: number,
): NativeBoardShow | null {
  if (screen.kind === "wait") return null;
  const native: NativeScreen =
    screen.kind === "handover" ? screen : { ...screen, clock: nativeClock(screen.clock, serverNow, dateNow) };
  return { screen: native, ops, resync };
}

/**
 * Feeds the native board from what the box sends. A show goes out only when the screen's JSON changes;
 * a wait hides the board, and the next show after a hide resyncs. Draws alone never show: they echo the board's own strokes, which it already has,
 * and re-sending the whole drawing per op would grow with its square. After each Welcome the box sends the
 * drawing from 0 and then the screen, so the show for that screen carries `resync` even when it looks the
 * same as before: strokes drawn while the socket was down never reached the box, and the board must match
 * the TV again. Until that show goes out the board's acts are dropped: the box would apply them after
 * building the resync show, so the TV would keep strokes the board throws away.
 */
export class NativeBoardFeed {
  readonly ink = new InkLog();
  // the JSON of the screen last shown or hidden; null until the first screen after a Welcome
  private shown: string | null = null;
  private resync = true;

  constructor(
    private serverNow: () => number,
    private send: (msg: ClientMsg) => void,
  ) {}

  /** On the hello and again on the Welcome: nothing for the board arrives between the two. */
  welcome() {
    this.resync = true;
    this.shown = null;
  }

  board(screen: BoardScreen) {
    if (JSON.stringify(screen) !== this.shown) this.push(screen);
  }

  draw(msg: { turn: number; from: number; ops: DrawOp[] }) {
    this.ink.apply(msg);
  }

  forward = (act: BoardAct) => {
    if (!this.resync) this.send({ t: "action", action: { t: "board", act } });
  };

  /** Board mode ends: the native board goes away. */
  stop() {
    hideBoard();
    this.welcome();
  }

  private push(screen: BoardScreen) {
    this.shown = JSON.stringify(screen);
    const show = nativeShow(screen, this.ink.ops, this.resync, this.serverNow(), Date.now());
    // after a hide the board may come back to a new game's turn of the same number: its old ink must go
    this.resync = !show;
    if (show) showBoard(show);
    else hideBoard();
  }
}
