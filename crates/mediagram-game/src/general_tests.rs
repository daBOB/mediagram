use super::*;

#[test]
fn general_pack_is_well_formed() {
    let entries = general_pack_entries();
    assert!(!entries.is_empty());
    for e in &entries {
        assert!(!e.category.is_empty() && !e.text.is_empty(), "{e:?}");
        let mut options = vec![&e.correct, &e.wrong[0], &e.wrong[1], &e.wrong[2]];
        options.sort();
        options.dedup();
        assert_eq!(options.len(), 4, "options must be distinct: {}", e.text);
    }
    assert_eq!(general_pack().len(), entries.len());
}
