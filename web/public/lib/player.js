/**
 * The player dialog: one title at a time, direct or transcoded.
 *
 * Kept apart from the shelves because opening a title is where all the state
 * is. A set played directly is a `src` and nothing else; a set the browser
 * refuses is an hls.js instance and a running ffmpeg on the server, and both
 * have to be torn down when the dialog closes or the server keeps encoding
 * for a viewer who has gone.
 */

import { playbackFor } from "./link.js";
import { playTranscoded } from "./hls-playback.js";
import { sourceBitrate, watchPlayback } from "./adapt-playback.js";
import { clockTime, endsAt, episodeLabel } from "./format.js";
import { languageLabel } from "./language-label.js";
import { defaultTrack, fillChooser, loadAudioTracks } from "./audio-chooser.js";
import { bufferedAhead, preloadReadout } from "./preload-readout.js";
import { renderNotes } from "./notes-view.js";
import * as state from "./watch-state.js";
import { isFinished, resumeAt, trustedRuntime } from "./resume-point.js";

const dialog = document.getElementById("player");
const video = document.getElementById("video");
const note = document.getElementById("note");
const summaryBox = document.getElementById("summary");
const now = document.getElementById("now");
const jump = document.getElementById("jump");
const jumpTo = document.getElementById("jump-to");
const jumpAt = document.getElementById("jump-at");
const ends = document.getElementById("ends");
const audio = document.getElementById("audio");
const audioTrackPicker = document.getElementById("audio-track");
const notesButton = document.getElementById("notes");
const preload = document.getElementById("preload");
const notesPanel = document.getElementById("notes-panel");
const notesClose = document.getElementById("notes-close");
const watchlistButton = document.getElementById("watchlist");
const addToButton = document.getElementById("add-to");
const upNext = document.getElementById("up-next");
const upNextTitle = document.getElementById("up-next-title");
const upNextIn = document.getElementById("up-next-in");

/**
 * What follows the title being played, and how the page finds it.
 *
 * Supplied by whoever opened the player rather than worked out here: the
 * shelves already hold the collection a title came from, and a player that
 * went looking would have to be told about the library to do it.
 */
let nextUp = null;
let onOpenNext = null;
/** Titles whose countdown was cancelled, so it does not start again. */
const cancelled = new Set();
let countdown = null;
/** The set whose start has already been asked for, so it is asked once. */
let preloaded = null;

/** What the last title left attached, or `null` when it was played directly. */
let detach = null;
/** The set being played, and where in it the current conversion started. */
let playing = null;
let base = 0;
/** The cap the running conversion was given, or `null` while playing direct. */
let capBits = null;
/**
 * Which audio stream the viewer is on, as ffmpeg's `0:a:N`.
 *
 * Module state rather than a parameter because every restart — a seek, a
 * bitrate switch, a track change — has to carry the same answer. A seek that
 * forgot it would drop the viewer back into the first language.
 */
let audioTrack = 0;

/**
 * Worded to cover both reasons a title is converted. It may be a codec the
 * browser will not decode, or a bitrate the link will not carry, and saying
 * "your browser cannot play this" about the second one is simply untrue.
 */
function noteFor(set) {
  const decision = playbackFor(set);
  return decision.kind === "direct"
    ? null
    : `Converting as you watch: ${decision.reason}.`;
}

/** Attaches whatever subtitle tracks the catalog said this set has. */
function attachSubtitles(set) {
  for (const existing of [...video.querySelectorAll("track")]) existing.remove();
  for (const [index, lang] of (set.subtitles ?? []).entries()) {
    const track = document.createElement("track");
    track.kind = "subtitles";
    track.srclang = lang;
    track.label = languageLabel(lang, "Subtitles");
    track.src = `/api/sets/${encodeURIComponent(set.setId)}/subtitles/${lang}.vtt`;
    if (index === 0) track.default = true;
    video.append(track);
  }
}

