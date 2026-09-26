use grammers_mtsender::RpcError;
use grammers_tl_types::types::RpcError as RawRpcError;

use super::*;

fn rpc(code: i32, name: &str) -> InvocationError {
    InvocationError::Rpc(RpcError::from(RawRpcError {
        error_code: code,
        error_message: name.into(),
    }))
}

fn row(current: bool, api_id: i32, hash: &str, last_active: i64) -> RawAuthorization {
    RawAuthorization {
        current,
        unconfirmed: false,
        hash: hash.into(),
        device_model: "device".into(),
        platform: "Linux".into(),
        api_id,
        app_name: "mediagram".into(),
        app_version: "1.0".into(),
        date_created: last_active,
        date_active: last_active,
        country: "DE".into(),
        region: "".into(),
    }
}

#[test]
fn a_foreign_api_id_is_excluded_unless_current() {
    let rows = vec![row(false, 1, "a", 10), row(false, 2, "b", 20), row(true, 2, "c", 5)];
    let shaped = shape(&rows, 1);
    assert_eq!(shaped.iter().map(|s| s.id.as_str()).collect::<Vec<_>>(), ["a", "c"]);
}

#[test]
fn sessions_sort_by_last_active_newest_first() {
    let rows = vec![row(false, 1, "old", 1), row(false, 1, "new", 100)];
    let shaped = shape(&rows, 1);
    assert_eq!(shaped.iter().map(|s| s.id.as_str()).collect::<Vec<_>>(), ["new", "old"]);
}

#[test]
fn an_empty_country_reads_as_no_location() {
    let mut a = row(false, 1, "a", 1);
    a.country = "  ".into();
    assert_eq!(shape(&[a], 1)[0].location, None);
}

#[test]
fn a_fresh_session_guard_reads_as_a_sentence() {
    let err = revoke_error(&rpc(400, "FRESH_RESET_AUTHORISATION_FORBIDDEN"));
    assert!(matches!(err, CoreError::NotAuthorized(msg) if msg.contains("24 hours")));
}

#[test]
fn other_4xx_refusals_name_telegrams_own_reason() {
    let err = revoke_error(&rpc(400, "HASH_INVALID"));
    assert!(matches!(err, CoreError::NotAuthorized(msg) if msg.contains("HASH_INVALID")));
}

#[test]
fn a_flood_wait_is_a_network_fault_not_a_refusal() {
    let err = revoke_error(&rpc(420, "FLOOD_WAIT_30"));
    assert!(matches!(err, CoreError::Network(_)));
}

#[test]
fn a_hash_already_gone_is_recognised() {
    assert!(already_gone(&rpc(400, "HASH_INVALID")));
    assert!(!already_gone(&rpc(400, "FRESH_RESET_AUTHORISATION_FORBIDDEN")));
}

/// Runs the web's own sessions fixture against this `shape`, the same
/// accommodation `channel/index_tests.rs` makes for `pick_index`. A case
/// that only passes after a change here does not belong in the fixture —
/// the two surfaces must show the same rows for the same account.
#[test]
fn shape_matches_the_shared_fixture() {
    #[derive(serde::Deserialize)]
    struct RawCase {
        current: bool,
        unconfirmed: bool,
        hash: String,
        #[serde(rename = "deviceModel")]
        device_model: String,
        platform: String,
        #[serde(rename = "apiId")]
        api_id: i32,
        #[serde(rename = "appName")]
        app_name: String,
        #[serde(rename = "appVersion")]
        app_version: String,
        #[serde(rename = "dateCreated")]
        date_created: i64,
        #[serde(rename = "dateActive")]
        date_active: i64,
        country: String,
        region: String,
    }

    #[derive(serde::Deserialize)]
    struct Fixture {
        #[serde(rename = "apiId")]
        api_id: i32,
        authorizations: Vec<RawCase>,
        expect: serde_json::Value,
    }

    let path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../web/test/fixtures/authorizations/cases.json");
    // Skipped without the web checkout, as the other shared fixtures are.
    let Ok(text) = std::fs::read_to_string(&path) else {
        eprintln!("skipping: {} is not present", path.display());
        return;
    };
    let fixture: Fixture = serde_json::from_str(&text).unwrap();

    let raw: Vec<RawAuthorization> = fixture
        .authorizations
        .into_iter()
        .map(|row| RawAuthorization {
            current: row.current,
            unconfirmed: row.unconfirmed,
            hash: row.hash,
            device_model: row.device_model,
            platform: row.platform,
            api_id: row.api_id,
            app_name: row.app_name,
            app_version: row.app_version,
            date_created: row.date_created,
            date_active: row.date_active,
            country: row.country,
            region: row.region,
        })
        .collect();

    let shaped = shape(&raw, fixture.api_id);
    let got = serde_json::to_value(
        shaped
            .into_iter()
            .map(|s| {
                serde_json::json!({
                    "id": s.id, "device": s.device, "platform": s.platform, "app": s.app,
                    "appVersion": s.app_version, "location": s.location,
                    "lastActive": s.last_active, "created": s.created,
                    "current": s.current, "unconfirmed": s.unconfirmed,
                })
            })
            .collect::<Vec<_>>(),
    )
    .unwrap();
    assert_eq!(got, fixture.expect);
}
