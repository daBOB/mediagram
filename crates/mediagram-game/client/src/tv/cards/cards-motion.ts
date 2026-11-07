// What is on the table each frame, worked out from the host's scene and the
// server clock alone, so a TV that reconnects mid-game picks up where it is:
// the fans of face-down cards, the deck, the discards, and the cards in flight
// between them (a played card flies seat → pile, drawn cards deck → seat).

import type { CardsEvent } from "../../protocol/CardsEvent";
import type { CardsScene } from "../../protocol/CardsScene";
import { RANKS, SUITS } from "../../ui/card-face";
import type { CardsHud } from "./cards-hud";
import {
  DECK_AT, DISCARD_AT, LAYER, TABLE_Y, WISH_AT, fanCard, openCard, pileCard, yawFacing, type Placed, type Spot,
} from "./cards-layout";
import type { World } from "./cards-world";

export const PLAY_MS = 420;
export const DRAW_MS = 380;
export const DRAW_STAGGER_MS = 150;
const LIFT = 1.1;
const FAN_SHOWN = 16;
const DECK_LAYERS = 5;

const seedOf = (c: { suit: string; rank: string }) => SUITS.indexOf(c.suit as never) * 13 + RANKS.indexOf(c.rank as never) + 1;
const ease = (f: number) => 1 - (1 - f) * (1 - f);

function between(a: Placed, b: Placed, f: number): Placed {
  const e = ease(f);
  return { x: a.x + (b.x - a.x) * e, z: a.z + (b.z - a.z) * e, yaw: a.yaw + (b.yaw - a.yaw) * e, y: a.y + (b.y - a.y) * e };
}

export class CardsMotion {
  constructor(
    private w: World,
    private hud: CardsHud,
    private spots: Spot[],
    private order: number[],
  ) {}

  /** Seconds-long happenings still in the air at `now`, as `[event, card index within it, progress]`. */
  private flights(scene: CardsScene, now: number) {
    const out: { ev: CardsEvent; j: number; f: number }[] = [];
    for (const ev of scene.events) {
      if (ev.kind.kind === "play") {
        const f = (now - ev.at) / PLAY_MS;
        if (f < 1) out.push({ ev, j: 0, f: Math.max(0, f) });
      } else if (ev.kind.kind === "draw") {
        for (let j = 0; j < ev.kind.count; j++) {
          const f = (now - ev.at - j * DRAW_STAGGER_MS) / DRAW_MS;
          if (f < 1) out.push({ ev, j, f: Math.max(0, f) });
        }
      }
    }
    return out;
  }

  update(scene: CardsScene, now: number) {
    const { cards } = this.w;
    const seatOf = (player: number) => this.order.indexOf(player);
    const flights = this.flights(scene, now);
    cards.begin();

    // hands: the cards still flying in are not in the fan yet
    scene.seats.forEach((seat, si) => {
      const spot = this.spots[si];
      const arriving = flights.filter((fl) => fl.ev.kind.kind === "draw" && fl.ev.player === seat.player).sort((a, b) => a.ev.id - b.ev.id || a.j - b.j);
      const shown = Math.max(0, seat.cards - arriving.length), total = seat.cards;
      for (let i = 0; i < Math.min(shown, FAN_SHOWN); i++) cards.put(null, fanCard(spot, i, total));
      seat.open.forEach((c, i) => cards.put(c, openCard(spot, i, seat.open.length)));
      arriving.forEach((fl, k) => {
        const slot = fanCard(spot, Math.min(shown + k, FAN_SHOWN - 1), total);
        const from: Placed = { x: DECK_AT.x, z: DECK_AT.z, yaw: 0, y: TABLE_Y + 0.12 };
        cards.put(null, between(from, slot, fl.f), Math.sin(fl.f * Math.PI) * LIFT);
      });
      this.hud.setTag(si, scene.turn === seat.player, seat.marker, seat.cards);
    });

    // deck: a few layers say how much is left
    const layers = scene.deck === 0 ? 0 : Math.min(DECK_LAYERS, 1 + Math.floor(scene.deck / 7));
    for (let i = 0; i < layers; i++) cards.put(null, { x: DECK_AT.x, z: DECK_AT.z, yaw: 0, y: TABLE_Y + 0.02 + i * LAYER * 3 });

    // discards, the one still flying in left out until it lands
    const landing = flights.find((fl) => fl.ev.kind.kind === "play");
    scene.piles.forEach((pile) => {
      pile.cards.forEach((c, i) => {
        const last = i === pile.cards.length - 1;
        if (last && landing && landing.ev.kind.kind === "play" && seedOf(landing.ev.kind.card) === seedOf(c)) return;
        cards.put(c, pileCard(DISCARD_AT, seedOf(c), i));
      });
    });
    for (const fl of flights) {
      if (fl.ev.kind.kind !== "play") continue;
      const si = seatOf(fl.ev.player), spot = this.spots[si];
      if (!spot) continue;
      const card = fl.ev.kind.card, pile = scene.piles[fl.ev.kind.pile] ?? scene.piles[0];
      const from: Placed = { x: spot.x, z: spot.z, yaw: yawFacing(spot.dx, spot.dz), y: TABLE_Y + 0.1 };
      cards.put(card, between(from, pileCard(DISCARD_AT, seedOf(card), pile.cards.length - 1), fl.f), Math.sin(fl.f * Math.PI) * LIFT);
    }
    cards.end();

    this.shade(scene, seatOf);
    for (const s of SUITS) this.w.badges[s].visible = scene.wish === s;
    if (scene.wish) this.w.badges[scene.wish].position.set(WISH_AT.x, TABLE_Y + 0.03, WISH_AT.z);

    const banner = scene.banner, bi = banner ? seatOf(banner.player) : -1;
    if (banner && bi >= 0) this.hud.setBanner(bi, banner.text);
    this.hud.setNote(scene.note);
  }

  /** Soft shadows under the fans and piles, and the glow under whoever's turn it is. */
  private shade(scene: CardsScene, seatOf: (p: number) => number) {
    const { blobs, glow } = this.w;
    scene.seats.forEach((seat, si) => {
      const s = this.spots[si], b = blobs[si], width = Math.min(s.fan + 2.1, 2.3 + Math.max(0, seat.cards - 1) * 0.62);
      b.position.set(s.x, TABLE_Y + 0.004, s.z);
      b.rotation.y = yawFacing(s.dx, s.dz);
      b.scale.set(width, 1, 2.8);
      b.visible = seat.cards > 0;
    });
    const n = scene.seats.length;
    for (const [k, at] of [DECK_AT, DISCARD_AT].entries()) {
      const b = blobs[n + k];
      b.position.set(at.x + 0.08, TABLE_Y + 0.004, at.z + 0.1);
      b.scale.set(2.3, 1, 2.9);
    }
    const turn = scene.turn === null ? -1 : seatOf(scene.turn);
    glow.visible = turn >= 0;
    if (turn >= 0) {
      const s = this.spots[turn];
      glow.position.set(s.x, TABLE_Y + 0.008, s.z);
      glow.rotation.y = yawFacing(s.dx, s.dz);
    }
  }
}
