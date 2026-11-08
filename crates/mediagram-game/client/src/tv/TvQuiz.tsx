import type { ReactNode } from "react";
import { useServerNow, type Connection } from "../net";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import type { QuizScene } from "../protocol/QuizScene";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";
import { TvFinal } from "./TvFinal";

/**
 * The buzzer quiz on the TV: question card, the four answers and the table.
 * Kopfnuss shows its questions with it too, naming its round in `round` and
 * putting each phone's tricks beside its seat with `seatExtra`.
 */
export function TvQuiz({
  scene,
  players,
  conn,
  round,
  seatExtra,
}: {
  scene: QuizScene;
  players: PlayerInfo[];
  conn: Connection;
  round?: string;
  seatExtra?: (player: number) => ReactNode;
}) {
  const now = useServerNow(conn);
  const { stage } = scene;

  if (stage.stage === "final") return <TvFinal players={players} conn={conn} />;

  const q = stage.question;
  const reveal = stage.stage === "reveal" ? stage : null;
  const answered = stage.stage === "open" ? new Set(stage.answered) : new Set<number>();
  const byId = new Map(players.map((p) => [p.id, p]));

  let meter = 1;
  let clock = "";
  if (stage.stage === "intro") {
    clock = `Gleich geht's los … ${Math.ceil((stage.opens_at - now) / 1000)}`;
  } else if (stage.stage === "open") {
    const left = Math.max(0, stage.closes_at - now);
    meter = left / (stage.closes_at - stage.opens_at);
    clock = `${Math.ceil(left / 1000)}`;
  } else {
    meter = 0;
  }

  return (
    <section className={`tv-quiz stage-${stage.stage}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">{q.category}</span>
        <span className="tv-round">
          {round ?? `Frage ${scene.round} von ${scene.rounds}`}
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

      <ol className="tv-answers">
        {q.options.map((label, i) => {
          const color = ANSWER_COLORS[i];
          const state = reveal ? (i === reveal.correct ? " is-correct" : " is-wrong") : "";
          const pickers = reveal ? reveal.results.filter((r) => r.choice === i) : [];
          return (
            <li key={i} className={`tv-answer key-${color}${state}`}>
              <span className="key-mark" aria-hidden="true">
                {ANSWER_MARKS[color]}
              </span>
              <span className="tv-answer-label">{label}</span>
              {pickers.length > 0 && (
                <span className="tv-pickers">
                  {pickers.map((r) => {
                    const p = byId.get(r.player);
                    return p ? <Token key={r.player} player={p} size="sm" /> : null;
                  })}
                </span>
              )}
            </li>
          );
        })}
      </ol>

      <footer className="tv-table">
        {players.map((p) => {
          const result = reveal?.results.find((r) => r.player === p.id);
          return (
            <div key={p.id} className={`tv-seat${answered.has(p.id) ? " is-locked" : ""}`}>
              <Token player={p} />
              <span className="tv-seat-name">{p.name}</span>
              {seatExtra?.(p.id)}
              <span className="tv-seat-score">
                {result && result.points > 0 ? `+${result.points}` : p.score}
              </span>
            </div>
          );
        })}
      </footer>
    </section>
  );
}
