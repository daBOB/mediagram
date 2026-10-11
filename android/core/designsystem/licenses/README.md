# Faces shipped in the app

`res/font/fraunces.ttf`, `res/font/newsreader.ttf` and `res/font/geist.ttf`
(with `res/font/newsreader_italic.ttf`, Newsreader's own italic) are the
same three variable faces the web player loads, decompressed from
the `woff2` cuts in `web/public/font/` so that Android can read them. All
three are under the SIL Open Font License 1.1; the licences sit here beside
them because an APK distributes them and the OFL travels with the file.

Regenerate with:

    woff2_decompress web/public/font/fraunces-latin.woff2
    woff2_decompress web/public/font/newsreader-latin.woff2
    woff2_decompress web/public/font/newsreader-italic-latin.woff2
    woff2_decompress web/public/font/geist-latin.woff2

The `latin` cut is the one taken: it carries the German the library is
described in, along with the em dash, the ellipsis and the curly
apostrophe. A title in Polish or Turkish falls back to the system face for
the letters the subset does not hold, which is legible rather than tofu.

## Faces the TV styles add

`res/font/geist_700.ttf` is Geist pinned at weight 700, the bold Apple TV
style's cover title asks for; `res/font/google_sans_flex_{400,500,600,700}.ttf`
are Google Sans Flex (OFL 1.1, `OFL-google-sans-flex.txt`; no Reserved Font
Name) pinned at each weight the TV asks for and cut to the same Latin range as
the web's faces. Static, not variable: see `Type.kt`'s top note. Regenerate
with the commands in `plans/261011-0052-tv-themes/phase-02-colours-and-fonts.md`
(Task 2.2, Step 1). "Google Sans" is Google's trademark; the app names the
style "Google TV style" and never the font.
