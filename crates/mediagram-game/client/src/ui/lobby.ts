import type { GameEntry } from "../protocol/GameEntry";
import type { GameSpeed } from "../protocol/GameSpeed";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

/** The lobby's tempo; so far it sets Preisfrage's time to guess (2 min, 1 min, 20 s). */
export const SPEEDS: { mode: GameSpeed; label: string }[] = [
  { mode: "relaxed", label: "Entspannt" },
  { mode: "normal", label: "Normal" },
  { mode: "speedrun", label: "Speedrun" },
];

const labelOf = <T>(list: { mode: T; label: string }[], mode: T) => list.find((m) => m.mode === mode)?.label;

/** "2–6 Spieler", or "4 Spieler" for a game that takes exactly that many. */
export const seatsLine = (min: number, max: number) => `${min === max ? min : `${min}–${max}`} Spieler`;

/** The line under a game's name on the TV: its seats, the quiz's questions, Preisfrage's tempo. */
export const gameLine = (game: GameEntry, s: Pick<LobbySettings, "quiz_mode" | "speed">) => {
  const seats = seatsLine(game.min, game.max);
  if (game.kind === "quiz") return `${seats} · Fragen: ${labelOf(QUIZ_MODES, s.quiz_mode)}`;
  if (game.kind === "preisfrage") return `${seats} · Tempo: ${labelOf(SPEEDS, s.speed)}`;
  return seats;
};
