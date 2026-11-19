use std::net::{IpAddr, UdpSocket};

/// The address phones on the same network can reach this machine at.
pub fn lan_ip() -> Option<IpAddr> {
    advertised().or_else(routed)
}

/// Connecting a UDP socket only picks a route; nothing is sent.
fn routed() -> Option<IpAddr> {
    let socket = UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("1.1.1.1:80").ok()?;
    Some(socket.local_addr().ok()?.ip())
}

/// Funstation tells an Android behind WayDroid's NAT the address phones reach it at, since
/// Android's own address there is private to the machine. Unset everywhere else.
#[cfg(target_os = "android")]
fn advertised() -> Option<IpAddr> {
    let out = std::process::Command::new("getprop").arg("funstation.lan_address").output().ok()?;
    parse_advertised(&String::from_utf8_lossy(&out.stdout))
}

#[cfg(not(target_os = "android"))]
fn advertised() -> Option<IpAddr> {
    None
}

/// The property's value, when it is an address; unset reads as an empty line.
#[cfg_attr(not(target_os = "android"), allow(dead_code))]
fn parse_advertised(value: &str) -> Option<IpAddr> {
    value.trim().parse().ok()
}

#[cfg(test)]
#[path = "lan_tests.rs"]
mod tests;
