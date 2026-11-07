// Every text a card game can put on the table's banner, note or seat tags, by
// game name, so the HUD draws them all at load. They mirror what the host
// sends (maumau.rs, maumau_views.rs); a text not listed still shows, drawn at
// first use.

import { SUIT_NAME, SUITS } from "../../ui/card-face";

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

const SCHWIMMEN: GameTexts = {
  banner: [
    " ist dran",
    " tauscht eine Karte",
    " tauscht alle drei Karten",
    " schiebt",
    " schiebt – neue Karten in der Mitte",
    " klopft!",
    " verliert ein Leben",
    " schwimmt jetzt",
    " scheidet aus",
    " und alle mit gleichem Blatt verlieren",
    " und alle anderen sind gleichauf",
    " gewinnt!",
  ],
  note: ["Geklopft – letzte Runde", "Aufgedeckt", "Feuer – 31!"],
  // the reveal adds a hand's value to these, drawn when first shown
  marker: ["●●●", "●●○", "●○○", "schwimmt", "●●● · klopft", "●●○ · klopft", "●○○ · klopft", "schwimmt · klopft", "raus"],
};

export const textsFor = (game: string): GameTexts => (game === "Mau-Mau" ? MAU_MAU : game === "Schwimmen" ? SCHWIMMEN : { banner: [" ist dran", " gewinnt!"], note: [], marker: [] });
