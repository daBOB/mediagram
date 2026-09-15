# Prebuilt Metadata Package: Encryption & Packaging Research Report

**Date:** 2026-09-15  
**Scope:** Rust uploader → tar.gz archive → Android TV player (Kotlin, minSdk 26)  
**Archive size:** ~10–50 MB (metadata + JPEG posters/backdrops for 300-title library)

---

## 1. ENCRYPTION: AES-256-GCM vs Streaming AEAD vs age Format

**Recommendation:** Use **whole-file AES-256-GCM with a random 96-bit nonce prepended**. This is the simplest and safest for a single-shot distribution archive.

| **Criterion** | **AES-256-GCM (whole)** | **Streaming AEAD** | **age Format** |
|---|---|---|---|
| **Crate** | `aes-gcm` 0.10.3 | `aes-gcm-stream` 0.1.0 | `age` 0.11.3+ |
| **Maintenance** | ✅ Active (RustCrypto) | ⚠️ Minimal (340/mo DL) | ✅ Active (Filippo) |
| **For this use:** | Perfect fit | Overkill | Overkill |
| **Kotlin library** | `javax.crypto.Cipher` native | `javax.crypto.Cipher` native | Needs Java impl. |
| **Code complexity** | ~15 lines Rust / ~20 Kotlin | ~30 lines | ~40 lines |
| **Max safe size** | Single archive: **OK** | Unlimited streaming | Unlimited |
| **Why** | Deterministic; no state mgmt | Designed for streams | Password-first design |

### 1.1 AES-256-GCM (Recommended)

**Rust-side (aes-gcm 0.10.3):**
```rust
use aes_gcm::{Aes256Gcm, Key, Nonce};
use rand::Rng;

let key = Key::<Aes256Gcm>::from(*key_bytes); // 32 bytes
let nonce = Nonce::from(*rand_96_bits);       // 12 bytes, generated fresh
let ciphertext = cipher.encrypt(&nonce, plaintext.as_ref())?;
// Write: nonce (12 bytes) + ciphertext || tag (together ~16 bytes overhead)
```

**Kotlin-side (minSdk 26+):**
```kotlin
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val cipher = Cipher.getInstance("AES/GCM/NoPadding")
val keySpec = SecretKeySpec(keyBytes, 0, 32, "AES")
val gcmSpec = GCMParameterSpec(128, nonceBytes) // 128-bit tag, 96-bit nonce
cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
val plaintext = cipher.doFinal(ciphertext)
```

**Safety limits (NIST SP 800-38D):**
- **2^32 invocations per key** with random nonces before rekey required. At 10 MB archives, this is **~40 TB total before nonce reuse risk**, so **not a concern for prebuilt packages**.
- **96-bit nonce:** per NIST, random 96-bit nonces collide around 2^48.3 invocations; for a single archive per nonce, this is **safe**.
- **Tag length:** use 128 bits (16 bytes). Anything shorter increases forgery risk.

**Known attack:** Nonce reuse with the same key recovers the authentication key H, allowing forgery. **Mitigation:** Generate a fresh nonce per archive, store it in plaintext alongside ciphertext (not secret).

---

### 1.2 Streaming AEAD (aes-gcm-stream 0.1.0)

Implements chunked AES-GCM with 64KiB or larger chunks. Use only if archive exceeds a few hundred MB or streaming is critical. For 10–50 MB, adds complexity without benefit.

---

### 1.3 age Format (age 0.11.3+)

Uses ChaCha20-Poly1305 in 64KiB STREAM chunks (not AES). **Drawbacks for this case:**
- Requires a separate Rust implementation or system age binary (adds dependency).
- Kotlin decryption requires either BouncyCastle or building a Java wrapper—no stdlib support.
- Designed for passphrases, not raw symmetric keys (scrypt overhead).
- **Not recommended** unless you need password-based asymmetric recipients later.

---

