# Independent transcode cleanup review

Status: DONE

Verdict: PASS for the cleanup delta in `web/src/transcode/registry.ts` and its
new cases in `web/test/transcode-registry.test.ts`. No additional finding.

- Cleanup removes only the unique discarded directory after a successful
  rename. If the old directory was already missing, it never removes the
  reusable original path after awaiting process shutdown, so a replacement's
  segments survive.
- An isolation error still reaches `process.stop()`. `ENOENT` is the expected
  absent-directory case; other filesystem errors are retained unchanged when
  alone, or as entries in `AggregateError` alongside stop/removal errors.
- The new replacement test uses real temporary directories and a gated stop,
  checks both successful isolation and missing-source cases, and releases its
  gate in `finally`. The ENOTDIR fixture verifies original rename diagnostics,
  process-stop execution and preservation of the unexpected filesystem entry.
- Existing shutdown ownership remains intact: in-flight stop promises stay
  tracked, shutdown drains starts/stops, and one rejected stop does not prevent
  the others from running.

Read-only review; no production edits or repeated tests. Verified log evidence:
`/tmp/web-registry-poster-red.log` contains the two intended registry failures
before repair (plus two separate poster diagnostic failures);
`/tmp/web-registry-poster-green.log` reports 66 passed, 0 failed across four files.
Poster implementation is outside this review's scope.

Concerns/Blockers: none in the reviewed delta. No process was started.
