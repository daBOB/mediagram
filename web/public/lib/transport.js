/**
 * The controls the browser used to lend us.
 *
 * `<video controls>` gave this player play, pause, seeking, volume, speed,
 * captions and fullscreen for nothing, and it gave them in Chromium's own
 * furniture, sitting directly beneath this player's own rail. Two bars, two
 * typefaces, two fade timers, and the same kind of choice in two places — the
 * audio track ours, the subtitles theirs. So the loan is being paid back.
 *
 * The shape is the phone's, from the Android player's transport design: skip
 * ten, play/pause, skip ten, elapsed on the left and duration on the right.
 * Two surfaces over one library should not be learnt twice.
 *
 * Kept out of `player.js`, which is over eight hundred lines against a
 * two-hundred-line guideline and holds the lifecycle. This holds the buttons.
 */

import { readVolume, writeVolume } from "./volume-store.js";
import { DEFAULT_FRAMING, framingLabel, framingStyle, nextFraming } from "./framing.js";
import { isLooping, loopBack, loopLabel, markLoop, NO_LOOP } from "./ab-loop.js";
import { clockTime } from "./format.js";

/**
 * Drawn rather than typed.
 *
 * A transport button is the one place in this player where a glyph beats a
 * word, and the glyphs for these are exactly the ones a typeface cannot be
 * relied on to have: `⏸` and `⛶` arrive as emoji, as tofu, or not at all
 * depending on what is installed. These always look the same.
 */
const ICONS = {
  play: "M8 5v14l11-7z",
  back: "M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z",
  forward: "M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z",
  pause: "M6 5h4v14H6zm8 0h4v14h-4z",
  loud: "M3 9v6h4l5 5V4L7 9H3zm13.5 3a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4z",
  muted: "M3 9v6h4l5 5V4L7 9H3zm16 1.4L17.6 9 16 10.6 14.4 9 13 10.4 14.6 12 13 13.6 14.4 15 16 13.4 17.6 15 19 13.6 17.4 12z",
  full: "M4 9V4h5v2H6v3zm11-5h5v5h-2V6h-3zM6 15v3h3v2H4v-5zm12 0h2v5h-5v-2h3z",
  unfull: "M9 4h2v5H6V7h3zm4 0h2v3h3v2h-5zm-7 11h5v5h-2v-3H6zm9 0h5v2h-3v3h-2z",
};

/** The speeds worth offering. Anything finer is a setting, not a choice. */
const SPEEDS = [0.75, 1, 1.25, 1.5, 1.75, 2];

/** Ten, the same ten the phone skips and the same ten the buttons say. */
export const SKIP_SECONDS = 10;

/** `1×`, `1.5×` — never `1.00×`, which is a measurement, not a speed. */
export function speedLabel(rate) {
  const at = Number(rate);
  if (!Number.isFinite(at) || at <= 0) return "1×";
  return `${String(Number(at.toFixed(2)))}×`;
}

/**
 * What the play button is *offering*, which is the opposite of what is
 * happening. A paused player offers "Play".
 */
export function playLabel(paused) {
  return paused ? "Play" : "Pause";
}

/** Muted, or turned all the way down, are the same thing to a listener. */
export function isSilent({ volume, muted }) {
  return muted === true || Number(volume) === 0;
}

/**
 * The options a subtitle picker offers for a set of text tracks.
 *
 * "Off" first and always, because it is the one choice the native menu made
 * awkward and the one a viewer most often wants back.
 */
export function subtitleOptions(tracks) {
  const found = [...(tracks ?? [])].filter((track) => track.kind === "subtitles");
  return [
    { value: "off", label: "Off" },
    ...found.map((track, index) => ({ value: String(index), label: track.label || "Subtitles" })),
  ];
}

function icon(path) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("focusable", "false");
  const shape = document.createElementNS("http://www.w3.org/2000/svg", "path");
  shape.setAttribute("d", path);
  svg.append(shape);
  return svg;
}

function setIcon(button, path) {
  button.replaceChildren(icon(path));
}

