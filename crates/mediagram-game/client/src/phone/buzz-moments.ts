// When the phone vibrates, read off the screens the host already sends. The
// host re-sends a player's screen whenever anything in the room changes, so
// every moment is a change from the previous screen, never the screen alone.

import type { Buzz } from "../protocol/Buzz";
import type { PhoneScreen } from "../protocol/PhoneScreen";
import type { BuzzKind } from "./app-bridge";

/** What the host took from this player on a question screen: a pick, or a sheet's sealed bid. */
function answer(s: PhoneScreen): number | null {
  if (s.kind === "choice") return s.picked;
  if (s.kind === "sheet") return s.bid?.placed ?? null;
  return null;
}

/** The screen wants something from this player right now. */
function asks(s: PhoneScreen): boolean {
  switch (s.kind) {
    case "choice":
      return s.picked === null;
    case "sheet":
      return s.options.length > 0 || (s.bid !== null && s.bid.placed === null);
    // off-turn card players get no buttons at all
    case "cards":
      return s.desk.buttons.length > 0;
    case "fluechtig":
      return s.phone.ask !== null;
    default:
      return false;
  }
}

/** Both screens belong to the same question or turn. */
function sameAsk(a: PhoneScreen, b: PhoneScreen): boolean {
  if (a.kind !== b.kind) return false;
  if ((a.kind === "choice" || a.kind === "sheet") && (b.kind === "choice" || b.kind === "sheet")) return a.opens_at === b.opens_at;
  // one move asks twice (field, then ticket) from the same field; the next move starts elsewhere
  if (a.kind === "fluechtig" && b.kind === "fluechtig") return a.phone.round === b.phone.round && a.phone.ask?.at === b.phone.ask?.at;
  return true;
}

export function buzzFor(prev: PhoneScreen | null, next: PhoneScreen): BuzzKind | null {
  if (next.kind === "final") return next.rank === 1 && prev?.kind !== "final" ? "won" : null;
  if (next.kind === "outcome") {
    const repeat = prev?.kind === "outcome" && prev.tone === "bad" && prev.title === next.title;
    return next.tone === "bad" && !repeat ? "wrong" : null;
  }
  if (answer(next) !== null) {
    const wasOpen = prev !== null && sameAsk(prev, next) && answer(prev) === null;
    return wasOpen ? "accepted" : null;
  }
  if (asks(next) && !(prev !== null && asks(prev) && sameAsk(prev, next))) return "turn";
  return null;
}

/** A buzz the game asked for fires once per changed screen; otherwise the screen decides. */
export function buzzForView(prev: PhoneScreen | null, next: PhoneScreen, ask: Buzz | null): BuzzKind | null {
  if (!ask) return buzzFor(prev, next);
  return prev === null || JSON.stringify(prev) !== JSON.stringify(next) ? ask : null;
}
