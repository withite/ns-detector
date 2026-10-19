# 构建、Actions 与签名

## 工具链与源码

构建固定版本的源码及 `uihelper` 子模块，不自动追随其最新分支。使用 JDK 17；Android 工具链以仓库 [gradle.properties](../../gradle.properties) 为准，当前为：

| 工具 | 固定版本 |
| --- | --- |
| Android compile / target SDK | 37 |
| 最低 Android | 10 / API 29 |
| Build Tools | 37.0.0 |
| NDK | 30.0.16138531 |
| CMake | 4.1.2 |

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

Debug 不需要签名 Secret，安装包 ID 为 `io.github.withite.nsdetector.debug`。Gradle 原生 APK 输出位于 `app/build/outputs/apk/debug/`，统一命名或收集的文件以 Actions artifact 清单为准。

## GitHub Actions

在 [withite/ns-detector Actions](https://github.com/withite/ns-detector/actions) 查看工作流、提交编号和日志。首次运行若 GitHub 要求启用仓库 Actions，需由仓库所有者启用。

| 工作流用途 | 触发方式 | 产物和门槛 |
| --- | --- | --- |
| CI / 预览构建 | `main` 提交、Pull Request、手动运行，以工作流定义为准 | 单元测试、架构守卫、Debug APK、SDK AAR、lint/测试报告；主分支增加模拟器启动检查与截图。无需自备 Secret。 |
| 预览分发 | `main` 构建与模拟器检查通过 | 版本化 `preview-<run_number>` prerelease，保留提交来源，使用测试应用 ID 和调试签名。 |
| 正式发布 | 手动运行 `release.yml` | 默认验证四项签名配置，产生正式 APK 和校验信息；不会退回调试签名。选 `external_signing=true` 时仅导出未签名工件，不自动发布。发布已有签名文件时创建版本标签不重复触发编译。 |
| 离线签名后验证 | 手动运行 `verify-release.yml` | 校验已签名公开文件和指定源码；`run_emulator_tests` 决定是否另做安装运行检查。可将文件上传到现有草稿 Release，保留最终发布步骤。 |
| 控制台绘制对比 | 手动运行 `motion.yml` | 同一模拟器依次清装固定 1.0.0 与指定签名候选版，启用系统动画，保存滚动、空闲和启动扫描窗口的原始 FRAMESTATS；结果不等同于真机帧率。 |

预览是开发验证产物。模拟器启动成功不能代替硬件 KeyStore、SELinux、挂载、app zygote 和真实 Root/Hook 环境的真机验证。某次运行是否通过、是否生成 APK，以其最终日志为准；文档没有代替未运行的检查作出通过声明。

默认版本为 `1.0.0` / `10000`。可使用以下 Gradle 参数，或环境变量 `VERSION_NAME` / `VERSION_CODE`：

```bash
./gradlew :app:assembleDebug \
  -Pnsdetector.versionName=1.0.1-preview \
  -Pnsdetector.versionCode=10001
```

CI 预览采用 `1.0.<run_number>-preview` 和 `10000 + run_number`；Debug 构建另附 `-debug`。正式发布应选择递增的 `versionCode`，确保新版本可以覆盖旧正式版。

## 正式签名：四个 GitHub Secrets

在仓库 **Settings → Secrets and variables → Actions** 配置以下 Secrets。长期密钥应由项目所有者创建并离线备份；不得提交到仓库或写入日志。

| Secret | 内容 | 构建时映射 |
| --- | --- | --- |
| `NS_KEYSTORE_BASE64` | keystore 文件的 Base64 文本 | 解码到临时文件，路径传入 `ANDROID_KEYSTORE_PATH` |
| `NS_KEYSTORE_PASSWORD` | keystore 密码 | `ANDROID_KEYSTORE_PASSWORD` |
| `NS_KEY_ALIAS` | 签名 key alias | `ANDROID_KEY_ALIAS` |
| `NS_KEY_PASSWORD` | key 密码 | `ANDROID_KEY_PASSWORD` |

GitHub 自动提供的 `GITHUB_TOKEN` 用于仓库内发布，不需要额外创建个人访问令牌；无需配置 Telegram。普通正式工作流缺少任一项会明确失败，不能产出冒充正式签名的调试包。没有仓库 Secrets 写入权限时，可以采用下述显式离线签名路径。

本地正式构建使用相同的四个 `ANDROID_*` 环境变量，其中 `ANDROID_KEYSTORE_PATH` 指向本地 keystore 文件，再执行：

```bash
./gradlew :app:assembleRelease \
  -Pnsdetector.versionName=1.0.0 \
  -Pnsdetector.versionCode=10000
```

首次正式发布时记录证书 SHA-256 指纹。此后保持相同密钥、应用 ID 与递增版本码；丢失签名密钥会影响侧载覆盖升级。Debug 与 Release 可共存，历史存储分别独立；上游应用与本项目也不共享存储。

## 无 Secrets 权限时：私有离线签名

手动运行 `release.yml`，设置 `external_signing=true` 和确定的版本名称/版本码。构建仍使用 Release 混淆与资源压缩，并执行相应检查，但只上传 `ns-detector-release-unsigned` 工件；APK 名含 `UNSIGNED`，`build-info.json` 的 `signed=false`，清单为 `unsigned-manifest.json`。此路径不会触发自动正式发布，也不会用调试签名代替。

将工件解压到经授权的私有工作区后，使用项目所有者持有的长期密钥和仓库脚本签名。以下 `$NS_SOURCE_COMMIT` 必须是已审阅且与工件一致的 40 位源码提交 SHA；`--output` 指定的目录必须不存在或为空。

```bash
python3 scripts/ns_sign_external_release.py \
  --input /private/unsigned-export \
  --output /private/signed-export \
  --tools /path/to/android-sdk/build-tools/37.0.0 \
  --keystore /private/ns-detector-release.keystore \
  --password-file /private/password.txt \
  --expected-commit "$NS_SOURCE_COMMIT" \
  --alias ns-detector
```

`--tools` 是同时包含 `apksigner` 和 `zipalign` 的目录。当前脚本从同一密码文件提供 keystore 与 key 密码；密钥使用不同密码时需采用相应的私有签名流程。脚本先校验未签名工件的清单、哈希、应用 ID、源码提交与四 ABI 原生库，再完成 16 KiB 对齐、签名和实际证书验证，重新生成 `build-info.json`、`update.json` 与 `SHA256SUMS`。签名会改变 APK 字节，不能沿用未签名 APK 的哈希。

首次创建的密钥与密码需要私有备份；私钥文件、密码和备份均不得上传 GitHub，不得包含在 APK、工件或日志内。工作区备份不代替项目所有者的长期离线保管。

若本地无法直接上传 Release 文件，可创建临时产物分支，仅把签名输出中的**可公开文件**放入该分支的 `release-assets/` 目录。该目录包括已签名 APK、公开签名指纹、构建/更新清单与校验值；不得包含 keystore、密码或私有备份。产物分支不需要合并到 `main`。

先创建目标草稿 Release，再手动运行验证工作流。以下 `$NS_SIGNED_ASSETS_REF` 为产物分支或其提交，`$NS_APK_SHA256` 为已审阅的签名后 APK 的 64 位小写 SHA-256，版本标签应与 APK 的版本名称一致。

```bash
gh release create v1.0.0 --draft --target "$NS_SOURCE_COMMIT" \
  --title 'NS Detector 1.0.0' --notes-file release-notes.md
gh workflow run verify-release.yml --ref main \
  -f tag_name=v1.0.0 \
  -f signed_assets_ref="$NS_SIGNED_ASSETS_REF" \
  -f expected_apk_sha256="$NS_APK_SHA256" \
  -f source_commit="$NS_SOURCE_COMMIT"
```

工作流验证所有公开文件的校验值、源码与包版本元数据、宿主证书策略、APK 实际签名及对齐。默认另做模拟器安装与界面检查；指定 `run_emulator_tests=false` 时跳过安装，留下仅文件验证的明确记录。通过后仅把文件上传到仍处于草稿状态的目标 Release，不自动公开。若文件已经在草稿中，省略 `signed_assets_ref` 即可直接下载该草稿进行验证，其余三个输入仍须提供。复核 Actions 结果和最终文件后，才公开对应 Release；临时产物分支可在之后删除。

这一流程的存在不表示某个版本已完成正式签名；具体下载是否为正式签名版，以其最终发布文件、签名校验和构建说明为准。

## 验证命令

```bash
./gradlew :build-logic:test unitTest checkPublicApi :app:assembleDebug
./gradlew buildHealth :sdk:aar:publish :sdk:aar:verifySdkAar
./gradlew -p samples/sdk-consumer assembleDebug
./gradlew :app:lintDebug
for checker in .github/scripts/check-*.py; do python3 "$checker"; done
```

完整仓库标准见 [CODING_STANDARDS.md](../../CODING_STANDARDS.md)。具体结果与尚待完成的真机项目见 [测试矩阵](test-matrix.md)。
