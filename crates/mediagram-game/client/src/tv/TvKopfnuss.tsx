import { useServerNow, type Connection } from "../net";
import type { KopfnussScene } from "../protocol/KopfnussScene";
import type { KopfnussStage } from "../protocol/KopfnussStage";
import type { PlayerInfo } from "../protocol/PlayerInfo";
import { ANSWER_COLORS, ANSWER_MARKS } from "../ui/answers";
import { Token } from "../ui/Token";
import { TRICK_NAMES } from "../ui/tricks";
import { KopfnussClimb, KopfnussFact } from "./kopfnuss/KopfnussClimb";
import { useKopfnussSound } from "./kopfnuss/kopfnuss-sound";
import { soundsDue } from "./kopfnuss/kopfnuss-sound-cues";
import { TvFinal, byFinish } from "./TvFinal";
import { TvQuiz } from "./TvQuiz";
import "./kopfnuss/kopfnuss.css";

type Props = { scene: KopfnussScene; players: PlayerInfo[]; conn: Connection };

/** Kopfnuss on the TV: the vote, the tricks landing, the questions, the climb. */
export function TvKopfnuss({ scene, players, conn }: Props) {
  useKopfnussSound(scene, conn, soundsDue);
  const { stage } = scene;
  const round = `Runde ${scene.round} von ${scene.rounds}`;
  switch (stage.stage) {
    case "pick":
    case "resolve":
      return <Vote stage={stage} round={round} players={players} conn={conn} />;
    case "question": {
      const loads = new Map(stage.tricks.map((t) => [t.player, t.tricks]));
      return (
        <TvQuiz
          scene={stage.quiz}
          players={players}
          conn={conn}
          round={`${round} · Frage ${stage.quiz.round} von ${stage.quiz.rounds}`}
          seatExtra={(id) =>
            loads.get(id)?.length ? (
              <span className="kn-seat-tricks">{loads.get(id)!.map((t) => TRICK_NAMES[t]).join(" · ")}</span>
            ) : null
          }
        />
      );
    }
    case "climb":
      return <KopfnussClimb stage={stage} players={players} conn={conn} />;
    case "fact":
      return <KopfnussFact stage={stage} players={players} />;
    case "final":
      return <TvFinal players={byFinish(players, stage.order)} conn={conn} ranked />;
  }
}

type VoteStage = Extract<KopfnussStage, { stage: "pick" } | { stage: "resolve" }>;

/** The category vote: open while phones pick, then the winner and the tricks. */
function Vote({ stage, round, players, conn }: { stage: VoteStage; round: string; players: PlayerInfo[]; conn: Connection }) {
  const now = useServerNow(conn);
  const resolved = stage.stage === "resolve" ? stage : null;
  const done = new Set(stage.stage === "pick" ? stage.done : players.map((p) => p.id));
  const byId = new Map(players.map((p) => [p.id, p]));
  const left = stage.stage === "pick" ? Math.max(0, stage.closes_at - now) : 0;

  return (
    <section className={`tv-quiz kn-vote stage-${stage.stage}`}>
      <header className="tv-quiz-head">
        <span className="tv-cat">Kopfnuss</span>
        <span className="tv-round">{round}</span>
      </header>

      <article className="qcard">
        <p className="qtext">{resolved ? "Gewählt!" : "Welche Kategorie? Und wen ärgert ihr?"}</p>
        {resolved ? (
          <ul className="kn-hits">
            {resolved.hits.length === 0 && <li>Keine Tricks diese Runde.</li>}
            {resolved.hits.map((h, i) => (
              <li key={i} className={h.bounced ? "is-bounced" : ""} style={{ animationDelay: `${i * 450}ms` }}>
                {byId.get(h.from)?.name} → {byId.get(h.to)?.name}: <b>{TRICK_NAMES[h.trick]}</b>
                {h.bounced && " – prallt ab!"}
              </li>
            ))}
          </ul>
        ) : (
          <div className="qcard-foot">
            <span className="qclock">{Math.ceil(left / 1000)}</span>
          </div>
        )}
      </article>

      <ol className="tv-answers">
        {stage.categories.map((c, i) => {
          const color = ANSWER_COLORS[i % ANSWER_COLORS.length];
          const state = resolved ? (i === resolved.chosen ? " is-correct" : " is-wrong") : "";
          return (
            <li key={c} className={`tv-answer key-${color}${state}`}>
              <span className="key-mark" aria-hidden="true">
                {ANSWER_MARKS[color]}
              </span>
              <span className="tv-answer-label">{c}</span>
              {resolved && <span className="kn-votes">{resolved.votes[i] ?? 0} ✋</span>}
            </li>
          );
        })}
      </ol>

      <footer className="tv-table">
        {players.map((p) => (
          <div key={p.id} className={`tv-seat${done.has(p.id) ? " is-locked" : ""}`}>
            <Token player={p} />
            <span className="tv-seat-name">{p.name}</span>
            <span className="tv-seat-score">{p.score}</span>
          </div>
        ))}
      </footer>
    </section>
  );
}
