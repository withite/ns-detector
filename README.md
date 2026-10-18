# NS Detector

Android 设备环境诊断工具，基于固定版本的 Duck Detector 开发，提供本地证据扫描、结果详情、扫描历史比较和默认脱敏报告。面向 Android 10 及以上设备，无需 Root 权限。

[下载与构建记录](https://github.com/withite/ns-detector/actions) · [版本下载](https://github.com/withite/ns-detector/releases) · [构建与签名](docs/ns-detector/build.md) · [隐私说明](docs/ns-detector/privacy.md) · [测试矩阵](docs/ns-detector/test-matrix.md) · [上游与归属](docs/ns-detector/upstream.md)

已发布 [1.0.0 签名候选版 APK](https://github.com/withite/ns-detector/releases/download/v1.0.0/NS-Detector-1.0.0.apk)。1,547 个单元测试、四 ABI 编译、SDK / 依赖 / API 与 lint 检查，以及签名 APK 的模拟器安装、20/20 模块返回和界面交互验证通过。详见[实际验证记录](docs/ns-detector/validation-1.0.0.md)；多厂商真机验证仍待执行。

## 当前交付范围

本项目完整保留 Duck 上游全部 18 个检测模块，新增网络环境、本应用完整性与进程沙箱，共 21 个模块。仪表盘提供分项证据、执行覆盖情况和解释；扫描历史保留最近 20 次已完成扫描的脱敏摘要，并比较类型化状态变化；TXT / JSON 导出报告默认脱敏。

| 范围 | 模块 |
| --- | --- |
| 启动与系统 | Bootloader、Custom ROM、SELinux、System Properties、Kernel Check |
| Root 与运行时 | SU、Native Root、Root Managers、Zygisk、LSPosed、Memory、Heap Residue、Mount |
| 应用与调试 | Dangerous Apps、ADB Runtime、Play Integrity Fix |
| 硬件与虚拟化 | TEE、Virtualization |
| 网络环境 | 应用默认网络的 VPN、Android 推荐代理、Private DNS 和当前用户可见 CA 存储计数 |
| 本应用完整性 | 当前签名证书与宿主策略、debuggable / testOnly、Java 调试器与本进程 TracerPid |
| 进程沙箱 | 采样线程 UID 视图一致性、已授权 Linux 能力与 Seccomp 模式 |

Dashboard V2 按实际返回的模块数展示进度，分开显示待完成、发现项、覆盖受限、执行错误与环境信息；完成度不等于设备安全评分。模块列表支持名称 / ID 搜索和类型化状态筛选，扫描期间保持固定顺序；筛选只影响浏览，不改变完整报告或历史记录。界面提供 Material / Miuix、明暗主题和大字体适配。

为减少主线程工作，扫描统计与结果投影在工作线程执行；同一检测器最多保留一个执行中的扫描和一个最新等待请求，旧结果不能覆盖新请求。控制台进度过渡仅更新绘制阶段，尊重系统动画缩放；指标按屏幕宽度和字体大小换列，水印缓存绘制资源与可视边界。更新 APK 的文件、包名和签名预检查在 IO 线程执行，验证期间防止重复确认，关闭弹窗会取消后续安装界面跳转。这些实现改进仍需结合真实设备验证流畅度与稳定性。

覆盖守卫 [`scripts/ns_check_upstream_coverage.py`](scripts/ns_check_upstream_coverage.py) 对照固定上游提交，检查全部 18 个检测器、18 个原生单元与 10 个进程/Provider 入口仍在；这不替代运行态验证。

每项检测的依据与观察限制见对应的 `feature/<模块>/EVIDENCE.md`。新增模块的依据见 [Network Environment](feature/networkenvironment/EVIDENCE.md)、[Application Integrity](feature/appintegrity/EVIDENCE.md) 与 [Process Sandbox](feature/processsandbox/EVIDENCE.md)；其他环境检测项目的参考与取舍见 [1.1.0 调研记录](docs/ns-detector/research-1.1.0.md)。VPN、代理、Private DNS、用户 CA、开发者配置、调试器或单个包名不能单独证明设备危险；相关观察作为环境信息展示。本应用签名与内置策略不匹配时会给出局部警告，但匹配不能证明 APK 或设备可信；该策略与进程都可能被修改，SDK 未提供策略时明确显示不支持。

“顶级”是持续验证目标，不是当前检出率承诺。源码、CI、模拟器和真机分别承担不同验证工作；实际通过的检查以对应提交的 Actions 记录为准。当前没有可发布的多厂商真机准确率或性能数据，尚不能宣称已完成正式版真机验收。

## 使用与结果解读

1. 在本仓库的 Actions 中下载成功运行的 APK artifact，或在 Releases 下载预览版/正式版。
2. 安装到 Android 10+ 设备，阅读启动说明；可拒绝非必要授权，受影响检查会减少观察范围。
3. 执行扫描并打开卡片查看证据。需要分享时导出报告，发送前仍应人工检查。

| 结果 | 应如何理解 |
| --- | --- |
| Danger / Warning | 观察到模块规则关注的证据，需结合详情、系统版本和设备情况解释。 |
| All Clear | 本次可执行的探针未观察到相应证据，不能证明设备从未被修改。 |
| Info / Support | 某些检查不支持或不可用；缺少观察能力不能视为通过。 |
| Info / Error | 检查执行失败，详情说明原因。 |
| Info / Context | 描述 VPN、代理、Private DNS 和本应用调试状态等已观察到的信息，不据此断言 Root 或受攻击；不会单独算作覆盖受限。 |

TEE 模块提供设备侧 KeyStore、证书链和证明字段检查。它不等同于独立可信服务端校验，也不提供 Google Play Integrity 官方判定；`Play Integrity Fix` 模块检查的是本地修改迹象。任何本地扫描都不能保证发现隐藏的 Root、Hook 或虚拟环境。

## 下载、升级与隐私

正式版应用 ID 为 `io.github.withite.nsdetector`，测试版为 `io.github.withite.nsdetector.debug`，可以共存。正式版使用独立的长期签名；构建工作流也提供明确标记的未签名导出，仅用于私有离线签名，不能直接安装或当作正式版下载。上游 Duck Detector 与 NS Detector 是不同应用。调试签名可能随构建环境变化，跨构建无法覆盖安装时需先备份需要的报告，再卸载旧测试版。

核心扫描在本机执行，首次冷启动不自动检查 GitHub 更新，第三方 GitHub 加速默认关闭。用户显式检查/下载更新或允许 TEE 在线吊销数据刷新时会联网。历史保存在应用私有存储；导出的文件由用户选择保存或分享。详见[隐私与数据处理](docs/ns-detector/privacy.md)。

## 本地构建

依赖版本由仓库固定，构建前请阅读 [AGENTS.md](AGENTS.md) 和 [CODING_STANDARDS.md](CODING_STANDARDS.md)。

```bash
git clone --recurse-submodules https://github.com/withite/ns-detector.git
cd ns-detector
./gradlew :app:assembleDebug
```

需要 JDK 17、Android SDK 37、Build Tools 37.0.0、NDK 30.0.16138531 和 CMake 4.1.2；最终以 [gradle.properties](gradle.properties) 为准。Debug 构建无需任何仓库 Secret。签名正式版的四项配置、版本号、产物和 CI 入口见[构建与签名指南](docs/ns-detector/build.md)。

工程继续采用 Kotlin / Compose、C++ / JNI、feature 分层和独立 SDK。内部 Kotlin/JNI 包名及 `libduckdetector.so` 保留，以减少原生符号与多进程入口迁移风险；应用 ID 和用户可见品牌独立。原架构与 SDK 说明见 [架构文档](docs/architecture/README.md) 和 [SDK 集成](docs/guides/sdk-integration.md)。

## 来源与许可

本项目以 [Apache License 2.0](LICENSE) 分发，保留原作者版权与许可声明。开发基线为 [Duck-Detector-Refactoring](https://github.com/eltavine/Duck-Detector-Refactoring) 的固定提交 [`b1135897cff4ddf01797f042bdd15f7d7e364e0d`](https://github.com/eltavine/Duck-Detector-Refactoring/tree/b1135897cff4ddf01797f042bdd15f7d7e364e0d)，改动详见本仓库提交记录和 [NOTICE](NOTICE)。

[春秋问题处理文档](https://github.com/mingzun09/Chunqiu-Detector-Problem-solution)仅作为需求与误报场景参考；本项目未从该仓库复制脚本、APK 或其他二进制。详细来源记录及固定版本的上游中英文说明见[上游与归属](docs/ns-detector/upstream.md)。