## 2. ARCHIVING: tar + flate2 with Deterministic Output

**Recommendation:** **Use `tar` 0.4.46 + `flate2` 1.0.31 (or 1.1.5+) with SOURCE_DATE_EPOCH and fixed metadata.**

| **Crate** | **Version** | **Status** | **Deterministic?** |
|---|---|---|---|
| `tar` | 0.4.46 | ✅ Active; fixed symlink CVE in 0.4.45 | Yes, with care |
| `flate2` | 1.0.31 (or 1.1.5+) | ✅ Active; miniz_oxide backend | Yes, backend fixed |

### 2.1 Deterministic Output

flate2 (miniz_oxide backend) produces **byte-identical output** if:
1. **Same backend.** Use `miniz_oxide` (default) consistently; never mix with zlib or libflate.
2. **Same timestamp.** Set all tar entries' mtime to a fixed Unix timestamp (e.g., `0` or from `SOURCE_DATE_EPOCH`).
3. **Fixed uid/gid.** Set all entries to `uid=0, gid=0`.
4. **Sorted order.** Write entries in alphabetical order.

**Rust snippet:**
```rust
use tar::Builder;
use flate2::Compression;
use flate2::write::GzEncoder;
use std::time::{SystemTime, UNIX_EPOCH};

let tar_gz = GzEncoder::new(file, Compression::default());
let mut tar = Builder::new(tar_gz);

// For each file:
let mut header = tar::Header::new_gnu();
header.set_mtime(SOURCE_DATE_EPOCH.as_secs()); // Fixed mtime
header.set_uid(0);
header.set_gid(0);
header.set_mode(0o644);
tar.append_data(&mut header, path, &file_data)?;
```

**Verification:**
Produce the archive twice; `sha256sum` should match if metadata is identical.

---

## 3. IMAGE HANDLING: In-Process Resize vs ffmpeg

**Recommendation:** **Use `image` 0.25.10 in-process for simplicity; ffmpeg only if batching 1000s of images.**

| **Approach** | **Crate** | **Version** | **Pros** | **Cons** |
|---|---|---|---|---|
| **In-process** | `image` | 0.25.10 | Zero deps; deterministic; ~10 lines; fast for <100 images | Slower than ffmpeg at scale |
| **ffmpeg spawn** | system binary | 6.1+ | Faster at scale (1000s); parallel | Process overhead; nondeterministic output |

### 3.1 In-Process (Recommended)

**Rust code:**
```rust
use image::ImageReader;

let img = ImageReader::open("poster.jpg")?.decode()?;
let resized = img.resize(342, 513, image::imageops::FilterType::Lanczos3);
resized.save_jpeg("poster_342.jpg", 85)?; // 85% quality
```

**Expected sizes (TMDB poster w342 → JPEG ~85% quality):**
- Poster w342 (342×513px, ~85% JPEG): **~20–30 KB** per image.
- 300 posters: **6–9 MB**.
- With backdrops (w780, 1280×720): **+15–20 MB**.
- **Total for 300-title library: ~25–35 MB** (metadata + images).

**TMDB image sizes available:**
- Poster: w92, w154, w185, w342, w500, w780, original.
- Backdrop: w300, w780, w1280, original.
- CDN: `https://image.tmdb.org/t/p/{size}{path}`.

### 3.2 ffmpeg (For large-scale)

If batching 1000+ images, spawn ffmpeg in parallel:
```bash
ffmpeg -i poster.jpg -vf scale=342:-1 poster_342.jpg
```
Overhead: ~50–100ms per image startup; 10–50× slower than `image` crate for <100 images.

---

## 4. KEY REPRESENTATION: Hex vs Base64 vs BIP39

**Recommendation:** **Use base64 (not hex, not BIP39) for config exchange.**

