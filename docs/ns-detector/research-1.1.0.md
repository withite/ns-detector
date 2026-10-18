# 1.1.0 检测增强研究记录

研究日期：2026-10-18。对照现有检测的 `EVIDENCE.md` 后选择新增信号；其他项目用于发现线索，平台语义以适用版本的 Android、AOSP 和 Android Common Kernel（ACK）为依据。本文记录设计与实现边界，不代表 APK、真机准确率或界面性能已经验证。

## 参考项目与采用范围

| 项目与固定提交 | 核验到的许可 | 结论 |
| --- | --- | --- |
| [RootBeer · de7bc7b](https://github.com/scottyab/rootbeer/tree/de7bc7b4b09c9db148b62b331b4a44076b2eb480) | [Apache-2.0](https://github.com/scottyab/rootbeer/blob/de7bc7b4b09c9db148b62b331b4a44076b2eb480/LICENSE) | `su`、包名、属性、挂载检查已由现有模块覆盖，不重复增加扫描或计数。其 README 明确说明 BusyBox 可预装在正常设备，单个工具文件不能证明 Root。 |
| [DetectFrida · 86a88fe](https://github.com/darvincisec/DetectFrida/tree/86a88feb9988b0d279c648dc8144c39cb128736d) | [MIT](https://github.com/darvincisec/DetectFrida/blob/86a88feb9988b0d279c648dc8144c39cb128736d/LICENSE) | 线程、命名管道、代码内存与文件比对已有 Memory / Zygisk 等模块覆盖。本轮未复制探针、混淆器或 APK，也未再加一套重复内存扫描。 |
| [Android-Native-Root-Detector · 7eb2127](https://github.com/reveny/Android-Native-Root-Detector/tree/7eb21276bd5da4573b6fb2b1356b513bf1068ea7) | [MIT](https://github.com/reveny/Android-Native-Root-Detector/blob/7eb21276bd5da4573b6fb2b1356b513bf1068ea7/LICENSE) | 当前源码树公开 UI 与调用 `libreveny` 的 JNI 声明，未公开可核验的对应原生检测函数实现。无法据此确认内部规则、移植全部检测或验证其版本边界；未引入外部二进制。 |
| [NativeDetector · d3527aa](https://github.com/Dr-TSNG/NativeDetector/tree/d3527aa6644a09fbbad8fab70498f8424a567dc1) | 当前树未声明许可 | 当前树只有简短 README，没有可研究的检测实现；未采用代码或二进制。 |
| [MagiskDetector · 1570969](https://github.com/vvb2060/MagiskDetector/tree/1570969c1070b372b63aa18c6ff30a3f897a1983) | [Apache-2.0](https://github.com/vvb2060/MagiskDetector/blob/1570969c1070b372b63aa18c6ff30a3f897a1983/LICENSE) | 读取自身 procfs、比较挂载与映射的思路已有覆盖。其历史 `TracerPid` 检查不能直接推广为当前某个隐藏工具的身份判断；本轮未复制实现。 |
| [HTTP Toolkit Android · 53d7e35](https://github.com/httptoolkit/httptoolkit-android/tree/53d7e356b1872d9ad20716113fc01b9388bdf4c3) | [AGPL-3.0](https://github.com/httptoolkit/httptoolkit-android/blob/53d7e356b1872d9ad20716113fc01b9388bdf4c3/LICENSE) | [`ProxySetup.kt`](https://github.com/httptoolkit/httptoolkit-android/blob/53d7e356b1872d9ad20716113fc01b9388bdf4c3/app/src/main/java/tech/httptoolkit/android/ProxySetup.kt#L174) 提供 CA 来源区分的研究线索。未复制其代码；新观察依据 AOSP Conscrypt 的提供者与别名语义独立实现。 |

这些项目之间没有组成投票系统；相同底层事实不会因被多个项目采用而成为多份独立证据。上游 Duck Detector 的固定基线与归属仍见 [upstream.md](upstream.md)。

## 新增自身进程沙箱观察

归属：`feature/processsandbox`，独立于应用签名与调试状态。只读取当前采样线程的有界 `/proc/thread-self/status` 快照及公开宿主元数据，不请求提权、不枚举其他应用进程，不改变凭据或系统调用过滤器。路径不可用时不回退到线程组 leader，以免把不同线程的凭据或过滤状态相互比较。

- **UID 四元组**：读取 real、effective、saved、filesystem UID，在同一同步采样线程前后调用 `Process.myUid()` 对照；采样期间 API UID 改变则为覆盖限制，不告警。仅普通应用身份采用应用基线；多用户 UID 取 appId 范围 10000–19999，系统/更新系统应用及其他身份不套用此基线。稳定不一致最多为 WARNING，含义是观测到身份分歧，不指认 Root、Hook 或某个工具。
- **已授予 capabilities**：读取 `CapEff`、`CapPrm`、`CapAmb` 无符号掩码。AOSP 可为 wakelock GID 授予 `CAP_BLOCK_SUSPEND`（第 36 位），该位保守排除；普通应用其他非零位最多为 WARNING。系统及特殊宿主只提供上下文。`CapBnd` 不是已授予权限，不参与评分；`NoNewPrivs=0` 也不作为异常。
- **Seccomp 模式**：0 为未启用、1 为 strict、2 为 filter，三者均为 CONTEXT。AOSP 在 SELinux 未强制执行时可合法跳过安装过滤器，故模式 0 不升级为告警。模式 2 仅表示存在过滤模式，不能证明规则完整、符合原厂策略或没有被修改。

缺字段、访问受限、未知模式与读取失败明确保留覆盖限制或错误，不能填成正常值。procfs 与公开 API 并非不可伪造的独立信任根；Linux 凭据、capabilities、Seccomp 存在按线程状态，此处只代表采样工作线程，不能推广为所有线程或整个设备。前后 UID 复核不能证明采样全过程原子性。普通应用基线是对经核对 AOSP 行为的保守比较，OEM 差异仍需真机核验。

主要依据：

- [Process.myUid() 官方定义](https://developer.android.com/reference/android/os/Process#myUid())；[ApplicationInfo 公共标志](https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_SYSTEM)。
- [Android 10 Zygote：Seccomp 安装门槛](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp#518)、[UID 设置](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp#1039)、[capabilities 计算及特殊身份](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp#1156)。
- [Android 16 Zygote：相同 Seccomp 门槛](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp#639)、[capabilities 与 wakelock 例外](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/jni/com_android_internal_os_Zygote.cpp#2219)。
- [ACK android14-6.1：UID 四元组](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/fs/proc/array.c#188)、[capabilities 与 Seccomp 输出](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/fs/proc/array.c#298)、[Seccomp 模式常量](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/include/uapi/linux/seccomp.h#10)、[CAP_BLOCK_SUSPEND 常量](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/include/uapi/linux/capability.h#369)。[ACK 4.19 对应输出](https://android.googlesource.com/kernel/common/+/refs/heads/deprecated/android-4.19-stable/fs/proc/array.c#193)作为旧内核对照；不假定所有厂商内核完全一致。
- [ACK android14-6.1：thread-self 路径解析](https://android.googlesource.com/kernel/common/+/refs/heads/android14-6.1/fs/proc/thread_self.c#11)：按当前任务的 tgid/pid 解析到其自身线程目录。

## 扩展网络环境：当前用户可见 CA 来源

归属：`feature/networkenvironment`。通过 `KeyStore.getInstance("AndroidCAStore")` 枚举别名，仅统计 `user:`、`system:` 与未识别来源，不保存别名、证书主体、指纹或证书内容，不联网验证。

所有成功观察均为 CONTEXT。企业设备管理、调试代理和私人 CA 都可能合法安装用户证书；发现用户 CA 不表示存在流量拦截、Root 或应用已信任该 CA。只观察当前应用所属用户/工作资料可见的存储，不能推断其他用户。

Android 7.0 起，target API 24 及以上的应用默认信任系统 CA；应用、域名及调试配置可以另行选择信任锚。因此 CA 枚举不验证 NS Detector 或其他应用的实际 TLS 信任规则。Conscrypt 内部目录不可列举时也可能静默跳过，所以“可见 0 张”不能写成“未安装 CA”或“环境安全”。未知来源、枚举限制和失败明确保留，不虚构零值。系统 CA 在新版本可来自 Conscrypt APEX，不能通过扫描固定 `/system` 目录替代提供者。

主要依据：

- [Android 官方网络安全配置](https://developer.android.com/privacy-and-security/security-config)：默认信任锚、用户 CA 与应用自定义配置。
- [Android 10 TrustedCertificateStore](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-10.0.0_r1/platform/src/main/java/org/conscrypt/TrustedCertificateStore.java)：旧版本的来源前缀与枚举语义。
- [Android 16 来源前缀](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-16.0.0_r1/platform/src/main/java/org/conscrypt/TrustedCertificateStore.java#86)、[APEX 系统证书目录](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-16.0.0_r1/platform/src/main/java/org/conscrypt/TrustedCertificateStore.java#104)、[别名枚举与不可见目录处理](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-16.0.0_r1/platform/src/main/java/org/conscrypt/TrustedCertificateStore.java#270)。
- [Android 16 KeyStore SPI 枚举委托](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-16.0.0_r1/platform/src/main/java/org/conscrypt/TrustedCertificateKeyStoreSpi.java#86)、[JSSEProvider 注册](https://android.googlesource.com/platform/external/conscrypt/+/refs/tags/android-16.0.0_r1/platform/src/main/java/org/conscrypt/JSSEProvider.java)、[ActivityThread 选择当前用户目录](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/java/android/app/ActivityThread.java#9031)。

## 待验证范围

解析、状态分类、取消与失败路径由相应模块测试覆盖设计；实际通过结果以 CI 产物为准。Android 10 至当前版本、多用户/工作资料、系统宿主、原厂与修改设备仍需对应设备实测。不存在由本轮研究即可支持的“全部环境必检出”或“零误报”结论。
