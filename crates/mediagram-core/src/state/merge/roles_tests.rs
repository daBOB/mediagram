//! `profile-roles-merge.json` pins each rule to the web's, two documents at a
//! time and in both orders; this holds them together across three.

use crate::state::merge::{MergedState, merge_states};
use crate::state::record::{AdminClaim, KidsAge, SyncRecord, parse_record};

fn doc(device: &str, body: &str) -> SyncRecord {
    parse_record(&format!(
        r#"{{"format":1,"device":"{device}","writtenAt":1,{body}}}"#
    ))
    .unwrap()
}

fn merged(docs: &[SyncRecord], order: [usize; 3]) -> MergedState {
    let mut state = merge_states(&order.map(|i| docs[i].clone()));
    state.profiles.sort_by(|a, b| a.name.cmp(&b.name));
    state
}

/// Every order three devices could wake up in gives one answer: the
/// earliest grown-up claim is the admin (a kid's earlier one counts for
/// nothing), a parent's limit beats an older build's bare `kids`, and a
/// mark from 6 survives that build's echo.
#[test]
fn three_devices_agree_whatever_order_their_documents_arrive_in() {
    let docs = [
        doc(
            "laptop",
            r#""profiles":[{"name":"André","admin":{"claimedAt":20}},
                {"name":"Mia","kids":true,"parent":"André","kidsAge":{"age":6,"updatedAt":5}}],
               "kids":[{"setId":"x","updatedAt":3,"age":6}]"#,
        ),
        doc(
            "old-tv",
            r#""profiles":[{"name":"andré"},{"name":"Mia","kids":true}],"kids":[{"setId":"x","updatedAt":3}]"#,
        ),
        doc(
            "phone",
            r#""profiles":[{"name":"Bea","admin":{"claimedAt":10}},{"name":"mia","admin":{"claimedAt":1}}]"#,
        ),
    ];
    let first = merged(&docs, [0, 1, 2]);
    for order in [[0, 2, 1], [1, 0, 2], [1, 2, 0], [2, 0, 1], [2, 1, 0]] {
        assert_eq!(merged(&docs, order), first, "order {order:?}");
    }

    let roles: Vec<_> = first
        .profiles
        .iter()
        .map(|p| (p.name.as_str(), &p.roles))
        .collect();
    assert_eq!(roles[0].0, "andré");
    assert_eq!(roles[0].1.admin, None);
    assert_eq!(
        (roles[1].0, &roles[1].1.admin),
        ("bea", &Some(AdminClaim { claimed_at: 10.0 }))
    );
    let mia = roles[2].1;
    assert_eq!(mia.admin, None);
    assert_eq!(
        mia.kids_age,
        Some(KidsAge {
            age: 6,
            updated_at: 5.0
        })
    );
    assert_eq!(mia.parent.as_deref(), Some("andré"));
    assert_eq!(first.kids[0].age, Some(6));
}
