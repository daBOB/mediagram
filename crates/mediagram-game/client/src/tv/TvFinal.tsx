import type { Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { Token } from "../ui/Token";

export function TvFinal({ players, conn }: { players: PlayerInfo[]; conn: Connection }) {
  const ranked = [...players].sort((a, b) => b.score - a.score);
  const rankOf = (p: PlayerInfo) => ranked.filter((o) => o.score > p.score).length + 1;
  return (
    <section className="tv-final">
      <h1 className="tv-final-title">
        {ranked.length > 0 ? `${ranked[0].name} gewinnt` : "Spiel beendet"}
      </h1>
      <ol className="podium">
        {ranked.map((p) => (
          <li key={p.id} className={`podium-row rank-${Math.min(rankOf(p), 4)}`}>
            <span className="podium-rank">{rankOf(p)}.</span>
            <Token player={p} />
            <span className="podium-name">{p.name}</span>
            <span className="podium-score">{p.score}</span>
          </li>
        ))}
      </ol>
      <button className="cta cta-tv" autoFocus onClick={() => conn.send({ t: "action", action: { t: "back_to_lobby" } })}>
        Zurück zur Lobby
      </button>
    </section>
  );
}
