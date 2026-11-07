import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

export const GAME_NAMES = { poster: "Poster-Raten", race: "Heimlauf", bauherr: "Bauherr" } as const;

export const gameBadge = (s: LobbySettings) =>
  s.game === "quiz"
    ? `Buzzer-Quiz · ${QUIZ_MODES.find((m) => m.mode === s.quiz_mode)?.label}`
    : s.game === "poster"
      ? GAME_NAMES.poster
      : `${GAME_NAMES[s.game]} · 2–4 Spieler`;
