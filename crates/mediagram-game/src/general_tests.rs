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

#[test]
fn pack_entries_are_sourced_and_well_formed() {
    let entries = general_pack_entries();
    let texts: std::collections::HashSet<_> = entries.iter().map(|q| &q.text).collect();
    assert_eq!(texts.len(), entries.len(), "texts are unique");
    for q in &entries {
        assert!(CATEGORIES.contains(&q.category.as_str()), "{}", q.text);
        assert!(q.text.ends_with('?') && q.text.chars().count() <= 140, "{}", q.text);
        assert!(q.source.starts_with("https://"), "{}", q.text);
        assert!(
            std::iter::once(&q.correct).chain(&q.wrong).all(|o| !o.is_empty() && o.chars().count() <= 40),
            "{}",
            q.text
        );
    }
}

#[test]
fn pack_has_300_questions() {
    assert!(general_pack_entries().len() >= 300);
}
