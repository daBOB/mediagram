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
import { conversionNote } from "./playable.js";
import { playTranscoded, warmTranscode } from "./hls-playback.js";
import { sourceBitrate, watchPlayback } from "./adapt-playback.js";
import { clockTime, endsAt, episodeLabel, technicalLine } from "./format.js";
import { languageLabel } from "./language-label.js";
import { defaultTrack, fillChooser, loadAudioTracks, trackForLanguage } from "./audio-chooser.js";
import { bufferedAhead, preloadReadout } from "./preload-readout.js";
import { seekModel, skipTo } from "./seek-model.js";
import { mountTransport } from "./transport.js";
import { keyAction, wantsKeys } from "./player-keys.js";
import { renderNotes } from "./notes-view.js";
import { COUNTDOWN_SECONDS, upNextPhase } from "./up-next.js";
import { autoplayReady } from "./autoplay.js";
import * as state from "./watch-state.js";
import { isFinished, resumeAt, trustedRuntime } from "./resume-point.js";
import { scopeOf } from "./preference-scope.js";
import { placeCues } from "./subtitle-style.js";
import { subtitlePanel } from "./subtitle-panel.js";
import { thumbStrip } from "./thumb-strip.js";

const dialog = document.getElementById("player");
const video = document.getElementById("video");
const note = document.getElementById("note");
const tech = document.getElementById("tech");
const summaryBox = document.getElementById("summary");
const now = document.getElementById("now");
const seek = document.getElementById("seek");
const seekTo = document.getElementById("seek-to");
const atNow = document.getElementById("at-now");
const atEnd = document.getElementById("at-end");
const ends = document.getElementById("ends");
const audio = document.getElementById("audio");
const audioTrackPicker = document.getElementById("audio-track");
const notesButton = document.getElementById("notes");
const preload = document.getElementById("preload");
const notesPanel = document.getElementById("notes-panel");
const notesClose = document.getElementById("notes-close");
const watchlistButton = document.getElementById("watchlist");
const kidsButton = document.getElementById("kids");
const playNextButton = document.getElementById("play-next");
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
/**
 * A conversion started for the title that is about to be wanted.
 *
 * `{ setId, release }`, or `null`. Held so it can be let go of — a warmed
 * session holds a hardware encoder exactly like a played one, and one nobody
 * ever goes on to watch must not keep it.
 */
let warm = null;

/** Lets go of a warmed conversion, if there is one. */
function dropWarm() {
  warm?.release();
  warm = null;
}

/**
 * Starts the next title converting, while this one finishes.
 *
 * Called when the up-next panel appears, which is half a minute out — long
 * enough to be worth it and short enough that a viewer who wanders off has
 * cost one session for forty seconds rather than an encoder for an hour.
 *
 * Only for a title that needs converting. A direct one has nothing to start:
 * `preloadNext` already warms its first bytes into the server's chunk cache,
 * and it does so *earlier* than this, whenever the current title is
 * comfortably buffered.
 *
 * The arguments have to match the ones `openPlayer` will use or the session
 * ids differ and this warms something nobody asks for — so the offset is
 * worked out the same way, and the audio track is the first, which is what an
 * open starts on before the chooser has answered.
 */
function warmNext() {
  if (!nextUp || warm?.setId === nextUp.setId) return;
  const next = nextUp;
  if (noteFor(next) === null) return;

  dropWarm();
  const at = resumeAt(state.progressOf(next.setId)) ?? 0;
  void warmTranscode(next.setId, { seekSeconds: at })
    .then((release) => {
      // The viewer may have moved on, or cancelled, while ffmpeg was starting.
      if (nextUp?.setId !== next.setId) release();
      else warm = { setId: next.setId, release };
    })
    .catch(() => {
      /* A conversion that would not start now is one the open will report. */
    });
}

/** What the last title left attached, or `null` when it was played directly. */
let detach = null;
/** The set being played, and where in it the current conversion started. */
let playing = null;
let base = 0;
/** The cap the running conversion was given, or `null` while playing direct. */
let capBits = null;
/**
 * Whether ffmpeg is making what is on screen.
 *
 * Decides two things that have no other way of knowing: whether the media
 * element's own `duration` is the film's or only as much of it as has been
 * encoded, and whether a seek is a `currentTime` or a new conversion.
 */
