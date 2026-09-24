use super::*;

#[test]
fn io_and_network_causes_never_reach_the_public_error_text() {
    let private = "chat=-100123456 document=987654 token=private-token";
    let io = CoreError::io("opening the catalog")(std::io::Error::other(private));
    let network = CoreError::network("reading the document")(private);
    assert!(matches!(&io, CoreError::Io(message) if message == "opening the catalog"));
    assert!(matches!(&network, CoreError::Network(message) if message == "reading the document"));
    assert_eq!(io.to_string(), "io error: opening the catalog");
    assert_eq!(network.to_string(), "network error: reading the document");
    for error in [io, network] {
        assert!(!format!("{error:?}").contains(private));
    }
}

#[test]
fn logging_a_cause_preserves_each_error_variant_and_its_public_sentence() {
    let refusals = [
        CoreError::NotAuthorized("sign in again".into()),
        CoreError::NotFound("set not found".into()),
        CoreError::Cipher("package failed authentication".into()),
        CoreError::Library("choose a readable library".into()),
    ];
    for refusal in refusals {
        let expected = refusal.to_string();
        let kind = std::mem::discriminant(&refusal);
        let returned = refusal.logged()("private path /home/viewer/session; message=123");
        assert_eq!(std::mem::discriminant(&returned), kind);
        assert_eq!(returned.to_string(), expected);
    }
}
