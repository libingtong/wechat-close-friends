# GitHub 在线编译

工作流文件：`.github/workflows/android-build.yml`，显示名称为 **Android构建**。

本配置用于在 GitHub 托管 Runner 上测试、检查和编译当前源码，生成可下载的 **Debug 调试包**。它不发布 Release，不使用正式签名私钥，不安装模拟器，也不连接手机、微信账号或 Root 环境。

> 新配置提交时不预先宣称流水线成功；请以 Actions 页面具体运行结果为准。构建通过也不代表微信隐藏功能已完成：当前 M1 的真实会话、通讯录、搜索与通知过滤尚未接入，真机兼容性和 ART 性能仍未验证。

## 如何手动在线编译

1. 打开仓库的 **Actions** 页面。
2. 在左侧选择 **Android构建**。
3. 点击 **Run workflow**，选择 `master`，再次点击 **Run workflow**。手动触发需要相应仓库权限；如果刚提交配置，请等待工作流出现在默认分支。
4. 打开此次运行，等待“测试、检查与构建调试包”完成。
5. 在运行页面底部 **Artifacts** 区域下载 `wechat-close-friends-debug-运行ID-重试次数`，得到一个 ZIP 压缩包。
6. 解压后可见调试 APK、`SHA256SUMS.txt` 和 `BUILD_INFO.txt`；先核对来源提交与 SHA-256，再在隔离测试环境使用。

下载 Artifacts 可能要求登录 GitHub。Artifact 不是 GitHub Release 附件，不保证永久下载链接，也不承诺匿名直链。当前保留期为 **14 天**；过期后请重新构建。

自动触发条件：

- 推送到 `master`。
- 目标分支为 `master` 的 Pull Request。
- 上述 `workflow_dispatch` 手动运行。

同一分支 / PR 的新运行会取消过时运行，单个构建最长 30 分钟。Fork PR 首次运行可能需要维护者批准。

## 实际执行哪些步骤

| 步骤 | 内容 |
| --- | --- |
| 检出与环境 | Ubuntu 24.04、Temurin JDK 17；官方 Action 固定完整提交 SHA |
| Gradle | 使用仓库 Wrapper 8.13，保留分发 SHA-256 校验；校验 Wrapper JAR |
| Android SDK | 显式发现或经官方校验自举 `sdkmanager`，安装固定的 `platforms;android-36` 和 `build-tools;36.0.0` |
| 质量检查 | `:core:test`、`:app:lintDebug`、`:app:assembleDebug` |
| APK 验证 | Build Tools 36.0.0 的 `apksigner verify` 与 `zipalign -c -p 4` |
| 可下载产物 | 明确命名的 Debug APK、APK SHA-256、提交及运行信息 |
| 诊断报告 | 尽力在失败时也上传已生成的单元测试与 Lint 报告 |

主构建命令：

```sh
sh ./gradlew :core:test :app:lintDebug :app:assembleDebug --no-daemon --console=plain --stacktrace
```

构建采用项目固定的 AGP 8.13.0、compileSdk / targetSdk 36、minSdk 24。SDK 初始化不假定 `sdkmanager` 已在 Runner 的 `PATH` 中：

1. 先检查 `PATH`，然后检查 SDK 根目录下的 `cmdline-tools/latest/bin/sdkmanager` 与 `tools/bin/sdkmanager`。
2. 若均不存在，从 Google 官方下载固定的 `commandlinetools-linux-15859902_latest.zip`，严格校验 SHA-256 后才解压至 `RUNNER_TEMP`。文件名虽含 `latest`，版本号、完整 URL 与摘要均已固定。
3. 显式设置并传递 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 和工具路径；缺少 SDK 根目录时使用 Runner 临时目录。
4. 以有限的许可证应答文件作为标准输入完成许可确认，再安装固定平台和 Build Tools。不使用会因 `pipefail` 产生退出码 141 的裸 `yes` 管道。

Google 官方下载地址与锁定摘要：

```text
https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
SHA-256: 4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
```

