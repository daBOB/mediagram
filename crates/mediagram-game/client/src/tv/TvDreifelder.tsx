import { useServerNow, type Connection } from "../net";
import type { DreifelderScene } from "../protocol/DreifelderScene";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";
import { placePicks } from "./dreifelder/dreifelder-fields";
import { TvFinal, byFinish } from "./TvFinal";
import "./dreifelder/dreifelder.css";

type Props = { scene: DreifelderScene; players: PlayerInfo[]; conn: Connection };

/**
 * Dreifelder on the TV: the question over three fields. At the reveal every
 * pawn stands in the field it picked, the right one lights up, and whoever is
 * out moves down to the row of players who are out.
 */
export function TvDreifelder({ scene, players, conn }: Props) {
  const now = useServerNow(conn);
  const { stage } = scene;
  if (stage.stage === "final") return <TvFinal players={byFinish(players, stage.order)} conn={conn} ranked />;

  const byId = new Map(players.map((p) => [p.id, p]));
  const token = (id: number, size: "sm" | "md" | "lg", className = "") => {
    const p = byId.get(id);
    return p ? (
      <span key={id} className={className} title={p.name}>
        <Token player={p} size={size} />
      </span>
    ) : null;
  };

  const q = stage.question;
  const reveal = stage.stage === "reveal" ? stage : null;
  const answered = new Set(stage.stage === "question" ? stage.answered : []);
  // Players still in when this question was asked: the alive and whoever this reveal puts out.
  const inRound = new Set([...scene.alive, ...(reveal?.eliminated ?? [])]);
  const placed = reveal ? placePicks(reveal.picks, inRound, q.options.length) : null;

  let phase = "reveal";
  let meter = 0;
  let clock = "";
  if (stage.stage === "question" && now < stage.opens_at) {
    phase = "intro";
    meter = 1;
    clock = `Gleich geht's los … ${Math.ceil((stage.opens_at - now) / 1000)}`;
  } else if (stage.stage === "question") {
    phase = "open";
    const left = Math.max(0, stage.closes_at - now);
    meter = left / (stage.closes_at - stage.opens_at);
    clock = `${Math.ceil(left / 1000)}`;
  } else if (reveal && placed) {
    const out = reveal.eliminated.map((id) => byId.get(id)?.name ?? "?");
    clock = out.length
      ? `Raus: ${out.join(", ")}`
      : placed.fields[reveal.correct].alive.length
        ? "Alle richtig – alle bleiben drin"
        : "Alle daneben – alle bleiben drin";
  }

  return (
    <section className={`tv-quiz df stage-${phase}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">{q.category}</span>
        <span className="tv-round">
          Runde {scene.round} von {scene.rounds} – {scene.alive.length} noch dabei
        </span>
      </header>

      <article className={q.image ? "qcard has-image" : "qcard"}>
        {q.image && <img className="qimg" src={q.image} alt="Bild zur Frage" />}
        <p className="qtext">{q.text}</p>
        <div className="qcard-foot">
          <div className="meter meter-card" aria-hidden="true">
            <div className="meter-fill" style={{ transform: `scaleX(${meter})` }} />
          </div>
          <span className="qclock">{clock}</span>
        </div>
      </article>

      <ol className="tv-answers df-fields">
        {q.options.map((label, i) => {
          const color = ANSWER_COLORS[i];
          const state = reveal ? (i === reveal.correct ? " is-correct" : " is-wrong") : "";
          const field = placed?.fields[i];
          return (
            <li key={i} className={`tv-answer df-field key-${color}${state}`}>
              <span className="df-field-head">
                <span className="df-field-number" aria-hidden="true">
                  {i + 1}
                </span>
                <span className="key-mark" aria-hidden="true">
                  {ANSWER_MARKS[color]}
                </span>
                <span className="tv-answer-label">{label}</span>
              </span>
              {field && (
                <span className="df-standing">
                  {field.alive.map((id) => token(id, "lg"))}
                  {field.ghosts.map((id) => token(id, "md", "df-ghost"))}
                </span>
              )}
            </li>
          );
        })}
        {placed && placed.unpicked.length > 0 && (
          <li className="df-nopick">
            <span>Kein Tipp</span>
            {placed.unpicked.map((id) => token(id, "sm"))}
          </li>
        )}
      </ol>

      <footer className="tv-table df-table">
        <div className="df-in">
          {scene.alive.map((id) => {
            const p = byId.get(id);
            return p ? (
              <div key={id} className={`tv-seat${answered.has(id) ? " is-locked" : ""}`}>
                <Token player={p} />
                <span className="tv-seat-name">{p.name}</span>
              </div>
            ) : null;
          })}
        </div>
        {scene.out.length > 0 && (
          <div className="df-out">
            <span className="df-out-label">Raus</span>
            {scene.out.map((o) => token(o.player, "sm", reveal?.eliminated.includes(o.player) ? "df-out-new" : ""))}
          </div>
        )}
      </footer>
    </section>
  );
}