| **Format** | **Bytes (32-byte key)** | **Transcription** | **Encoding crate** | **Why** |
|---|---|---|---|---|
| **Hex** | 64 chars | Easy (0–9, a–f) | `hex` 0.4 | ❌ Double length; tedious |
| **Base64** | 44 chars | Standard | `base64` 0.23.1 | ✅ Compact; widely known |
| **BIP39** | 24 words | Memorable | `bip39` 0.6.0-beta | ❌ Overkill; requires phrase validation |

### 4.1 Base64 (Recommended)

**Rust (TOML config storage):**
```rust
use base64::Engine;

// Generate key
let key = rand::thread_rng().gen::<[u8; 32]>();
let encoded = base64::engine::general_purpose::STANDARD.encode(&key);
// Store `encoded` in toml: "AY7k3k...=="

// Load from toml
let decoded = base64::engine::general_purpose::STANDARD.decode(&encoded)?;
```

**Kotlin (Android storage via EncryptedSharedPreferences):**
```kotlin
import android.util.Base64

val encoded = preferences.getString("db_key", "")
val keyBytes = Base64.decode(encoded, Base64.DEFAULT) // 32 bytes
```

---

### 4.2 Hex (Not Recommended)

64 characters for a 32-byte key. Transcription error rate higher than base64 because no error-detection.

---

### 4.3 BIP39 (Not Recommended)

24 words from a fixed dictionary. **Pros:** Human-readable. **Cons:** Requires loading 2048-word list; overkill for a symmetric key; BIP39 is designed for HD wallets, not config rotation.

---

## 5. KNOWN FOOTGUNS & MITIGATION

### 5.1 Nonce Reuse (Critical)

**Footgun:** Using the same (key, nonce) pair twice leaks the authentication key H. An attacker collecting two ciphertexts with the same nonce can recover H and forge arbitrary messages.

**Mitigation:** Generate a **fresh random nonce per archive**. Store nonce in plaintext (e.g., first 12 bytes of the encrypted file). Never reuse a nonce under the same key.

---

### 5.2 Invocation Limit with Random Nonces

**Footgun:** NIST SP 800-38D recommends max 2^32 encryptions per key with random nonces. At 10 MB per archive, this is ~40 TB before rekey risk, but high-throughput systems (>100 archives/sec) can hit this.

**Mitigation:** For a **distribution system**, rotate the key every 6–12 months (publish a new `latest.json` with a new key). This is standard practice for CDN-distributed secrets.

---

### 5.3 Compression Side-Channel (Minor)

**Footgun:** DEFLATE compression ratio leaks info about plaintext structure. Encrypted tar.gz is safe, but if metadata is highly repetitive, an attacker could infer structure.

**Mitigation:** No action needed for this use case. Compression after encryption is standard.

---

### 5.4 Determinism Failures

**Footgun:** tar or flate2 producing different output on different machines due to timestamp or backend changes.

**Mitigation:**
- Pin `tar` ≥0.4.45 and `flate2` version.
- Always set `SOURCE_DATE_EPOCH` in CI/CD.
- Verify sha256 of the produced archive in tests.

---

### 5.5 Android Keystore vs Plaintext Config

**Footgun:** Storing the 32-byte key in plaintext in Android SharedPreferences (even EncryptedSharedPreferences) exposes it to reverse engineering if the app is decompiled.

**Mitigation:** Use Android KeyStore for wrapping the key (via EncryptedSharedPreferences + MasterKey with AES256_GCM), or use AndroidKeyStore to store the key directly. Never hardcode keys in code.

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context, "prebuilt_db", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
prefs.edit().putString("db_key", base64_key).apply()
```

---

### 5.6 TMDB Image CDN Availability

**Footgun:** Images disappear from TMDB CDN or availability rates vary. Archiving images at ingest time is safer than streaming from CDN.

**Mitigation:** Prebuilt package includes cached copies of images. Refresh them on a schedule (e.g., weekly).

---

## ARCHITECTURE SUMMARY

```
┌─────────────────────────────────────────┐
│  Mediagram Uploader (Rust)              │
├─────────────────────────────────────────┤
│ 1. Fetch metadata + posters from TMDB   │
│ 2. Downsize posters to w342 (image 0.25)│
│ 3. Build SQLite index snapshot          │
│ 4. tar (mtime=0, uid=0, sorted)         │
│ 5. gzip (flate2 miniz_oxide)            │
│ 6. AES-256-GCM encrypt (nonce prepended)│
│ 7. Publish to S3 + latest.json          │
└─────────────────────────────────────────┘
                    ↓ (HTTPS)
        https://example.com/prebuilt_db_*.tar.gz
        + latest.json (metadata + nonce)
                    ↓
