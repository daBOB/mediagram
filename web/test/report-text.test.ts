/** The report's words, held to `fixtures/report/report-text.json`, which Android's port reads too. */
import { expect, test } from "bun:test";
import fixture from "./fixtures/report/report-text.json";
import { NOTE_MAX, REPORT_DELAY_SECONDS, REPORT_REASONS, REPORT_WORDS, cleanNote, reportText } from "../public/lib/report/report-text.js";

test("every case reads as the fixture says", () => {
  for (const c of fixture.cases) expect(reportText(c.facts as never), c.name).toBe(c.text);
});
test("notes are cleaned as the fixture says", () => {
  for (const n of fixture.notes) expect(cleanNote(n.note), n.name).toBe(n.clean);
});
test("reasons, words and limits are the fixture's", () => {
  expect(REPORT_REASONS as { id: string; words: string }[]).toEqual(fixture.reasons);
  expect(REPORT_WORDS).toEqual(fixture.words);
  expect(NOTE_MAX).toBe(fixture.noteMax);
  expect(REPORT_DELAY_SECONDS).toBe(fixture.delaySeconds);
});
