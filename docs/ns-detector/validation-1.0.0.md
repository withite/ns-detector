# 1.0.0 签名候选版验证记录

发布文件为 [NS-Detector-1.0.0.apk](https://github.com/withite/ns-detector/releases/download/v1.0.0/NS-Detector-1.0.0.apk)，Android 10+，版本码 10000，应用 ID `io.github.withite.nsdetector`。编译源码固定为 `0d9f163b935c59c5c365537fd8552dc594c3b7c7`；之后的提交仅完善验证工具和文档，未修改检测或应用源码。

| 检查 | 实际结果与依据 |
| --- | --- |
| 上游保留与架构 | 18 个上游检测器、18 个原生单元与 10 个进程/Provider 入口保留；总计 20 个检测模块。全部仓库守卫通过。 |
| 单元测试 | 268 个 XML 测试报告，共 1,547 个用例，零失败、零跳过；包含 build-logic、调度、错误恢复、签名策略、网络、历史、脱敏、筛选和更新安装回归。 |
| 构建 / SDK | 四 ABI Debug 编译、公共 API、buildHealth、SDK AAR 校验及独立消费示例通过；依赖分析无建议。 |
| Android lint | Debug 与 Release lint 通过。Debug 报告有 201 条 Warning、3 条 Hint；沿用上游 baseline，未扩大屏蔽。新代码警告未发现正确性或稳定性阻断。 |
| Release | 混淆与资源压缩编译通过；私有工作区签名，四 ABI 完整；实际单签名证书与内置策略一致，16KB ZIP 对齐通过。 |
| 模拟器运行 | API 35 / x86_64 / Pixel 6 配置；真实安装、NativeActivity 启动、同意说明与跳过通知、20/20 模块返回、搜索、筛选、空结果/重置、导出取消/旋转恢复、进程重启和中文界面全部通过。该次运行异常恢复次数为 0。 |
| 发布文件 | GitHub 五个附件的大小和服务端 SHA-256 均与本地验证文件一致。 |

可复核原始记录：

- [完整构建检查与测试报告](https://github.com/withite/ns-detector/actions/runs/39135219840/job/117698245952)
- [Release 编译与 lint](https://github.com/withite/ns-detector/actions/runs/39135231922)
- [最终签名 APK 的运行结果、截图、窗口 XML 与日志](https://github.com/withite/ns-detector/actions/runs/39137161359)

```text
APK SHA-256:
4b52648295e3c361b3d9233f000d430d1ac8911fa71b0f293efc88bb3bd9e3ea

签名证书 SHA-256:
488a010361e5dc289c7041eb4dbe0bc611dcae5b37d030d384e1f7d9101a1a7c
```

主分支早期模拟器运行遇到第三方 Action 缺少依赖，后续验证脚本也修复了裁切按钮误点及模拟器系统桌面弹窗的识别。最终运行使用已修复的工具验证上表中的同一源码 APK，全部通过。发布标签曾触发旧版自动签名工作流，因为本项目采用私有离线签名且未配置仓库签名 Secrets，该重复工作流失败；它没有替换已验证的发布文件。当前正式构建改为手动触发，避免标签发布重复编译。

本记录不包含真机硬件证明、跨厂商 Root/Hook 检出率、误报率或真机性能达标结论。文件选择器测试验证了交互与文件名恢复；报告字节冻结和缓存清理由单元测试覆盖，不能声称已完成相应的真机验收。签名候选版因此保留 prerelease 标记；后续验收范围见[测试矩阵](test-matrix.md)。
