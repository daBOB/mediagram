/**
 * The player dialog: one title at a time, direct or transcoded.
 *
 * Kept apart from the shelves because opening a title is where all the state
 * is. A set played directly is a `src` and nothing else; a set the browser
 * refuses is an hls.js instance and a running ffmpeg on the server, and both
 * have to be torn down when the dialog closes or the server keeps encoding
 * for a viewer who has gone.
 */

import { playbackFor } from "../link.js";
import { conversionNote } from "../playable.js";
import { playTranscoded } from "./streaming/hls-playback.js";
import { sourceBitrate, watchPlayback } from "./streaming/adapt-playback.js";
import { clockTime, endsAt, episodeLabel, technicalLine } from "../format.js";
import { languageLabel } from "../language-label.js";
import { defaultTrack, fillChooser, loadAudioTracks, trackIndexForLanguage } from "./audio-chooser.js";
import { bufferedAhead, preloadReadout } from "./preload-readout.js";
import { seekModel, skipTo } from "./seek-model.js";
import { mountTransport } from "./transport.js";
import { keyAction, wantsKeys } from "./player-keys.js";
import { mountPlayerNotes } from "./notes/player-notes.js";
import { mountPlayerLibraryMarks } from "./player-library-marks.js";
import { mountPlayerHud } from "./player-hud.js";
import { mountPlayerNextTitle } from "./player-next-title.js";
import { autoplayReady } from "./autoplay.js";
import * as state from "../watch-state.js";
import { isFinished, resumeAt, trustedRuntime } from "../resume-point.js";
import { scopeOf } from "./preference-scope.js";
import { placeCues } from "./subtitle-style.js";
import { subtitlePanel } from "./subtitle-panel.js";
import { thumbStrip } from "./thumb-strip.js";

let mounted = null;

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {Object} PlayerOptions
 * @property {"buffered"|"asap"|null} [autoplay]
 * @property {CatalogSet|null} [next]
 * @property {(set: CatalogSet, options: PlayerOptions) => void} [onOpenNext]
 */

/** Mount controls after the page exists. Repeated initialization is a no-op. */
export function initializePlayer() {
  if (mounted === null) mounted = mountPlayer();
}

/**
 * Opens a title on the player initialized by the application entrypoint.
 * @param {CatalogSet} set
 * @param {PlayerOptions} [options]
 */
export function openPlayer(set, options = {}) {
  if (mounted === null) throw new Error("The player has not been initialized");
  mounted.openPlayer(set, options);
}

