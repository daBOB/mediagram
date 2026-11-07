import Foundation

/// `raw` when it can be the TV box's game night: plain http to a private LAN address
/// (10/8, 172.16/12, 192.168/16) or a `.local` name. Everything else — a website, a Wi-Fi
/// QR code, a lookalike host — is `nil`, because the page we load gets the native bridge.
public func lanGameURL(_ raw: String) -> String? {
    guard let parts = URLComponents(string: raw), parts.scheme == "http", let host = parts.host, !host.isEmpty else {
        return nil
    }
    let local = host.hasSuffix(".local") && host.count > ".local".count
    return isPrivateIPv4(host) || local ? raw : nil
}

/// Same scheme, host and port: the web view may move within one box, never off it.
public func sameBox(_ a: String, _ b: String) -> Bool {
    guard let x = URLComponents(string: a), let y = URLComponents(string: b) else { return false }
    return x.scheme == y.scheme && x.host == y.host && port(x) == port(y)
}

private func port(_ parts: URLComponents) -> Int? {
    parts.port ?? (parts.scheme == "http" ? 80 : nil)
}

private func isPrivateIPv4(_ host: String) -> Bool {
    let octets = host.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) }
    guard octets.count == 4, octets.allSatisfy({ ($0 ?? -1) >= 0 && ($0 ?? 256) <= 255 }) else { return false }
    let first = octets[0]!, second = octets[1]!
    return first == 10 || (first == 172 && (16...31).contains(second)) || (first == 192 && second == 168)
}
