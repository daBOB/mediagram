import type { GameEntry } from "../protocol/GameEntry";
import type { GameKind } from "../protocol/GameKind";

/** The shelf's two halves, one row at a time: quick party rounds, and the board and card games. */
export type ShelfGroup = "mini" | "full";
export const GROUP_NAMES: Record<ShelfGroup, string> = { mini: "Minispiele", full: "Spiele" };
export const GROUPS: readonly ShelfGroup[] = ["mini", "full"];

/** A game on the shelf that cannot be started yet: being built, or only planned. */
export type Badge = "wip" | "soon";
export const BADGE_TEXT: Record<Badge, string> = { wip: "WIP", soon: "Coming soon" };

/** One poster on the shelf. `key` is the host's game kind for a playable game, the art's file stem otherwise. */
export type ShelfItem = {
  key: string;
  name: string;
  min: number;
  max: number;
  blurb: string;
  group: ShelfGroup;
  /** The host's entry; absent while the game is not playable. */
  game?: GameEntry;
  badge?: Badge;
};

/** What the shelf says about each game the host offers. No wildcard: a new game does not compile without its text. */
const PLAYABLE: Record<GameKind, { blurb: string; group: ShelfGroup }> = {
  quiz: { group: "mini", blurb: "Acht Runden Fragen aus Filmen, Allgemeinwissen oder beidem. Wer zuerst richtig drückt, holt die meisten Punkte." },
  poster: { group: "mini", blurb: "Ein Film- oder Serienplakat wird Schritt für Schritt schärfer. Wer es zuerst erkennt, gewinnt die Runde – falsch getippt heißt aussetzen." },
  kopfnuss: { group: "mini", blurb: "Kategorie wählen, den anderen Streiche aufs Handy schicken, Fragen beantworten – und am Ende der Gipfelsturm um den Sieg." },
  dreifelder: { group: "mini", blurb: "Eine Frage, drei Felder. Wer falsch steht, fliegt raus – wer als Letzter übrig bleibt, gewinnt." },
  preisfrage: { group: "mini", blurb: "Seltsame Produkte aus dem Netz: Wer schätzt den Preis am genauesten? Je näher dran, desto mehr Punkte." },
  kritzelei: { group: "mini", blurb: "Einer zeichnet mit dem Finger auf dem iPad, der Fernseher zeigt jeden Strich live. Alle raten laut – wer's zuerst hat, punktet." },
  race: { group: "full", blurb: "Das Rennen ums Brett: vier Figuren, mit einer Sechs geht es los, und wer geschlagen wird, muss zurück nach Hause." },
  grand_allee: { group: "full", blurb: "Grundstücke kaufen, Hotels bauen, Eingänge setzen – und die Gäste zahlen. Der reichste Hotelier am Boulevard gewinnt." },
  fluechtig: { group: "full", blurb: "Einer flieht unsichtbar durch die Stadt, die anderen jagen ihn. Nur seine Tickets verraten, wie er fährt – und ab und zu taucht er auf." },
  mau_mau: { group: "full", blurb: "Farbe oder Wert bedienen: Sieben zieht zwei, Acht setzt aus, der Bube wünscht – und wer die vorletzte Karte legt, ruft „Mau!“." },
  schwimmen: { group: "full", blurb: "Drei Karten auf der Hand, drei in der Mitte: tauschen, klopfen, 31 sammeln. Wer am schwächsten bleibt, verliert ein Leben." },
  durak: { group: "full", blurb: "Angreifen, verteidigen, dazulegen. Wer am Ende noch Karten hält, ist der Durak." },
};

