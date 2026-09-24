# Hidden login prompt integration

Status: DONE

## Verified defect and repair

A real Bun 1.4.2 pseudo-terminal exposed a production defect: `hidden()` returned
the correct answer, but the complete dummy password was also echoed into terminal
output. The existing private readline `_writeToOutput` replacement had no effect
in this runtime. The first real-adapter run failed the password-output assertion
in `/tmp/web-hidden-prompt-first.log`.

`web/src/login/prompts.ts` now supplies readline with an owned public `Writable`
stream. Its output gate discards hidden-input echo while the question itself is
written directly. A `finally` restores ordinary output; the same readline reader
continues to serve subsequent questions. Terminal width/resize forwarding is
preserved, and `close()` removes the owned resize listener and destroys the
wrapper after closing readline. Public prompt signatures are unchanged.

## Production adapter coverage

Added `web/test/login-prompts.test.ts`. It starts a Bun child using the production
module and Bun's native pseudo-terminal, with no mocked readline methods or
additional dependency. The child asserts real TTY input/output and invokes
`hidden()` followed by `ask()`.

The parent waits for each question before writing its answer. A distinctive
nonsecret dummy password is supplied only through the isolated PTY. Returned
answers travel through a temporary result file, avoiding contamination of the
terminal capture. Assertions establish the exact returned values, absence of
the password from terminal output, restored ordinary echo, normal child exit
and terminal closure.

The child runs in a fresh temporary directory with a minimal environment, never
the user's interactive terminal or real credential files. Prompt waits are
bounded, the child has a five-second kill deadline, and `finally` terminates and
awaits any remaining child before closing the terminal and removing temporary
files. A final process inspection found no owned prompt process remaining.

The native PTY API was checked against Bun's official
[spawn documentation](https://bun.sh/docs/runtime/child-process). The executed
validation is on Linux with Bun 1.4.2; no cross-platform runtime result is claimed.

## Regression strength and validation

- An isolated temporary copy of the current production module and test removed
  only `if (!hidden)` from the output gate. The test failed specifically because
  the captured terminal contained the complete dummy password. Evidence:
  `/tmp/web-hidden-prompt-mutation.log`.
- The mutation copy was removed in `finally`. Original source and test bytes
  were checked unchanged; no mutation was applied to the shared production tree.
- Final focused command: `bun test test/login-prompts.test.ts
  test/login-setup.test.ts` — **29 pass, 0 fail, 238 assertions**;
  `/tmp/web-hidden-prompt-focused.log`.
- `bunx --no-install --package typescript tsc --noEmit --pretty false` — PASS,
  `/tmp/web-hidden-prompt-types.log`. The direct `tsc` executable was not on PATH;
  the existing cached TypeScript package was used without installation.
- Scoped whitespace validation passed. The production module is 36 lines.

## Scope and handoff

Changed only the owned prompt adapter, new focused test and this report. No
manifests, dependencies, real credentials, unrelated source, scanner state or
commits changed; no whole-web test run was performed. Root owns independent
review and finding tracking. Concerns: none within the requested scope.
