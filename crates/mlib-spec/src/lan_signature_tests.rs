use super::*;

const TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

#[test]
fn chunk_vector_signs_to_the_published_signature() {
    assert_eq!(
        sign(TOKEN, "PUT", "/v1/sets/abc123/chunks/0", 5, b"hello"),
        "c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85"
    );
}

#[test]
fn blob_vector_signs_with_the_body_length() {
    assert_eq!(
        sign(TOKEN, "PUT", "/v1/art/w342/abc.jpg", 5, b"hello"),
        "e1919b6ff65a768f6d2bc1b80f88296901cbb04fe0a0f74736e2ff0e8b7cdf65"
    );
}