/** Fetched on open rather than carried in the catalog, which would be large. */
async function showSummary(set) {
  showNotes(false);
  summaryBox.textContent = "";
  notesButton.hidden = true;
  if (!set.hasSummary) return;
  try {
    const response = await fetch(`/api/sets/${encodeURIComponent(set.setId)}/summary`);
    if (!response.ok) return;
    // Same guard as the audio probe: the viewer may have moved on while this
    // was in flight, and the previous title's notes are not these notes.
    if (playing?.setId !== set.setId) return;

    // Markdown, as nodes. Never as markup — see `notes-view.js`.
    renderNotes(summaryBox, await response.text());
    notesButton.hidden = false;
    // A lesson's notes are the point of a lesson, so they are already open. A
    // film's are an extra, and wait to be asked for.
    if (set.kind === "tut") showNotes(true);
  } catch {
    /* A missing summary is not worth interrupting playback for. */
  }
}

/**
 * Opens or closes the notes column.
 *
 * The class on the dialog is what moves the picture over: the video and both
 * rails are told to stop short of the column rather than the column being
 * laid on top of them, so nothing is ever hidden behind it.
 */
function showNotes(open) {
  notesPanel.hidden = !open;
  dialog.classList.toggle("with-notes", open);
  notesButton.setAttribute("aria-expanded", String(open));
}

function stop() {
  video.pause();
  if (detach) {
    detach();
    detach = null;
  }
  video.removeAttribute("src");
  video.load();
}

/**
 * Starts converting `set` from `seconds` and plays the result.
 *
 * Also the seek: a conversion has only encoded as far as it has got, so the
 * video's own scrub bar cannot reach past it. Landing somewhere else in the
 * film means starting the conversion again there, which is what the slider
 * above the note does.
 */
function convert(set, seconds, warning, maxrateBits) {
  stop();
  base = seconds;
  capBits = maxrateBits ?? null;
  watch.begin({ capBits, sourceBits: sourceBitrate(set) });
  // Shown unconditionally: a title that was playing directly has its note
  // hidden, and a switch that explains itself invisibly explains nothing.
  note.textContent = `${warning} Starting at ${clockTime(seconds)}…`;
  note.hidden = false;

  playTranscoded(video, set.setId, {
    seekSeconds: seconds,
    maxrateBits,
    audioTrack,
    onFatal: (error) => {
      note.textContent = `The conversion stopped: ${error.message}. Pick a position to start it again.`;
    },
  })
    .then((release) => {
      detach = release;
      note.textContent = warning;
      refreshEnds();
    })
    .catch((error) => {
      note.textContent = `Could not start the conversion: ${error.message}`;
    });
}

/** Where the viewer is in the film, not in the current conversion. */
function filmTime() {
  return base + (Number.isFinite(video.currentTime) ? video.currentTime : 0);
}

function showJump(set) {
  const runtime = Number(set.duration) || 0;
  jump.hidden = runtime === 0;
  if (jump.hidden) return;
  jumpTo.max = String(Math.floor(runtime));
  jumpTo.value = "0";
  jumpAt.textContent = `0:00 / ${clockTime(runtime)}`;
}

/**
 * When this title will finish, by the clock on the wall.
 *
 * Read from the catalog's runtime rather than `video.duration`, because a
 * conversion's duration is only as long as it has encoded so far — a film
 * would claim to end four minutes from now and keep moving. Divided by the
 * playback rate so a viewer at 1.5x is told the truth, and left blank when
 * nothing knows the runtime: a projected end time from an unknown length is a
 * guess wearing the clothes of a fact.
 */
function refreshEnds() {
  const catalogued = Number(playing?.duration) || 0;
  const runtime = catalogued || (Number.isFinite(video.duration) ? video.duration : 0);
  if (!runtime) {
    ends.textContent = "";
    return;
  }
  const rate = video.playbackRate > 0 ? video.playbackRate : 1;
  const at = endsAt(Math.max(0, runtime - filmTime()) / rate, new Date());
  ends.textContent = at === "" ? "" : `ends ${at}`;
}

/** The runtime to judge a position against. The rule is in `resume-point.js`. */
function runtimeOf(set) {
  return trustedRuntime({
    catalogued: set?.duration,
    observed: video.duration,
    direct: set !== null && playbackFor(set).kind === "direct",
  });
}

/**
 * Records where the viewer is.
 *
 * A title watched to the end is forgotten rather than recorded at its end:
 * the Continue shelf exists to hold things to go back to, and a finished film
 * sitting on it forever is the shelf slowly becoming useless.
 */
function saveProgress(final = false) {
  if (!playing) return;
  const runtime = runtimeOf(playing);
  const at = filmTime();
  if (isFinished(at, runtime)) {
    state.clearProgress(playing.setId);
    return;
  }
  if (final) state.flushProgress(playing.setId, at, runtime || null);
  else state.setProgress(playing.setId, at, runtime || null);
}

