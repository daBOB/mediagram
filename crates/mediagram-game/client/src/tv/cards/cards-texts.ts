// Every text a card game can put on the table's banner, note or seat tags, by
// game name, so the HUD draws them all at load. They mirror what the host
// sends (maumau.rs, maumau_views.rs); a text not listed still shows, drawn at
// first use.

import { SUIT_NAME, SUIT_SYMBOL, SUITS } from "../../ui/card-face";

export interface GameTexts {
  /** What follows a player's name in the banner. */
  banner: string[];
  /** The standing note beside the piles. */
  note: string[];
  /** Small words under a seat's name. */
  marker: string[];
}

const MAU_MAU: GameTexts = {
  banner: [
    " ist dran",
    " zieht eine Karte",
    ...[2, 3, 4, 5, 6, 7, 8].map((n) => ` zieht ${n} Karten`),
    " setzt aus",
    ...SUITS.map((s) => ` wünscht ${SUIT_NAME[s]}`),
    " ruft Mau!",
    " vergisst Mau – Strafkarte!",
    " gewinnt!",
  ],
  note: [2, 3, 4, 5, 6, 7, 8].map((n) => `Zieh ${n}`),
  marker: ["Mau!"],
};

const DURAK: GameTexts = {
  banner: [
    " greift an",
    " verteidigt",
    " schlägt",
    " legt dazu",
    " darf dazulegen",
    " darf noch dazulegen",
    " nimmt auf",
    " nimmt die Karten auf",
    " hat alles geschlagen – Bito!",
    " ist der Durak!",
    " – keiner ist Durak!",
  ],
  note: SUITS.map((s) => `Trumpf ${SUIT_SYMBOL[s]} ${SUIT_NAME[s]}`),
  marker: ["Angreifer", "Verteidiger", "Raus", "Durak"],
};

const TEXTS: Record<string, GameTexts> = { "Mau-Mau": MAU_MAU, Durak: DURAK };

export const textsFor = (game: string): GameTexts => (TEXTS[game] ?? { banner: [" ist dran", " gewinnt!"], note: [], marker: [] });
