//! Set identity: sha256 over the concatenated lowercase-hex part hashes in
//! part order. Equivalent in strength to a whole-file hash, but computable
//! incrementally and verifiable part by part.

use sha2::{Digest, Sha256};

pub fn set_hash<S: AsRef<str>>(part_hashes: &[S]) -> String {
    let mut h = Sha256::new();
    for p in part_hashes {
        h.update(p.as_ref().trim().to_ascii_lowercase().as_bytes());
    }
    hex::encode(h.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deterministic_and_order_sensitive() {
        let a = set_hash(&["aa", "bb"]);
        assert_eq!(a, set_hash(&["AA", "bb "]));
        assert_ne!(a, set_hash(&["bb", "aa"]));
        assert_eq!(a.len(), 64);
    }
}
