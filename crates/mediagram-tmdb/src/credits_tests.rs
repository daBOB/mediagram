use super::*;
use crate::tmdb_client::TmdbApi;
use serde_json::{Value, json};
use std::cell::RefCell;

/// Answers one fixed body per path, and panics on anything unexpected —
/// a wrong path asked here is a bug in the code under test, not a case
/// to skip quietly.
struct Stub {
    by_path: Vec<(&'static str, Value)>,
    calls: RefCell<Vec<String>>,
}

impl TmdbApi for Stub {
    async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
        self.calls.borrow_mut().push(path.to_string());
        self.by_path
            .iter()
            .find(|(p, _)| *p == path)
            .map(|(_, v)| v.clone())
            .with_context(|| format!("stub asked for unexpected path {path}"))
    }
}

fn movie_credits_body() -> Value {
    json!({
        "cast": [
            {"id": 3, "name": "Third Billed", "character": "Cameo", "order": 2, "profile_path": "/c.jpg"},
            {"id": 1, "name": "Lead", "character": "Hero", "order": 0, "profile_path": "/a.jpg"},
            {"id": 2, "name": "Second Lead", "character": "Sidekick", "order": 1, "profile_path": null}
        ],
        "crew": [
            {"id": 9, "name": "A Director", "job": "Director", "profile_path": "/d.jpg"},
            {"id": 10, "name": "A Writer", "job": "Writer", "profile_path": "/w.jpg"}
        ]
    })
}

#[tokio::test]
async fn a_movie_orders_cast_by_billing_then_its_director() {
    let stub = Stub {
        by_path: vec![("/movie/550/credits", movie_credits_body())],
        calls: RefCell::new(Vec::new()),
    };
    let rows = credits(&stub, Kind::Movie, 550).await.unwrap();
    assert_eq!(
        rows.iter().map(|r| r.person_id).collect::<Vec<_>>(),
        [1, 2, 3, 9]
    );
    assert_eq!(rows[0].role.as_deref(), Some("Hero"));
    assert_eq!(rows[1].dept, "cast");
    assert_eq!(rows[3].dept, "crew");
    assert_eq!(rows[3].role.as_deref(), Some("Director"));
    // A writer is not a director and must not show up as one.
    assert!(rows.iter().all(|r| r.person_id != 10));
    // `ord` is sequential and unique, the primary key relies on it.
    let ords: Vec<u32> = rows.iter().map(|r| r.ord).collect();
    assert_eq!(ords, [0, 1, 2, 3]);
}

#[tokio::test]
async fn more_than_the_limit_of_cast_is_trimmed() {
    let cast: Vec<Value> = (0..20)
        .map(|i| json!({"id": i, "name": format!("Actor {i}"), "order": i}))
        .collect();
    let stub = Stub {
        by_path: vec![("/movie/1/credits", json!({"cast": cast, "crew": []}))],
        calls: RefCell::new(Vec::new()),
    };
    let rows = credits(&stub, Kind::Movie, 1).await.unwrap();
    assert_eq!(rows.len(), CAST_LIMIT);
    assert_eq!(rows.last().unwrap().person_id, (CAST_LIMIT - 1) as u64);
}

#[tokio::test]
async fn a_series_adds_its_creators_from_the_details_payload() {
    let stub = Stub {
        by_path: vec![
            (
                "/tv/1396/credits",
                json!({
                    "cast": [{"id": 1, "name": "Lead", "character": "Walt", "order": 0}],
                    "crew": []
                }),
            ),
            (
                "/tv/1396",
                json!({
                    "id": 1396,
                    "created_by": [{"id": 66, "name": "A Creator", "profile_path": "/e.jpg"}]
                }),
            ),
        ],
        calls: RefCell::new(Vec::new()),
    };
    let rows = credits(&stub, Kind::Ep, 1396).await.unwrap();
    assert_eq!(rows.len(), 2);
    let creator = &rows[1];
    assert_eq!(creator.person_id, 66);
    assert_eq!(creator.role.as_deref(), Some("Creator"));
    assert_eq!(creator.dept, "crew");
    assert_eq!(creator.profile_path.as_deref(), Some("/e.jpg"));
}

#[tokio::test]
async fn a_course_has_no_credits_endpoint() {
    let stub = Stub {
        by_path: vec![],
        calls: RefCell::new(Vec::new()),
    };
    assert!(credits(&stub, Kind::Tut, 1).await.is_err());
}

#[tokio::test]
async fn an_empty_character_is_no_role() {
    let stub = Stub {
        by_path: vec![(
            "/movie/1/credits",
            json!({"cast": [{"id": 1, "name": "Extra", "character": "  ", "order": 0}], "crew": []}),
        )],
        calls: RefCell::new(Vec::new()),
    };
    let rows = credits(&stub, Kind::Movie, 1).await.unwrap();
    assert_eq!(rows[0].role, None);
}

#[test]
fn a_null_franchise_or_creator_name_does_not_cost_a_title_its_details() {
    let details: crate::tmdb_types::DetailsResponse = serde_json::from_value(serde_json::json!({
        "id": 1,
        "belongs_to_collection": { "id": 151, "name": null },
        "created_by": [{ "id": 7, "name": null }],
        "overview": "Still described."
    }))
    .expect("a null name is tolerated");
    let row = crate::details::from_details(mlib_spec::Kind::Movie, "de-DE", &details);
    assert_eq!(row.overview.as_deref(), Some("Still described."));
    assert_eq!(row.collection_id, Some(151));
    assert_eq!(row.collection_name, None);
}
