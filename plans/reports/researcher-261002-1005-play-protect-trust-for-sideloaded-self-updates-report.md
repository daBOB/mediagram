# Play Protect and Google developer verification for a sideloaded, self-updating family app (2026-10-02)

Context: spike (`plans/261002-0213-android-self-update/reports/spike-silent-self-update-results.md`) — Play Protect blocks app-driven updates of `com.mediagram.android`'s key on a Play-certified tablet; Google TV box updates silently. User decision: updater on TV devices only; this research looks for a way to bring phones/tablets in later.

## Findings
- **Developer verification:** enforced since 2026-09-30 in Brazil, Indonesia, Singapore, Thailand (certified Android 7+); global "2027, timeline TBD"; nothing in Germany yet. Register package + signing key(s) in the Android Developer Console.
- **Limited-distribution account:** free, no government ID (Google account + 2-step verification + payments profile + contact email); up to **20 devices**, each authorized by QR/link plus consent on the device; meant for hobbyists sharing with family and friends. Full account: $25 + ID.
- Unregistered apps under enforcement update only via an "advanced flow" (developer mode, restart, 24 h wait, PIN; valid 7 days or indefinitely) or adb (exempt). Registered apps update normally.
- **Play Protect:** Google's guidance does not document the exact "hasn't seen an app from this developer" wording; it documents "App blocked" for sideloaded apps requesting sensitive permissions and "Send app for security check" for unevaluated apps. Nothing documented says registration or an appeal clears an unknown-developer block, or that "Install anyway" is remembered per app or survives a session-based update.
- **TV:** Google's FAQ (via summary) says enforcement applies mainly to mobile/tablet, TV and Wear exempt for now — consistent with the TV box result, not proven by it.

| Option for phones/tablets | Cost | Verdict |
|---|---|---|
| Limited-distribution registration of package + key | free, ~1 h, ≤20 devices | best fit; Play Protect effect unproven — test on the tablet |
| Play Protect appeal form | free | for wrongly flagged apps; effect on unknown-developer block undocumented |
| Advanced flow / Play Protect off per device | free, per device | fallback only |
| Play internal app sharing / closed testing | $25 + Play Console | Play installs it; not "without Play" |
| Managed Google Play private apps | enterprise setup | overkill |

## Recommendation
Keep the TV-only updater. To bring phones/tablets in later: register `com.mediagram.android` + the release key on a free limited-distribution account, authorize the tablet, re-run the spike probe (signed with the same key) on the tablet. If still blocked, one appeal; else stay on adb for phones.

## Sources
- https://developer.android.com/developer-verification
- https://developer.android.com/developer-verification/guides/faq
- https://developer.android.com/developer-verification/guides/limited-distribution
- https://android-developers.googleblog.com/2026/03/android-developer-verification.html
- https://developers.google.com/android/play-protect/warning-dev-guidance
- https://www.helpnetsecurity.com/2026/06/19/android-developer-verification-rollout-markets/
- https://cordcuttersnews.com/google-eases-new-android-rules-to-allow-sideloading-of-apps-on-google-tv/

## Unresolved
- Is Play Protect's unknown-developer check independent of developer verification?
- Does "Install anyway" survive a session-based update?
- When does enforcement reach Germany? Must the limited-distribution handshake precede the first install?
