import { useEffect, useRef } from "react";
import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { Token } from "../ui/Token";
import type { CeremonyHandle } from "./ceremony/ceremony-stage";
import "./ceremony/ceremony.css";

/**
 * The awards ceremony every game ends on: title above, the top three on a
 * podium as a WebGL band (three.js loads with this screen only; no HTML over
 * the canvas), everyone's points below.
 * `ranked`: `players` already come in finishing order (ties broken by the game), so rank by position.
 */
export function TvFinal({ players, conn, ranked: inOrder = false }: { players: PlayerInfo[]; conn: Connection; ranked?: boolean }) {
  const ranked = inOrder ? players : [...players].sort((a, b) => b.score - a.score);
  const rankOf = (p: PlayerInfo) => (inOrder ? ranked.indexOf(p) : ranked.filter((o) => o.score > p.score).length) + 1;
  const band = useRef<HTMLDivElement>(null);
  const top = ranked.slice(0, 3).map((p) => ({ name: p.name, color: p.color, avatar: p.avatar }));
  const topKey = JSON.stringify(top);

  useEffect(() => {
    if (top.length === 0) return;
    let handle: CeremonyHandle | null = null, gone = false;
    import("./ceremony/ceremony-stage")
      .then((m) => m.startCeremony(band.current!, top))
      .then((h) => (gone ? h.dispose() : (handle = h)))
      .catch((e) => console.error("awards ceremony", e)); // the title and the points still stand
    return () => {
      gone = true;
      handle?.dispose();
    };
    // `top` is rebuilt every render; its content (topKey) is what restarts the scene
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topKey]);

  return (
    <section className="ceremony">
      <header className="ceremony-head">
        <h1 className="ceremony-title">{ranked.length > 0 ? `${ranked[0].name} gewinnt!` : "Spiel beendet"}</h1>
        <button className="cta cta-tv ceremony-back" autoFocus onClick={() => conn.send({ t: "action", action: { t: "back_to_lobby" } })}>
          Zurück zur Lobby
        </button>
      </header>
      <div className="ceremony-band" ref={band} />
      <ol className="ceremony-points">
        {ranked.map((p) => (
          <li key={p.id} className={`ceremony-row${rankOf(p) > 3 ? " ceremony-rest" : ""}`}>
            <span className="ceremony-place">{rankOf(p)}.</span>
            <Token player={p} />
            <span className="ceremony-name">{p.name}</span>
            <span className="ceremony-score">{p.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
