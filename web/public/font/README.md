# Faces

Two variable faces, subset by Google Fonts into the `latin` and `latin-ext`
cuts `style.css` declares. Both carry an optical-size axis, which is why they
are worth their bytes: one file sets the wordmark and the codec line without
either looking like the other scaled.

| File | Family | Axes | Licence |
|---|---|---|---|
| `fraunces-latin*.woff2` | Fraunces | `opsz` 9–144, `wght` 400–700 | OFL 1.1 — `OFL-fraunces.txt` |
| `newsreader-latin*.woff2` | Newsreader | `opsz` 6–72, `wght` 300–600 | OFL 1.1 — `OFL-newsreader.txt` |

Served from here rather than from a font CDN because the player is routinely
opened on a link with no way out to the internet, and a catalogue that falls
back to Times because the house wifi is down looks broken. `web/src/routes.ts`
maps `.woff2` to `font/woff2`; nothing else is needed to serve them.

The `latin-ext` cuts are declared with their own `unicode-range`, so a browser
only fetches one if a title actually contains a character from that range.

To refresh, re-request the same axis ranges from the Google Fonts CSS API with
a browser user-agent and take the `latin` and `latin-ext` `src` URLs:

```
https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400..700
https://fonts.googleapis.com/css2?family=Newsreader:opsz,wght@6..72,300..600
```

Copy the `unicode-range` lines into `style.css` alongside the files; they are
what keeps the two cuts from both downloading.
