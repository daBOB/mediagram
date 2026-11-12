import { expect, test } from "bun:test";
import { type RawResult, priceCents, shortName, sortResult } from "./articles";

const raw = (over: Partial<RawResult>): RawResult => ({
  asin: "B0TEST0001",
  title: "Möwe Schlüsselanhänger mit Geräusch, Klicker Fidget",
  image: "https://m.media-amazon.com/images/I/61ybDXtjbtL._AC_UY218_.jpg",
  price: "7,99 €",
  reviews: "(1)",
  sponsored: false,
  text: "Möwe Schlüsselanhänger 7,99 €",
  ...over,
});

test("prices read German euros into cents", () => {
  expect(priceCents("7,99 €")).toBe(799);
  expect(priceCents("1.299,00 €")).toBe(129900);
  expect(priceCents("0,00 €")).toBe(0);
  expect(priceCents("")).toBeNull();
});

test("short names stop at the first separator after two words, at most 6 words and 48 characters", () => {
  expect(shortName("4er Grillen Geräuschmacher Prank für Halloween, Lustige Scherzartikel")).toBe(
    "4er Grillen Geräuschmacher Prank für Halloween",
  );
  expect(shortName("The Butt Klebefilm-Abroller – Lustige Wichtelgeschenke für Kollegen")).toBe(
    "The Butt Klebefilm-Abroller",
  );
  expect(shortName("snagger - Der Snackspender // saubere Finger beim Snacken (schwarz)")).toBe(
    "snagger - Der Snackspender",
  );
  expect(shortName("Das Original WC Licht. Coole LED Toilettenlicht Gadgets")).toBe("Das Original WC Licht");
  // Six words would be 53 characters: words come off the end until it fits.
  expect(shortName("Relaxdays Newton Pendel klassisches Kugelstoßpendel 5 Kugeln Silber")).toBe(
    "Relaxdays Newton Pendel klassisches",
  );
  expect(shortName("Möwe Klicker Fidget Spielzeug Schlüsselanhänger Geräusch Lustig")).toBe(
    "Möwe Klicker Fidget Spielzeug Schlüsselanhänger",
  );
  expect(shortName("Hochleistungsknoblauchpressenreinigungsbürste Edelstahlgriffverlängerung extra")).toBe(
    "Hochleistungsknoblauchpressenreinigungsbürste",
  );
});

test("a plain result becomes an article with a big photo", () => {
  const held = new Set<string>();
  expect(sortResult(raw({}), held)).toEqual({
    asin: "B0TEST0001",
    name: "Möwe Schlüsselanhänger mit Geräusch",
    title: "Möwe Schlüsselanhänger mit Geräusch, Klicker Fidget",
    cents: 799,
    reviews: 1,
    image: "https://m.media-amazon.com/images/I/61ybDXtjbtL._AC_SL800_.jpg",
  });
  expect(sortResult(raw({ reviews: "(10.293)", asin: "B0TEST0002", title: "Anderes Ding" }), held)).toMatchObject({
    reviews: 10293,
  });
  expect(sortResult(raw({ reviews: "", asin: "B0TEST0003", title: "Drittes Ding" }), held)).toMatchObject({
    reviews: 0,
  });
});

test("sponsored, priceless, free, media, books, adult, slogan clothing and duplicates are skipped", () => {
  const held = new Set<string>();
  expect(sortResult(raw({ sponsored: true }), held)).toBe("sponsored");
  expect(sortResult(raw({ price: "" }), held)).toBe("no price");
  expect(sortResult(raw({ price: "0,00 €" }), held)).toBe("no price");
  expect(sortResult(raw({ image: "" }), held)).toBe("no photo");
  expect(sortResult(raw({ text: "Kindle Ausgabe 0,99 €" }), held)).toBe("media");
  expect(sortResult(raw({ text: "Taschenbuch 12,95 €" }), held)).toBe("media");
  expect(sortResult(raw({ asin: "3831038147" }), held)).toBe("book");
  expect(sortResult(raw({ title: "Lustiger Penis Kugelschreiber" }), held)).toBe("adult");
  expect(sortResult(raw({ title: "Funny Singles Day DEKO Fun Spruch Damen Kapuzenjacke" }), held)).toBe("clothing");
  expect(sortResult(raw({ title: "Bester Papa T-Shirt Herren" }), held)).toBe("clothing");
  expect(typeof sortResult(raw({}), held)).toBe("object");
  expect(sortResult(raw({}), held)).toBe("duplicate");
  expect(sortResult(raw({ asin: "B0TEST0009" }), held)).toBe("duplicate");
});
