//! Holds the migrations, versions and state spellings in `schema.rs` together.
/// `PLAYABLE_SQL` is a `const`, so it cannot be built from the spellings
/// above; this holds the two together instead.
#[test]
fn the_playable_gate_spells_states_as_the_constants_do() {
    let gate = super::PLAYABLE_SQL;
    assert!(gate.contains(&format!("s.status = '{}'", super::SET_COMPLETE)));
    assert!(gate.contains(&format!("p.status = '{}'", super::PART_DONE)));
}

#[test]
fn readers_accept_every_version_from_the_oldest_to_the_current() {
    let range: Vec<i64> = (super::OLDEST_READABLE_SCHEMA..=super::SCHEMA_VERSION).collect();
    assert_eq!(super::READABLE_SCHEMAS, range.as_slice());
}

#[test]
fn the_first_group_creates_the_tables_idempotently() {
    let v1 = super::GROUPS[0];
    assert!(v1.len() >= 4);
    assert!(v1.iter().all(|m| m.contains("IF NOT EXISTS")));
}

/// Later groups run once, gated by the recorded version, so they are free
/// to use statements SQLite cannot express idempotently.
#[test]
fn there_is_one_group_per_version() {
    assert_eq!(super::GROUPS.len() as i64, super::SCHEMA_VERSION);
}

#[test]
fn migrations_up_to_accumulates_groups() {
    assert!(super::migrations_up_to(0).is_empty());
    assert_eq!(super::migrations_up_to(1).len(), super::GROUPS[0].len());
    assert_eq!(
        super::migrations_up_to(2).len(),
        super::GROUPS[0].len() + super::GROUPS[1].len()
    );
    // Every group, whatever the current version, so this keeps holding
    // as versions are added.
    assert_eq!(
        super::migrations_up_to(super::SCHEMA_VERSION).len(),
        super::GROUPS.iter().map(|g| g.len()).sum::<usize>()
    );
}
