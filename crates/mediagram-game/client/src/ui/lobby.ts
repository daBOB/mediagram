import type { GameEntry } from "../protocol/GameEntry";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

/** The line under a game's name on the TV: its seats, and for the quiz which questions it asks. */
export const gameLine = (game: GameEntry, quizMode: QuizMode) => {
  const seats = `${game.min}–${game.max} Spieler`;
  return game.kind === "quiz" ? `${seats} · Fragen: ${QUIZ_MODES.find((m) => m.mode === quizMode)?.label}` : seats;
};
