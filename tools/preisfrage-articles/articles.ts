// From one search result card to an article for the game, or the reason it is left out.

/** A result card as the page shows it, before any judging. */
export type RawResult = {
  asin: string;
  title: string;
  image: string;
  price: string;
  reviews: string;
  sponsored: boolean;
  /** All the card's text, where a book or film names its format. */
  text: string;
};

export type Article = {
  asin: string;
  /** The short name the TV and the phones show. */
  name: string;
  title: string;
  cents: number;
  /** 0 for none: few reviews stands for an article nobody knows. */
  reviews: number;
  /** Amazon's 800 px photo, fetched once and stored as `photos-{asin}.jpg`. */
  image: string;
};

export type Skip = "sponsored" | "no price" | "no photo" | "media" | "book" | "adult" | "clothing" | "duplicate";

// Books, e-books and films make dull guesses; their card names the format.
const MEDIA = /Kindle|Taschenbuch|Gebundene Ausgabe|Broschiert|Hörbuch|Audible|DVD|Blu-ray|Prime Video|Audio-CD/;
// A family night: gag-gift searches bring adult novelties; these words catch the obvious ones.
const ADULT = /penis|brust|busen|nackt|naughty|erotik|\bsex|kondom|dildo|vibrator|stripper|wichs|ab 18|18\+|po[ -]?shorts|furz/i;
// Print-on-demand slogan shirts flood gift searches, many copies at the same price.
const CLOTHING = /\b(T-?Shirt|Shirt|Hoodie|Kapuzenjacke|Kapuzenpullover|Sweatshirt|Pullover|Langarmshirt|Tanktop)\b/i;
// Every product that is not a book has a `B…` ASIN; a book's is its ISBN-10.
const PRODUCT_ASIN = /^B[0-9A-Z]{9}$/;
const SEPARATORS = [" | ", " - ", " – ", " — ", " // ", ", ", " (", ". ", ": "];

/** `"1.299,00 €"` → 129900; `null` when there is no price on the card. */
export function priceCents(text: string): number | null {
  const m = text.match(/(\d{1,3}(?:\.\d{3})*,\d{2})/);
  return m ? Math.round(Number(m[1].replace(/\./g, "").replace(",", ".")) * 100) : null;
}

/**
 * The title up to its first separator that follows at least two words, then at most six words
 * and 48 characters, words coming off the end until it fits.
 */
export function shortName(title: string): string {
  const clean = title.replace(/\s+/g, " ").trim();
  let cut = clean.length;
  for (const sep of SEPARATORS) {
    let at = clean.indexOf(sep);
    while (at !== -1 && clean.slice(0, at).split(" ").length < 2) at = clean.indexOf(sep, at + 1);
    if (at !== -1 && at < cut) cut = at;
  }
  const words = clean.slice(0, cut).split(" ").slice(0, 6);
  while (words.length > 1 && words.join(" ").length > 48) words.pop();
  return words.join(" ").replace(/[\s,.;:–-]+$/, "");
}

/** The article this card stands for, or why it is skipped. `held` remembers ASINs and names. */
export function sortResult(raw: RawResult, held: Set<string>): Article | Skip {
  if (raw.sponsored) return "sponsored";
  const cents = priceCents(raw.price);
  if (!cents) return "no price";
  if (!/^https:\/\//.test(raw.image)) return "no photo";
  if (MEDIA.test(raw.text)) return "media";
  if (!PRODUCT_ASIN.test(raw.asin)) return "book";
  if (ADULT.test(raw.title)) return "adult";
  if (CLOTHING.test(raw.title)) return "clothing";
  const name = shortName(raw.title);
  const nameKey = `name:${name.toLowerCase()}`;
  if (held.has(raw.asin) || held.has(nameKey)) return "duplicate";
  held.add(raw.asin).add(nameKey);
  return {
    asin: raw.asin,
    name,
    title: raw.title.replace(/\s+/g, " ").trim(),
    cents,
    reviews: Number(raw.reviews.replace(/\D/g, "") || 0),
    image: raw.image.replace(/(\._[^/.]+_)?\.(jpe?g|png)$/i, "._AC_SL800_.jpg"),
  };
}
