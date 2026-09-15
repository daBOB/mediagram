# mlib prebuilt package, format 1

A **package** is one encrypted file holding a snapshot of the library index
and its poster artwork, published to a static HTTPS URL beside a small
plaintext **pointer**. A player fetches the pointer, downloads the package,
decrypts it, and has a browsable catalog without scanning the Telegram
channel and without a TMDB key.

This document is normative and self-contained: a reader can be implemented
from it without reading Rust. The uploader side is
`mediagram export-package`.

The pinned `library.db` in the channel is unaffected and remains the
disaster-recovery copy. A package is a convenience for players, not a
backup.

## 1. Layout

```
https://example.com/latest.json                                  <- pointer, plaintext
https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc
https://example.com/prebuilt_mediagram_db_20260601-9a14ff02.tar.gz.enc   (older, kept)
```

The pointer lives at a fixed path. Packages are named
`prebuilt_mediagram_db_YYYYMMDD-<8 hex>.tar.gz.enc`, where the date is the
export day in UTC and the hex is the first four bytes of the package's own
SHA-256. The name depends only on the bytes it names, so two exports on one
day cannot collide and no local counter can cause a silent overwrite.

Decrypted, a package is a gzipped tar containing:

```
manifest.json        first member, always
library.db           SQLite, the index snapshot
posters/tmdb-movie-693134.jpg
posters/tmdb-tv-1396.jpg
```

## 2. The pointer (`latest.json`)

```json
{"format":1,"created_at":1781568000,"file":"prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc","url":"https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc","bytes":8127744,"sha256":"…64 hex…","cipher":"aes-256-gcm","key_id":"630dcd29","schema":2,"spec":3}
```

| Field | Type | Meaning |
|---|---|---|
| `format` | integer | This document's version. A reader refuses what it does not know |
| `created_at` | integer | Unix seconds when the package was built |
| `file` | string | Package file name |
| `url` | string | Where to fetch it |
| `bytes` | integer | Package size; a reader enforces its own ceiling against this |
| `sha256` | string | Lowercase hex digest of the encrypted file |
| `cipher` | string | Always `aes-256-gcm` in format 1 |
| `key_id` | string | First four bytes of `sha256(key)`, hex. Distinguishes keys; proves nothing |
| `schema` | integer | `library.db` table layout version (2 at the time of writing) |
| `spec` | integer | Caption spec version the rows came from (3 at the time of writing) |

The pointer is published in the clear and deliberately says nothing about
the library: no titles, no counts, no chat or message identifiers.

**Five of these fields are authenticated, five are not.** See §4.

## 3. The manifest (`manifest.json`)

Inside the ciphertext, so it may describe the library.

```json
{"format":1,"created_at":1781568000,"schema":2,"spec":3,"sets":312,"parts":468,"posters":[{"key":"tmdb-movie-693134","file":"posters/tmdb-movie-693134.jpg"}]}
```

`sets` and `parts` are row counts in the enclosed `library.db`. A poster
`key` is `tmdb-movie-<id>` or `tmdb-tv-<id>`: TMDB's movie and television id
spaces are independent, so the kind is part of the key and a film and a
series sharing an id never collide.

## 4. Cipher framing and associated data

```
file = nonce (12 bytes) || ciphertext || tag (16 bytes)
```

AES-256-GCM, 96-bit nonce from the operating system generator, fresh for
every package, 128-bit tag. The key is 32 bytes, shared out of band with the
player.

The **associated data** is the minified JSON of exactly five pointer fields,
in this order:

```json
{"format":1,"created_at":1781568000,"key_id":"630dcd29","schema":2,"spec":3}
```

A reader rebuilds these bytes from the pointer it fetched and passes them to
the cipher. If any of the five was altered in transit, decryption fails.
This is what stops an old package being presented as current: its
`created_at` is baked into its tag.

`file`, `url`, `bytes` and `sha256` are **not** authenticated, of necessity:
`sha256` is the digest of the very ciphertext the tag protects, so
authenticating it would be circular, and the other three describe where the
bytes were fetched from rather than what they are.

The consequence is a rule, not a footnote: **a reader must never decide "I
already have this package" from `sha256`.** Anyone who can rewrite the
pointer can set it to the digest of the copy the reader already holds, and a
reader that skips on that match never runs the cipher at all. Decide from
the authenticated fields instead (§5, step 4).

