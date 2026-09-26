---
report_date: 2026-09-25
researcher: claude
topic: Android LAN access, NSD discovery, cleartext HTTP, HTTP client choice
target_sdk: 37 (Android 17)
min_sdk: 24
---

# Android LAN Access: Permissions, NSD, Cleartext, HTTP Client — Report

## 1. Local Network Access Protection (SDK 37 / Android 17)

### Permission Name & API Level
- **Permission:** `ACCESS_LOCAL_NETWORK` (declared and runtime-requested)
- **Introduced:** Android 16 (SDK 36) as opt-in `NEARBY_WIFI_DEVICES` only
- **Enforced:** Android 17+ (SDK 37+) — **mandatory runtime permission** for apps targeting API 37+
- **Permission group:** `NEARBY_DEVICES` (if user already granted another nearby permission, no dialog shown again)

### Runtime Dialog & Denial Behavior
- **Dialog:** Yes, runtime permission dialog shown on first app use requiring LAN access
- **On denial:**
  - **TCP:** Connection attempts timeout (SocketTimeoutException or similar on prolonged wait)
  - **UDP:** SocketException with `EPERM` error code
  - **NDK check:** Use `android_getnetworkblockedreason(sockFd)` → returns `ANDROID_NETWORK_BLOCKED_REASON_LNP` if blocked by Local Network Protection
- **No exception thrown immediately** — TCP connections fail silently via timeout; UDP is more explicit

### Android TV Behavior
- **No distinct behavior documented.** TV apps targeting SDK 37+ follow **same split permission model** as phones
- **CHANGE_WIFI_MULTICAST_STATE** still required for MulticastLock if relying on raw UDP multicast (NsdManager handles internally)
- **ACCESS_LOCAL_NETWORK** enforcement applies the same way

### Mitigation: System-Mediated Device Picker (API 37+)
- Use `DiscoveryRequest#FLAG_SHOW_PICKER` — system dialog lets user select one device without broad permission
- Requires zero permission request; user consent captured via system UI
- Recommended for privacy-conscious flows but not required for traditional permission request

