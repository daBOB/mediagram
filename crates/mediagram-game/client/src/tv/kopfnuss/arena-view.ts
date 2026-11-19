import type { KopfnussScene } from "../../protocol/KopfnussScene";
import type { PlayerInfo } from "../../protocol/PlayerInfo";

export type ArenaKind = "pick" | "resolve" | "question" | "reveal" | "climb" | "fact";
export type ArenaPlayer = { id: number; avatar: string | null; color: string; step: number };
export type ArenaView = { kind: ArenaKind; players: ArenaPlayer[]; winner: number | null };
export type ArenaSet = "Doors" | "Quiz" | "Pyramid" | "Winner";

/** The baked set each stage plays in. A stage's result stays where the stage was: the tricks land in the
 *  category hall, the reveal in the quiz studio. */
export const ARENA_SET: Record<ArenaKind, ArenaSet> = {
  pick: "Doors",
  resolve: "Doors",
  question: "Quiz",
  reveal: "Quiz",
  climb: "Pyramid",
  fact: "Winner",
};

export function arenaView(scene: KopfnussScene, players: PlayerInfo[]): ArenaView {
  const stage = scene.stage;
  const kind: ArenaKind =
    stage.stage === "question" && stage.quiz.stage.stage === "reveal"
      ? "reveal"
      : stage.stage === "final"
        ? "fact"
        : stage.stage;
  const steps = new Map<number, number>(stage.stage === "climb" ? stage.climbers.map((c) => [c.player, c.step]) : []);
  return {
    kind,
    winner: stage.stage === "fact" ? stage.winner : null,
    players: players.map((p) => ({ id: p.id, avatar: p.avatar, color: p.color, step: steps.get(p.id) ?? 0 })),
  };
}
