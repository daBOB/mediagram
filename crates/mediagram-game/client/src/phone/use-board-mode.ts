import { useMemo, useRef, useState } from "react";
import type { Connection } from "../net";
import type { BoardScreen } from "../protocol/BoardScreen";
import type { ClientMsg } from "../protocol/ClientMsg";
import type { ServerMsg } from "../protocol/ServerMsg";
import { memory } from "./app-bridge";
import { NativeBoardFeed } from "./board-native";

const BOARD_KEY = "spieltisch.board";
const BOARD_TOKEN_KEY = "spieltisch.board-token";

/**
 * The page as the iPad's drawing board: it says hello as the board, drives the app's native board
 * and renders only the waits. Kept apart so PhoneApp only dispatches to it.
 */
export function useBoardMode(conn: Connection) {
  // a ref as well as state: a hello sent right after become/leave must already see the switch
  const on = useRef(memory.get(BOARD_KEY) === "1");
  const welcomed = useRef(false);
  const [asBoard, setAsBoard] = useState(on.current);
  const [screen, setScreen] = useState<BoardScreen | null>(null);
  const feed = useMemo(() => new NativeBoardFeed(() => conn.serverNow()), [conn]);

  const turn = (board: boolean) => {
    on.current = board;
    setAsBoard(board);
    memory.set(BOARD_KEY, board ? "1" : null);
  };

  const become = () => {
    turn(true);
    conn.rehello();
  };

  /** Back to playing; PhoneApp's hello takes over (a phone, or nothing until a name is entered). */
  const leave = () => {
    turn(false);
    // the box drops the board in the lobby, the only place the board can be left
    memory.set(BOARD_TOKEN_KEY, null);
    feed.stop();
    setScreen(null);
    conn.rehello();
  };

  const hello = (): ClientMsg | null => {
    if (!on.current) return null;
    welcomed.current = false;
    return { t: "hello", role: "board", name: null, token: memory.get(BOARD_TOKEN_KEY) };
  };

  /** True when board mode handled `msg`; everything else is PhoneApp's. */
  const onMessage = (msg: ServerMsg): boolean => {
    if (!on.current) return false;
    switch (msg.t) {
      case "welcome":
        welcomed.current = true;
        if (msg.token) memory.set(BOARD_TOKEN_KEY, msg.token);
        feed.welcome();
        return true;
      case "board":
        feed.board(msg.screen);
        setScreen(msg.screen);
        return true;
      case "draw":
        feed.draw(msg);
        return true;
      case "error":
        // the box refused the board (another device holds it): back to playing, and PhoneApp toasts why
        if (!welcomed.current) leave();
        return false;
      default:
        return false;
    }
  };

  return { asBoard, screen, hello, onMessage, become, leave };
}
