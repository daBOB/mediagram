use std::net::{IpAddr, UdpSocket};

/// The address phones on the same network can reach this machine at.
/// Connecting a UDP socket only picks a route; nothing is sent.
pub fn lan_ip() -> Option<IpAddr> {
    let socket = UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("1.1.1.1:80").ok()?;
    Some(socket.local_addr().ok()?.ip())
}
