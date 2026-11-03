import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

export const gameBadge = (s: LobbySettings) =>
  s.game === "quiz"
    ? `Buzzer-Quiz · ${QUIZ_MODES.find((m) => m.mode === s.quiz_mode)?.label}`
    : "Poster-Raten";
