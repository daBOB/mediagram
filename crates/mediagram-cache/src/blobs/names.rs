//! The shapes a blob name may take; anything else never reaches the disk.

use super::BlobKind;

/// `key` of a `tmdb` blob: 64 lowercase hex.
pub fn valid_tmdb_key(key: &str) -> bool {
    key.len() == 64 && key.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
}

/// `size` of an `art` blob: `w`/`h` plus 2-4 digits, or `original`.
pub fn valid_art_size(size: &str) -> bool {
    size == "original"
        || size
            .strip_prefix(['w', 'h'])
            .is_some_and(|d| (2..=4).contains(&d.len()) && d.bytes().all(|b| b.is_ascii_digit()))
}

/// `name` of an `art` blob: 1-64 of `[A-Za-z0-9_-]`, then `.jpg`, `.png` or `.svg`.
pub fn valid_art_name(name: &str) -> bool {
    let Some((stem, ext)) = name.rsplit_once('.') else {
        return false;
    };
    (1..=64).contains(&stem.len())
        && stem
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
        && matches!(ext, "jpg" | "png" | "svg")
}

/// The name a kind is stored under: the key itself for `Tmdb`,
/// `{size}/{name}` for `Art`. `None` when it is not a valid one.
pub(super) fn relative_path(kind: BlobKind, name: &str) -> Option<String> {
    match kind {
        BlobKind::Tmdb => valid_tmdb_key(name).then(|| format!("tmdb/{}/{name}", &name[..2])),
        BlobKind::Art => {
            let (size, file) = name.split_once('/')?;
            (valid_art_size(size) && valid_art_name(file)).then(|| format!("art/{name}"))
        }
    }
}
