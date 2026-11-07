import { useEffect, useRef, useState } from "react";
import { useServerNow, type Connection } from "../net";
import type { BauherrScene } from "../protocol/BauherrScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { Token } from "../ui/Token";
import { H, W, drawBauherr, taler } from "./bauherr/bauherr-draw";
import { describeTurn } from "./bauherr/bauherr-status";
import { TvFinal } from "./TvFinal";

/** How long the finished board stays up before the awards. */
const CHEER_MS = 4000;

/**
 * A plain top-down board: a 2D canvas for the ring, hotels and pawns, and a
 * side panel with the round, the turn in words and everyone's money. It
 * follows a whole game; the 3D board will replace it.
 */
export function TvBauherr({ scene, players, conn }: { scene: BauherrScene; players: PlayerInfo[]; conn: Connection }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const now = useServerNow(conn);
  const [cheered, setCheered] = useState(false);
  const over = scene.turn.stage === "final";

  useEffect(() => {
    const g = canvas.current?.getContext("2d");
    if (g && !cheered) drawBauherr(g, scene, players, now);
  }, [scene, players, now, cheered]);

  useEffect(() => {
    if (!over) {
      setCheered(false);
      return;
    }
    const t = window.setTimeout(() => setCheered(true), CHEER_MS);
    return () => clearTimeout(t);
  }, [over]);

  if (cheered && scene.turn.stage === "final") {
    // the game's own ranking: unseated joiners are not in it, and the broke rank by who lasted longer
    const order = new Map(scene.turn.ranking.map((id, i) => [id, i]));
    const ranked = players.filter((p) => order.has(p.id)).sort((a, b) => order.get(a.id)! - order.get(b.id)!);
    return <TvFinal players={ranked} conn={conn} ranked />;
  }

  const { text, die } = describeTurn(scene, players, now);
  const onTurn = "player" in scene.turn ? scene.turn.player : null;
  return (
    <section className="tv-bauherr">
      <canvas ref={canvas} className="tv-bauherr-board" width={W} height={H} />
      <aside className="tv-bauherr-side">
        <h1 className="tv-bauherr-round">
          Runde {scene.round} von {scene.rounds}
        </h1>
        <ul className="tv-bauherr-seats">
          {scene.seats.map((s) => {
            const p = players.find((q) => q.id === s.player);
            return (
              <li key={s.player} className={`tv-bauherr-seat${s.player === onTurn ? " is-on" : ""}${s.out ? " is-out" : ""}`}>
                {p && <Token player={p} size="sm" />}
                <span className="tv-bauherr-name">{p?.name}</span>
                <span className="tv-bauherr-cash">{s.out ? "raus" : taler(s.cash)}</span>
                <span className="tv-bauherr-worth">Vermögen {taler(s.worth)}</span>
              </li>
            );
          })}
        </ul>
        <p className="tv-bauherr-text">{text}</p>
        {die && <p className="tv-bauherr-die">{die}</p>}
      </aside>
    </section>
  );
}