/**
 * A skip button: which way, and how far.
 *
 * Both, because neither alone is enough. Two arrows without a number are a
 * skip of some unstated length — the phone had to be told ten explicitly
 * because media3's own defaults are five back and fifteen forward, and a
 * control that does not say is a control that can quietly disagree with its
 * twin. A number without arrows does not say which way it goes.
 */
function skipButton(button, path, seconds) {
  const far = document.createElement("span");
  far.textContent = String(seconds);
  button.replaceChildren(icon(path), far);
}

/**
 * Wires the bar to `video` and returns the one way to refresh it.
 *
 * `onSeekTo` rather than touching `currentTime` here: a title being converted
 * cannot be seeked by moving a playhead, and the player owns that decision.
 * `filmTime` and `runtime` are asked for the same reason — a conversion's own
 * clock and duration are about the encode, not the film.
 *
 * `recall(name)` and `remember(name, value)` are what this viewer chose for
 * the open show. Both are given rather than reached for, because what "this
 * show" means is the player's question and not the bar's.
 */
export function mountTransport({ video, onSeekTo, filmTime, runtime, recall, remember }) {
  const playPause = document.getElementById("play-pause");
  const back = document.getElementById("skip-back");
  const forward = document.getElementById("skip-forward");
  const mute = document.getElementById("mute");
  const volume = document.getElementById("volume");
  const speed = document.getElementById("speed-rate");
  const subs = document.getElementById("subs");
  const subPicker = document.getElementById("sub-track");
  const full = document.getElementById("fullscreen");

  for (const rate of SPEEDS) {
    const option = document.createElement("option");
    option.value = String(rate);
    option.textContent = speedLabel(rate);
    speed.append(option);
  }

  /**
   * The speed this show was last watched at.
   *
   * Applied per title rather than once at mount: a lecture course watched at
   * 1.5x and a film watched at 1x are two different answers, and the bar is
   * mounted once for both.
   */
  /**
   * How this show was last framed, and where any loop from the last title went.
   *
   * A loop belongs to the passage it was marked in, so it is dropped: carrying
   * one into the next episode would trap a viewer in a stretch of a film they
   * have not started. A framing is a property of how a show was mastered, so
   * it is remembered.
   */
  function recallFraming() {
    loop = { ...NO_LOOP };
    framing = recall?.("framing") ?? DEFAULT_FRAMING;
    applyFraming();
  }

  function recallSpeed() {
    const asked = Number(recall?.("speed") ?? Number.NaN);
    chosenRate = SPEEDS.includes(asked) ? asked : 1;
    video.playbackRate = chosenRate;
  }

  // Before the first title, so nothing ever plays at a volume the viewer
  // turned down on the last one.
  const held = readVolume();
  video.volume = held.volume;
  video.muted = held.muted;
  volume.value = String(held.volume);

  /**
   * What the controls do, named once.
   *
   * The buttons call these and so does the keyboard, because they are the
   * same acts — a viewer who presses space and a viewer who clicks play are
   * asking for one thing, and two implementations of it would be two chances
   * to disagree.
   */
  function togglePlay() {
    if (video.paused) void video.play();
    else video.pause();
  }

  /**
   * A frame at a time, which only means anything while paused.
   *
   * Pauses first: stepping a playing film is a seek it immediately runs away
   * from, and a viewer pressing `.` has said they want to look at something.
   */
  function step(by) {
    if (!video.paused) video.pause();
    onSeekTo(filmTime() + by);
  }

  /** One rung up or down the speeds the picker already offers. */
  function stepSpeed(by) {
    const at = SPEEDS.indexOf(chosenRate);
    const to = SPEEDS[Math.min(SPEEDS.length - 1, Math.max(0, (at === -1 ? 1 : at) + by))];
    chosenRate = to;
    video.playbackRate = to;
    speed.value = String(to);
    remember?.("speed", String(to));
  }

  /**
   * The picture out of the window and into a corner of the screen.
   *
   * The one control left out when this bar was built, on the grounds that
   * nobody had asked for it. Somebody has.
   */
  function togglePictureInPicture() {
    if (document.pictureInPictureElement) void document.exitPictureInPicture().catch(() => {});
    else void video.requestPictureInPicture?.().catch(() => {});
  }

  /** `fit` to `fill` to `16:9` to `4:3` and round again. */
  function cycleFraming() {
    framing = nextFraming(framing);
    applyFraming();
    remember?.("framing", framing);
    return framingLabel(framing);
  }

  function applyFraming() {
    const style = framingStyle(framing);
    video.style.objectFit = style.objectFit;
    video.style.aspectRatio = style.aspectRatio;
  }

  function setVolume(to) {
    const level = Math.min(1, Math.max(0, Number(to)));
    if (!Number.isFinite(level)) return;
    video.volume = level;
    // Turning it up from silence is how a viewer unmutes without having to go
    // looking for the mute button.
    if (level > 0) video.muted = false;
  }

  playPause.addEventListener("click", togglePlay);
  skipButton(back, ICONS.back, SKIP_SECONDS);
  skipButton(forward, ICONS.forward, SKIP_SECONDS);
  back.addEventListener("click", () => onSeekTo(filmTime() - SKIP_SECONDS));
  forward.addEventListener("click", () => onSeekTo(filmTime() + SKIP_SECONDS));

  mute.addEventListener("click", () => {
    video.muted = !video.muted;
  });
  volume.addEventListener("input", () => setVolume(volume.value));
  video.addEventListener("volumechange", () => {
    writeVolume({ volume: video.volume, muted: video.muted });
  });

  /**
   * The rate the viewer asked for, which the element keeps forgetting.
   *
   * `load()` resets `playbackRate`, and this player calls it on every
   * conversion — so a viewer watching at 1.25× who crossed a bitrate switch
   * would silently be put back to 1× by machinery that has nothing to do with
   * speed. Re-applied per source instead.
   */
  let chosenRate = 1;
  /** How the picture sits in the window. Per title, not per player. */
  let framing = DEFAULT_FRAMING;
  /** The stretch being repeated, if any. */
  let loop = { ...NO_LOOP };
  speed.addEventListener("change", () => {
    chosenRate = Number(speed.value);
    video.playbackRate = chosenRate;
    remember?.("speed", speed.value);
  });
  video.addEventListener("loadedmetadata", () => {
    if (video.playbackRate !== chosenRate) video.playbackRate = chosenRate;
  });

  /** Puts the element's tracks where the picker says they should be. */
  function applySubtitles() {
    const chosen = subPicker.value;
    for (const [index, track] of [...video.textTracks].entries()) {
      track.mode = String(index) === chosen ? "showing" : "disabled";
    }
  }
  subPicker.addEventListener("change", () => {
    applySubtitles();
    // The language, not the ordinal, for the same reason the audio menu does
    // it: which track is `0` is a fact about this file and not about the show.
    remember?.("subtitle", subPicker.selectedOptions[0]?.dataset.lang ?? "off");
  });

  /**
   * `c`, which turns them off and back on to whatever they were.
   *
   * Never a cycle through every language: a viewer reaching for a key wants
   * the subtitles gone, or back, and stepping them through four tracks to get
   * where they started is not that.
   */
  function toggleSubtitles() {
    if (subs.hidden) return;
    subPicker.value = subPicker.value === "off" ? "0" : "off";
    applySubtitles();
  }

  /**
   * The page, not the dialog and not the picture.
   *
   * Chromium refuses a modal `<dialog>` outright — "Dialog elements are
   * invalid" — and the video element would take the picture fullscreen and
   * leave this bar behind it, which is the opposite of the point. The dialog
   * is already `fixed; inset: 0`, so a fullscreen page is a fullscreen
   * player with everything still on it.
   */
  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.documentElement.requestFullscreen().catch(() => {});
  }
  full.addEventListener("click", toggleFullscreen);
  document.addEventListener("fullscreenchange", () => refresh());

  /** Everything the bar shows, from what the element and the film both say. */
  function refresh() {
    setIcon(playPause, video.paused ? ICONS.play : ICONS.pause);
    playPause.setAttribute("aria-label", playLabel(video.paused));

    const silent = isSilent({ volume: video.volume, muted: video.muted });
    setIcon(mute, silent ? ICONS.muted : ICONS.loud);
    mute.setAttribute("aria-label", silent ? "Unmute" : "Mute");
    if (document.activeElement !== volume) volume.value = String(video.muted ? 0 : video.volume);

    speed.value = String(video.playbackRate);

    const full_ = document.fullscreenElement !== null;
    setIcon(full, full_ ? ICONS.unfull : ICONS.full);
    full.setAttribute("aria-label", full_ ? "Leave fullscreen" : "Fullscreen");

    // The two clocks belong to the scrub bar, which is the one thing that
    // knows where a drag has got to before playback has followed it there.
    // A skip is only offered where there is something to skip within.
    const length = runtime();
    back.disabled = length <= 0;
    forward.disabled = length <= 0;
  }

  /** Rebuilt per title: a film's tracks are not the last film's tracks. */
  function offerSubtitles() {
    const tracks = [...video.textTracks].filter((track) => track.kind === "subtitles");
    const options = subtitleOptions(video.textTracks);
    subPicker.replaceChildren();
    for (const [at, { value, label }] of options.entries()) {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = label;
      // `at - 1` because "Off" is first and is not a track.
      const lang = tracks[at - 1]?.language;
      if (lang) option.dataset.lang = lang;
      subPicker.append(option);
    }
    // One option is "Off" alone, which is not a choice.
    subs.hidden = options.length < 2;

    /**
     * What this viewer chose for this show, if the file still offers it.
     *
     * `off` is a choice like any other and has to survive: a viewer who turned
     * subtitles off on episode one meant it for the series, and a fallback
     * that treated "off" as "nothing remembered" would turn them back on
     * every episode.
     */
    const wanted = recall?.("subtitle") ?? null;
    if (wanted === "off") subPicker.value = "off";
    else {
      const found = tracks.findIndex(
        (track) => (track.language ?? "").toLowerCase() === String(wanted ?? "").toLowerCase(),
      );
      // Nothing remembered, or a language this file no longer carries: the
      // first track, which is what `default` used to do before this player
      // took the decision off the browser.
      subPicker.value = found === -1 ? (options.length > 1 ? "0" : "off") : String(found);
    }
    applySubtitles();
  }

  for (const event of ["play", "pause", "volumechange", "ratechange", "loadedmetadata", "durationchange"]) {
    video.addEventListener(event, refresh);
  }

  // The only thing here that runs on every tick. `loopBack` answers `null` for
  // all but one of them and says so cheaply, which is why it can.
  video.addEventListener("timeupdate", () => {
    const back = loopBack(loop, filmTime());
    if (back !== null) onSeekTo(back);
  });

  /**
   * Does what `keyAction` decided, whatever that was.
   *
   * Here rather than in the player because every one of these is a control on
   * this bar, and the bar is where they already live.
   */
  function act(action) {
    switch (action?.do) {
      case "playPause":
        return togglePlay();
      case "skip":
        return onSeekTo(filmTime() + action.by);
      case "seekFraction": {
        // A tenth of a runtime nobody knows is not a place.
        const length = runtime();
        if (length > 0) onSeekTo(length * action.by);
        return undefined;
      }
      case "volume":
        return setVolume((video.muted ? 0 : video.volume) + action.by);
      case "mute":
        video.muted = !video.muted;
        return undefined;
      case "fullscreen":
        return toggleFullscreen();
      case "subtitles":
        return toggleSubtitles();
      case "pictureInPicture":
        return togglePictureInPicture();
      case "framing":
        return cycleFraming();
      case "step":
        return step(action.by);
      case "speed":
        return stepSpeed(action.by);
      case "loop": {
        loop = markLoop(loop, action.end, filmTime());
        return isLooping(loop) || loop.from !== null
          ? loopLabel(loop, clockTime)
          : "";
      }
      default:
        return undefined;
    }
  }

  return { refresh, offerSubtitles, recallSpeed, recallFraming, act };
}
