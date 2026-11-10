use super::*;

#[test]
fn general_pack_is_well_formed() {
    let entries = general_pack_entries();
    assert!(!entries.is_empty());
    for e in &entries {
        assert!(!e.category.is_empty() && !e.text.is_empty(), "{e:?}");
    }
    assert_eq!(general_pack().len(), entries.len());
}

fn assert_sourced_and_well_formed(entries: &[GeneralEntry], categories: &[&str]) {
    let texts: std::collections::HashSet<_> = entries.iter().map(|q| &q.text).collect();
    assert_eq!(texts.len(), entries.len(), "texts are unique");
    for q in entries {
        let mut options = vec![&q.correct, &q.wrong[0], &q.wrong[1], &q.wrong[2]];
        options.sort();
        options.dedup();
        assert_eq!(options.len(), 4, "options must be distinct: {}", q.text);
        assert!(categories.contains(&q.category.as_str()), "{}", q.text);
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
fn pack_entries_are_sourced_and_well_formed() {
    assert_sourced_and_well_formed(&general_pack_entries(), &CATEGORIES);
}

#[test]
fn adult_pack_is_its_own_category_and_file() {
    let adult = adult_pack_entries();
    assert!(adult.len() >= 40, "{}", adult.len());
    assert_sourced_and_well_formed(&adult, &[ADULT_CATEGORY]);
    assert!(!CATEGORIES.contains(&ADULT_CATEGORY), "never in the general pack's categories");
    let general: std::collections::HashSet<_> = general_pack_entries().into_iter().map(|q| q.text).collect();
    assert!(adult.iter().all(|q| !general.contains(&q.text)));
    assert_eq!(adult_pack().len(), adult.len());
}

#[test]
fn pack_has_300_questions() {
    assert!(general_pack_entries().len() >= 300);
}
