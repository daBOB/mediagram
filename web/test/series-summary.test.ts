/**
 * What a show adds up to.
 *
 * Every field is optional in the index, so the interesting cases are the
 * absent and the disagreeing ones: a field nobody recorded must not become a
 * blank claim, and a show that is half one thing must not be reported as
 * wholly either.
 */

import { describe, expect, test } from "bun:test";
import type { CatalogSet } from "../public/lib/library.js";
import { catalogSet } from "./support/catalog-set";

import {
  detailRows,
  pictureLine,
  rangeOf,
  scaleLine,
  summarize,
  yearLine,
} from "../public/lib/catalog/series-summary.js";

/** A collection shaped the way `groupLibrary` builds one: seasons of items. */
function show(...seasons: CatalogSet[][]) {
  return {
    name: "A Show",
    divisions: seasons.map((items, i) => ({
      title: `Season ${i + 1}`,
      season: i + 1,
      items,
      children: [],
    })),
  };
}

function episode(over: Partial<CatalogSet> = {}) {
  return catalogSet({
    setId: `s${Math.random()}`,
    year: 2026,
    quality: "1080p",
    hdr: "SDR",
    vcodec: "h264",
    acodec: "aac",
    alang: '["eng","ger"]',
    slang: '["eng"]',
    duration: 2700,
    total: 3_000_000_000,
    ...over,
  });
}

describe("a show's totals", () => {
  test("counts episodes and seasons, and adds up runtime and size", () => {
    const facts = summarize(show([episode(), episode()], [episode()]));

    expect(facts.episodes).toBe(3);
    expect(facts.seasons).toBe(2);
    expect(facts.runtime).toBe(8100);
    expect(facts.size).toBe(9_000_000_000);
  });

  test("a runtime nobody recorded is absent, not zero", () => {
    const facts = summarize(show([episode({ duration: null }), episode({ duration: null })]));

    expect(facts.runtime).toBeNull();
  });

  test("an episode with no duration does not drag the total to nothing", () => {
    const facts = summarize(show([episode({ duration: 2700 }), episode({ duration: null })]));

    expect(facts.runtime).toBe(2700);
  });
});

describe("what the picture is", () => {
  test("one quality is stated plainly", () => {
    expect(rangeOf(summarize(show([episode(), episode()])).qualities)).toBe("1080p");
  });

  test("a show that is not all one quality shows the span", () => {
    const facts = summarize(show([episode({ quality: "1080p" }), episode({ quality: "720p" })]));

    // Smallest first, whatever order the episodes arrived in.
    expect(rangeOf(facts.qualities)).toBe("720p–1080p");
  });

  test("SDR is not worth saying, but HDR is", () => {
    expect(summarize(show([episode()])).hdr).toEqual([]);
    expect(summarize(show([episode({ hdr: "HDR10" })])).hdr).toEqual(["HDR10"]);
  });

  test("a quality nobody recorded yields nothing to show", () => {
    expect(rangeOf(summarize(show([episode({ quality: null })])).qualities)).toBeNull();
  });
});

describe("languages", () => {
  test("audio languages are named, deduplicated across episodes", () => {
    const facts = summarize(show([episode(), episode({ alang: '["eng"]' })]));

    expect(facts.audioLanguages).toEqual(["English", "German"]);
  });

  test("a malformed language list is no languages rather than a crash", () => {
    expect(summarize(show([episode({ alang: "not json" })])).audioLanguages).toEqual([]);
  });

  test("subtitle languages come from the tracks the files carry", () => {
    const facts = summarize(show([episode({ slang: '["eng","ger"]' })]));

    expect(facts.subtitleLanguages).toEqual(["English", "German"]);
  });

  test("a code the browser cannot name is shown as it is", () => {
    // Named through Intl, like every other language the player shows, so a
    // series header and the audio chooser call the same track the same thing.
    const facts = summarize(show([episode({ alang: '["zzz"]' })]));

    expect(facts.audioLanguages).toEqual(["zzz"]);
  });

  test("a language the browser does know is named in full", () => {
    const facts = summarize(show([episode({ alang: '["ko"]' })]));

    expect(facts.audioLanguages).toEqual(["Korean"]);
  });
});

describe("years", () => {
  test("a single year is one number", () => {
    expect(rangeOf(summarize(show([episode()])).years.map(String))).toBe("2026");
  });

  test("a show spanning years shows the span, not every year in it", () => {
    const facts = summarize(
      show([episode({ year: 2006 }), episode({ year: 2009 }), episode({ year: 2013 })]),
    );

    expect(rangeOf(facts.years.map(String))).toBe("2006–2013");
  });
});

