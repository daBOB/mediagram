# Login setup behavior

Status: DONE_WITH_CONCERNS. Production login orchestration is import-safe and covered through raw IO boundaries. Full-suite failures occurred in concurrent browser work; a privacy hook also limits the literal default-filename assertion described below.

## Changes

- `src/login.ts` is a thin executable wrapper over `runLogin`, so importing it opens no prompt or Telegram connection. Its documentation correctly describes default file output and the stdout option.
- `login/setup.ts` owns channel selection, credentials, output, and cleanup. `authenticate.ts` retains QR/phone/SMS routing and delivery guidance; `prompts.ts` owns readline and hidden-password echo suppression.
- Prompt close, disconnect, and destroy are all attempted after success or failure. Console redirection is restored in the cleanup path. Library logs and errors redact known API hashes, passwords, codes, QR tokens, and saved sessions; the explicit QR approval challenge remains intentional setup output on stderr.
- The native file writer creates owner-readable files and tightens an existing file's permissions before writing credentials. Output occurs after client cleanup; a failed write does not spill credentials to stdout.

## Verification

- **23 focused tests pass**, 201 assertions, using actual `runLogin` and controlled Telegram/prompt/output IO. Cases cover isolated executable import, QR priority, phone/SMS/in-app delivery, invalid-phone retry, hidden 2FA, title/bare/bot channel identifiers, missing channels, prompted configuration, failures throughout resource acquisition/authentication/cleanup, console restoration, output-only credentials, and actual temporary-file creation/replacement with mode 0600.
- `bunx --package typescript tsc --noEmit --pretty false`: **pass**, no diagnostics.
- `git diff --check`: pass. Production files are 21, 66, 25, and 127 lines.
- Full Bun snapshot during concurrent browser implementation: **1,427 passed, 16 failed**, 104 files. Failures were in genres/age-rating (`firstItemOf` children handling) and player lifetime/seek regressions; none were in login. The lead agent was notified. Log: `/tmp/mediagram-login-full.log`.
- Focused/type logs: `/tmp/mediagram-login-focused.log`, `/tmp/mediagram-login-types.log`.
- The import-safety test owns and awaits a real subprocess, with termination in its cleanup path. No owned process remains. No Telegram service, user credential file, scanner mutation, or commit was used.

## Test-hook limitation

The privacy hook rejected creation of test source that contained a literal default credential filename, and separately even a pure equality assertion for that filename, interpreting these as secret-file reads. No access was attempted through another mechanism. Native file-permission tests therefore use a clearly named temporary `settings.fixture`; the production default destination is verified from source. This exercises the identical writer without touching a real user credential file.
