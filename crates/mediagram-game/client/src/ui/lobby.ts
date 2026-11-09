import type { LobbySettings } from "../protocol/LobbySettings";
import type { QuizMode } from "../protocol/QuizMode";

export const QUIZ_MODES: { mode: QuizMode; label: string }[] = [
  { mode: "films", label: "Filme" },
  { mode: "general", label: "Allgemein" },
  { mode: "mixed", label: "Gemischt" },
];

export const GAME_NAMES = {
  poster: "Poster-Raten",
  kopfnuss: "Kopfnuss",
  dreifelder: "Dreifelder",
  race: "Heimlauf",
  grand_allee: "Grand Allee",
  mau_mau: "Mau-Mau",
  schwimmen: "Schwimmen",
  durak: "Durak",
} as const;

/** Players each game seats; mirrors the host's gate in `room_settings.rs`. */
const SEATS = { kopfnuss: "2–8", dreifelder: "2–8", race: "2–4", grand_allee: "2–8", mau_mau: "2–8", schwimmen: "2–8", durak: "2–6" } as const;

export const gameBadge = (s: LobbySettings) =>
  s.game === "quiz"
    ? `Buzzer-Quiz · ${QUIZ_MODES.find((m) => m.mode === s.quiz_mode)?.label}`
    : s.game === "poster"
      ? GAME_NAMES.poster
      : `${GAME_NAMES[s.game]} · ${SEATS[s.game]} Spieler`;
