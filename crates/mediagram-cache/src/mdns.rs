//! Advertises `_mediagram-cache._tcp.local.` so a phone on the same network
//! finds this server without being told its address. No unit test: a
//! multicast socket needs a real network stack. Verified by hand with
//! `avahi-browse -rt _mediagram-cache._tcp` alongside avahi-daemon, which
//! `mdns-sd` is documented to coexist with.

use anyhow::{Context, Result};
use mdns_sd::{ServiceDaemon, ServiceInfo};

pub const SERVICE_TYPE: &str = "_mediagram-cache._tcp.local.";

/// Keeps the mDNS daemon thread alive; dropping it unregisters the service
/// and stops the thread.
pub struct Handle(ServiceDaemon);

impl Drop for Handle {
    fn drop(&mut self) {
        // Best-effort: the daemon's own thread is exiting either way, and
        // there is nobody left to hand an error to.
        let _ = self.0.shutdown();
    }
}

/// Registers this server on `port`, address auto-detected from the host's
/// interfaces. The `v=1` TXT record lets a client tell the API version
/// apart from the wire protocol before it connects.
pub fn register(port: u16) -> Result<Handle> {
    let daemon = ServiceDaemon::new().context("starting the mDNS daemon")?;
    let host_name = format!("mediagram-cache-{port}.local.");
    let info = ServiceInfo::new(
        SERVICE_TYPE,
        "mediagram-cache",
        &host_name,
        (),
        port,
        &[("v", "1")][..],
    )
    .context("building the mDNS service record")?
    .enable_addr_auto();
    daemon
        .register(info)
        .context("registering the mDNS service")?;
    Ok(Handle(daemon))
}