/** Every ten seconds while playing, which is little enough to lose. */
let saveTimer = null;
function startSaving() {
  clearInterval(saveTimer);
  saveTimer = setInterval(() => {
    if (!video.paused) saveProgress();
  }, 10_000);
}

/**
 * Warms the next title's first bytes.
 *
 * The bytes are dropped; the point is the server's chunk cache, which is what
 * actually holds them and which the transcoder reads through as well — so it
 * helps whichever way the next title turns out to play. Bounded to one
 * request per title, and only once the current one is comfortably buffered,
 * because a link that cannot keep up with what is playing must not be asked
 * to fetch something nobody is watching yet.
 */
const PRELOAD_BYTES = 8 * 1024 * 1024;
function preloadNext() {
  if (!nextUp || preloaded === nextUp.setId) return;
  if (bufferedAhead(video.buffered, video.currentTime) < 30) return;

  preloaded = nextUp.setId;
  void fetch(`/api/sets/${encodeURIComponent(nextUp.setId)}/stream`, {
    headers: { range: `bytes=0-${PRELOAD_BYTES - 1}` },
  })
    .then((response) => response.body?.cancel())
    .catch(() => {
      /* A warm cache is a convenience; failing to warm one is not an event. */
    });
}

/** The card over the end of a title, and the countdown that acts on it. */
function showUpNext() {
  if (!nextUp || !playing || cancelled.has(playing.setId) || countdown !== null) return;

  upNextTitle.textContent = [nextUp.show, episodeLabel(nextUp), nextUp.title]
    .filter(Boolean)
    .join(" · ");
  upNext.hidden = false;
  showHud();

  let left = 10;
  upNextIn.textContent = `starting in ${left}…`;
  countdown = setInterval(() => {
    left -= 1;
    upNextIn.textContent = `starting in ${left}…`;
    if (left <= 0) playNext();
  }, 1000);
}

function hideUpNext() {
  clearInterval(countdown);
  countdown = null;
  upNext.hidden = true;
}

function playNext() {
  const next = nextUp;
  hideUpNext();
  if (!next) return;
  // Through the page rather than straight into `openPlayer`, so whatever
  // opened this player can work out what follows *that* one.
  if (onOpenNext) onOpenNext(next);
  else openPlayer(next);
}

/** How much is held, and whether the browser thinks that is enough. */
function refreshPreload() {
  preload.textContent = preloadReadout(
    video.readyState,
    bufferedAhead(video.buffered, video.currentTime),
  );
}

