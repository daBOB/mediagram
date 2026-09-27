//! A fake connection for upload sessions: the in-memory transport and
//! channel, and a count of how often a session actually connected.

use std::cell::Cell;
use std::path::Path;

use anyhow::{Result, bail};
use mediagram::config::Config;
use mediagram::upload::session::link::Link;

use super::channel::FakeChannel;
use super::upload::FakeTransport;

pub struct FakeLink<'a> {
    pub transport: &'a FakeTransport,
    pub channel: &'a FakeChannel,
    pub connects: &'a Cell<u32>,
    pub down: bool,
    connected: bool,
}

impl<'a> FakeLink<'a> {
    pub fn new(
        transport: &'a FakeTransport,
        channel: &'a FakeChannel,
        connects: &'a Cell<u32>,
    ) -> Self {
        FakeLink {
            transport,
            channel,
            connects,
            down: false,
            connected: false,
        }
    }
}

impl Link for FakeLink<'_> {
    type Transport = FakeTransport;
    type Remote = FakeChannel;

    async fn open(&mut self) -> Result<(&FakeTransport, &FakeChannel)> {
        if self.down {
            bail!("the line is down");
        }
        if !self.connected {
            self.connected = true;
            self.connects.set(self.connects.get() + 1);
        }
        Ok((self.transport, self.channel))
    }

    async fn close(self) {}
}

/// A config whose data directory is `dir`, and nothing a test would reach
/// the network with.
pub fn config_in(dir: &Path) -> Config {
    let mut cfg: Config =
        toml::from_str("api_id = 1\napi_hash = 'fixture'\nchannel = 'fixture'").unwrap();
    cfg.data_dir = Some(dir.to_path_buf());
    cfg
}
