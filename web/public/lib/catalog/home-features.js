/**
 * The three features under the cover: each one title, set like a magazine's
 * feature opener — its backdrop, a department label, the title in the
 * display face and its tagline as the standfirst.
 *
 * The label names the rule that chose the title (`editorial-picks.js`), so a
 * card never claims more than the library knows: "Trending on TMDB" is
 * TMDB's figure, not a claim about this household.
 */

import { el } from "../dom.js";
import { genresOf, scoreLabel } from "./genres.js";
import { artUrl } from "./home-cover.js";

export const FEATURE_LABELS = {
  editor: "Editor’s choice",
  staff: "Staff pick",
  trending: "Trending on TMDB",
  new: "New in the library",
};

/** Where a featured title opens: a film's page, or its show's. */
export function featureHref(set) {
  return set.kind === "ep" && set.show
    ? `#/series/${encodeURIComponent(set.show)}`
    : `#/film/${encodeURIComponent(set.setId)}`;
}

/** @param {import("./editorial-picks.js").Feature[]} features */
export function featureStrip(features) {
  const strip = el("section", "features reveal");
  strip.setAttribute("aria-label", "Features");
  for (const feature of features) strip.append(featureCard(feature));
  return strip;
}

function featureCard({ kind, set }) {
  const series = set.kind === "ep" && set.show;
  const title = series ? set.show : (set.title ?? set.setId);
  const card = el("a", `feature feature-${kind}`);
  card.href = featureHref(set);

  const art = set.backdrop ?? set.poster;
  if (art) {
    const image = el("img", set.backdrop ? "feature-image" : "feature-image poster-crop");
    image.src = artUrl(art);
    image.alt = "";
    image.loading = "lazy";
    image.decoding = "async";
    card.append(image);
  }
  card.append(el("span", "feature-scrim"));

  const copy = el("span", "feature-copy");
  copy.append(el("span", "eyebrow feature-eyebrow", FEATURE_LABELS[kind]));
  copy.append(el("span", "feature-title", title));
  copy.append(el("span", "feature-rule"));
  const deck = set.tagline ?? genresOf(set).slice(0, 3).join(", ");
  if (deck) copy.append(el("span", "feature-deck", deck));
  const facts = [series ? null : set.year, scoreLabel(set.rating)].filter(Boolean);
  if (facts.length > 0) copy.append(el("span", "feature-meta", facts.join(" · ")));
  card.append(copy);
  return card;
}
