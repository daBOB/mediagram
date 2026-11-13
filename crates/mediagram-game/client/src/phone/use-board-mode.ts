import { useMemo, useReducer } from "react";
import type { Connection } from "../net";
import type { BoardScreen } from "../protocol/BoardScreen";
import type { ClientMsg } from "../protocol/ClientMsg";
import type { ServerMsg } from "../protocol/ServerMsg";
import { boardCapable, memory } from "./app-bridge";
import { NativeBoardFeed } from "./board-native";

// "board" or "player" once someone chose; unset, the app on an iPad is the board
const CHOICE_KEY = "spieltisch.board";
const BOARD_TOKEN_KEY = "spieltisch.board-token";

type Conn = Pick<Connection, "serverNow" | "send" | "rehello">;

/**
 * The page as the iPad's drawing board: it says hello as the board, drives the app's native board
 * and renders only the waits. The app on an iPad is the board by itself until someone picks
 * „Doch mitspielen“; browsers and phones never are. Kept apart so PhoneApp only dispatches to it.
 */
export class BoardMode {
  asBoard = boardCapable() && memory.get(CHOICE_KEY) !== "player";
  screen: BoardScreen | null = null;
  readonly forward: NativeBoardFeed["forward"];
  private feed: NativeBoardFeed;
  private welcomed = false;

  constructor(
    private conn: Conn,
    private changed: () => void,
  ) {
    this.feed = new NativeBoardFeed(() => conn.serverNow(), (msg) => conn.send(msg));
    this.forward = this.feed.forward;
  }

  /** „Als Zeichenbrett verwenden“, remembered across restarts. */
  become = () => {
    memory.set(CHOICE_KEY, "board");
    this.asBoard = true;
    this.changed();
    this.conn.rehello();
  };

  /** „Doch mitspielen“, remembered across restarts: PhoneApp's hello takes over. */
  leave = () => {
    memory.set(CHOICE_KEY, "player");
    this.stop();
    // with no name yet PhoneApp says no hello, and the box would keep this socket as the board
    if (!this.conn.rehello()) this.conn.send({ t: "hello", role: "phone", name: null, token: null });
  };

  hello(): ClientMsg | null {
    if (!this.asBoard) return null;
    this.welcomed = false;
    this.feed.welcome();
    return { t: "hello", role: "board", name: null, token: memory.get(BOARD_TOKEN_KEY) };
  }

  /** True when board mode handled `msg`; everything else is PhoneApp's. */
  onMessage(msg: ServerMsg): boolean {
    if (!this.asBoard) return false;
    switch (msg.t) {
      case "welcome":
        this.welcomed = true;
        if (msg.token) memory.set(BOARD_TOKEN_KEY, msg.token);
        this.feed.welcome();
        return true;
      case "board":
        this.feed.board(msg.screen);
        this.screen = msg.screen;
        this.changed();
        return true;
      case "draw":
        this.feed.draw(msg);
        return true;
      case "error":
        // the box refused the board (another device holds it): play for now, keep the choice; PhoneApp toasts why
        if (!this.welcomed) {
          this.stop();
          this.conn.rehello();
        }
        return false;
      default:
        return false;
    }
  }

  private stop() {
    this.asBoard = false;
    // a board token outlives neither a lobby nor a refusal
    memory.set(BOARD_TOKEN_KEY, null);
    this.feed.stop();
    this.screen = null;
    this.changed();
  }
}

export function useBoardMode(conn: Connection): BoardMode {
  const [, changed] = useReducer((n: number) => n + 1, 0);
  return useMemo(() => new BoardMode(conn, changed), [conn]);
}
