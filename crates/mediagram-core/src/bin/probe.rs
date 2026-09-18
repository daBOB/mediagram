//! Throwaway spike binary: proves grammers connects and reads a range on
//! Android. Deleted once the spike reports its finding.
//!
//! Both SQLite users are exercised on purpose. Rust links only what is
//! reachable, so a probe that merely declared the dependencies would link one
//! sqlite and prove nothing about the collision between the session store's
//! statically-linked copy and the index's.

use std::env;
use std::sync::Arc;

use anyhow::{Context, Result, anyhow, bail};
use grammers_client::media::{Document, Media};
use grammers_client::sender::SenderPoolFatHandle;
use grammers_client::{Client, SenderPool};
use grammers_session::SessionData;
use grammers_session::storages::MemorySession;
use grammers_session::types::{PeerId, PeerRef};
use sha2::{Digest, Sha256};

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> Result<()> {
    let auth_key = env::var("PROBE_AUTH_KEY").context("PROBE_AUTH_KEY")?;
    let dc_id: i32 = env::var("PROBE_DC_ID")?.parse()?;
    let index_path = env::var("PROBE_INDEX").context("PROBE_INDEX")?;
    let api_id: i32 = env::var("PROBE_API_ID")?.parse()?;
    let chat_id: i64 = env::var("PROBE_CHAT_ID")?.parse()?;
    let message_id: i32 = env::var("PROBE_MESSAGE_ID")?.parse()?;
    let want: u64 = 1024 * 1024;

    // Reached before Telegram, mirroring the real client: every command opens
    // the index first. This is the call that puts a second sqlite in the link.
    let sqlite_version = open_index(&index_path)?;
    println!("index sqlite {sqlite_version}");

    let started = std::time::Instant::now();
    let (client, handle) = connect(dc_id, &auth_key, api_id).await?;
    let bytes = first_bytes(&client, chat_id, message_id, want).await?;
    handle.quit();

    let mut hasher = Sha256::new();
    hasher.update(&bytes);
    println!(
        "read {} bytes in {} ms",
        bytes.len(),
        started.elapsed().as_millis()
    );
    println!("sha256 {}", hex::encode(hasher.finalize()));
    Ok(())
}

/// Opens the package index and returns the sqlite build actually linked.
///
/// The returned version identifies which of the two copies won the link, so a
/// run that survives the collision still says whose sqlite it is using.
fn open_index(path: &str) -> Result<String> {
    let conn = rusqlite::Connection::open(path)
        .with_context(|| format!("cannot open index {path}"))?;
    let version: String = conn
        .query_row("select sqlite_version()", [], |row| row.get(0))
        .context("querying sqlite version")?;
    Ok(version)
}

/// Rebuilds the session from a stored auth key and starts its sender pool.
///
/// The auth key is the whole authorization, so a session needs no database to
/// be restored: seeding the home datacentre's key is the inverse of exporting
/// one, and leaves the binary with no session-owned sqlite.
fn session_from_auth_key(dc_id: i32, auth_key_hex: &str) -> Result<MemorySession> {
    let mut key = [0u8; 256];
    hex::decode_to_slice(auth_key_hex, &mut key)
        .context("PROBE_AUTH_KEY must be 512 hex characters")?;
    let mut data = SessionData {
        home_dc: dc_id,
        ..Default::default()
    };
    data.dc_options
        .get_mut(&dc_id)
        .ok_or_else(|| anyhow!("unknown datacentre {dc_id}"))?
        .auth_key = Some(key);
    Ok(MemorySession::from(data))
}

/// Restores the session and starts its sender pool.
async fn connect(
    dc_id: i32,
    auth_key_hex: &str,
    api_id: i32,
) -> Result<(Client, SenderPoolFatHandle)> {
    let session = Arc::new(session_from_auth_key(dc_id, auth_key_hex)?);
    let SenderPool { runner, handle, .. } = SenderPool::new(Arc::clone(&session), api_id);
    let client = Client::new(handle.clone());
    tokio::spawn(runner.run());
    Ok((client, handle))
}

/// Downloads the first `want` bytes of a part's document.
async fn first_bytes(
    client: &Client,
    chat_id: i64,
    message_id: i32,
    want: u64,
) -> Result<Vec<u8>> {
    let channel = PeerId::from_bot_api_dialog_id(chat_id)
        .ok_or_else(|| anyhow!("{chat_id} is not a bot-API dialog id"))?
        .to_ambient_ref();
    let document = part_document(client, channel, message_id).await?;

    let mut out: Vec<u8> = Vec::with_capacity(want as usize);
    let mut chunks = client.iter_download(&document);
    while (out.len() as u64) < want {
        let chunk = match chunks.next().await.context("downloading chunk")? {
            Some(chunk) => chunk,
            None => bail!(
                "download ended with {} bytes still owed",
                want - out.len() as u64
            ),
        };
        let take = (want - out.len() as u64).min(chunk.len() as u64) as usize;
        out.extend_from_slice(&chunk[..take]);
    }
    Ok(out)
}

/// The document of a part's message, resolved by message id.
async fn part_document(client: &Client, channel: PeerRef, message_id: i32) -> Result<Document> {
    let messages = client
        .get_messages_by_id(channel, &[message_id])
        .await
        .with_context(|| format!("fetching message {message_id}"))?;
    let message = messages
        .into_iter()
        .next()
        .flatten()
        .ok_or_else(|| anyhow!("message {message_id} no longer exists"))?;
    match message.media() {
        Some(Media::Document(document)) => Ok(document),
        _ => bail!("message {message_id} carries no downloadable document"),
    }
}
