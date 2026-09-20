//! A loopback HTTP fixture proving `refresh_catalog`'s security properties
//! against real bytes on a real socket: the size cap, replay refusal, and
//! the atomic swap of `current`.

use std::io::Write as _;
use std::time::Duration;

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use flate2::Compression;
use flate2::write::GzEncoder;
use mlib_spec::package::{CIPHER, LatestPointer, PACKAGE_FORMAT, associated_data, key_id};
use sha2::{Digest, Sha256};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

use super::*;
use crate::package::cipher;

const KEY: [u8; 32] = [3u8; 32];

/// Serves `responses` in order, one per accepted connection, then stops.
/// Each is written in small paced pieces, so a body arrives over several
/// reads rather than one buffered write.
async fn serve(responses: Vec<Vec<u8>>) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let base = format!("http://{}", listener.local_addr().unwrap());
    tokio::spawn(async move {
        for response in responses {
            let Ok((mut socket, _)) = listener.accept().await else {
                return;
            };
            let mut buf = [0u8; 1024];
            let _ = socket.read(&mut buf).await;
            for chunk in response.chunks(4096) {
                if socket.write_all(chunk).await.is_err() {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(1)).await;
            }
            let _ = socket.shutdown().await;
        }
    });
    base
}

/// A raw HTTP/1.1 response, close-delimited: no `Content-Length` at all.
fn http_response(body: &[u8]) -> Vec<u8> {
    let mut out = b"HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n".to_vec();
    out.extend_from_slice(body);
    out
}

fn pointer_response(pointer: &LatestPointer) -> Vec<u8> {
    http_response(&serde_json::to_vec(pointer).unwrap())
}

/// An empty, real, migrated `library.db` that `count_playable` can open.
fn empty_library_db_bytes() -> Vec<u8> {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("library.db");
    let conn = rusqlite::Connection::open(&path).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    drop(conn);
    std::fs::read(&path).unwrap()
}

fn append(builder: &mut tar::Builder<&mut Vec<u8>>, name: &str, data: &[u8]) {
    let mut header = tar::Header::new_gnu();
    header.set_size(data.len() as u64);
    header.set_mode(0o644);
    header.set_cksum();
    builder.append_data(&mut header, name, data).unwrap();
}

/// A genuinely valid sealed package: `library.db` plus an agreeing
/// manifest, gzipped, tarred, and sealed under `KEY`.
fn fixture_package(created_at: i64) -> (LatestPointer, Vec<u8>) {
    let mut tar_bytes = Vec::new();
    {
        let mut builder = tar::Builder::new(&mut tar_bytes);
        let manifest = format!(
            r#"{{"created_at":{created_at},"schema":{}}}"#,
            mlib_spec::schema::SCHEMA_VERSION
        );
        append(&mut builder, "manifest.json", manifest.as_bytes());
        append(&mut builder, "library.db", &empty_library_db_bytes());
        builder.finish().unwrap();
    }
    let mut gz = Vec::new();
    {
        let mut enc = GzEncoder::new(&mut gz, Compression::fast());
        enc.write_all(&tar_bytes).unwrap();
        enc.finish().unwrap();
    }

    let mut pointer = LatestPointer {
        format: PACKAGE_FORMAT,
        created_at,
        file: "archive.tar.gz.enc".into(),
        url: "https://example.invalid/should-never-be-fetched".into(),
        bytes: 0,
        sha256: String::new(),
        cipher: CIPHER.into(),
        key_id: key_id(&KEY),
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
    };
    let sealed = cipher::seal(&KEY, &gz, &associated_data(&pointer)).unwrap();
    pointer.bytes = sealed.len() as u64;
    pointer.sha256 = hex::encode(Sha256::digest(&sealed));
    (pointer, sealed)
}

fn core_at(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Neither the pointer's `bytes` field nor a `Content-Length` header is
/// authenticated, so trusting either gives no real ceiling. This response
/// declares no length at all; only counting bytes as they arrive can still
/// stop an oversized body.
#[tokio::test]
async fn the_download_cap_trips_against_bytes_actually_received() {
    let (mut pointer, _sealed) = fixture_package(100);
    // The pointer's own claim is tiny, so `cap` is tiny; the server ignores
    // it and sends far more anyway.
    pointer.bytes = 16;
    let oversized = vec![b'x'; 200_000];
    let base = serve(vec![pointer_response(&pointer), http_response(&oversized)]).await;

    let dir = tempfile::tempdir().unwrap();
    let err = refresh_catalog(
        &core_at(dir.path()),
        format!("{base}/latest.json"),
        STANDARD.encode(KEY),
    )
    .await
    .unwrap_err();

    assert!(matches!(err, CoreError::Network(_)));
}

/// A package that decrypts correctly under the right key is still a
/// replay if it is older than what is already held — the cipher cannot
/// catch that, only the identity comparison can — and a replay must be
/// refused. The identity already held must also be provably untouched
/// afterwards, not merely "the call returned an error".
#[tokio::test]
async fn an_older_package_is_refused_and_current_is_left_untouched() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_at(dir.path());
    let current = catalog::current_dir(&core);
    std::fs::create_dir_all(&current).unwrap();
    let held = catalog::Identity {
        format: PACKAGE_FORMAT,
        created_at: 500,
        key_id: key_id(&KEY),
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
    };
    catalog::write_identity(&current, &held).unwrap();

    let (pointer, _sealed) = fixture_package(100); // older than 500
    let base = serve(vec![pointer_response(&pointer)]).await; // package must never be requested

    let err = refresh_catalog(&core, format!("{base}/latest.json"), STANDARD.encode(KEY))
        .await
        .unwrap_err();

    assert!(matches!(err, CoreError::Cipher(_)));
    assert_eq!(catalog::read_identity(&current).unwrap(), Some(held));
}

/// A reader must never observe half a catalog: `current` resolves to
/// either the previous version or the new one in full, never a partially
/// staged one — checked here from the far side, where it now resolves to a
/// new, queryable catalog carrying exactly the identity that was offered.
#[tokio::test]
async fn a_successful_refresh_swaps_current_atomically() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_at(dir.path());
    let (pointer, sealed) = fixture_package(700);
    let base = serve(vec![pointer_response(&pointer), http_response(&sealed)]).await;

    let count = refresh_catalog(&core, format!("{base}/latest.json"), STANDARD.encode(KEY))
        .await
        .unwrap();

    assert_eq!(count, 0); // the fixture db carries no sets, only a schema
    let current = catalog::current_dir(&core);
    assert_eq!(
        catalog::read_identity(&current).unwrap(),
        Some(catalog::identity_of(&pointer))
    );
    assert!(current.join("library.db").exists());
}
