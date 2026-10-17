export type ReasonId = "wont-play" | "picture-sound" | "subtitles" | "wrong-title";
export interface ReportFacts {
  reason: ReasonId;
  title: string;
  note: string | null;
  profile: string | null;
  screen: string;
  device: string | null;
  app: string;
  position: number | null;
  played: "direct" | "converted" | "copied" | null;
  held: boolean;
  error: string | null;
  setId: string;
}
export const REPORT_REASONS: { id: ReasonId; words: string }[];
export const REPORT_WORDS: {
  button: string;
  sent: string;
  heading: string;
  note: string;
  send: string;
  cancel: string;
  failed: string;
};
export const NOTE_MAX: number;
export const ERROR_MAX: number;
export const REPORT_DELAY_SECONDS: number;
export function oneLine(text: string | null | undefined): string;
export function cleanNote(note: string | null | undefined): string;
export function reportText(facts: ReportFacts): string;