`key_id` is constrained to lowercase hex so that the associated data
contains no character whose JSON escaping two libraries could disagree
about. A reader that builds this string with a different JSON writer still
produces identical bytes.

## 5. Reader algorithm

1. Fetch `{base}/latest.json`. Refuse an unknown `format`.
2. Refuse a `created_at` older than the package already held, and one
   implausibly far in the future. A cheap first filter; step 6 is the real
   protection.
3. Refuse if `key_id` does not match the held key, before downloading.
4. Skip the download only when the **authenticated** identity
   (`format`, `created_at`, `key_id`, `schema`, `spec`) equals the one
   recorded after the last successful decrypt. Never skip on `sha256`.
5. Refuse if `bytes` exceeds the reader's ceiling. Download, then verify the
   SHA-256 against `sha256`, refusing on mismatch.
6. Rebuild the associated data from the pointer and decrypt in one shot,
   over the whole ciphertext.
   **Never through a streaming wrapper.** On Android, `CipherInputStream`
   swallows `AEADBadTagException` and hands back truncated plaintext instead
   of failing, which silently accepts tampered data. Use `Cipher.doFinal`,
   which returns plaintext only after the tag verifies. A failure here means
   a wrong key, a corrupt download, or an edited pointer, and must be
   reported rather than retried.
7. Gunzip and untar. Refuse any member whose path is absolute, contains
   `..`, or is not a regular file. Read `manifest.json`, confirm its
   `created_at` matches the pointer, and confirm `schema` is a layout the
   reader supports.
8. Open `library.db` read-only. `PLAYABLE_SQL` in the spec crate defines
   what is playable. Stream parts from Telegram by `(chat_id, message_id)`.

Memory: the tag covers the whole file, so a reader holds the ciphertext and
its plaintext at once. The exporter refuses to produce a package over 48 MB
and a reader should refuse over 64 MB.

## 6. Security model

State this plainly, because it decides how the URL may be treated.

- The package contains the **private channel id and every message id** of
  the library. Anyone who can decrypt it learns the full contents of the
  library, though not the media itself, which still requires an account with
  access to the channel.
- **The key is the only thing protecting it.** The URL is not a secret and
  must not be treated as one. A leaked URL yields ciphertext.
- Losing the key costs the package, never the library: `library.db` is local
  and also pinned in the channel.
- **Format 1 does not sign the pointer.** A tampered pointer cannot deliver
  stale content as fresh, because the archive fails its tag. It can,
  however, be used to withhold updates: anyone who can serve or cache
  `latest.json` can keep serving an old one. Encryption cannot prevent that.
  If detecting starvation matters, a later format adds a signature and an
  expiry; the `format` field exists so that is a version bump, not a break.
- The publish command in the uploader's config executes with the uploader's
  privileges. It is the operator's own configuration file, which already
  holds the Telegram API hash, but it does execute a program.

## 7. Versioning

- `format` versions this document. A reader accepts the versions it knows.
- `schema` versions `library.db`'s tables; `spec` versions the captions the
  rows came from. All three are independent and all three travel in both the
  pointer and the manifest.
- A package is never rewritten in place. A new export is a new file with a
  new name, and the pointer moves to it.

## 8. Verifying a package by hand

```sh
# the key as configured, base64
KEY=$(grep '^package_key' ~/.config/mediagram/config.toml | cut -d'"' -f2)

python3 - "$KEY" latest.json prebuilt_mediagram_db_*.tar.gz.enc <<'PY'
import base64, gzip, io, json, sys, tarfile
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

key = base64.b64decode(sys.argv[1])
pointer = json.load(open(sys.argv[2]))
blob = open(sys.argv[3], "rb").read()

aad = json.dumps(
    {k: pointer[k] for k in ("format", "created_at", "key_id", "schema", "spec")},
    separators=(",", ":"),
).encode()

plaintext = AESGCM(key).decrypt(blob[:12], blob[12:], aad)
tar = tarfile.open(fileobj=io.BytesIO(gzip.decompress(plaintext)))
print(tar.getnames())
print(json.load(tar.extractfile("manifest.json")))
PY
```

This is also how the format is tested across implementations: the Rust
exporter writes, an independent library reads.
