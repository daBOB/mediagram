import { Fragment } from "react";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import { ANSWER_MARKS } from "../ui/answers";
import "./numbered-keys.css";

type Outcome = Extract<PhoneScreen, { kind: "outcome" }>;

/** A question's result: title, points, the answers as small keys, the running score. */
export function OutcomeScreen({ screen, score }: { screen: Outcome; score: number }) {
  return (
    <section className={`screen outcome tone-${screen.tone}`}>
      <h1 className="screen-title">{screen.title}</h1>
      {screen.points > 0 && <p className="points">+{screen.points}</p>}
      {screen.keys.length > 0 && (
        <div className="outcome-keys">
          {screen.keys.map((k) => (
            <Fragment key={`${k.caption}-${k.number}`}>
              <span className="outcome-key-caption">{k.caption}</span>
              <span className={`outcome-key key-${k.color}${k.crossed ? " outcome-key-crossed" : ""}`}>
                <span className="outcome-key-num">{k.number}</span>
                <span className="key-mark" aria-hidden="true">
                  {ANSWER_MARKS[k.color]}
                </span>
                {k.label}
              </span>
            </Fragment>
          ))}
        </div>
      )}
      {screen.text && <p className="screen-text">{screen.text}</p>}
      {screen.show_score && <p className="hint">Stand: {score} Punkte</p>}
    </section>
  );
}
