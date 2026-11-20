use super::*;

#[test]
fn only_a_network_input_gets_a_read_timeout() {
    assert_eq!(
        network_args(Path::new("http://127.0.0.1:8770/file")),
        ["-rw_timeout", NETWORK_RW_TIMEOUT_US]
    );
    assert!(network_args(Path::new("/media/film.mkv")).is_empty());
    assert!(network_args(Path::new("file:///media/film.mkv")).is_empty());
}
