// Run with `bun test` in crates/mediagram-game/client.
import { expect, test } from "bun:test";
import type { GameEntry } from "../src/protocol/GameEntry";
import type { GameKind } from "../src/protocol/GameKind";
import type { QuizMode } from "../src/protocol/QuizMode";
import { gameBadge } from "../src/ui/lobby";

const games: GameEntry[] = [
  { kind: "quiz", name: "Buzzer-Quiz", min: 1, max: 8 },
  { kind: "poster", name: "Poster-Raten", min: 1, max: 8 },
  { kind: "race", name: "Heimlauf", min: 2, max: 4 },
];
const badge = (game: GameKind, quiz_mode: QuizMode = "mixed") =>
  gameBadge({ settings: { game, quiz_mode, kids_only: false, kids_locked: false }, games });

test("the badge names the chosen game from the host's catalogue", () => {
  expect(badge("quiz", "films")).toBe("Buzzer-Quiz · Filme");
  expect(badge("poster")).toBe("Poster-Raten");
  expect(badge("race")).toBe("Heimlauf · 2–4 Spieler");
});
