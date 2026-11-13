import type { GameEntry } from "../protocol/GameEntry";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

/** "2–6 Spieler", or "4 Spieler" for a game that takes exactly that many. */
export const seatsLine = (min: number, max: number) => `${min === max ? min : `${min}–${max}`} Spieler`;

/** The line under a game's name on the TV: its seats, and for the quiz which questions it asks. */
export const gameLine = (game: GameEntry, quizMode: QuizMode) => {
  const seats = seatsLine(game.min, game.max);
  return game.kind === "quiz" ? `${seats} · Fragen: ${QUIZ_MODES.find((m) => m.mode === quizMode)?.label}` : seats;
};
