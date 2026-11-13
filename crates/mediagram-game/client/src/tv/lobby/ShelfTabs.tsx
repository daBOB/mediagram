import { GROUPS, GROUP_NAMES, type ShelfGroup } from "../../ui/shelf-games";

/**
 * The switch above the row between the minigames and the full games. The remote reaches it with Up
 * from the row and switches with Left and Right; a tap switches too.
 */
export function ShelfTabs({ group, focused, onPick }: { group: ShelfGroup; focused: boolean; onPick: (g: ShelfGroup) => void }) {
  return (
    <div className={`shelf-tabs${focused ? " is-focused" : ""}`} role="tablist">
      {GROUPS.map((g) => (
        <button key={g} type="button" role="tab" aria-selected={g === group} className={g === group ? "is-active" : undefined} onClick={() => onPick(g)}>
          {GROUP_NAMES[g]}
        </button>
      ))}
    </div>
  );
}
