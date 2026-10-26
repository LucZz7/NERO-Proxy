# NERO Proxy 0.2.0 — full-device SOCKS5 / HTTP proxy VPN (Android 9+)

**STATUS: written, NOT compiled, NOT run, NOT tested by the author** (no Android SDK/NDK/network was available).
Expect to fix small build errors on first sync. Nothing here has been verified on a real phone.

## How it works
apps -> Android TUN (VpnService) -> hev-socks5-tunnel (native, MIT) -> LocalBridge (Kotlin, 127.0.0.1, random per-session
SOCKS5 login) -> your upstream HTTP-CONNECT or SOCKS5 proxy.
- HTTP and SOCKS5 upstreams are both handled by LocalBridge, so HTTP proxies work too (TCP only).
- DNS: engine's MapDNS answers locally with fake 100.64.0.0/10 addresses; the proxy resolves the real name (no DNS leak via UDP).
- Fail-closed: all IPv4/IPv6 routes enter the TUN. UDP/QUIC/IPv6 are dropped. If upstream is down, connections are refused.
- App's own sockets are excluded from the VPN (addDisallowedApplication) so the engine/bridge/test never loop.
- Connected is shown only after: saved config valid -> engine present -> real pre-flight test (DNS, TCP, auth, tunnel, TLS, HTTPS request) passed
  -> TUN established -> engine started. "Forwarding verified" appears only after real bytes came back from the proxy.
- Stats = real bytes counted in LocalBridge. Latency = TCP connect time to the proxy (every 15 s).

## Build (required steps)
1. Install Android Studio (Koala or newer) + in SDK Manager > SDK Tools install **NDK (Side by side)** and CMake not needed.
2. In this folder run `./setup.sh` (Windows: run the single `git clone --recursive ...` line inside it). This downloads the native engine
   https://github.com/heiher/hev-socks5-tunnel into app/src/main/jni/. Without it the app builds but Connect shows "engine missing".
3. Open the folder in Android Studio, sync (creates the Gradle wrapper), then:
   - Tests: `./gradlew testDebugUnitTest`
   - APK: `./gradlew assembleDebug` -> app/build/outputs/apk/debug/app-debug.apk
4. Phone (Android 9): Settings > About > tap Build number 7x, enable USB debugging, `adb install -r app-debug.apk`.

## Unverified / risks (check these first if something fails)
- JNI: `TProxy.java` assumes hev exports TProxyStartService(String,int)/TProxyStopService via -DPKGNAME/-DCLSNAME and library name
  `hev-socks5-tunnel`. I confirmed the build method and YAML keys from docs, not the exact JNI signatures. Compare with the repo's Android notes.
- Licenses: MIT/BSD claims are from memory; read the LICENSE files in the submodule.
- Target SDK 33 on purpose (avoids Android 14 foreground-service-type rules). Google Play now requires a higher target; raise it and add the
  proper foregroundServiceType before publishing.
- Some phone makers kill background services; disable battery optimisation for the app.
- Not supported: UDP, QUIC, IPv6, per-app routing, proxy chains, subscription links.
- Unit tests (core clients + bridge) are written but have never been executed. VPN start/stop, Android 9 runtime and reconnect need manual device testing.
- Upstream proxy services may cost money; this app has no servers or ads.
