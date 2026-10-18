# Application Integrity evidence record

Status: reviewed

This detector asks what the current host application's signing identity and debugging state expose through public Android APIs and its own procfs status. It preserves all Duck detectors and adds a local diagnostic surface; it does not independently verify an APK, prove device trust, infer malicious intent, or establish absence of hooks. A modified process can alter both the observations and the host-bundled policy. No other process, package inventory, root command, network request or native probe is involved.

本检测仅观察当前宿主应用的签名身份、构建标记与调试状态，不进行独立 APK 签名验证，不据此证明设备安全、恶意意图或没有 Hook。被修改的进程可以更改观察结果及宿主内置策略。不会读取其他进程、枚举应用、申请 Root、联网或增加 Native 探针。

## Signals

### Current signing certificate and host policy

- Observable signal: SHA-256 of the current host's signing certificate DER bytes from `PackageManager.getPackageInfo(ownPackage, GET_SIGNING_CERTIFICATES)` and optional exact-package host-supplied certificate pins.
- Producing subsystem: Android PackageManager signing metadata and `SigningInfo`; the host provides a policy asset, not a platform or remotely authenticated trust anchor.
- Mechanism: On API 28+, a single signer uses `getSigningCertificateHistory()` with the last entry as current; historical entries are not treated as current pins. Multiple signers use `getApkContentsSigners()` for display only, because their identity is the complete signer set. SHA-256 hashes `Signature.toByteArray()`. The optional version-1 `ns_signing_policy.json` limits comparison to one exact `allowedPackage` and explicitly allowed current single certificates. A non-debug, non-test build with applicable policy and a different current certificate raises a local WARNING; matching stays CONTEXT.
- References: [Android SigningInfo API](https://developer.android.com/reference/android/content/pm/SigningInfo), [PackageManager GET_SIGNING_CERTIFICATES](https://developer.android.com/reference/android/content/pm/PackageManager#GET_SIGNING_CERTIFICATES), and reviewed [frameworks/base Android 15 SigningInfo.java](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r1/core/java/android/content/pm/SigningInfo.java) defining multiple-signer and single-signer history access.
- Applicability: Public signing information is API 28+ and ABI independent. The project minimum is newer; an explicit API guard still reports unsupported below 28. No deprecated GET_SIGNATURES fallback is used. API 33's typed flag overload is not required; the stable integer overload retains equivalent requested flags.
- Visibility limits: Only the requesting application's exact own package is queried. The platform, installer and process can be modified. Bundled pins and code can be repackaged together; this observation is diagnostic and cannot authenticate itself. No installed-APK byte verification or hardware/server proof occurs. Debuggable and testOnly variants commonly have alternate certificates/packages and are context, never release-key mismatch warnings.
- Result states: No asset => MISSING/SUPPORT, different exact package => SUPPORT, unsupported API or missing metadata => SUPPORT, malformed schema/empty fingerprints => ERROR, exceptions => ERROR except inaccessible sources => SUPPORT. Multi-signer policy comparisons are explicitly unsupported; no single matching signer is accepted. Current matching => CONTEXT, production applicable current mismatch => WARNING. Loading is not evaluated.
- Interpretation: A current certificate differs from this host's supplied release policy, or matches that local policy. Neither outcome establishes device integrity. Rotation is supported by explicitly provisioning every desired current certificate pin; an old pinned history certificate never implicitly authorizes a new current certificate. Multi-signer APK validation requires a future versioned complete-set policy and is not supported by this schema.

### Host debuggable and testOnly flags

- Observable signal: `ApplicationInfo.FLAG_DEBUGGABLE` and `FLAG_TEST_ONLY` from the current application metadata.
- Producing subsystem: Android application manifest parsing and PackageManager/ApplicationInfo metadata.
- Mechanism: Bit tests on the current host's own flags preserve both independent values. A debug or test-only host with an otherwise applicable signing policy is classified as DEVELOPMENT_BUILD/CONTEXT rather than generating a release-signing mismatch warning. Missing or malformed policies remain unsupported/error even in development builds.
- References: [Android ApplicationInfo flags API](https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_DEBUGGABLE) and [FLAG_TEST_ONLY](https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_TEST_ONLY).
- Applicability: FLAG_DEBUGGABLE has existed since API 1 and FLAG_TEST_ONLY since API 4; all project-supported Android versions and ABIs apply.
- Visibility limits: These are application build attributes, not the device-wide developer options, ADB setting, compromise evidence or intent. A modified runtime can falsify returned metadata.
- Result states: Both observed booleans => CONTEXT, platform denial/unavailable metadata => SUPPORT, read exception => ERROR; unavailable data never becomes false.
- Interpretation: This application allows debugging and/or declares itself test-only. Legitimate developer, QA, automation and SDK consumers can set these flags. These values alone never raise device severity.

### Java debugger attached and waiting

- Observable signal: `Debug.isDebuggerConnected()` and `Debug.waitingForDebugger()` for the current runtime.
- Producing subsystem: Android framework Debug and ART VM debugging state.
- Mechanism: Two read-only public API calls are captured together. No call waits for a debugger or changes debugging settings; `waitingForDebugger()` only reads the state maintained around the framework debugger wait loop.
- References: [Android Debug API](https://developer.android.com/reference/android/os/Debug#isDebuggerConnected()) and [waitingForDebugger](https://developer.android.com/reference/android/os/Debug#waitingForDebugger()), plus reviewed [frameworks/base Android 15 Debug.java](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-15.0.0_r1/core/java/android/os/Debug.java), where connection delegates to VMDebug and waiting reads `mWaiting`.
- Applicability: Both APIs are public since API 1 and apply to all project-supported ABIs. This is a point-in-time observation, with no claim that the calls form an atomic runtime snapshot.
- Visibility limits: Java debugging and Linux ptrace are different observations and need not agree. Instrumentation and debuggers can be intentional; false does not exclude hooks, instrumentation or altered API results. Only this app is in scope.
- Result states: Observed true or false => CONTEXT, denied/unavailable => SUPPORT, exceptions => ERROR; cancellation is rethrown and never converted into a completed scan.
- Interpretation: The current runtime reports a Java debugger attachment or a wait for one. It does not establish malicious behavior or device compromise, and absence does not prove no instrumentation.

### Self-process TracerPid

- Observable signal: The exact `TracerPid:` integer field in a bounded read of `/proc/self/status`.
- Producing subsystem: Android Common Kernel procfs task status, `task_state()` in `fs/proc/array.c`, using `ptrace_parent()` and `task_pid_nr_ns()`.
- Mechanism: The reviewed ACK android14-6.1 branch formats the tracer task PID in the selected PID namespace or zero when no visible tracer was selected. The parser requires exactly one field containing a nonnegative Int, treats absent/duplicate/malformed/overflow values distinctly, and never defaults failed parsing to zero. Only the process represented by `/proc/self/status` is read; other threads and processes are not enumerated.
- References: Reviewed [kernel/common ACK android14-6.1 fs/proc/array.c](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/fs/proc/array.c), specifically the `ptrace_parent`, `task_pid_nr_ns` and `TracerPid` output sequence; [Linux proc status documentation](https://www.kernel.org/doc/html/latest/filesystems/proc.html) supplies provenance, with ACK as the implementation reference.
- Applicability: The proc status ABI is independent of CPU architecture. ACK 6.1 was reviewed; vendor/other kernel implementations and procfs access policy are not assumed identical. A field missing or denied on another device yields explicit unavailable rather than a universal kernel guarantee.
- Visibility limits: PID namespaces, ptrace races, per-thread tracing and kernel modifications limit visibility. A positive value can come from legitimate LLDB/GDB, crash analysis or testing. Reading this status cannot determine tracer intent. PID zero does not establish the absence of Java debugging, hooks, tracers on other threads, or future attachments. It also does not distinguish hidden tracers from legitimate namespace behavior.
- Result states: Observed zero or positive integer => CONTEXT, missing file/field or access denial => SUPPORT, malformed/duplicate/overflow or truncated bounded input => ERROR, other read exception => ERROR. The raw status file, credentials and unrelated process fields are not retained.
- Interpretation: A visible ptrace relationship was or was not reported for this process at this moment. It is a self-process diagnostic observation and never a device-wide security verdict.

## Host policy contract

Supply `assets/ns_signing_policy.json` in the consuming application only when the host intends to define its own current-certificate policy:

```json
{
  "version": 1,
  "allowedPackage": "com.example.host",
  "certificateSha256": ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"]
}
```

`allowedPackage` is case-sensitive and exact; no wildcard, prefix, suffix or substring matching. Fingerprints are 64 hexadecimal characters for X.509 DER certificate bytes, case-insensitive on input and normalized to uppercase; colon-separated values and malformed pins fail explicitly. The version, package and array have strict types, unknown/missing fields and trailing content are rejected, up to 16 explicit current certificate pins and 8 KiB total input are accepted. No asset is shipped in the SDK; no host-specific key, certificate or package is hardcoded in this feature. Hosts without a policy get SUPPORT, never a mismatch or fabricated pass. The public certificate is sufficient to provision pins; private signing keys are never accessed by the detector.

证书轮换需要主动把新证书指纹加入策略；不自动信任历史链中的新当前证书。多个 pin 允许多个明确指定的当前单签名证书，不等于支持多签名 APK 的完整签名集合验证。调试或 testOnly 构建只显示上下文；缺失策略仍为不支持、格式错误仍为错误。

## Validation and remaining runtime work

Domain tests exercise exact package scope, current-key rotation, full/partial evidence, missing SDK policy, development variants, multi-signer limitations, observation invariants and warning/error distinctions. Data tests exercise typed JSON and proc parsers, independent failure preservation and cancellation; mapper tests cover Chinese/English text, local-scope wording, export consistency and no fabricated zero on failures.

JVM tests do not validate PackageManager, ART or procfs on physical devices. Required device cases before claiming runtime validation: signed release, alternate-key release, debug package/key, testOnly, no-policy SDK host, known rotated single signer, multiple signers, Java debugger attached/waiting, LLDB ptrace and inaccessible/malformed proc environments. No physical-device validation is claimed in this record.
