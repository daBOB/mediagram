/*
 * The film leader: the 3·2·1 countdown threaded ahead of a reel, shown while
 * something is on its way — the library loading, and the stage while a title
 * opens. One drawing, `#film-leader` in index.html, cloned into each; how it
 * moves is in styles/film-leader.css.
 *
 * A classic script, loaded blocking right after the markup it fills, so the
 * library's leader is there at first paint rather than when the modules land.
 */
(() => {
  const drawing = document.getElementById("film-leader").content.firstElementChild;
  const stage = document.getElementById("stage-leader");
  document.querySelector(".loading-state").prepend(drawing.cloneNode(true));
  stage.prepend(drawing.cloneNode(true));

  // On the stage, from a source starting to load until its first frame is in
  // hand. A stall after that leaves it down: the player shows no buffering
  // wait, and this is not one. A source dropped or failed takes it down too.
  const video = document.getElementById("video");
  video.addEventListener("loadstart", () => { stage.hidden = false; });
  for (const event of ["loadeddata", "emptied", "error"]) {
    video.addEventListener(event, () => { stage.hidden = true; });
  }
})();
