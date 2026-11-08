import { useServerNow, type Connection } from "../../net";
import type { KopfnussStage } from "../../protocol/KopfnussStage";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { Token } from "../../ui/Token";
import { SUMMIT, TRAIL, VIEW, pointAt, spots } from "./mountain-trail";

type ClimbStage = Extract<KopfnussStage, { stage: "climb" }>;
type FactStage = Extract<KopfnussStage, { stage: "fact" }>;

/** Gipfelsturm: a mountain with a switchback trail; every player stands on their step. */
export function KopfnussClimb({ stage, players, conn }: { stage: ClimbStage; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const left = Math.max(0, stage.ends_at - now);
  const byId = new Map(players.map((p) => [p.id, p]));
  const at = spots(stage.climbers, stage.steps);
  const stations = Array.from({ length: stage.steps }, (_, i) => ({ step: i, ...pointAt(i / stage.steps) }));

  return (
    <section className="kn-climb">
      <header className="tv-quiz-head">
        <span className="tv-cat">Gipfelsturm</span>
        <span className="tv-round">Antwortet auf euren Handys! · {Math.ceil(left / 1000)} s</span>
      </header>
      <div className="kn-mountain">
        <div className="kn-stage">
          <Mountain stations={stations} />
          {stage.climbers.map((c) => {
            const p = byId.get(c.player);
            const spot = at.get(c.player);
            if (!p || !spot) return null;
            return (
              <div
                key={c.player}
                className="kn-climber"
                style={{ left: `${(spot.x / VIEW.width) * 100}%`, top: `${(spot.y / VIEW.height) * 100}%` }}
              >
                <Token player={p} />
                <span className="kn-climber-name">{p.name}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/** The drawing: a back range, the peak with its shaded side and snowcap, the trail and a summit flag. */
function Mountain({ stations }: { stations: { step: number; x: number; y: number }[] }) {
  return (
    <svg className="kn-mountain-art" viewBox={`0 0 ${VIEW.width} ${VIEW.height}`} aria-hidden="true">
      {/* The range and the ground run past the drawing to the screen's edges, where the page clips them. */}
      <polygon className="kn-range" points="-600,880 -600,600 -260,700 0,600 240,520 420,660 600,500 820,620 1050,470 1260,600 1420,520 1600,620 1860,520 2200,660 2200,880" />
      <polygon className="kn-rock" points="100,880 520,420 640,470 800,100 980,420 1090,380 1500,880" />
      <polygon className="kn-shade" points="800,100 980,420 1090,380 1500,880 800,880" />
      <polygon className="kn-snow" points="800,100 884,250 850,232 822,262 795,236 768,258 735,250" />
      <rect className="kn-ground" x="-600" y="860" width={VIEW.width + 1200} height="300" />
      <polyline className="kn-trail" points={TRAIL.map((p) => `${p.x},${p.y}`).join(" ")} />
      {stations.map((s) => (
        <g key={s.step} className="kn-station">
          <circle cx={s.x} cy={s.y} r="17" />
          <text x={s.x} y={s.y + 7}>
            {s.step}
          </text>
        </g>
      ))}
      <line className="kn-pole" x1={SUMMIT.x} y1={SUMMIT.y} x2={SUMMIT.x} y2={SUMMIT.y - 92} />
      <polygon className="kn-flag" points={`${SUMMIT.x},${SUMMIT.y - 92} ${SUMMIT.x + 70},${SUMMIT.y - 74} ${SUMMIT.x},${SUMMIT.y - 56}`} />
    </svg>
  );
}

/** The winner on top, and the fact they take home. */
export function KopfnussFact({ stage, players }: { stage: FactStage; players: PlayerInfo[] }) {
  const winner = players.find((p) => p.id === stage.winner);
  return (
    <section className="kn-fact">
      {winner && <Token player={winner} size="lg" />}
      <h1 className="kn-fact-title">{winner ? `${winner.name} erreicht den Gipfel!` : "Gipfel erreicht!"}</h1>
      <article className="qcard">
        <p className="kn-fact-kicker">Wusstest du schon?</p>
        <p className="qtext">{stage.text}</p>
      </article>
    </section>
  );
}