/** Opens the dialog on `set` and starts it playing, whichever way it plays. */
export function openPlayer(set, options = {}) {
  stop();
  hideUpNext();
  nextUp = options.next ?? null;
  onOpenNext = options.onOpenNext ?? null;
  preloaded = null;
  attachSubtitles(set);
  void showSummary(set);
  void offerAudioTracks(set);
  now.textContent = [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");

  const warning = noteFor(set);
  note.textContent = warning ?? "";
  note.hidden = warning === null;
  playing = set;
  base = 0;
  // Reset before the chooser answers: a title opened while the previous one's
  // list is still in hand must not start on the previous one's ordinal.
  audioTrack = 0;
  audio.hidden = true;
  refreshPreload();
  refreshWatchlist();
  startSaving();
  showHud();
  dialog.showModal();

  // Where this profile left off, if that is a place worth going back to —
  // `resume-point.js` decides what counts. Announced rather than done
  // silently, because a film that opens 34 minutes in looks broken.
  const resume = resumeAt(state.progressOf(set.setId)) ?? 0;

  if (warning === null) {
    jump.hidden = true;
    capBits = null;
    // Watched too: the original is the stream most likely to be too much for
    // a link, since nothing caps what it was mastered at.
    watch.begin({ capBits: null, sourceBits: sourceBitrate(set) });
    video.src = `/api/sets/${encodeURIComponent(set.setId)}/stream`;
    if (resume > 0) {
      // Once metadata is in: `currentTime` before then is discarded, which is
      // how a resume silently becomes a start.
      video.addEventListener("loadedmetadata", () => (video.currentTime = resume), { once: true });
      note.textContent = `Carrying on from ${clockTime(resume)}.`;
      note.hidden = false;
    }
    // No `play()`. `preload="auto"` fills the buffer; the viewer starts it.
    refreshEnds();
    return;
  }

  showJump(set);
  // A conversion resumes by starting there, which it already knows how to do.
  base = resume;
  convert(set, resume, resume > 0 ? `${warning} Carrying on from ${clockTime(resume)}.` : warning);
}

/**
 * Asks what this title's audio is and offers it, if there is a choice.
 *
 * Fetched as the dialog opens rather than held in the catalog: the answer
 * comes from probing the file, which is a second or two the first time and
 * cached after. The menu appears when it arrives; playback never waits for it.
 */
async function offerAudioTracks(set) {
  const found = await loadAudioTracks(set.setId);
  // The viewer may have closed this title, or opened another, while the probe
  // was running. Answering into the wrong film would be worse than not
  // answering at all.
  if (playing?.setId !== set.setId) return;

  audioTrack = defaultTrack(found);
  audio.hidden = !fillChooser(audioTrackPicker, found, audioTrack);
}

/**
 * The viewer picked a language.
 *
 * Always a conversion, even for a title that was playing perfectly well on
 * its own: Chrome and Firefox do not implement `audioTracks`, so there is no
 * way to tell a `<video>` to use a different stream of the file it already
 * has. Keeps the viewer's place, exactly as a seek does.
 */
audioTrackPicker.addEventListener("change", () => {
  const chosen = Number(audioTrackPicker.value);
  if (!playing || !Number.isInteger(chosen) || chosen === audioTrack) return;
  audioTrack = chosen;

  showJump(playing);
  convert(
    playing,
    filmTime(),
    noteFor(playing) ??
      "Converting as you watch: the browser cannot change audio track on its own.",
    capBits ?? undefined,
  );
});

// Notes stopped opening by themselves when the player went fullscreen: a page
// of text laid over a picture is not a caption, it is something in the way.
/** Whether this title is on the list, on the button that changes it. */
function refreshWatchlist() {
  const listed = playing !== null && state.isWatchlisted(playing.setId);
  watchlistButton.setAttribute("aria-pressed", String(listed));
  watchlistButton.textContent = listed ? "On the list" : "Watchlist";
}

watchlistButton.addEventListener("click", () => {
  if (!playing) return;
  state.setWatchlisted(playing.setId, !state.isWatchlisted(playing.setId));
  refreshWatchlist();
});

addToButton.addEventListener("click", () => {
  if (!playing) return;
  const lists = state.collections();
  if (lists.length === 0) {
    window.alert("No lists yet. Make one on the Collections shelf.");
    return;
  }
  // A prompt of names rather than a menu built here: this is a list of one
  // from a handful, and the platform's own is reachable by keyboard for free.
  const names = lists.map((list, index) => `${index + 1}. ${list.name}`).join("\n");
  const answer = window.prompt(`Add to which list?\n\n${names}\n\nNumber:`);
  if (answer === null) return;
  const chosen = lists[Number(answer) - 1];
  if (!chosen) return;
  state.setInCollection(chosen.id, playing.setId, true);
});

document.getElementById("up-next-play").addEventListener("click", playNext);
document.getElementById("up-next-cancel").addEventListener("click", () => {
  // Remembered for this title, so watching the last minute again does not
  // start the countdown a second time.
  if (playing) cancelled.add(playing.setId);
  hideUpNext();
});

notesButton.addEventListener("click", () => showNotes(notesPanel.hidden));
notesClose.addEventListener("click", () => showNotes(false));

/**
 * The floating controls, and when they are in the way.
 *
 * Hidden once the pointer has rested, shown again the moment it moves. Never
 * hidden while playback is paused, because a paused picture is not what the
 * viewer is looking at, and never while focus is inside them, or a viewer
 * tabbing through the controls would watch them vanish mid-tab.
 */
const HUD_REST_MS = 2600;
let hudTimer = null;

/** Whether something is going on that the controls must not hide during. */
function hudIsHeld() {
  if (video.paused) return true;
  const focused = document.activeElement;
  // The video itself holds focus for the whole time it is playing, so it does
  // not count; anything else focused inside the dialog is a control someone
  // is using.
  return focused !== null && focused !== video && dialog.contains(focused);
}

function showHud() {
  dialog.classList.remove("resting");
  clearTimeout(hudTimer);
  if (hudIsHeld()) return;
  hudTimer = setTimeout(() => dialog.classList.add("resting"), HUD_REST_MS);
}

// These bubble, so the dialog hears them wherever they happen inside it.
for (const event of ["pointermove", "pointerdown", "focusin", "focusout"]) {
  dialog.addEventListener(event, showHud);
}
// These do not bubble — they are media events on the element itself, and a
// listener on the dialog would never hear one.
for (const event of ["play", "pause", "ratechange"]) {
  video.addEventListener(event, showHud);
}
video.addEventListener("ratechange", refreshEnds);
video.addEventListener("pause", () => saveProgress());

/** The last thirty seconds is where a title is over and the next one begins. */
const UP_NEXT_SECONDS = 30;

video.addEventListener("timeupdate", () => {
  preloadNext();
  const runtime = runtimeOf(playing);
  if (runtime > 0 && runtime - filmTime() <= UP_NEXT_SECONDS) showUpNext();
});

// The end, however it arrives: a title that ran out rather than one seeked
// past its last frame.
video.addEventListener("ended", () => {
  saveProgress();
  showUpNext();
});

// Closing the tab is the moment a position matters most and the moment an
// ordinary request does not survive. See `flushProgress`.
window.addEventListener("pagehide", () => saveProgress(true));

for (const event of [
  "progress",
  "loadstart",
  "loadedmetadata",
  "canplay",
  "canplaythrough",
  "waiting",
  "playing",
  "seeking",
  "seeked",
  "emptied",
]) {
  video.addEventListener(event, refreshPreload);
}

// The slider only follows playback while the viewer is not holding it.
video.addEventListener("timeupdate", () => {
  refreshEnds();
  refreshPreload();
  if (jump.hidden || document.activeElement === jumpTo) return;
  jumpTo.value = String(Math.floor(filmTime()));
  jumpAt.textContent = `${clockTime(filmTime())} / ${clockTime(Number(jumpTo.max))}`;
});

jumpTo.addEventListener("input", () => {
  jumpAt.textContent = `${clockTime(Number(jumpTo.value))} / ${clockTime(Number(jumpTo.max))}`;
});

// `change`, not `input`: restarting an encode on every pixel of a drag would
// start dozens of them and finish none.
jumpTo.addEventListener("change", () => {
  if (!playing) return;
  // Keeps whatever cap the link was found to need; a seek is not new evidence
  // that the connection got better.
  convert(playing, Number(jumpTo.value), noteFor(playing), capBits ?? undefined);
});

/**
 * The link is losing ground: move to something it can carry.
 *
 * Deliberately keeps the viewer's place — `filmTime()`, not zero — so the
 * interruption costs seconds rather than the film starting again.
 */
const watch = watchPlayback({
  video,
  onSwitch: (targetBits) => {
    if (!playing) return;
    showJump(playing);
    convert(
      playing,
      filmTime(),
      `The connection is slower than this title needs, so it is being converted` +
        ` to ${(targetBits / 1e6).toFixed(1)} Mbit/s.`,
      targetBits,
    );
  },
  onExhausted: () => {
    note.textContent = "This connection is too slow for this title, even at the lowest quality.";
    note.hidden = false;
  },
});

// Closing the tab never fires the dialog's `close`, and a transcode nobody
// released keeps the encoder until the reaper notices.
window.addEventListener("pagehide", stop);

document.getElementById("close").addEventListener("click", () => dialog.close());
dialog.addEventListener("close", () => {
  // Drops the connection so the server stops pulling bytes from Telegram, and
  // the transcode so it stops encoding for nobody.
  stop();
  for (const track of [...video.querySelectorAll("track")]) track.remove();
  // Before `playing` is cleared, or there is nothing left to record against.
  saveProgress(true);
  clearInterval(saveTimer);
  hideUpNext();
  nextUp = null;
  onOpenNext = null;
  preloaded = null;

  showNotes(false);
  summaryBox.textContent = "";
  notesButton.hidden = true;
  jump.hidden = true;
  audio.hidden = true;
  ends.textContent = "";
  preload.textContent = "";
  playing = null;
  capBits = null;
  audioTrack = 0;
  clearTimeout(hudTimer);
  dialog.classList.remove("resting");
  watch.begin({ capBits: null, sourceBits: null });
});
