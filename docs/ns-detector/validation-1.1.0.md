# 1.1.0 签名候选版验证记录

候选版本为 1.1.0，版本码 10100，Android 10+，应用 ID `io.github.withite.nsdetector`。本轮 APK 与已通过的 Debug 检查对应源码 `2bfdd88b71f66a6f88e508884fe60de1e2562eee`；Debug 包使用 `.debug` 后缀。Release 构建与私有离线签名已完成。用户随后要求不进行安装测试，因此不再执行签名候选包安装、界面运行或动画帧时间对比；下列 Debug 运行记录来自此要求之前。

| 检查 | 实际结果与依据 |
| --- | --- |
| 上游保留与架构 | 保留 18 个上游检测器、18 个原生单元和 10 个进程/Provider 入口；新增自身进程沙箱模块后，总计 21 个检测模块。完整 Debug 工作流的仓库守卫通过。 |
| 单元测试 | 下载的 277 个 XML 测试报告共 1,618 个用例，零失败、零错误、零跳过。覆盖新增的沙箱解析与映射、CA 来源观察、控制台布局和水印边界回归。 |
| 构建 / SDK | 四 ABI Debug、公共 API、buildHealth、SDK AAR 校验与独立消费示例通过；依赖分析报告无建议。 |
| Android lint | Debug lint 通过；报告有 202 条 Warning、3 条 Hint，无阻断项。Release lint 同样通过，报告也是 202 条 Warning、3 条 Hint。 |
| Debug 模拟器运行 | API 35 / x86_64；真实安装、启动、同意说明与跳过通知、21/21 模块返回、搜索、筛选语义、空结果/重置、JSON 文件选择器旋转/取消、文本导出取消与重启通过。异常恢复次数为 0。 |
| Release | [外部签名 Release 编译](https://github.com/withite/ns-detector/actions/runs/39198092520)通过；混淆包保留四 ABI，该工作流另有 269 个 XML / 1,562 项测试，零失败、错误和跳过；Debug 多出的 56 项 build-logic 测试不在此工作流中重复执行。最终 APK 的签名与 16KB ZIP 对齐验证通过，独立文件验证工作流也已通过。 |
| 最终签名 APK 运行 | 按用户要求未执行；仅做签名、文件及元数据验证。已有 Debug 记录不替代签名 Release 运行结果。 |
| 动画对比 | 已增加可选采集工具，但按用户要求未执行，不报告帧率或流畅度改善幅度。 |
| 发布文件 | [签名候选版 1.1.0](https://github.com/withite/ns-detector/releases/tag/v1.1.0)已发布。APK 为 18,942,319 字节；5 个 GitHub 附件的大小和服务端 SHA-256 均与本地签名文件逐项一致。 |

最终文件为 `NS-Detector-1.1.0.apk`，版本码 10100，`detectorCount=21`。离线读取 APK manifest 确认包名、版本、minSdk 29、targetSdk 37、四 ABI，且非 debuggable；签名证书与 1.0.0 相同。文件校验不等同于安装运行验证。

```text
APK SHA-256: c6499663101eae07e0a2473387a905c6acccc21c6a2fb239ab3e708447765c8c
Certificate SHA-256: 488a010361e5dc289c7041eb4dbe0bc611dcae5b37d030d384e1f7d9101a1a7c
Public-assets commit: a6246da4d107f1618d351b47ae05c81dc2fe3c35
```

可复核原始记录：

- [完整 Debug 检查、测试与模拟器运行](https://github.com/withite/ns-detector/actions/runs/39175021768)
- [Release 编译与 lint](https://github.com/withite/ns-detector/actions/runs/39198092520)
- [最终签名文件验证与上传](https://github.com/withite/ns-detector/actions/runs/39199433296)：`run_emulator_tests=false`，安装运行与动画对比不执行。

本次控制台把进度插值放到绘制阶段，使用 240ms 缓动；数值、模块分段与无障碍进度仍取真实结果，新扫描降低进度时立即复位。指标按宽度和字体缩放换列，避免手机窄屏挤压；水印缓存绘制资源并缩小旋转后的遍历范围。这些实现与几何测试已通过构建，本轮按要求不进行安装测试，因此没有实际帧时间效果数据。Debug 冒烟报告中的 `emulatorCompletionWaitSeconds=17.546` 只是脚本等待模块返回的采样时间，不能当作真机扫描性能或动画指标。

新增沙箱模块读取采样工作线程的有界 `/proc/thread-self/status` 快照，观察 UID 四元组、已授予 capabilities 和 Seccomp 模式。仅稳定的普通应用身份采用保守 AOSP 基线；系统/特殊宿主保持上下文，合法 `CAP_BLOCK_SUSPEND` 位排除在告警外，所有已知 Seccomp 模式均为上下文。该观察不代表所有线程或整个设备，也不能独立证明 Root、Hook 或特定工具存在。

网络环境新增当前用户/资料可见的 `AndroidCAStore` 来源计数，只保留数量。用户 CA、零张可见 CA 均为上下文，不证明流量拦截、实际 TLS 信任关系或环境安全。其他项目用于发现研究线索，未增加重复计数或引入闭源原生库；采用范围、许可与版本匹配的一手依据见[研究记录](research-1.1.0.md)及各模块 `EVIDENCE.md`。

文件选择器冒烟仅验证交互与文件名恢复；冻结报告字节和缓存清理由单元测试覆盖。本记录尚不包含真机硬件证明、跨厂商 Root/Hook 检出率、误报率、OEM procfs/CA 可见性或真机帧率结论。发布仍应保留 prerelease 标记，真机验收范围见[测试矩阵](test-matrix.md)。