下载、摘要检查、许可确认或安装任何一步失败都会使构建失败，不会跳过 SDK 验证。首次远端运行曾因 `sdkmanager: command not found` 退出 127，上述发现与自举流程针对该实际故障修正；修正配置本身不等于后续远端运行已经成功。

不把 `:core:benchmark` 设为 CI 性能门槛：共享 Runner 的调度、频率和负载噪声不足以证明 Android 真机表现。性能设计与真机验收见 [性能文档](PERFORMANCE_zh.md)。

## 下载内容与校验

APK Artifact 包含：

```text
wechat-close-friends-debug.apk
SHA256SUMS.txt
BUILD_INFO.txt
```

`BUILD_INFO.txt` 记录源码提交、工作流运行地址、构建时间、工具链与 Android SDK 条件，以及调试版和未验证边界。`SHA256SUMS.txt` 校验解压后的 APK，不是外层 Artifact ZIP 的摘要。

Linux 可在解压目录运行：

```sh
sha256sum -c SHA256SUMS.txt
```

macOS 可使用 `shasum -a 256 wechat-close-friends-debug.apk` 并对照记录中的摘要。Windows PowerShell 可计算并对照：

```powershell
Get-FileHash .\wechat-close-friends-debug.apk -Algorithm SHA256
```

另外的 `android-reports-运行ID-重试次数` Artifact 保存已生成的测试 HTML / XML 与 Lint 报告。若构建在生成报告之前失败，上传步骤会提示报告缺失，不会把原构建失败改成成功。运行页面摘要还会列出已上传产物的链接。

## 调试签名不是正式发布签名

此工作流由 Android 构建工具在 Runner 上生成 / 使用调试签名，不保存正式私钥。不同运行的调试证书可能不同，因此新 APK **不保证能覆盖安装上次的调试包**。

遇到签名不匹配时，先核对 APK 包名、证书与测试环境。不要为了安装本项目而卸载日常微信，也不要在日常账号设备上尝试破坏性排障。测试模块自身的配置迁移和重装应在可备份、可重置的隔离环境中处理。

正式发布签名、私钥保存、升级连续性及 Release 流程将在后续单独设计；当前不会自动创建 Release 或对外发布正式 APK。

## 权限、缓存与供应链边界

- 工作流仅申请 `contents: read`，检出后不保留 Git 凭据。
- 不使用 `pull_request_target`，不向 PR 提供签名私钥或业务 Secrets，不申请发布写权限。
- 仅使用官方 `actions/checkout`、`actions/setup-java`、`gradle/actions/setup-gradle` 和 `actions/upload-artifact`，全部固定完整 SHA。
- Gradle 使用 GitHub Actions 基础缓存；PR 只读缓存，不回写主分支缓存。
- 上传采用明确文件名单，不包含 Keystore、整个工作区、账号数据或私钥。
- Fork PR 产物仍是未受信任源码的构建结果；不要因显示绿色就直接安装。

Action 固定 SHA 和 Wrapper 校验不能证明所有依赖永远安全；升级版本时仍应审查来源、变更与构建结果。云端构建需要联网下载依赖，“本地隐私”不等于构建过程完全离线。

## 常见失败

- **Wrapper / 依赖下载超时**：查看具体下载地址与失败日志，排除网络问题后重新运行；不要关闭 SHA 校验。
- **SDK 初始化 / 安装失败**：检查发现到的工具路径、Google 下载与摘要校验、许可确认及 `sdkmanager` 输出；不要把已有目录视为成功，也不要关闭摘要检查。
- **测试或 Lint 失败**：优先下载报告并修复根因，不用 `continue-on-error` 掩盖质量检查。
- **没有 Artifact**：检查构建、签名、对齐或上传步骤；失败运行不保证有 APK。
- **已取消 / 超时**：检查是否被更新的提交取代；超过 30 分钟的运行会停止。
- **APK 可编译但入口无效**：编译不验证微信版本，需按 [入口文档](ENTRY_POINT_zh.md) 收集脱敏版本与证据，并在测试机适配。

本地复现方式见 [README](../README.md)，本轮已有验证事实见 [验证报告](VERIFICATION_zh.md)。
