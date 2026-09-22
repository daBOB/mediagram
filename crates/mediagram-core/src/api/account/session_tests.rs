use super::*;

#[test]
fn a_stored_key_reads_back_exactly() {
    let dir = tempfile::tempdir().unwrap();
    let key = [7u8; AUTH_KEY_LEN];
    store_auth_key(dir.path(), 2, &key).unwrap();
    assert_eq!(load_auth_key(dir.path()), Some((2, key)));
}

#[test]
fn the_session_file_is_never_group_or_world_readable() {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    store_auth_key(dir.path(), 2, &[1u8; AUTH_KEY_LEN]).unwrap();
    let mode = std::fs::metadata(dir.path().join(SESSION_FILE))
        .unwrap()
        .permissions()
        .mode();
    assert_eq!(mode & 0o777, 0o600);
}

/// A relogin, or simply calling twice, must overwrite rather than fail
/// on the file already existing.
#[test]
fn storing_a_key_twice_replaces_it() {
    let dir = tempfile::tempdir().unwrap();
    store_auth_key(dir.path(), 2, &[1u8; AUTH_KEY_LEN]).unwrap();
    store_auth_key(dir.path(), 3, &[9u8; AUTH_KEY_LEN]).unwrap();
    assert_eq!(load_auth_key(dir.path()), Some((3, [9u8; AUTH_KEY_LEN])));
}

/// A first launch and a corrupted file must look identical: both mean
/// "log in", never a different kind of error.
#[test]
fn a_missing_or_truncated_file_reads_back_as_no_session() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(load_auth_key(dir.path()), None);

    std::fs::write(dir.path().join(SESSION_FILE), [0u8; 10]).unwrap();
    assert_eq!(load_auth_key(dir.path()), None);
}

/// Forgetting is what a revoked login does, and it may run twice when two
/// calls are refused at once.
#[test]
fn a_forgotten_key_reads_back_as_no_session() {
    let dir = tempfile::tempdir().unwrap();
    store_auth_key(dir.path(), 2, &[1u8; AUTH_KEY_LEN]).unwrap();
    forget_auth_key(dir.path()).unwrap();
    assert_eq!(load_auth_key(dir.path()), None);
    forget_auth_key(dir.path()).unwrap();
}