function mountPlayer() {
  const dialog = document.getElementById("player");
  const video = document.getElementById("video");
  const notes = mountPlayerNotes({ dialog });
  const marks = mountPlayerLibraryMarks();
  const hud = mountPlayerHud({ dialog, video });
  const note = document.getElementById("note");
  const tech = document.getElementById("tech");
  const now = document.getElementById("now");
  const seek = document.getElementById("seek");
  const seekTo = document.getElementById("seek-to");
  const atNow = document.getElementById("at-now");
  const atEnd = document.getElementById("at-end");
  const ends = document.getElementById("ends");
  const audio = document.getElementById("audio");
  const audioTrackPicker = document.getElementById("audio-track");
  const preload = document.getElementById("preload");
  const upNext = mountPlayerNextTitle({
    showControls: () => hud.show(),
    openTitle: (set, options) => openPlayer(set, options),
  });

  /** A title owns its probes; each source owns startup, media and its session. */
  let title = null;
  let source = null;
  let manualPlayRequest = 0;
  let manualPlayNotice = null;
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
  /** The chosen ordinal is not applied until its source actually attaches. */
  let appliedAudioTrack = null;

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

  function stop() {
    const previous = source;
    manualPlayRequest++;
    manualPlayNotice = null;
    video.pause();
    source = null;
    appliedAudioTrack = null;
    previous?.controller.abort();
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
    const autoplay = waitingToStart?.mode ?? (!video.paused ? "asap" : null);
    stop();
    const operation = { controller: new AbortController(), audioTrack };
    source = operation;
    const current = () => source === operation && !operation.controller.signal.aborted;
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
      signal: operation.controller.signal,
      seekSeconds: seconds,
      maxrateBits,
      audioTrack,
      onStarted: ({ copied }) => {
        if (!current()) return;
        said = noteFor(set, copied) ?? said;
        note.textContent = `${said} Starting at ${clockTime(seconds)}…`;
      },
      onFatal: (error) => {
        if (!current()) return;
        appliedAudioTrack = null;
        operation.controller.abort();
        upNext.releaseWarm(set.setId);
        stopWaitingToStart();
        note.textContent = `The conversion stopped: ${error.message}. Pick a position to start it again.`;
      },
    })
      .then((release) => {
        if (!current()) return release();
        appliedAudioTrack = operation.audioTrack;
        // After the join, never before. A release that took the last watcher
        // would stop the very session this open just joined.
        upNext.releaseWarm(set.setId);
        note.textContent = said;
        refreshEnds();
      })
      .catch((error) => {
        if (!current()) return;
        operation.controller.abort();
        upNext.releaseWarm(set.setId);
        stopWaitingToStart();
        note.textContent = `Could not start the conversion: ${error.message}`;
      });
    if (autoplay) startWhenReady(autoplay);
    refreshUpNext({ ended: false });
  }

  /** Where the viewer is in the film, not in the current conversion. */
  function filmTime() {
    return source?.resume ?? base + (Number.isFinite(video.currentTime) ? video.currentTime : 0);
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
    return trustedRuntime({
      catalogued: playing?.duration,
      observed: video.duration,
      direct: playing !== null && !converting,
    });
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
    onPlay: playManually,
    onPause: () => { manualPlayRequest++; },
    onSeekTo: (seconds) => seekFilmTo(skipTo(seconds, 0, runtimeSeconds())),
    filmTime,
    runtime: runtimeSeconds,
    // What "this show" means is the player's question — the bar only asks.
    recall: (name) => state.preferenceOf(scope, name),
    remember: (name, value) => state.setPreference(scope, name, value),
  });

  /** A manual refusal stays retryable and belongs to this request and source. */
  async function playManually() {
    const operation = source;
    if (!operation || operation.controller.signal.aborted) return;
    const request = ++manualPlayRequest;
    const current = () => request === manualPlayRequest && source === operation && !operation.controller.signal.aborted;
    try {
      await video.play();
      if (!current()) return;
      if (manualPlayNotice && note.textContent === manualPlayNotice.message) {
        note.textContent = manualPlayNotice.previousText;
        note.hidden = manualPlayNotice.previousHidden;
      }
      manualPlayNotice = null;
    } catch (error) {
      if (!current() || error?.name === "AbortError") return;
      const message = "Could not start playback. Press Play to try again.";
      if (!manualPlayNotice || note.textContent !== manualPlayNotice.message) {
        manualPlayNotice = { message, previousText: note.textContent, previousHidden: note.hidden };
      }
      note.textContent = message;
      note.hidden = false;
    }
  }

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
      if (source?.resume != null) source.resume = seconds;
      video.currentTime = seconds;
      refreshUpNext({ ended: false });
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

  /**
   * Records where the viewer is.
   *
   * A title watched to the end is forgotten rather than recorded at its end:
   * the Continue shelf exists to hold things to go back to, and a finished film
   * sitting on it forever is the shelf slowly becoming useless.
   */
  function saveProgress(final = false) {
    if (!playing) return;
    const runtime = runtimeSeconds();
    const at = filmTime();
    if (isFinished(at, runtime)) {
      state.markFinished(playing.setId);
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

  function refreshUpNext({ ended = video.ended } = {}) {
    upNext.update({ runtime: runtimeSeconds(), at: filmTime(), ended });
  }

  /** How a title is named in the current-title HUD. */
  function titleLine(set) {
    return [set.show, episodeLabel(set), set.title].filter(Boolean).join(" · ");
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
  async function askHeld(set, signal) {
    try {
      const response = await fetch(`/api/sets/${encodeURIComponent(set.setId)}/held`);
      const answer = response.ok ? await response.json() : null;
      // The viewer may have moved on while this was asked.
      if (signal.aborted) return;
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
      const runtime = runtimeSeconds();
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

  /**
   * Opens the dialog on `set` and starts it playing, whichever way it plays.
   * @param {CatalogSet} set
   * @param {PlayerOptions} [options]
   */
  function openPlayer(set, options = {}) {
    saveProgress(true);
    stop();
    title?.abort();
    title = new AbortController();
    const signal = title.signal;
    stopWaitingToStart();
    // Install the complete title state before controls, media events or probes
    // can read it. Nothing below should observe the preceding title's values.
    playing = set;
    upNext.open(set, options);
    base = 0;
    capBits = null;
    converting = false;
    audioTrack = 0;
    audio.hidden = true;
    starved = false;
    held = false;
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
    void thumbs.open(set, signal);
    void notes.open(set);
    void offerAudioTracks(set, signal);
    now.textContent = titleLine(set);
    tech.textContent = technicalLine(set);

    const warning = noteFor(set);
    note.textContent = warning ?? "";
    note.hidden = warning === null;
    refreshPreload();
    void askHeld(set, signal);
    marks.open(set);
    startSaving();
    hud.open();
    dialog.showModal();

    // Where this profile left off, if that is a place worth going back to —
    // `resume-point.js` decides what counts. Announced rather than done
    // silently, because a film that opens 34 minutes in looks broken.
    const resume = resumeAt(state.progressOf(set.setId)) ?? 0;
    // Armed before the source is attached, so nothing can become ready in the
    // gap between setting it and starting to watch for it.
    const autoplay = options.autoplay ?? null;

    if (warning === null) {
      const operation = {
        controller: new AbortController(),
        audioTrack: 0,
        resume: resume > 0 ? resume : null,
      };
      source = operation;
      appliedAudioTrack = 0;
      // Watched too: the original is the stream most likely to be too much for
      // a link, since nothing caps what it was mastered at.
      watch.begin({ capBits: null, sourceBits: sourceBitrate(set) });
      if (resume > 0) {
        // Once metadata is in: `currentTime` before then is discarded, which is
        // how a resume silently becomes a start.
        video.addEventListener(
          "loadedmetadata",
          () => {
            const at = operation.resume;
            operation.resume = null;
            video.currentTime = at;
          },
          {
            once: true,
            signal: operation.controller.signal,
          },
        );
        note.textContent = `Carrying on from ${clockTime(resume)}.`;
        note.hidden = false;
      }
      video.src = `/api/sets/${encodeURIComponent(set.setId)}/stream`;
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
    convert(
      set,
      resume,
      resume > 0 ? `${warning} Carrying on from ${clockTime(resume)}.` : warning,
    );
    if (autoplay) startWhenReady(autoplay);
  }

  /**
   * Asks what this title's audio is and offers it, if there is a choice.
   *
   * Fetched as the dialog opens rather than held in the catalog: the answer
   * comes from probing the file, which is a second or two the first time and
   * cached after. The menu appears when it arrives; playback never waits for it.
   */
  async function offerAudioTracks(set, signal) {
    const found = await loadAudioTracks(set.setId);
    // The viewer may have closed this title, or opened another, while the probe
    // was running. Answering into the wrong film would be worse than not
    // answering at all.
    if (signal.aborted) return;

    /**
     * The language this viewer chose for this show, if the file still has it.
     *
     * Falls back to the file's own default rather than to the first stream, so
     * a re-upload that dropped a language leaves a title that opens correctly
     * instead of one that opens in a commentary.
     */
    const remembered = trackIndexForLanguage(found, state.preferenceOf(scope, "audio"));
    audioTrack = remembered ?? defaultTrack(found);
    audio.hidden = !fillChooser(audioTrackPicker, found, audioTrack);
    applyAudioTrack();
  }

  /** A desired choice may be pending, applied, or available to retry after failure. */
  function applyAudioTrack() {
    if (!playing || audioTrack === appliedAudioTrack) return;
    if (source?.audioTrack === audioTrack && !source.controller.signal.aborted) return;
    convert(
      playing,
      filmTime(),
      noteFor(playing) ??
        "Converting as you watch: the browser cannot change audio track on its own.",
      capBits ?? undefined,
    );
    refreshSeek();
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
    if (!playing || !Number.isInteger(chosen) || chosen < 0) return;
    audioTrack = chosen;
    // The language, never the ordinal — see `trackIndexForLanguage`. Taken from the
    // option's own label rather than kept in a second list beside the menu.
    const picked = audioTrackPicker.selectedOptions[0]?.dataset.lang;
    if (picked) state.setPreference(scope, "audio", picked);

    applyAudioTrack();
  });

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
      inControl: wantsKeys(target) || notes.contains(target),
      onButton: target?.tagName === "BUTTON",
    });
    if (action === null) return;
    // Only what is actually taken. A blanket `preventDefault` here would stop
    // every key this player has no opinion about, including the browser's.
    event.preventDefault();
    transport.act(action);
    hud.show();
  });
  video.addEventListener("ratechange", refreshEnds);
  // The final flush, not the periodic one: a pause is a natural break point
  // worth telling another device about soon, the same as leaving a title —
  // and `flushProgress`'s `sendBeacon` is how the server tells the two apart
  // (`write-debounce.ts`), which the ordinary ten-second tick is not.
  video.addEventListener("pause", () => saveProgress(true));

  video.addEventListener("timeupdate", () => {
    upNext.preload(bufferedAhead(video.buffered, video.currentTime));
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

  /** Save before detaching resets the media clock; release every shared owner. */
  function teardown() {
    if (title === null) return;
    saveProgress(true);
    title.abort();
    title = null;
    stop();
    upNext.clear();
    stopWaitingToStart();
    clearInterval(saveTimer);
    saveTimer = null;
    notes.clear();
    marks.clear();
    hud.clear();
  }

  // Page exit does not fire dialog close. Both release playback and warming.
  window.addEventListener("pagehide", teardown);

  document.getElementById("close").addEventListener("click", () => dialog.close());
  dialog.addEventListener("close", () => {
    teardown();
    for (const track of [...video.querySelectorAll("track")]) track.remove();
    // A panel left open belongs to the title it was opened on.
    cuePanel.panel.hidden = true;
    cuePanel.trigger.setAttribute("aria-expanded", "false");
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
    watch.begin({ capBits: null, sourceBits: null });
  });

  return { openPlayer };
}
