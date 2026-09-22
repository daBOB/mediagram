//! How each mediagram client names itself to Telegram.
//!
//! Telegram lists every session of an account — in its own apps' Active
//! Sessions, and to `account.getAuthorizations` — by what the client said in
//! `initConnection`. grammers' default is the operating system ("Linux
//! 64-bit") and its own version, so every uploader, player and phone of one
//! library looked alike and none could be told apart to be signed out. The
//! web player names itself the same way through teleproto's client options.

use grammers_mtsender::ConnectionParams;

/// What a session is called: `mediagram <surface> · <device>`, or just
/// `mediagram <surface>` when the device has no usable name.
pub fn device_model(surface: &str, device: &str) -> String {
    let device = device.trim();
    if device.is_empty() {
        format!("mediagram {surface}")
    } else {
        format!("mediagram {surface} · {device}")
    }
}

/// The connection parameters for `surface` on `device`, with the project's
/// own version in place of the client library's.
pub fn connection_params(surface: &str, device: &str) -> ConnectionParams {
    ConnectionParams {
        device_model: device_model(surface, device),
        app_version: env!("CARGO_PKG_VERSION").to_string(),
        ..ConnectionParams::default()
    }
}

/// This machine's host name, or empty when it cannot be read. Linux only,
/// which is where the uploader runs.
pub fn host_name() -> String {
    std::fs::read_to_string("/proc/sys/kernel/hostname")
        .map(|name| name.trim().to_string())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_session_is_named_for_its_surface_and_device() {
        assert_eq!(device_model("uploader", "homelab"), "mediagram uploader · homelab");
    }

    #[test]
    fn a_device_with_no_name_still_says_what_it_is() {
        assert_eq!(device_model("Android", "  "), "mediagram Android");
    }

    #[test]
    fn the_version_is_the_projects_not_the_client_librarys() {
        let params = connection_params("uploader", "homelab");
        assert_eq!(params.app_version, env!("CARGO_PKG_VERSION"));
        assert_eq!(params.device_model, "mediagram uploader · homelab");
    }
}