┌─────────────────────────────────────────┐
│  Android TV Player (Kotlin)             │
├─────────────────────────────────────────┤
│ 1. Fetch latest.json → discover URL     │
│ 2. Decrypt with javax.crypto AES-256-GCM
│ 3. Decompress gzip → tar                │
│ 4. Extract SQLite + poster JPEGs        │
│ 5. Load UI                              │
└─────────────────────────────────────────┘
```

---

## UNRESOLVED QUESTIONS

1. **Key rotation frequency:** How often should the encryption key rotate? Recommend annually, but product decision.
2. **Poster dimensions:** Should the uploader archive w342, w500, or both? w342 is recommended for TV UIs (lower bandwidth), but product may need w500 for 4K displays.
3. **Metadata freshness:** Full rebuild weekly/daily/monthly? Affects storage and bandwidth.
4. **Backwards compatibility:** Should old encrypted archives remain accessible after key rotation? If yes, need key versioning scheme in `latest.json`.
5. **Signing:** The `latest.json` should be signed (Ed25519 or RSA) to prevent MITM attacks. Not covered in this report but critical for security.

---

## CITATIONS & REFERENCES

**Crates & Versions:**
- [aes-gcm 0.10.3](https://docs.rs/aes-gcm/latest/aes_gcm/) — RustCrypto AES-GCM
- [tar 0.4.46](https://docs.rs/crate/tar/latest) — Tar reading/writing
- [flate2 1.0.31 / 1.1.5](https://docs.rs/crate/flate2/latest) — DEFLATE/gzip compression
- [image 0.25.10](https://docs.rs/crate/image/latest) — Image codecs & resizing
- [base64 0.23.1](https://docs.rs/crate/base64/latest) — Base64 encoding
- [bip39 0.6.0-beta](https://docs.rs/bip39/latest/bip39/) — BIP39 mnemonics

**Standards & Security:**
- [NIST SP 800-38D](https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf) — GCM mode specification
- [RFC 5116](https://tools.ietf.org/html/rfc5116) — AEAD interface
- [age format](https://docs.google.com/document/d/11yHom20CrsuX8KQJXBBw04s80Unjv8zCg_A7sPAX_9Y/mobilebasic) — Specification
- [GCM nonce reuse attacks](https://www.elttam.com/blog/key-recovery-attacks-on-gcm) — elttam security blog

**Android & Java:**
- [GCMParameterSpec](https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec) — Android docs
- [EncryptedSharedPreferences](https://developer.android.com/training/data-storage/shared-preferences/encrypted-shared-preferences) — Android security
- [Cipher.getInstance("AES/GCM/NoPadding")](https://developer.android.com/reference/javax/crypto/Cipher) — Standard API

**TMDB API:**
- [Image Basics](https://developer.themoviedb.org/docs/image-basics) — TMDB image sizes and CDN
- [Configuration endpoint](https://developer.themoviedb.org/reference/configuration-details) — Image size options

**Reproducible Builds:**
- [reproducible-builds.org](https://reproducible-builds.org/docs/source-date-epoch/) — SOURCE_DATE_EPOCH spec
- [GNU Tar reproducibility](https://www.gnu.org/software/tar/manual/html_section/Reproducibility.html) — Tar best practices

