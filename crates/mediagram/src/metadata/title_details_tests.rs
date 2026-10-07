//! What recording a title leaves in the log when the provider will not answer.

use std::sync::{Arc, Mutex};

use anyhow::anyhow;
use serde_json::Value;

use super::*;

/// A provider that cannot be reached at all.
struct Unreachable;

impl TmdbApi for Unreachable {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
        Err(anyhow!("connection refused"))
    }
}

/// Everything the formatter writes, for reading back.
#[derive(Clone, Default)]
struct Captured(Arc<Mutex<Vec<u8>>>);

impl std::io::Write for Captured {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(buf);
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

/// The warning names what was being asked and why it failed: the outer
/// context alone ("asking for /movie/603") cannot tell a refused connection
/// from a revoked key.
#[tokio::test]
async fn a_title_the_provider_will_not_describe_is_logged_with_its_cause() {
    let captured = Captured::default();
    let writer = captured.clone();
    let subscriber = tracing_subscriber::fmt()
        .with_writer(move || writer.clone())
        .with_ansi(false)
        .finish();
    let _logging = tracing::subscriber::set_default(subscriber);
    let conn = crate::index::sqlite_init::open(":memory:").unwrap();

    let recorded = record_for_title(&conn, &Unreachable, Kind::Movie, 603, "en-US")
        .await
        .unwrap();

    assert_eq!(recorded, None);
    let logs = String::from_utf8(captured.0.lock().unwrap().clone()).unwrap();
    assert!(
        logs.contains("asking for /movie/603: connection refused"),
        "{logs}"
    );
}
