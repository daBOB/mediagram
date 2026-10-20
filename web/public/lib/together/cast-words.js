/**
 * What casting says. The web is the reference: the Android surfaces copy
 * these word for word. Types are in `cast-words.d.ts`.
 */

import { say } from "../playback/together-words.js";

export const CAST_WORDS = {
  playOnTv: "Play on TV",
  asking: "Asking the TV…",
  replace: "Replace",
  cancel: "Cancel",
  noAnswer: "Mediagram isn't open on the TV. Open it there and try again.",
  leaveFirst: "Leave watch together first",
  playingOnTv: "Playing on TV",
  stop: "Stop",
  finished: "Finished",
  stopped: "Stopped on the TV",
  libraryMissing: "the TV's library doesn't have it yet",
  profileUnknown: "the TV doesn't know this profile yet",
  joinFirst: "Join a household first",
};

export const busyQuestion = (title) => `The TV is playing ${title}. Replace it?`;
export const refusedLine = (reason) => `The TV won't play this: ${reason}`;
export const castFrom = (from) => `Cast from ${from}`;
export const barLine = (title) => `Playing on TV · ${title}`;
export const ageReason = (name) => `it's rated above ${name}'s age limit`;
export const timeReason = (name) => `${name}'s time is up for today`;

/** The notice for a cast's edit: the TV did it "on TV", and its own joining and leaving is no news. */
export function sayOnRemote(noticed, session) {
  if (noticed.label !== "TV") return say(noticed, session);
  if (noticed.change === "joined" || noticed.change === "left") return null;
  return say(noticed, session).replace(/ by TV$/, " on TV");
}