**Source:** [developer.android.com — Local Network Permission](https://developer.android.com/privacy-and-security/local-network-permission), [Behavior Changes Android 17](https://developer.android.com/about/versions/17/behavior-changes-17)

---

## 2. Cleartext HTTP for Private IP Ranges

### Key Limitation: No CIDR/Range Support
- `network_security_config.xml` **cannot express CIDR ranges or wildcards**
- Matches **exact domain names or exact IP address literals only**
- No modern extensions added (as of 2026)

### Configuration Options
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Option 1: Per-IP cleartext (repeat for each IP) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain>192.168.1.20</domain>
        <domain>192.168.1.21</domain>
    </domain-config>
    
    <!-- Option 2: Global cleartext (not recommended, but simplest) -->
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

### Recommendation: Per-IP Listing
1. **Enumerate known IPs** in `domain-config` with `cleartextTrafficPermitted="true"` for each
2. **If IP is dynamic** (DHCP), must list all possible IPs OR use dynamic DNS hostname instead
3. **Hostnames work better** — define `network_security_config` for `cache.local` (mDNS FQDN) with cleartext enabled, let DNS resolution find whatever IP
4. **Global cleartext** (`base-config cleartextTrafficPermitted="true"`) is **acceptable for minSdk 24 internal LAN use** but mark as technical debt; revisit when HTTP/2 adoption is 100%

### Why No CIDR?
- XML `domain` element was designed for web domains + exact IPs
- CIDR logic never added to Android's security model (design choice, not gap)
- Apps handle IP range validation at runtime if needed, not in config

**Source:** [developer.android.com — Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)

---

## 3. NsdManager Pitfalls (API 24–37)

### Known Issues Across All API Levels
| Issue | Impact | Mitigation |
|-------|--------|-----------|
| TXT record data often empty | SRV record resolves but TXT is missing/null | Expect TXT failure; store metadata in service name or re-query manually |
| `onServiceLost()` then `onServiceFound()` (same service) | Spurious flapping | Deduplicate by cache; ignore re-discovery within 1–2s |
| ResolveListener state instability | Callbacks can fire in unexpected order or get dropped | Use newer `registerServiceInfoCallback()` on API 34+ |
| No persistent multicast without lock | mDNS queries fail after screen sleep without MulticastLock | Always acquire CHANGE_WIFI_MULTICAST_STATE + WifiManager.MulticastLock |

### Listener Architecture Change at API 34+
- **API 24–33:** `resolveService(NsdServiceInfo, ResolveListener)`
  - Single-use listener (fires once, unregisters automatically)
  - No persistent subscription model
  - Host + port returned in `NsdServiceInfo.host` and `NsdServiceInfo.port`
- **API 34+:** `registerServiceInfoCallback(NsdServiceInfo, Executor, ServiceInfoCallback)`
  - Callback unregisters on first resolve (unlike old ResolveListener's continued firing)
  - Returns hostnames + addresses separately (new data model)
  - Cleaner API; preferred for API 34+ apps

**Migration:** Target minSdk 24 — use `resolveService()` for legacy. If minSdk 34+, adopt `registerServiceInfoCallback()` for cleaner lifecycle.

### MulticastLock Requirement
- **Permission:** Declare `<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />`
- **Runtime:** Not a runtime permission; declared only
- **Usage:**
  ```kotlin
  val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
  val lock = wifi.createMulticastLock("mediagram-cache")
  lock.acquire() // Before discoverServices()
  // ... discovery code ...
  lock.release() // In tearDown()
  ```
- **Requirement varies by device:** Some Motorola/Samsung devices enforce; others do not. Acquiring is defensive and cheap.
- **Without lock:** mDNS multicast packets are filtered by the stack; discovery fails silently

**Source:** [developer.android.com — Use Network Service Discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd), [WifiManager.MulticastLock](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock)

---

## 4. HTTP Client Choice for 1 MiB Chunks

### Comparison

| Aspect | HttpURLConnection (DefaultHttpDataSource) | OkHttpDataSource | media3 DefaultHttpDataSource |
|--------|-----------|-----------|-----------|
| **Deps** | Built-in (0 extra) | +1 dep (OkHttp ~1 MB) | Built-in (0 extra) |
| **Connection pooling** | Per-connection; limited reuse | Persistent pool; excellent LAN reuse | Per-connection; basic reuse |
| **HTTP/2 support** | API 24–27: no; API 28+: yes (framework) | Full HTTP/2; modern ALPN | Framework-dependent |
| **Timeout control** | Limited (per request, not pool-level) | Full control via OkHttpClient builder | Limited timeout config |
| **LAN use case** | Adequate for 1 MiB chunks | Slightly faster (~100ms on file transfers) | Adequate for 1 MiB chunks |
| **Maintenance burden** | Low (Android-built) | Low (OkHttp well-maintained, stable) | Low (Android-built) |

### Recommendation: **DefaultHttpDataSource (built-in) for minSdk 24, targetSdk 37**
- **Why:** Zero extra dependencies, adequate performance for 1 MiB chunks on LAN
- **Timeout configuration:**
  ```kotlin
  val dataSource = DefaultHttpDataSource().apply {
      setConnectTimeoutMs(5000)  // 5s for LAN (generous; local networks are fast)
      setReadTimeoutMs(10000)    // 10s read (data transfer shouldn't stall on LAN)
  }
  ```
- **Fallback:** If chunk performance is measured as a bottleneck (<10 Mbps sustained throughput), upgrade to OkHttpDataSource for better connection pooling

### HttpEngine (API 34+)
- System network stack; not a separate download
- Experimental in 2026; skip unless profiling shows OS stack is faster
- **Not recommended yet** for stable production

**Source:** [developer.android.com — DefaultHttpDataSource](https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource), [Media3 Network Stacks](https://developer.android.com/media/media3/exoplayer/network-stacks), [OkHttp Performance](https://blog.codavel.com/android-http-libraries-performance-comparison-okhttp-vs.-httpurlconnection)

---

## Summary Table

| Requirement | Answer | Priority |
|-------------|--------|----------|
| Local network permission (SDK 37) | `ACCESS_LOCAL_NETWORK` runtime permission; TCP timeout on denial, UDP EPERM | **MUST HAVE** for targetSdk 37 |
| Cleartext for private IPs | Per-IP exact match in domain-config; no CIDR support; consider hostname + mDNS instead | **REQUIRED** unless HTTPS to LAN |
| NsdManager discovery | Use `discoverServices()` + `resolveService()` (API 24–33) or `registerServiceInfoCallback()` (API 34+); always acquire MulticastLock; expect TXT record loss | **CRITICAL FOR RELIABILITY** |
| HTTP client | DefaultHttpDataSource; connect timeout 5s, read timeout 10s for LAN | **ADEQUATE; NO UPGRADE NEEDED** for 1 MiB chunks |

---

## Unresolved Questions

1. **Device-specific MulticastLock quirks:** Will all Android TV boxes honor MulticastLock? Motorola/Samsung history suggests device-specific enforcement; recommend testing on target devices before release.
2. **mDNS hostname resolution:** If `.local` hostname is used instead of IP literal in cleartext config, will it resolve on all API 24–37 devices? Typical yes, but varies by ROM. Test.
3. **HTTP/2 on targetSdk 37:** Is HTTP/2 via framework guaranteed on all API 37 devices, or should DefaultHttpDataSource configuration explicitly request it? Not clear from 2026 docs.

---

**Status:** DONE
