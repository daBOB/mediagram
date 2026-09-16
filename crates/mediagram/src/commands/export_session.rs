//! `mediagram export-session`: everything the player backend needs to start.
//!
//! The player speaks MTProto through teleproto, which cannot read the
//! uploader's libsql session file. It can read a `StringSession`, and the
//! auth key inside both is the same 256 bytes, so exporting one avoids a
//! second login and a second entry in the account's active sessions.
//!
//! The channel's access hash is exported with it deliberately. An access hash
//! is bound to the account, the uploader already holds one that works, and
//! handing it over saves the player a resolution round trip on every start.
//!
//! What this prints is the account. Not "access to the library" — the
//! account. It goes to stdout so it can be piped into a secret store, and
//! never to a file this command chooses.
//!
//! **One auth key does not survive two concurrent MTProto clients.** Measured
//! against the live channel: a player using an exported session answered 3/3
//! range requests alone, then 0/3 from the moment `mediagram serve` started,
//! and never recovered — not when the other client stopped, not at all, until
//! the player was restarted. The teleproto side fills its log with response
//! parse failures. So an exported session is for a player that runs when this
//! account's other clients do not; a player that runs alongside the uploader
//! needs a login of its own.

use anyhow::{Context, Result, bail};
use rusqlite::{Connection, OpenFlags};

use crate::config::Config;
use crate::telegram::client::Tg;
use crate::telegram::string_session::encode_string_session;

pub async fn run(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let session_path = data_dir.join("session.sqlite");
    if !session_path.exists() {
        bail!(
            "no session at {}; run `mediagram login` first",
            session_path.display()
        );
    }

    // Resolving the channel is what needs the network, and it is the whole
    // reason to do it here rather than in the player.
    let tg = Tg::connect(cfg).await?;
    let chat_id = tg.chat_id();
    let access_hash = tg.channel.auth.hash();
    let title = tg.channel_title.clone();
    tg.shutdown().await;

    let encoded = encode_session(&session_path)?;

    eprintln!(
        "This session string is the Telegram account, not just the library.\n\
         Anyone holding it can act as you. Put it in the player's environment\n\
         and nowhere else; revoke it by terminating the session in Telegram.\n\
         \n\
         Do not run a player on this session while another client of this\n\
         account is running. Two MTProto clients sharing one auth key break\n\
         each other, and the broken one stays broken until it restarts.\n\
         \n\
         Channel: {title}\n"
    );

    println!("MEDIAGRAM_API_ID={}", cfg.api_id);
    println!("MEDIAGRAM_API_HASH={}", cfg.api_hash);
    println!("MEDIAGRAM_SESSION={encoded}");
    println!("MEDIAGRAM_CHAT_ID={chat_id}");
    println!("MEDIAGRAM_CHANNEL_ACCESS_HASH={access_hash}");
    println!(
        "MEDIAGRAM_LIBRARY_DB={}",
        data_dir.join("library.db").display()
    );
    Ok(())
}

/// Reads the home datacentre's authorization and encodes it for teleproto.
fn encode_session(session_path: &std::path::Path) -> Result<String> {
    // Read-only: exporting must never be able to disturb the session the
    // uploader depends on.
    let session = Connection::open_with_flags(session_path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .with_context(|| format!("opening {} read-only", session_path.display()))?;

    let dc_id: i64 = session
        .query_row("SELECT dc_id FROM dc_home", [], |row| row.get(0))
        .context("reading the home datacentre; is this session authorized?")?;

    let (address, auth_key): (String, Option<Vec<u8>>) = session
        .query_row(
            "SELECT ipv4, auth_key FROM dc_option WHERE dc_id = ?1",
            [dc_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .with_context(|| format!("reading datacentre {dc_id}"))?;

    let Some(auth_key) = auth_key else {
        bail!("datacentre {dc_id} has no auth key; run `mediagram login` first");
    };

    // Stored as `host:port`, which the string session keeps as two fields.
    let (host, port) = address
        .rsplit_once(':')
        .with_context(|| format!("datacentre address {address} is not host:port"))?;

    encode_string_session(
        u8::try_from(dc_id).context("datacentre id does not fit a byte")?,
        host,
        port.parse().with_context(|| format!("port {port}"))?,
        &auth_key,
    )
    .context("encoding the session")
}
