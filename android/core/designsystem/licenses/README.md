# Faces shipped in the app

`res/font/fraunces.ttf` and `res/font/newsreader.ttf` are the same two
variable faces the web player loads, decompressed from the `woff2` cuts in
`web/public/font/` so that Android can read them. Both are under the SIL
Open Font License 1.1; the licences sit here beside them because an APK
distributes them and the OFL travels with the file.

Regenerate with:

    woff2_decompress web/public/font/fraunces-latin.woff2
    woff2_decompress web/public/font/newsreader-latin.woff2

The `latin` cut is the one taken: it carries the German the library is
described in, along with the em dash, the ellipsis and the curly
apostrophe. A title in Polish or Turkish falls back to the system face for
the letters the subset does not hold, which is legible rather than tofu.
