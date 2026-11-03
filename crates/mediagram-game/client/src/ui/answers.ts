import type { AnswerColor } from "../protocol/AnswerColor";

export const ANSWER_COLORS: AnswerColor[] = ["blue", "orange", "yellow", "pink"];

/** Shape per colour, so answers stay distinguishable without colour vision. */
export const ANSWER_MARKS: Record<AnswerColor, string> = {
  blue: "●",
  orange: "▲",
  yellow: "■",
  pink: "◆",
};