/** The games in the works and the ones planned, in the order the shelf shows them within their half. */
export const UPCOMING: readonly ShelfItem[] = [
  { key: "neunmalklug", name: "Neunmalklug", min: 2, max: 8, group: "mini", badge: "soon", blurb: "Ein Quiz mit frechem Moderator: knifflige Fragen, fiese Fallen und jede Menge Sprüche über eure Antworten." },
  { key: "lebensweg", name: "Lebensweg", min: 2, max: 6, group: "full", badge: "wip", blurb: "Mit dem Auto den Lebensberg hinauf: Ausbildung, Beruf, Familie und Haus. Wer oben am meisten erreicht hat, gewinnt." },
  { key: "wissensreise", name: "Wissensreise", min: 2, max: 6, group: "full", badge: "soon", blurb: "Würfeln, ziehen, Frage beantworten: In sechs Kategorien je einen Stein sammeln – wer zuerst alle hat, gewinnt die Reise." },
  { key: "grundstueck", name: "Grundstück", min: 2, max: 6, group: "full", badge: "soon", blurb: "Grundstücke in einer erfundenen Stadt kaufen, Häuser bauen und Miete kassieren, wenn die anderen bei dir landen." },
  { key: "spielwiese", name: "Spielwiese", min: 2, max: 8, group: "full", badge: "soon", blurb: "Über die Wiese würfeln und auf jedem Feld ein schnelles Minispiel am Handy. Die meisten Punkte nach einer Runde gewinnen." },
  { key: "wortwerk", name: "Wortwerk", min: 2, max: 4, group: "full", badge: "soon", blurb: "Buchstabensteine legen, Wörter kreuzen, Bonusfelder nutzen. Die höchste Punktzahl gewinnt, wenn die Steine ausgehen." },
  { key: "neuland", name: "Neuland", min: 3, max: 4, group: "full", badge: "soon", blurb: "Eine neue Insel: Rohstoffe sammeln, tauschen, Straßen und Siedlungen bauen. Wer zuerst sein Bauziel erreicht, gewinnt." },
  { key: "feldzug", name: "Feldzug", min: 2, max: 6, group: "full", badge: "soon", blurb: "Gebiete auf einer erfundenen Karte erobern: Armeen setzen, mit Würfeln angreifen, Grenzen halten." },
  { key: "gassenwechsel", name: "Gassenwechsel", min: 2, max: 4, group: "full", badge: "soon", blurb: "Gassen ins Labyrinth schieben und dir den Weg zu deinen geheimen Schätzen bahnen. Wer zuerst alle hat, gewinnt." },
  { key: "pleitier", name: "Pleitier", min: 2, max: 4, group: "full", badge: "soon", blurb: "Alle starten mit einer Million – und müssen sie loswerden. Wer zuerst pleite ist, gewinnt." },
  { key: "radau", name: "Radau", min: 3, max: 6, group: "full", badge: "soon", blurb: "Würfeln, dann erklären, vorspielen, Geräusche machen oder zeichnen – gegen die Uhr. Richtig geraten bringt euch voran." },
  { key: "sprechstunde", name: "Sprechstunde", min: 3, max: 6, group: "full", badge: "soon", blurb: "Ehrliche Fragen über dich und die anderen, durch sechs Lebensabschnitte: Wie gut kennt ihr euch wirklich?" },
  { key: "nachtwache", name: "Nachtwache", min: 2, max: 6, group: "full", badge: "soon", blurb: "Ein unheimlicher Wächter im Fernseher treibt euch an: Sammelt eure Schlüssel, bevor die Stunde um ist – sonst gewinnt er." },
  { key: "herz", name: "Herz", min: 4, max: 4, group: "full", badge: "soon", blurb: "Stiche vermeiden – vor allem Herzen und die Pik-Dame. Wenige Punkte gewinnen; wer alles einsammelt, dreht den Spieß um." },
  { key: "tien-len", name: "Tiến Lên", min: 2, max: 4, group: "full", badge: "soon", blurb: "Der vietnamesische Klassiker: Paare, Drillinge und Straßen höher legen als die anderen. Wer zuerst alle Karten los ist, gewinnt." },
  { key: "euchre", name: "Euchre", min: 4, max: 4, group: "full", badge: "soon", blurb: "Zwei gegen zwei: Trumpf wählen, die Buben stechen am höchsten. Drei von fünf Stichen bringen Punkte." },
  { key: "pikett", name: "Pikett", min: 2, max: 2, group: "full", badge: "soon", blurb: "Das alte französische Duell zu zweit: Blätter melden, Stiche machen, Punkte sammeln." },
  { key: "schafkopf", name: "Schafkopf", min: 4, max: 4, group: "full", badge: "soon", blurb: "Der bayerische Klassiker zu viert: Ober und Unter sind Trumpf, Spieler und Partner gegen die anderen zwei." },
  { key: "klabberjass", name: "Klabberjass", min: 2, max: 4, group: "full", badge: "soon", blurb: "Trumpf wählen, Folgen melden, Stiche zählen: das Kartenspiel aus der Jass-Familie, in Holland und der Schweiz zu Hause." },
];

const RANK = { mini: 0, full: 10, playable: 0, wip: 1, soon: 2 } as const;
const rank = (i: ShelfItem) => RANK[i.group] + RANK[i.badge ?? "playable"];

/**
 * The shelf, in order: the minigames, then the full games; in each half the playable ones in the
 * host's order, then the ones being built, then the planned ones.
 */
export function shelfItems(games: readonly GameEntry[]): ShelfItem[] {
  const playable = games.map((game): ShelfItem => ({ key: game.kind, name: game.name, min: game.min, max: game.max, game, ...PLAYABLE[game.kind] }));
  // sort is stable, so each rank keeps the order it came in
  return [...playable, ...UPCOMING].sort((a, b) => rank(a) - rank(b));
}

/** The line under an unplayable game's name, where a playable one says what OK does. */
export const upcomingLine = (badge: Badge) => (badge === "wip" ? "In Arbeit – noch nicht spielbar" : "Kommt bald – noch nicht spielbar");
