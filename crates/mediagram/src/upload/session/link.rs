//! The one connection an upload session sends parts and publishes over.
//!
//! Two adapters: [`TelegramLink`] in production, and the integration tests'
//! fake over the in-memory transport and channel. Opened only when an item
//! reaches its bytes or a publish is due, so a re-run that finds everything
//! held never connects.

use anyhow::{Context, Result};

use crate::channel_index::TelegramRemote;
use crate::channel_index::remote::ChannelRemote;
use crate::config::Config;
use crate::telegram::client::Tg;
use crate::upload::transport::{TelegramTransport, Transport};

// Static dispatch only, like the two ports it bundles.
#[allow(async_fn_in_trait)]
pub trait Link {
    type Transport: Transport;
    type Remote: ChannelRemote;

    /// Connects on the first call; the same pair after that.
    async fn open(&mut self) -> Result<(&Self::Transport, &Self::Remote)>;

    /// Disconnects, if it ever connected.
    async fn close(self);
}

/// The Telegram connection, made on first use.
pub struct TelegramLink<'c> {
    cfg: &'c Config,
    open: Option<(Tg, TelegramTransport, TelegramRemote)>,
}

impl<'c> TelegramLink<'c> {
    pub fn new(cfg: &'c Config) -> Self {
        TelegramLink { cfg, open: None }
    }
}

impl Link for TelegramLink<'_> {
    type Transport = TelegramTransport;
    type Remote = TelegramRemote;

    async fn open(&mut self) -> Result<(&TelegramTransport, &TelegramRemote)> {
        if self.open.is_none() {
            let tg = Tg::connect(self.cfg)
                .await
                .context("connecting to Telegram")?;
            let transport = TelegramTransport::new(&tg, self.cfg.max_attempts);
            let remote = TelegramRemote::new(&tg, self.cfg.max_attempts);
            self.open = Some((tg, transport, remote));
        }
        let (_, transport, remote) = self.open.as_ref().expect("opened above");
        Ok((transport, remote))
    }

    async fn close(self) {
        if let Some((tg, _, _)) = self.open {
            tg.shutdown().await;
        }
    }
}
