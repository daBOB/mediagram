import type { FieldPick } from "../../protocol/FieldPick";

export type Field = { alive: number[]; ghosts: number[] };

/**
 * Who stands in which field at the reveal. `inRound` are the players who were
 * still in when the question was asked; the others guessed as ghosts. A player
 * in the round without a pick is listed in `unpicked`; a ghost without one is not shown.
 */
export function placePicks(picks: FieldPick[], inRound: Set<number>, fields: number): { fields: Field[]; unpicked: number[] } {
  const placed: Field[] = Array.from({ length: fields }, () => ({ alive: [], ghosts: [] }));
  const unpicked: number[] = [];
  for (const { player, choice } of picks) {
    const alive = inRound.has(player);
    if (choice === null) {
      if (alive) unpicked.push(player);
    } else {
      placed[choice][alive ? "alive" : "ghosts"].push(player);
    }
  }
  return { fields: placed, unpicked };
}
