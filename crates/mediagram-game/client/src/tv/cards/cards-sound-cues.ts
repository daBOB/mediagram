// Which sounds the card table makes and when, in host time: a wooden knock, softer than the race's, where each card lands,
// and the winner's jingle at the end (the stage plays it at once, with no host time).

import type { CardsScene } from "../../protocol/CardsScene";
import { BOARD_SOUNDS } from "../table/board-sounds";
import type { Cue, Effects, Plan } from "../table/tv-sound";
import { DRAW_MS, DRAW_STAGGER_MS, PLAY_MS } from "./cards-motion";

export type CardsSound = "knock" | "winnerJingle";

export const CARDS_SOUNDS: Effects<CardsSound> = { knock: { ...BOARD_SOUNDS.knock, gain: 0.4 }, winnerJingle: BOARD_SOUNDS.winnerJingle };

/**
 * The knocks of every event not yet in `heard`, which they then join: each event sounds once, however late the TV joined
 * (the engine skips the ones long past). Keyed by those events; null: nothing new.
 */
export function cardsCues(scene: CardsScene, heard: Set<number>): Plan<CardsSound> | null {
  const cues: Cue<CardsSound>[] = [], fresh: number[] = [];
  for (const ev of scene.events) {
    if (heard.has(ev.id)) continue;
    heard.add(ev.id);
    fresh.push(ev.id);
    const k = ev.kind;
    if (k.kind === "play") cues.push({ sound: "knock", at: ev.at + PLAY_MS });
    else if (k.kind === "draw") for (let j = 0; j < k.count; j++) cues.push({ sound: "knock", at: ev.at + j * DRAW_STAGGER_MS + DRAW_MS });
  }
  return fresh.length ? { key: `events ${fresh.join()}`, cues } : null;
}
