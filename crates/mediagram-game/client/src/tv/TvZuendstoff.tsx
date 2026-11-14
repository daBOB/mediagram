import { useServerNow, type Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { ZuendstoffScene } from "../protocol/ZuendstoffScene";
import type { ZuendstoffStage } from "../protocol/ZuendstoffStage";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";
import { TvFinal, byFinish } from "./TvFinal";
import "./zuendstoff/zuendstoff.css";

type Props = { scene: ZuendstoffScene; players: PlayerInfo[]; conn: Connection };

/** A spiky star for the explosion, in a 200 by 200 box. */
const burst = (outer: number, inner: number) =>
  Array.from({ length: 24 }, (_, i) => {
    const r = i % 2 ? inner : outer;
    const a = (i / 24) * Math.PI * 2;
    return `${(100 + r * Math.cos(a)).toFixed(1)},${(100 + r * Math.sin(a)).toFixed(1)}`;
  }).join(" ");

function Bomb({ boom }: { boom: boolean }) {
  if (boom) {
    return (
      <svg className="zs-burst" viewBox="0 0 200 200" aria-hidden="true">
        <polygon points={burst(98, 58)} fill="#f2862e" />
        <polygon points={burst(72, 44)} fill="#f6c731" />
        <text x="100" y="112" textAnchor="middle" className="zs-boom-word">
          BUMM!
        </text>
      </svg>
    );
  }
  return (
    <svg className="zs-bomb" viewBox="0 0 120 120" aria-hidden="true">
      <path d="M84 26 q12 -18 26 -10" fill="none" stroke="#c8a96b" strokeWidth="4" strokeLinecap="round" />
      <circle className="zs-spark" cx="110" cy="16" r="7" fill="#f6c731" />
      <rect x="64" y="24" width="22" height="16" rx="3" transform="rotate(35 75 32)" fill="#3a3a3c" />
      <circle cx="52" cy="72" r="40" fill="#1d1d1f" />
      <circle cx="38" cy="58" r="10" fill="#fff" opacity="0.16" />
    </svg>
  );
}

/** The line under the bomb: who holds it and what just happened. */
function headline(stage: Exclude<ZuendstoffStage, { stage: "final" }>, nameOf: (id: number) => string, now: number): string {
  if (stage.stage === "boom") return `${nameOf(stage.victim)} ${stage.out ? "ist raus!" : "verliert ein Leben"}`;
  if (stage.stage === "throw") return `Richtig! ${nameOf(stage.holder)} wirft …`;
  if (stage.wrong && now < stage.opens_at) return `Falsch! ${nameOf(stage.holder)} muss kurz warten`;
  if (stage.thrown_by !== null) return `${nameOf(stage.thrown_by)} wirft zu ${nameOf(stage.holder)}!`;
  return `${nameOf(stage.holder)} hat die Bombe`;
}

/**
 * Zündstoff on the TV: the bomb sits with whoever holds it and their
 * question is shown big; the fuse stays hidden. Every player's lives run
 * along the bottom, and the bomb's holder is ringed there too.
 */
export function TvZuendstoff({ scene, players, conn }: Props) {
  const now = useServerNow(conn);
  const { stage } = scene;
  if (stage.stage === "final") return <TvFinal players={byFinish(players, stage.order)} conn={conn} ranked />;

  const byId = new Map(players.map((p) => [p.id, p]));
  const nameOf = (id: number) => byId.get(id)?.name ?? "Jemand";
  const holder = stage.stage === "boom" ? stage.victim : stage.holder;
  const holderInfo = byId.get(holder);
  const q = stage.stage === "answer" ? stage.question : null;
  const live = stage.stage === "answer" && now >= stage.opens_at;

  return (
    <section className={`tv-quiz zs zs-${stage.stage}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">Zündstoff</span>
        <span className="tv-round">Runde {scene.round}</span>
      </header>

      <div className="zs-stage">
        <div className="zs-holder">
          <Bomb boom={stage.stage === "boom"} />
          {holderInfo && <Token player={holderInfo} size="lg" />}
          <p className="zs-headline">{headline(stage, nameOf, now)}</p>
        </div>
        {q && (
          <article className="qcard zs-question">
            <p className="qtext">{q.text}</p>
            <ol className={`tv-answers zs-answers${live ? "" : " is-waiting"}`}>
              {q.options.map((label, i) => {
                const color = ANSWER_COLORS[i];
                return (
                  <li key={i} className={`tv-answer key-${color}`}>
                    <span className="key-mark" aria-hidden="true">
                      {ANSWER_MARKS[color]}
                    </span>
                    <span className="tv-answer-label">{label}</span>
                  </li>
                );
              })}
            </ol>
          </article>
        )}
      </div>

      <footer className="tv-table zs-table">
        {scene.seats.map((s) => {
          const p = byId.get(s.player);
          if (!p) return null;
          return (
            <div key={s.player} className={`tv-seat${s.lives === 0 ? " is-out" : ""}${s.player === holder ? " has-bomb" : ""}`}>
              <Token player={p} />
              <span className="tv-seat-name">{p.name}</span>
              <span className="zs-lives" aria-label={`${s.lives} Leben`}>
                {s.lives === 0 ? "raus" : "♥".repeat(s.lives)}
              </span>
            </div>
          );
        })}
      </footer>
    </section>
  );
}
