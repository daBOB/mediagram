import type { GameEntry } from "../protocol/GameEntry";
import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

/** The chosen game's name, with the quiz's questions or a seat-gated game's seat range. */
export const gameBadge = ({ settings: s, games }: { settings: LobbySettings; games: GameEntry[] }) => {
  const game = games.find((g) => g.kind === s.game);
  if (!game) return "";
  return s.game === "quiz"
    ? `${game.name} · ${QUIZ_MODES.find((m) => m.mode === s.quiz_mode)?.label}`
    : s.game === "poster"
      ? game.name
      : `${game.name} · ${game.min}–${game.max} Spieler`;
};