test("a show with a single episode still summarizes", () => {
  const facts = summarize(show([episode()]));

  expect(facts.episodes).toBe(1);
  expect(facts.seasons).toBe(1);
  expect(facts.runtime).toBe(2700);
});

/**
 * What the header actually prints. A row with nothing to say is left out, so
 * these assert absence as carefully as presence.
 */
describe("the lines a header prints", () => {
  test("the scale line names how much there is", () => {
    expect(scaleLine(summarize(show([episode(), episode()])))).toBe(
      "two episodes \u00b7 one season \u00b7 1h 30m \u00b7 5.6 GB",
    );
  });

  test("the picture line names quality, codecs and HDR when there is any", () => {
    expect(pictureLine(summarize(show([episode()])))).toBe("1080p \u00b7 h264 \u00b7 aac");
    expect(pictureLine(summarize(show([episode({ hdr: "HDR10" })])))).toBe(
      "1080p \u00b7 HDR10 \u00b7 h264 \u00b7 aac",
    );
  });

  test("a show with nothing recorded about its picture prints no picture line", () => {
    const bare = episode({ quality: null, hdr: null, vcodec: null, acodec: null });

    expect(pictureLine(summarize(show([bare])))).toBeNull();
  });

  test("a language nobody recorded is no row, not an empty one", () => {
    const rows = detailRows(summarize(show([episode({ alang: "[]", slang: "[]" })])));

    expect(rows).toEqual([]);
  });

  test("only the languages that exist get a row", () => {
    const rows = detailRows(summarize(show([episode({ alang: '["eng"]', slang: "[]" })])));

    expect(rows).toEqual([{ label: "Audio", value: "English" }]);
  });

  test("a year nobody recorded prints no year line", () => {
    expect(yearLine(summarize(show([episode({ year: null })])))).toBeNull();
  });
});

/**
 * A library knows what it holds; only the provider knows what exists. Saying
 * both is the difference between "eight episodes" and "the first eight of
 * sixteen", which is the thing someone browsing actually wants to know.
 */
describe("what is held against what exists", () => {
  const meta = { totalSeasons: 2, totalEpisodes: 16, firstAir: "2023-04-21", lastAir: "2024-11-06" };

  test("an incomplete show counts itself against the total", () => {
    const line = scaleLine(summarize(show([episode(), episode()])), meta);

    expect(line).toContain("2 of 16 episodes");
    expect(line).toContain("1 of 2 seasons");
  });

  test("a complete show says nothing about totals", () => {
    const complete = { totalSeasons: 1, totalEpisodes: 2 };

    expect(scaleLine(summarize(show([episode(), episode()])), complete)).toContain("two episodes");
    expect(scaleLine(summarize(show([episode(), episode()])), complete)).not.toContain(" of ");
  });

  test("holding more than the provider counted is not reported as a shortfall", () => {
    // Specials and double episodes really do outnumber a provider's count.
    const line = scaleLine(summarize(show([episode(), episode(), episode()])), {
      totalEpisodes: 2,
      totalSeasons: 1,
    });

    expect(line).not.toContain(" of ");
  });

  test("with no provider counts it reports only what is held", () => {
    expect(scaleLine(summarize(show([episode(), episode()])), null)).toContain("two episodes");
  });
});

describe("the years a show ran", () => {
  test("the provider's dates win over the years of the episodes held", () => {
    // One episode from 2026, of a show that ran 2006 to 2013.
    const facts = summarize(show([episode({ year: 2026 })]));

    expect(yearLine(facts, { firstAir: "2006-10-11", lastAir: "2013-01-31" })).toBe(
      "2006\u20132013",
    );
  });

  test("a show still running has no end, and is not given one", () => {
    const facts = summarize(show([episode()]));

    expect(yearLine(facts, { firstAir: "2026-01-08", lastAir: null })).toBe("2026");
  });

  test("a show that began and ended in one year is one number", () => {
    const facts = summarize(show([episode()]));

    expect(yearLine(facts, { firstAir: "2025-03-01", lastAir: "2025-06-01" })).toBe("2025");
  });

  test("without provider dates it falls back to the episodes held", () => {
    const facts = summarize(show([episode({ year: 2006 }), episode({ year: 2013 })]));

    expect(yearLine(facts, null)).toBe("2006\u20132013");
  });

  test("a malformed date is ignored rather than printed", () => {
    const facts = summarize(show([episode({ year: 2026 })]));

    expect(yearLine(facts, { firstAir: "", lastAir: null })).toBe("2026");
    expect(yearLine(facts, { firstAir: "not-a-date", lastAir: null })).toBe("2026");
  });
});
