import { useEffect } from "react";
import type { BoardAct } from "../protocol/BoardAct";
import type { BoardScreen } from "../protocol/BoardScreen";
import { listenToBoard } from "./app-bridge";

type Props = { screen: BoardScreen | null; forward: (act: BoardAct) => void; onLeave: () => void };

/** The page under the native board: it passes the board's acts to the box and draws the waits itself. */
export function BoardPage({ screen, forward, onLeave }: Props) {
  useEffect(() => listenToBoard(forward), [forward]);

  const wait = screen?.kind === "wait" ? screen : null;
  return (
    <section className="screen">
      <h1 className="screen-title">{wait ? wait.title : "Zeichenbrett"}</h1>
      {wait?.text && <p className="screen-text">{wait.text}</p>}
      {wait?.can_leave && (
        <button className="cta" onClick={onLeave}>
          Doch mitspielen
        </button>
      )}
    </section>
  );
}
