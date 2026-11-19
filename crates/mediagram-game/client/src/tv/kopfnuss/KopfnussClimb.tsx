import { useState, type CSSProperties } from "react";
import { useServerNow, type Connection } from "../../net";
import type { KopfnussStage } from "../../protocol/KopfnussStage";
import type { PlayerInfo } from "../../protocol/PlayerInfo";
import { Token, avatarFigureUrl } from "../../ui/Token";
import { TRAIL, VIEW, pointAt, spots } from "./mountain-trail";

type ClimbStage = Extract<KopfnussStage, { stage: "climb" }>;
type FactStage = Extract<KopfnussStage, { stage: "fact" }>;

const TIER_COLORS = [
  "#7650c8", "#25b8bd", "#59bd71", "#a9cf45", "#f5d04c",
  "#f3aa43", "#ed7a4d", "#e65772", "#c3439a", "#d93c58",
];

const TIERS = TIER_COLORS.map((color, index) => {
  const step = index + 1;
  const width = 1320 - index * 112;
  return { step, color, width, y: TRAIL[step].y, x: VIEW.width / 2 - width / 2 };
});

const STAR_POINTS = Array.from({ length: 10 }, (_, i) => {
  const angle = -Math.PI / 2 + (i * Math.PI) / 5;
  const radius = i % 2 === 0 ? 78 : 38;
  return `${800 + Math.cos(angle) * radius},${118 + Math.sin(angle) * radius}`;
}).join(" ");

function Cloud({ x, y, scale = 1, dim = false }: { x: number; y: number; scale?: number; dim?: boolean }) {
  return (
    <g className={`kn-cloud${dim ? " kn-cloud-dim" : ""}`} transform={`translate(${x} ${y}) scale(${scale})`}>
      <ellipse cx="0" cy="20" rx="118" ry="44" />
      <circle cx="-58" cy="-4" r="52" />
      <circle cx="4" cy="-34" r="76" />
      <circle cx="72" cy="-2" r="54" />
    </g>
  );
}

function TierPattern({ x, y, kind }: { x: number; y: number; kind: number }) {
  if (kind === 0) return <circle className="kn-tier-pattern" cx={x} cy={y} r="16" />;
  if (kind === 1) {
    return <path className="kn-tier-pattern" d={`M ${x} ${y - 19} L ${x + 20} ${y + 16} L ${x - 20} ${y + 16} Z`} />;
  }
  return <path className="kn-tier-pattern" d={`M ${x - 18} ${y - 12} h 36 M ${x - 18} ${y} h 36 M ${x - 18} ${y + 12} h 36`} />;
}

/** Full-body figure, with the existing head token as its missing-image fallback. */
function ClimberFigure({ player }: { player: PlayerInfo }) {
  const [failed, setFailed] = useState<string | null>(null);
  const figure = player.avatar && failed !== player.avatar ? player.avatar : null;
  return figure ? (
    <img
      className="kn-climber-figure"
      src={avatarFigureUrl(figure)}
      alt=""
      draggable={false}
      onError={() => setFailed(figure)}
    />
  ) : (
    <Token player={player} size="lg" />
  );
}

/** Gipfelsturm: a candy pyramid; every player stands on their step. */
export function KopfnussClimb({ stage, players, conn }: { stage: ClimbStage; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const left = Math.max(0, stage.ends_at - now);
  const byId = new Map(players.map((p) => [p.id, p]));
  const at = spots(stage.climbers, stage.steps);
  const stations = Array.from({ length: stage.steps + 1 }, (_, i) => ({ step: i, ...pointAt(i / stage.steps) }));

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
                style={{
                  left: `${(spot.x / VIEW.width) * 100}%`,
                  top: `${(spot.y / VIEW.height) * 100}%`,
                  zIndex: spot.y,
                  "--player": p.color,
                } as CSSProperties}
              >
                <ClimberFigure player={p} />
                <span className="kn-climber-name" title={p.name}>{p.name}</span>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/** Ten stacked candy tiers under the rainbow, with a star at the summit. */
function Mountain({ stations }: { stations: { step: number; x: number; y: number }[] }) {
  return (
    <svg className="kn-mountain-art" viewBox={`0 0 ${VIEW.width} ${VIEW.height}`} aria-hidden="true">
      <rect className="kn-pyramid-sky" width={VIEW.width} height={VIEW.height} />
      <g className="kn-rainbow">
        <circle cx="800" cy="820" r="730" stroke="#b982dc" />
        <circle cx="800" cy="820" r="650" stroke="#f08bb8" />
        <circle cx="800" cy="820" r="570" stroke="#ffe083" />
        <circle cx="800" cy="820" r="490" stroke="#96e3c1" />
      </g>
      <Cloud x={1110} y={176} scale={0.62} dim />
      <Cloud x={218} y={685} scale={1.22} dim />
      <Cloud x={1390} y={650} scale={1.12} dim />
      {TIERS.map((tier) => {
        const right = tier.x + tier.width;
        const symbols = Math.max(2, Math.floor(tier.width / 220));
        return (
          <g className="kn-tier" key={tier.step}>
            <path className="kn-tier-top" d={`M ${tier.x + 28} ${tier.y - 24} H ${right - 28} L ${right} ${tier.y} H ${tier.x} Z`} />
            <path className="kn-tier-face" fill={tier.color} d={`M ${tier.x} ${tier.y} H ${right} V ${tier.y + 60} H ${tier.x} Z`} />
            <path className="kn-tier-side" d={`M ${right - 54} ${tier.y} H ${right} V ${tier.y + 60} H ${right - 54} Z`} />
            {Array.from({ length: symbols }, (_, i) => (
              <TierPattern key={i} x={800 + (i - (symbols - 1) / 2) * 196} y={tier.y + 32} kind={(tier.step + i) % 3} />
            ))}
          </g>
        );
      })}
      <Cloud x={92} y={832} scale={1.1} />
      <Cloud x={1510} y={842} scale={1.18} />
      <circle className="kn-summit-halo" cx="800" cy="118" r="108" />
      <polygon className="kn-summit-star" points={STAR_POINTS} />
      {stations.map((s) => (
        <g key={s.step} transform={`translate(${s.x} ${s.y})`}>
          <circle className="kn-station-disc" r={s.step === 0 || s.step === 10 ? 21 : 17} />
          <text className="kn-station-no" y="6" textAnchor="middle">{s.step}</text>
        </g>
      ))}
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
