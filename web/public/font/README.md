# Faces

Variable faces served locally. `styles/theme.css` uses Geist for the interface
and Fraunces for the preserved Mediagram wordmark. Newsreader is retained from
the previous theme but is no longer loaded by the web player.

| File | Family | Axes | Licence |
|---|---|---|---|
| `geist-latin*.woff2` | Geist | `wght` 400-700 | OFL 1.1, `OFL-geist.txt` |
| `fraunces-latin*.woff2` | Fraunces | `opsz` 9–144, `wght` 400–700 | OFL 1.1 — `OFL-fraunces.txt` |
| `newsreader-latin*.woff2` | Newsreader | `opsz` 6–72, `wght` 300–600 | OFL 1.1 — `OFL-newsreader.txt` |

Served from here rather than from a font CDN because the player is routinely
opened on a link with no way out to the internet.
`web/src/http/static-files.ts` maps `.woff2` to `font/woff2`.

Geist's `latin-ext` cut has its own `unicode-range`, so a browser only fetches
it if a title contains a character from that range.

To refresh, re-request the same axis ranges from the Google Fonts CSS API with
a browser user-agent and take the `latin` and `latin-ext` `src` URLs:

```
https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400..700
https://fonts.googleapis.com/css2?family=Newsreader:opsz,wght@6..72,300..600
https://fonts.googleapis.com/css2?family=Geist:wght@400..700&display=swap
```

Copy the `unicode-range` lines into `styles/theme.css` alongside the files; they are
what keeps the two cuts from both downloading.
