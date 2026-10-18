# Network Environment evidence record

Status: reviewed

The detector reports the public Android connectivity configuration visible to the calling app at scan time. VPN, recommended HTTP proxy and Private DNS are contextual information, never evidence of Root, compromise or insecure behavior. All results have INFO severity. Successful observations carry INFO/CONTEXT and do not imply reduced coverage; missing, unsupported or incomplete observations carry INFO/SUPPORT, and failures carry INFO/ERROR. The snapshot cannot establish traffic interception, network trust, reachability or whole-device VPN coverage.

Research reviewed on 2026-10-17. The implementation uses only the documented public API contracts below. It does not depend on hidden settings, undocumented properties, kernel interfaces or vendor-specific behavior.

## Signals

### App-default-network VPN transport

- Observable signal: A non-null default Network has NetworkCapabilities with TRANSPORT_VPN present or absent.
- Producing subsystem: Android ConnectivityManager and the NetworkCapabilities reported for the calling app's default network.
- Mechanism: Check ACCESS_NETWORK_STATE; obtain getActiveNetwork(), obtain getNetworkCapabilities(network), and read hasTransport(TRANSPORT_VPN). Re-read the default-network handle after the VPN/DNS reads and discard both network observations if the handle changed. A null capabilities response remains unavailable, not a negative result.
- References: Android Developers [ConnectivityManager.getActiveNetwork](https://developer.android.com/reference/android/net/ConnectivityManager#getActiveNetwork()), [getNetworkCapabilities](https://developer.android.com/reference/android/net/ConnectivityManager#getNetworkCapabilities(android.net.Network)), and [NetworkCapabilities.TRANSPORT_VPN](https://developer.android.com/reference/android/net/NetworkCapabilities#TRANSPORT_VPN). The documentation explicitly defines a null active network as no default network or a network blocked for the caller.
- Applicability: All ABIs. The default-network API is public from API 23; earlier versions return UNSUPPORTED. NetworkCapabilities and TRANSPORT_VPN are public from API 21. The data module declares the normal ACCESS_NETWORK_STATE permission; the probe checks access and also handles SecurityException.
- Visibility limits: Only the default network visible to this app is sampled. Per-app VPN exclusions, work profiles, other users, process/socket binding and split routing can differ. Framework-provided evidence is not an independent verification against a modified framework. The handle recheck catches observed disconnects/switches but cannot make separate public API reads atomic or catch a switch away and back between reads.
- Result states: OBSERVED(true/false), NO_DEFAULT_NETWORK, PERMISSION_REQUIRED, UNAVAILABLE, UNSUPPORTED, NETWORK_CHANGED, FAILED; NOT_EVALUATED before collection. A null active network does not distinguish disconnection from policy blocking.
- Interpretation: A present VPN transport is a legitimate network configuration and remains INFO. An absent VPN transport is reported only for this observed default network and is not a claim that no VPN exists on the device. Missing evidence never becomes a disabled VPN result.

### Recommended default HTTP proxy

- Observable signal: ConnectivityManager.getDefaultProxy() returns null or a ProxyInfo describing a manual/PAC recommendation.
- Producing subsystem: Android connectivity proxy configuration for the global proxy, process-bound network or default network.
- Mechanism: Call getDefaultProxy independently of the default-network metadata read. Null means no recommended default proxy. A non-null non-empty PAC URI identifies the automatic/PAC mode; other ProxyInfo results identify manual mode. No connection is opened and the PAC script is not fetched or executed. The endpoint, port, exclusions and URI are not retained in the domain report or export.
- References: Android Developers [ConnectivityManager.getDefaultProxy](https://developer.android.com/reference/android/net/ConnectivityManager#getDefaultProxy()), [ProxyInfo.getPacFileUrl](https://developer.android.com/reference/android/net/ProxyInfo#getPacFileUrl()), and [LinkProperties.setHttpProxy](https://developer.android.com/reference/android/net/LinkProperties#setHttpProxy(android.net.ProxyInfo)). The default-proxy documentation defines global > process-bound network > default-network priority. LinkProperties explicitly states that HTTP proxies are hints which applications can ignore.
- Applicability: All ABIs, API 23+. ProxyInfo.getPacFileUrl is public from API 21. No additional runtime permission request is made. SecurityException is reported separately if the platform denies this read; a missing connectivity service is UNAVAILABLE.
- Visibility limits: The recommended default proxy does not prove that any traffic uses it. Custom clients, VPN tunneling, transparent interception, per-request proxy settings and applications ignoring proxy hints are outside scope. PAC mode does not reveal the selected destination-specific proxy. A recommendation can be readable even when the app has no accessible default network.
- Result states: OBSERVED(NONE/MANUAL/AUTOMATIC), PERMISSION_REQUIRED, UNAVAILABLE, UNSUPPORTED, FAILED; NOT_EVALUATED before collection. A network-metadata failure does not erase an independently observed proxy result.
- Interpretation: All three modes are INFO. No proxy setting is classified as tampering or a high-risk environment. The UI explicitly states that actual traffic routing is not tested.

### Default-network Private DNS runtime state

- Observable signal: LinkProperties.isPrivateDnsActive and whether getPrivateDnsServerName() is non-null for the same default-network handle sampled for VPN.
- Producing subsystem: Android LinkProperties populated by the connectivity and system DNS resolver components.
- Mechanism: For API 28+, obtain getLinkProperties(network) and map the active flag plus presence of the strict hostname to INACTIVE, ACTIVE_OPPORTUNISTIC, ACTIVE_STRICT or STRICT_NOT_ACTIVE. A null properties object is UNAVAILABLE. The hostname is inspected only for nullness; it is not retained. No independent DNS query or TLS verification is performed.
- References: Android Developers [LinkProperties.isPrivateDnsActive](https://developer.android.com/reference/android/net/LinkProperties#isPrivateDnsActive()), [getPrivateDnsServerName](https://developer.android.com/reference/android/net/LinkProperties#getPrivateDnsServerName()), and [ConnectivityManager.getLinkProperties](https://developer.android.com/reference/android/net/ConnectivityManager#getLinkProperties(android.net.Network)). The API defines a non-null hostname as strict mode, and a null hostname plus active flag as opportunistic mode. isPrivateDnsActive means currently in use on that network.
- Applicability: All ABIs. Private DNS public APIs start at API 28 (Android 9); earlier versions are explicitly UNSUPPORTED. Network metadata requires ACCESS_NETWORK_STATE. The current application supports a higher minimum API, while the probe still documents and guards these API boundaries.
- Visibility limits: A runtime state is not a read of the user's global setting. INACTIVE cannot distinguish Off from Automatic without an active encrypted resolver. App-managed DoH/DoT, VPN DNS implementations and other networks are outside scope. No DNS server identity, encryption quality, certificate trust or end-to-end traffic behavior is verified. The snapshot shares the VPN handle-coherence check and its non-atomicity limits.
- Result states: OBSERVED(INACTIVE/ACTIVE_OPPORTUNISTIC/ACTIVE_STRICT/STRICT_NOT_ACTIVE), NO_DEFAULT_NETWORK, PERMISSION_REQUIRED, UNAVAILABLE, UNSUPPORTED, NETWORK_CHANGED, FAILED; NOT_EVALUATED before collection.
- Interpretation: Every mode is INFO. INACTIVE is phrased as not in use on this network, never as a disabled global setting. A strict hostname without an active flag remains explicitly not active rather than receiving an active-encryption claim.

## Failure handling and validation

Each source returns typed availability and value fields, with invariants preventing unavailable values from masquerading as observed settings. A denied read is distinct from a read failure. Ordinary Exceptions preserve the remaining source's observations; cancellation propagates. Stable FailureName types are retained without exception messages because messages can contain endpoints or network identifiers.

The domain tests cover every VPN/proxy/DNS mode combination and all unavailable states; repository tests cover independent failures, permission denial, network-transition propagation, cancellation and exception redaction. Presentation tests cover scope, inactive DNS wording, localized Chinese export and unknown states. The module uses the existing pure-JVM presentation mapper pattern with English/Chinese text selected from the active Locale. No other feature is referenced.

JVM tests do not validate OEM connectivity services, manifest merge behavior or real network transitions. Before a production release, verify stock offline/online devices, normal and excluded-app VPN, work profiles, manual proxy, PAC, Private DNS Off/Automatic/strict, unavailable strict resolver and network switching on Android 10 through the current version. No true-device validation has been performed in this task.