let converting = false;
/**
 * What a choice made on the open title is remembered against.
 *
 * `preference-scope.js` decides: a series, a course, or the title itself. Held
 * rather than recomputed because every picker asks for it, and it cannot
 * change while one title is open.
 */
let scope = null;
/**
 * Where this show's subtitles sit along the clock.
 *
 * Held rather than read from the panel each time, because the thing that most
 * needs it is a `load` event on a track and that fires long after the viewer
 * set it.
 */
let placement = { offset: 0 };

/**
 * Puts every attached track where the panel says.
 *
 * Called far more often than it does anything, on purpose. A cue does not
 * exist until its file has been fetched, which only happens once a track
 * stops being `disabled`; a conversion re-attaches every track from scratch;
 * and turning subtitles back on re-enables a track that may or may not still
 * hold its cues. `placeCues` is idempotent so all three can simply ask.
 */
function placeSubtitles() {
  for (const track of video.textTracks) placeCues(track, placement);
}
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
function noteFor(set, copied = false) {
  const decision = playbackFor(set);
  return decision.kind === "direct" ? null : conversionNote(`${decision.reason}.`, copied);
}

/**
 * Attaches whatever subtitle tracks the catalog said this set has.
 *
 * Deliberately marks none of them `default`. That attribute asks the browser
 * to pick a track for itself, and it does so during resource selection —
 * *after* this player has set the modes it wants, so a viewer who turned
 * subtitles off for a series watched them come back on every episode, with
 * the picker still saying "Off". The choice is `transport.offerSubtitles`'s
 * alone now, which is also the only place that knows what was remembered.
 */
function attachSubtitles(set) {
  for (const existing of [...video.querySelectorAll("track")]) existing.remove();
  for (const lang of set.subtitles ?? []) {
    const track = document.createElement("track");
    track.kind = "subtitles";
    track.srclang = lang;
    track.label = languageLabel(lang, "Subtitles");
    track.src = `/api/sets/${encodeURIComponent(set.setId)}/subtitles/${lang}.vtt`;
    track.addEventListener("load", placeSubtitles);
    video.append(track);
  }
}

