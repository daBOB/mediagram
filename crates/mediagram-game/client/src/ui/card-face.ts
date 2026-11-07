// How a playing card is written, on the phone and on the TV table: German
// indices (B, D, K, A) and French suits.

import type { Card } from "../protocol/Card";
import type { Rank } from "../protocol/Rank";
import type { Suit } from "../protocol/Suit";

export const SUITS: Suit[] = ["clubs", "spades", "hearts", "diamonds"];
export const SUIT_SYMBOL: Record<Suit, string> = { clubs: "♣", spades: "♠", hearts: "♥", diamonds: "♦" };
export const SUIT_NAME: Record<Suit, string> = { clubs: "Kreuz", spades: "Pik", hearts: "Herz", diamonds: "Karo" };

export const RANKS: Rank[] = ["two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "jack", "queen", "king", "ace"];
export const RANK_LABEL: Record<Rank, string> = {
  two: "2", three: "3", four: "4", five: "5", six: "6", seven: "7", eight: "8", nine: "9", ten: "10",
  jack: "B", queen: "D", king: "K", ace: "A",
};
const RANK_NAME: Record<Rank, string> = {
  two: "Zwei", three: "Drei", four: "Vier", five: "Fünf", six: "Sechs", seven: "Sieben", eight: "Acht", nine: "Neun", ten: "Zehn",
  jack: "Bube", queen: "Dame", king: "König", ace: "Ass",
};

export const isRed = (s: Suit) => s === "hearts" || s === "diamonds";
export const cardName = (c: Card) => `${SUIT_NAME[c.suit]} ${RANK_NAME[c.rank]}`;
export const cardKey = (c: Card) => `${c.suit}-${c.rank}`;
