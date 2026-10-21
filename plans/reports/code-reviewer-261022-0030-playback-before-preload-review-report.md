# Code review: fix/playback-before-preload (pointer)

The full report is at `plans/261021-2336-playback-before-preload-and-download-facts/reports/code-reviewer-261022-0014-playback-before-preload-review-report.md`.

Headline: there is no deadlock. But the background gate opens between two sequential player fetches, which a real-thread probe showed (H1). A preload of the episode now playing makes the player stream it uncached (M1). The warm-up now holds titles already on disk (M2).