/** Fetched on open rather than carried in the catalog, which would be large. */
async function showSummary(set) {
  showNotes(false);
  // A panel left open belongs to the title it was opened on.
  cuePanel.panel.hidden = true;
  cuePanel.trigger.setAttribute("aria-expanded", "false");
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
  converting = true;
  base = seconds;
  capBits = maxrateBits ?? null;
  watch.begin({ capBits, sourceBits: sourceBitrate(set) });
  /**
   * The sentence, which the server may correct.
   *
   * The catalog can say a title needs converting but not whether the picture
   * has to be rebuilt to do it — only the server decides that, from the same
   * policy, once asked. So this starts as what the catalog knows and is
   * replaced when the answer comes back. Held in a variable because the
   * `then` below restores it, and restoring the stale one would undo the
   * correction a moment after making it.
   */
  let said = warning;
  // Shown unconditionally: a title that was playing directly has its note
  // hidden, and a switch that explains itself invisibly explains nothing.
  note.textContent = `${said} Starting at ${clockTime(seconds)}…`;
  note.hidden = false;

  playTranscoded(video, set.setId, {
    seekSeconds: seconds,
    maxrateBits,
    audioTrack,
    onStarted: ({ copied }) => {
      said = noteFor(set, copied) ?? said;
      note.textContent = `${said} Starting at ${clockTime(seconds)}…`;
    },
    onFatal: (error) => {
      note.textContent = `The conversion stopped: ${error.message}. Pick a position to start it again.`;
    },
  })
    .then((release) => {
      detach = release;
      // After the join, never before. A release that took the last watcher
      // would stop the very session this open just joined.
      if (warm?.setId === set.setId) dropWarm();
      note.textContent = said;
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

/**
 * The runtime to scale a bar and a finishing time against.
 *
 * The catalog's answer first, and the media element's only when playing
 * directly — a conversion's `duration` is as long as ffmpeg has written and
 * no longer, so a film would claim to end four minutes from now and keep
 * moving. Nought when nothing knows, which the callers treat as "say
 * nothing" rather than as a length.
 */
function runtimeSeconds() {
  const catalogued = Number(playing?.duration) || 0;
  if (catalogued > 0) return catalogued;
  if (!converting && Number.isFinite(video.duration)) return video.duration;
  return 0;
}

/**
 * Draws the one timeline, whichever way the title is playing.
 *
 * Both ways used to have their own. A file played directly had the video
 * element's bar; a conversion had this one, because the element's covers only
 * what ffmpeg has written and its clock counts from wherever the encode
 * started. Two bars, one of them lying, and which one depended on a fact no
 * viewer could see.
 *
 * Now there is one, scaled to the film. The element's timeline and clocks are
 * hidden in the stylesheet for every title, not only converted ones.
 */
function refreshSeek() {
  const bar = seekModel({
    runtime: runtimeSeconds(),
    at: filmTime(),
    ahead: bufferedAhead(video.buffered, video.currentTime),
    converting,
    held,
  });
  seek.hidden = !bar.usable;
  if (!bar.usable) {
    atNow.textContent = "";
    atEnd.textContent = "";
    return;
  }
  seekTo.max = String(bar.max);
  // A bar the viewer is holding belongs to the viewer: it is theirs to move
  // and its own `input` listener says what it reads while they move it.
  //
  // Both tests, because either alone has a gap. Focus is how a keyboard holds
  // it; `:active` is how a pointer does. A drag that satisfied neither would
  // be fought by `timeupdate` four times a second, snapping the thumb back to
  // the playhead under the viewer's finger.
  if (document.activeElement === seekTo || seekTo.matches(":active")) return;
  seekTo.value = String(bar.value);
  showSeekAt(bar);
}

/** The clocks at either end of the transport, and the paint on the track. */
function showSeekAt(bar) {
  atNow.textContent = bar.elapsed;
  atEnd.textContent = bar.total;
  // A range's own value is a number of seconds, which is not how anybody says
  // where they are in a film. The two ends as one line, because they are read
  // out together and printed apart.
  seekTo.setAttribute("aria-valuetext", bar.label);
  seekTo.style.setProperty("--played", `${(bar.played * 100).toFixed(3)}%`);
  seekTo.style.setProperty("--buffered", `${(bar.buffered * 100).toFixed(3)}%`);
}

/**
 * The buttons the browser used to lend us. Mounted once; it holds no title.
 *
 * Asks rather than reaches: `filmTime` and `runtimeSeconds` are the film's
 * answers, and a conversion's own clock is not.
 */
const cuePanel = subtitlePanel({
  recall: (name) => state.preferenceOf(scope, name),
  remember: (name, value) => state.setPreference(scope, name, value),
  onPlacement: (where) => {
    placement = where;
    placeSubtitles();
  },
});
document.getElementById("subs").after(cuePanel.trigger);
document.querySelector(".hud-bottom").prepend(cuePanel.panel);

/**
 * Cues arriving, or a track being switched on.
 *
 * `load` fires on the element when a file has been fetched and parsed;
 * `change` fires on the list when a mode changes, which is the case `load`
 * misses — a track that was already loaded, disabled, and turned back on.
 */
video.textTracks.addEventListener("change", placeSubtitles);

/**
 * The frame under the pointer, above the scrub bar.
 *
 * `positionAt` is the player's answer because only it knows whether this
 * title's clock is the film's or an encode's — the bar is scaled to the film
 * either way, so a fraction of it is a fraction of the runtime.
 */
const thumbs = thumbStrip({
  bar: seek,
  slider: seekTo,
  positionAt: (fraction) => fraction * runtimeSeconds(),
});

const transport = mountTransport({
  video,
  onSeekTo: (seconds) => seekFilmTo(skipTo(seconds, 0, runtimeSeconds())),
  filmTime,
  runtime: runtimeSeconds,
  // What "this show" means is the player's question — the bar only asks.
  recall: (name) => state.preferenceOf(scope, name),
  remember: (name, value) => state.setPreference(scope, name, value),
});

/**
 * Go to `seconds` of the film, by whichever route this title plays.
 *
 * The one place that answers it, because the bar, the skip buttons and the
 * keyboard all ask. A direct file moves its playhead; a conversion has only
 * encoded what it has encoded, so landing elsewhere means starting ffmpeg
 * again there.
 */
function seekFilmTo(seconds) {
  if (!playing) return;
  if (!converting) {
    video.currentTime = seconds;
    return;
  }
  // Keeps whatever cap the link was found to need; a seek is not new evidence
  // that the connection got better.
  convert(playing, seconds, noteFor(playing) ?? "", capBits ?? undefined);
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
  const runtime = runtimeSeconds();
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
    // The position goes, because a finished title has nowhere to resume to.
    // The fact that it finished stays, because otherwise nothing anywhere
    // would remember it was ever watched.
    state.clearProgress(playing.setId);
    if (!state.isWatched(playing.setId)) {
      state.setWatched(playing.setId, true);
      keptChanged();
    }
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
/**
 * Offers the next title, and — only once this one has ended — starts it.
 *
 * The two used to be one call, which is how a ten second countdown came to
 * expire with twenty seconds of the episode still playing. `upNextPhase`
 * decides which of them is wanted; this does it.
 */
/**
 * The phase the panel is currently drawn in, so it is drawn once per change.
 *
 * `timeupdate` runs four times a second, and for the last half minute of
 * every title the phase it reports is the same `waiting` each time. Redrawing
 * on each of those repeated the card a hundred and twenty times — and, worse,
 * called `showHud` with it, which re-arms the rest timer: the controls could
 * never fade for the whole of a title's last thirty seconds.
 */
let shownPhase = null;

function refreshUpNext({ ended = false } = {}) {
  const runtime = runtimeOf(playing);
  const phase = upNextPhase({
    hasNext: nextUp !== null && playing !== null,
    cancelled: playing !== null && cancelled.has(playing.setId),
    remainingSeconds: runtime > 0 ? runtime - filmTime() : null,
    ended,
  });

  if (phase === shownPhase) return;
  shownPhase = phase;
  if (phase === "hidden") return;

  upNextTitle.textContent = titleLine(nextUp);
  upNext.hidden = false;
  showHud();

  // A running timer owns the line, whatever the phase says.
  if (countdown !== null) return;
  if (phase === "waiting") {
    // A heads-up, and a way past the credits for anyone who wants one.
    // Nothing *plays* until the title is over — but the next one can start
    // converting now, so that when it does there is nothing to wait for.
    upNextIn.textContent = "when this ends";
    warmNext();
    return;
  }

  let left = COUNTDOWN_SECONDS;
  upNextIn.textContent = `starting in ${left}…`;
  countdown = setInterval(() => {
    left -= 1;
    upNextIn.textContent = `starting in ${left}…`;
    if (left <= 0) playNext("buffered");
  }, 1000);
}

/** How a title is named to the viewer: the HUD, the up-next card, a tooltip. */
function titleLine(set) {
  return [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");
}

/** The standing offer, which cancelling the countdown does not withdraw. */
function refreshPlayNext() {
  const has = nextUp !== null;
  playNextButton.hidden = !has;
  // The rail has no room to spell out a title, and the panel is not always
  // showing, so the name lives on the tooltip.
  playNextButton.title = has ? titleLine(nextUp) : "";
}

function hideUpNext() {
  clearInterval(countdown);
  countdown = null;
  upNext.hidden = true;
  shownPhase = null;
}

/**
 * @param {"buffered"|"asap"} how the countdown running out waits for a
 * buffer, because nobody is watching the screen; a viewer who pressed a
 * button is, and gets the picture as soon as the browser can give it.
 */
function playNext(how = "asap") {
  const next = nextUp;
  hideUpNext();
  if (!next) return;
  // Through the page rather than straight into `openPlayer`, so whatever
  // opened this player can work out what follows *that* one.
  if (onOpenNext) onOpenNext(next, { autoplay: how });
  else openPlayer(next, { autoplay: how });
}

/**
 * Whether the element is waiting on data right now.
 *
 * Taken from the events that mean exactly that, rather than inferred from
 * `readyState`: see `preloadReadout`. A title fed by hls.js sits below
 * `HAVE_ENOUGH_DATA` for its whole running time because the buffer is capped
 * on purpose, and reading that as "buffering" described a film playing from
 * local disk as though it were stuck.
 */
let starved = false;

/**
 * Whether the title playing is on the server's disk in full.
 *
 * Asked as the dialog opens rather than read from the catalog, which the page
 * fetched once at load and which still says "streaming" about an episode that
 * finished caching since.
 *
 * True of a conversion as much as of a file played directly: the encoder
 * reads the same cache, so nothing it waits on is the network, and a seek
 * restarts it from disk too. Leaving conversions out made every held
 * Matroska title on a browser without HEVC read as streaming.
 */
let held = false;

/** Asks whether `set` is held, and redraws if it is still the one playing. */
async function askHeld(set) {
  try {
    const response = await fetch(`/api/sets/${encodeURIComponent(set.setId)}/held`);
    const answer = response.ok ? await response.json() : null;
    // The viewer may have moved on while this was asked.
    if (playing?.setId !== set.setId) return;
    held = answer?.held === true;
  } catch {
    // Not knowing is the old behaviour: the readout shows the buffer.
    return;
  }
  refreshPreload();
  refreshSeek();
}

/**
 * A title that is going to start itself, and the wait before it does.
 *
 * `null` while nothing is waiting. Unattended starts hold for a buffer — a
 * viewer whose episode ended a minute ago would rather the next one arrive
 * whole than arrive at once and stop again — and an asked-for start goes as
 * soon as the browser can, because somebody is looking at the screen.
 */
let waitingToStart = null;

function stopWaitingToStart() {
  if (waitingToStart === null) return;
  clearInterval(waitingToStart.timer);
  waitingToStart = null;
  refreshPreload();
}

/**
 * Starts `mode` — "buffered" or "asap" — once the title is ready for it.
 *
 * Polled rather than driven by events: the condition is a buffer length,
 * which no single event announces, and half a second is far below what a
 * viewer notices while nothing is on screen anyway.
 */
function startWhenReady(mode) {
  stopWaitingToStart();
  const began = Date.now();

  const look = () => {
    if (!playing || waitingToStart === null) return stopWaitingToStart();

    const ahead = bufferedAhead(video.buffered, video.currentTime);
    const runtime = runtimeOf(playing);
    const ready =
      mode === "asap"
        ? video.readyState >= 3
        : autoplayReady({
            ahead,
            remaining: runtime > 0 ? runtime - filmTime() : null,
            waitedMs: Date.now() - began,
          });
    if (!ready) return;

    stopWaitingToStart();
    // A browser that refuses is not an error worth showing: the viewer still
    // has a play button, and the title is loaded and waiting under it.
    void video.play().catch(() => {});
  };

  waitingToStart = { mode, timer: setInterval(look, 500) };
  refreshPreload();
  look();
}

/** How much is held, and whether the player is waiting on any of it. */
function refreshPreload() {
  // `getVideoPlaybackQuality` is absent on older engines and on an element
  // with no video track at all, so it is asked for rather than assumed.
  const quality = video.getVideoPlaybackQuality?.();
  preload.textContent = preloadReadout({
    readyState: video.readyState,
    ahead: bufferedAhead(video.buffered, video.currentTime),
    starved,
    // Only the buffered wait is worth announcing. "asap" is over in the time
    // it takes to say it.
    awaitingStart: waitingToStart?.mode === "buffered",
    // The watch is the only thing measuring the link, and it measures whether
    // or not it ever decides to switch. Reading its rate here is what turns a
    // decision the viewer never sees into one they can.
    fillRate: watch.fillRate(),
    dropped: quality?.droppedVideoFrames,
    held,
  });
}

/** Opens the dialog on `set` and starts it playing, whichever way it plays. */
export function openPlayer(set, options = {}) {
  stop();
  hideUpNext();
  nextUp = options.next ?? null;
  onOpenNext = options.onOpenNext ?? null;
  preloaded = null;
  // First of all, because everything below that asks what this viewer chose
  // asks against it — a scope set later would answer for the previous title.
  scope = scopeOf(set);
  attachSubtitles(set);
  // After the tracks are attached, because the picker is built from them, and
  // before anything plays, so nothing is heard at the wrong speed.
  // Before the tracks are offered, so a cue that is already in hand is placed
  // rather than shown at the old show's offset for a moment.
  placement = cuePanel.recallFor();
  transport.offerSubtitles();
  cuePanel.trigger.hidden = document.getElementById("subs").hidden;
  placeSubtitles();
  transport.recallSpeed();
  transport.recallFraming();
  transport.refresh();
  void thumbs.open(set);
  void showSummary(set);
  void offerAudioTracks(set);
  now.textContent = titleLine(set);
  tech.textContent = technicalLine(set);

  const warning = noteFor(set);
  note.textContent = warning ?? "";
  note.hidden = warning === null;
  playing = set;
  base = 0;
  // Reset before the chooser answers: a title opened while the previous one's
  // list is still in hand must not start on the previous one's ordinal.
  audioTrack = 0;
  audio.hidden = true;
  starved = false;
  held = false;
  shownPhase = null;
  stopWaitingToStart();
  refreshPreload();
  void askHeld(set);
  refreshWatchlist();
  refreshKids();
  refreshPlayNext();
  startSaving();
  showHud();
  dialog.showModal();

  // Where this profile left off, if that is a place worth going back to —
  // `resume-point.js` decides what counts. Announced rather than done
  // silently, because a film that opens 34 minutes in looks broken.
  const resume = resumeAt(state.progressOf(set.setId)) ?? 0;
  // Armed before the source is attached, so nothing can become ready in the
  // gap between setting it and starting to watch for it.
  const autoplay = options.autoplay ?? null;

  if (warning === null) {
    converting = false;
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
    // No `play()` unless this title started itself: `preload="auto"` fills
    // the buffer and the viewer starts it, which is the whole behaviour for
    // anything opened by hand.
    if (autoplay) startWhenReady(autoplay);
    refreshEnds();
    return;
  }

  refreshSeek();
  // A conversion resumes by starting there, which it already knows how to do.
  base = resume;
  convert(set, resume, resume > 0 ? `${warning} Carrying on from ${clockTime(resume)}.` : warning);
  if (autoplay) startWhenReady(autoplay);
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

  /**
   * The language this viewer chose for this show, if the file still has it.
   *
   * Falls back to the file's own default rather than to the first stream, so
   * a re-upload that dropped a language leaves a title that opens correctly
   * instead of one that opens in a commentary.
   */
  const remembered = trackForLanguage(found, state.preferenceOf(scope, "audio"));
  audioTrack = remembered ?? defaultTrack(found);
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
  // The language, never the ordinal — see `trackForLanguage`. Taken from the
  // option's own label rather than kept in a second list beside the menu.
  const picked = audioTrackPicker.selectedOptions[0]?.dataset.lang;
  if (picked) state.setPreference(scope, "audio", picked);

  refreshSeek();
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

/**
 * Says a shelf built from marks has changed.
 *
 * The player owns the buttons; the masthead owns the counts beside them. An
 * event rather than another callback through `openPlayer`, because nothing
 * about playing a title needs to know a number on a shelf is stale.
 */
function keptChanged() {
  document.dispatchEvent(new CustomEvent("mediagram:kept-changed"));
}

/** Whether this title is a child's, and the way to say it is or is not. */
function refreshKids() {
  const marked = playing !== null && state.isKids(playing.setId);
  kidsButton.setAttribute("aria-pressed", String(marked));
  kidsButton.textContent = marked ? "For kids" : "Kids";
}

watchlistButton.addEventListener("click", () => {
  if (!playing) return;
  state.setWatchlisted(playing.setId, !state.isWatchlisted(playing.setId));
  refreshWatchlist();
  keptChanged();
});

// Marked here rather than on a shelf, because this is where a viewer is when
// they find out what a film actually is.
kidsButton.addEventListener("click", () => {
  if (!playing) return;
  state.setKids(playing.setId, !state.isKids(playing.setId));
  refreshKids();
  keptChanged();
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

// Wrapped, not passed: a listener hands its event to the function, and
// `playNext(MouseEvent)` would take the event for the kind of start wanted.
document.getElementById("up-next-play").addEventListener("click", () => playNext("asap"));
playNextButton.addEventListener("click", () => playNext("asap"));
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

/**
 * The keyboard, which the native controls used to bring with them.
 *
 * `player-keys.js` decides what a keystroke means; this does it and puts the
 * rails back up, because a viewer who just skipped ten seconds wants to see
 * where they landed.
 */
dialog.addEventListener("keydown", (event) => {
  const target = event.target;
  const action = keyAction({
    key: event.key,
    ctrlKey: event.ctrlKey,
    altKey: event.altKey,
    metaKey: event.metaKey,
    // The notes column counts as a field of its own: it is several screens of
    // text, and space is how a reader gets down a page of it.
    inControl: wantsKeys(target) || notesPanel.contains(target),
    onButton: target?.tagName === "BUTTON",
  });
  if (action === null) return;
  // Only what is actually taken. A blanket `preventDefault` here would stop
  // every key this player has no opinion about, including the browser's.
  event.preventDefault();
  transport.act(action);
  showHud();
});
// These do not bubble — they are media events on the element itself, and a
// listener on the dialog would never hear one.
for (const event of ["play", "pause", "ratechange"]) {
  video.addEventListener(event, showHud);
}
video.addEventListener("ratechange", refreshEnds);
video.addEventListener("pause", () => saveProgress());

video.addEventListener("timeupdate", () => {
  preloadNext();
  // Whether this is near enough the end to offer anything is `upNextPhase`'s
  // to decide, not this listener's.
  refreshUpNext();
});

// The end, however it arrives: a title that ran out rather than one seeked
// past its last frame. This is the only thing that may start the next one.
video.addEventListener("ended", () => {
  saveProgress();
  refreshUpNext({ ended: true });
});

// Closing the tab is the moment a position matters most and the moment an
// ordinary request does not survive. See `flushProgress`.
window.addEventListener("pagehide", () => saveProgress(true));

// Registered before the refresh below, so the flag is already right by the
// time the readout is rebuilt from it.
// A viewer who presses play has done the thing the wait was waiting to do.
video.addEventListener("play", stopWaitingToStart);

const STARVED_BY = ["waiting", "stalled"];
const FED_BY = ["playing", "canplay", "canplaythrough", "seeked"];
for (const event of [...STARVED_BY, ...FED_BY]) {
  video.addEventListener(event, () => {
    starved = STARVED_BY.includes(event);
  });
}
// A new source is not a starved one: whatever the last title was doing, this
// one has not begun.
for (const event of ["loadstart", "emptied"]) {
  video.addEventListener(event, () => {
    starved = false;
  });
}

for (const event of [
  "progress",
  "loadstart",
  "loadedmetadata",
  "canplay",
  "canplaythrough",
  "waiting",
  "stalled",
  "playing",
  "seeking",
  "seeked",
  "emptied",
  // A runtime can arrive after the title does, and the bar is scaled to it.
  "durationchange",
]) {
  video.addEventListener(event, () => {
    refreshPreload();
    refreshSeek();
  });
}

// The slider only follows playback while the viewer is not holding it, which
// `refreshSeek` decides for itself.
video.addEventListener("timeupdate", () => {
  refreshEnds();
  refreshPreload();
  refreshSeek();
});

/**
 * The viewer is dragging.
 *
 * A file the server can seek moves under the thumb, the way the native bar
 * always did. A conversion does not: landing somewhere means starting ffmpeg
 * there, and every pixel of a drag would start an encode and finish none. So
 * it moves the clock and waits for `change`.
 */
seekTo.addEventListener("input", () => {
  const at = Number(seekTo.value);
  const bar = seekModel({
    runtime: runtimeSeconds(),
    at,
    ahead: bufferedAhead(video.buffered, video.currentTime),
    converting,
    held,
  });
  showSeekAt(bar);
  if (bar.seeksWhileDragging) seekFilmTo(at);
});

// Letting go. For a direct file this lands where the drag already went; for a
// conversion it is the whole of the seek.
seekTo.addEventListener("change", () => seekFilmTo(Number(seekTo.value)));

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
    refreshSeek();
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
  stopWaitingToStart();
  nextUp = null;
  onOpenNext = null;
  dropWarm();
  // The offer goes with the title it was an offer about.
  refreshPlayNext();
  preloaded = null;


  showNotes(false);
  // A panel left open belongs to the title it was opened on.
  cuePanel.panel.hidden = true;
  cuePanel.trigger.setAttribute("aria-expanded", "false");
  summaryBox.textContent = "";
  notesButton.hidden = true;
  seek.hidden = true;
  thumbs.hide();
  converting = false;
  held = false;
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
