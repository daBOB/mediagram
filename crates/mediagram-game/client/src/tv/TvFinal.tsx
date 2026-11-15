import { memo, useEffect, useRef, useState } from "react";
import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { Ranking } from "../protocol/Ranking";
import { Token } from "../ui/Token";
import type { CeremonyHandle } from "./ceremony/ceremony-stage";
import "./ceremony/ceremony.css";

/**
 * The awards ceremony every game ends on: title above, the top three on a
 * podium as a WebGL band (three.js loads with this screen only; no HTML over
 * the canvas), everyone's points below, in the order and places the room's `ranking` gives.
 */
export const TvFinal = memo(function TvFinal({
  ranking,
  players,
  conn,
  jingle = true,
  title,
}: {
  ranking: Ranking;
  players: PlayerInfo[];
  conn: Connection;
  /** false: the game already played the winner jingle at the win. */
  jingle?: boolean;
  /** Overrides "<first player> gewinnt!", for a game a side wins together. */
  title?: string;
}) {
  const byId = new Map(players.map((p) => [p.id, p]));
  const ranked = ranking.places.flatMap(({ player, place }) => {
    const p = byId.get(player);
    return p ? [{ ...p, place }] : [];
  });
  const band = useRef<HTMLDivElement>(null);
  // frozen at mount: a late joiner or a rename must not rebuild the 3D scene (the strip still updates)
  const [top] = useState(() => ranked.slice(0, 3).map((p) => ({ name: p.name, color: p.color, avatar: p.avatar, place: p.place })));

  useEffect(() => {
    if (top.length === 0) return;
    let handle: CeremonyHandle | null = null, gone = false;
    import("./ceremony/ceremony-stage")
      .then((m) => (band.current && !gone ? m.startCeremony(band.current, top, () => gone, jingle) : null))
      .then((h) => h && (gone ? h.dispose() : (handle = h)))
      .catch((e) => console.error("awards ceremony", e)); // the title and the points still stand
    return () => {
      gone = true;
      handle?.dispose();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <section className="ceremony">
      <header className="ceremony-head">
        <h1 className="ceremony-title">{title ?? (ranked.length > 0 ? `${ranked[0].name} gewinnt!` : "Spiel beendet")}</h1>
        <button className="cta cta-tv ceremony-back" autoFocus onClick={() => conn.send({ t: "action", action: { t: "back_to_lobby" } })}>
          Zurück zur Lobby
        </button>
      </header>
      <div className="ceremony-band" ref={band} />
      <ol className="ceremony-points">
        {ranked.map((p) => (
          <li key={p.id} className={`ceremony-row${p.place > 3 ? " ceremony-rest" : ""}`}>
            <span className="ceremony-place">{p.place}.</span>
            <Token player={p} />
            <span className="ceremony-name">{p.name}</span>
            <span className="ceremony-score">{p.score}</span>
          </li>
        ))}
      </ol>
    </section>
  );
});
