import type { GameEntry } from "../protocol/GameEntry";
import type { GameSpeed } from "../protocol/GameSpeed";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

/** The lobby's tempo: Entspannt doubles a timed game's clocks, Speedrun halves them. */
export const SPEEDS: { mode: GameSpeed; label: string }[] = [
  { mode: "relaxed", label: "Entspannt" },
  { mode: "normal", label: "Normal" },
  { mode: "speedrun", label: "Speedrun" },
];

const labelOf = <T>(list: { mode: T; label: string }[], mode: T) => list.find((m) => m.mode === mode)?.label;

/** "2–6 Spieler", or "4 Spieler" for a game that takes exactly that many. */
export const seatsLine = (min: number, max: number) => `${min === max ? min : `${min}–${max}`} Spieler`;

/** The line under a game's name on the TV: its seats, the quiz's questions, a timed game's tempo. */
export const gameLine = (game: GameEntry, s: Pick<LobbySettings, "quiz_mode" | "speed">) =>
  [
    seatsLine(game.min, game.max),
    game.kind === "quiz" && `Fragen: ${labelOf(QUIZ_MODES, s.quiz_mode)}`,
    game.timed && `Tempo: ${labelOf(SPEEDS, s.speed)}`,
  ]
    .filter(Boolean)
    .join(" · ");
