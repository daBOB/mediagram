//! Counts outside the known schema range must never wrap on narrower hosts.

use mlib_spec::schema::{GROUPS, migrations_up_to};

#[test]
fn negative_versions_select_no_migrations() {
    assert!(migrations_up_to(-1).is_empty());
    assert!(migrations_up_to(i64::MIN).is_empty());
}

#[test]
fn any_version_past_the_known_groups_selects_all_migrations() {
    let all: Vec<_> = GROUPS
        .iter()
        .flat_map(|group| group.iter().copied())
        .collect();
    assert_eq!(migrations_up_to(i64::MAX), all);
    assert_eq!(migrations_up_to(1_i64 << 32), all);
}
