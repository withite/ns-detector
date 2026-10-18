# 上游、参考资料与归属

NS Detector 是 Duck Detector 的独立衍生项目，使用自己的应用 ID、分发仓库、版本号和发布签名。保留源码内部包名与原生库名称不表示与上游应用共用身份，也不表示上游作者为本项目背书。

## 固定开发基线

- 源码：[eltavine/Duck-Detector-Refactoring](https://github.com/eltavine/Duck-Detector-Refactoring)
- 固定提交：[`b1135897cff4ddf01797f042bdd15f7d7e364e0d`](https://github.com/eltavine/Duck-Detector-Refactoring/tree/b1135897cff4ddf01797f042bdd15f7d7e364e0d)
- 原始英文说明：[固定提交 README.md](https://github.com/eltavine/Duck-Detector-Refactoring/blob/b1135897cff4ddf01797f042bdd15f7d7e364e0d/README.md)
- 原始中文说明：[固定提交 README_ZH.md](https://github.com/eltavine/Duck-Detector-Refactoring/blob/b1135897cff4ddf01797f042bdd15f7d7e364e0d/README_ZH.md)
- 许可：[Apache License 2.0](../../LICENSE)，原作者版权和源码文件头予以保留；另见 [NOTICE](../../NOTICE)。

上游说明用于查阅原始设计、探针边界与历史行为；其中上游下载地址、更新策略、Telegram 分发和签名配置不构成 NS Detector 当前发布流程。NS Detector 当前操作步骤以本项目 [README](../../README.md) 和 [构建指南](build.md) 为准。

## 需求参考

[mingzun09/Chunqiu-Detector-Problem-solution](https://github.com/mingzun09/Chunqiu-Detector-Problem-solution)作为检测需求、设备差异和误报问题的参考。本项目未从该仓库复制脚本、APK 或其他二进制，也没有将其问题处理建议直接作为检测规则。新增安全结论仍需以适用版本的官方文档、AOSP 或相应内核实现为依据。

## 本项目改动范围

- NS Detector 品牌、独立应用 ID、版本与更新来源。
- Android 公共 API 网络环境观察；VPN、代理与 Private DNS 作为上下文信息。
- 本地历史、扫描比较及默认脱敏导出。
- 独立 GitHub Actions、预览产物、正式签名检查和验证文档。

改动以本仓库提交记录为准，依赖与第三方许可继续由原有清单和相应依赖许可证说明。
