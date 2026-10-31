# M1 验收记录

日期：2026-10-31。工程和原参考对象未更换。结论仅覆盖代码、构建、规则测试、静态审阅及文件交付，不扩大为真机微信功能已通过。本文记录首次本地验收；路径相对于仓库根目录，外部证据、参考 APK 和反编译材料不随公开源码分发，也不是正常构建的前提。

## 已观察结果

- Git 仓库：`.git/`。首次验收时已初始化、尚未提交；随后已按用户要求提交首版 `b34f9f8`，未修改全局 Git 配置。
- 最终命令：`gradle :core:test :app:assembleDebug :app:lintDebug --no-daemon --console=plain`。
- 工具链：已安装的 Gradle 8.13 / JDK 17 / AGP 8.13.0。
- 逐字末尾：`BUILD SUCCESSFUL in 55s`；`49 actionable tasks: 10 executed, 1 from cache, 38 up-to-date`；退出 0。
- JUnit XML 独立解析：关怀入口 6、搜索命令 4、标题手势 8、规则/快照 10，共 28；失败 0、错误 0。
- Lint 退出 0；报告含 7 项 Warning、0 项 Error，不能描述成「零警告」。
- 首版 APK：51,445 字节，SHA-256 `7362D9BC049D96320E82BD91C453B00731E307DD8F0F0ACC8570579B03BF7E54`。
- 原 APK SHA-256 仍为 `1571EE9D7A589DF17569DBD61D867C04352144CD3E272A88DF23A42BD80E67AC`。

最终构建合并日志和退出码记录分别在外部 `../inkveil-delivery/final-installed-build.log`、`final-installed-build.json`。测试原始 XML 在 `core/build/test-results/test`，Lint 报告在 `app/build/reports/lint-results-debug.html` 和同名 XML；构建目录不入 Git。

## 入口与规则完成范围

两种设置入口都调用同一个宿主内 `SettingsPanel.show(Activity)`，共享 `ConfigRepository`；不跨 App 打开另一个设置 Activity。搜索命令保留首尾空白兼容，不生成中间字符串。关怀模式只匹配指定页面唯一「开启/開啟」按钮，不改原短按监听。

首页标题仅在精确 `LauncherUI` 中沿已有证据的 `chattingTabUI/chattingTabUl → b → e` 定位，并验证可见文本「微信」。已经移除可能误绑聊天昵称/正文的全树文本猜测。三次相邻间隔小于 800ms 的点击或系统标准长按，切换同一临显状态机；长按阈值尊重设备无障碍设置，不固定声称每台设备都是 500ms。保存并调用宿主原监听；若目标微信已有触摸监听消费全部事件，本版不抢占事件，需真机版本适配。

显隐查询基于不可变快照和原子引用，配置规范化/序列化/持久化在后台串行执行。设置列表复用行。临显状态不持久化，冷启动不恢复临显。未知结构不误绑定，也不把失败当作已支持。

**尚未实现：微信会话/通讯录/搜索/通知的数据出口过滤、完整多进程同步及真实列表刷新。标题切换目前改变规则状态，不是已经在微信界面隐藏/显示好友的证明。**

## 性能样例

原始输出：`core/build/benchmark-raw.txt`。5 轮预热、7 轮中位、每轮 100 万次查询；命中校验和 `36000000`。10,000 名单样例：普通可见 2.72、隐藏 4.94、临显 8.14、总开关关闭 0.73 ns/op。

这是固定 ID、单 JVM 场景的微基准 smoke，存在分支预测/JIT/常量特化优势，没有 JMH 多 fork 和 ART/实际微信调用链测量。数值不能代表真机尾延迟、分配、功耗或「极致性能已达到」。验收硬约束仍是热路径无 I/O/JSON/反射扫描、语义正确和生命周期清理。

## 独立审阅

另一审阅代理独立核对原 Java/smali 证据、入口代码、监听共存、配置线程、测试 XML、Git 状态及原/新哈希；修复了标题误匹配、绑定状态过早提交和 View/Activity 清理问题。修复后独立代码审阅通过（M1 范围）。

Lint 警告：固定工具链的升级提示、Android 12+ dataExtractionRules 建议、未设置应用图标、程序构建的自定义 View 构造器提示、两项宿主 TextView 触摸可访问性提示、中文状态文本拼接。没有为消警随意升级工具链；也没有在宿主触摸回调强行调用 performClick，避免重复触发原点击。发布前继续核对备份/图标/无障碍场景，不把本轮 Warning 当作真机已兼容。

## 可逆文件交付

本地验收的固定四角色均已生成、执行相关验证并重新打开；以下目录不包含在公开仓库：

1. `../inkveil-delivery/MODIFIED_FILE.apk`
2. `../inkveil-delivery/DIFF_FILE.patch`
3. `../inkveil-delivery/VERIFICATION.txt`
4. `../inkveil-delivery/ROLLBACK.sh`

它们是原参考 APK 与独立新工程 APK 的文件交付证据，不是对原 APK 就地改名或证明两个 APK 已功能等价。补丁为原生 Git 二进制补丁，在独立副本上精确重建新 APK；不写原 APK。

同一 `aapt2 dump badging <APK副本>` 检查，字面首行关键字段为：

| 标签 | package | versionName | 退出码 |
|---|---|---|---|
| BASELINE | com.lu.wxmask272 | 3.0.5 | 0 |
| MODIFIED | io.github.inkveil | 0.1.0-dev | 0 |
| ROLLBACK | com.lu.wxmask272 | 3.0.5 | 0 |

签名检查、zipalign、补丁应用、回滚执行均退出 0；Git diff 退出 1 表示预期存在差异，不是失败。重建 APK 哈希等于新 APK，回滚副本哈希等于原 APK；BASELINE/ROLLBACK 的原始 stdout 与 stderr 逐字符串相等。回滚后重新应用新 APK 到 MODIFIED_FILE，原 APK 始终不变。

完整命令、参数、原文 stdout/stderr、退出码与哈希位于 `VERIFICATION.txt` 和 `runs/20261031-234355-830/summary.json`。回滚脚本自含 Python 标准库恢复逻辑，需 sh + Python 3，并保持同目录 `BASELINE.apk`；只在独立 `ROLLBACK_COPY.apk` 上实际运行。文件回滚不是手机上已安装 App 或微信数据的回滚。

## 已保留的环境纠错与边界

- 初始补丁的脚本路径换行被错误解析成目录；独立审阅发现后，将本次错误目录保留到交付区纠错目录，改用相对路径/实际换行重新生成。一次清理尝试在执行前被工具策略拒绝，无进程退出码；另一次错误绝对路径解析退出 1，随后按实际工作区解析并原生重命名成功，未删除原文件。
- 首次 ROLLBACK 执行因 Git Bash/MSYS fork 失败退出 254；改成单次 exec Python 标准库后，曾因多个 python.exe 路径被拼接退出 127，修复为首个确定解释器后退出 0。失败的原始 stderr/exitCode 保留在各自 runs 目录，没有覆盖为成功记录。
- Wrapper 已生成并固定官方分发包 SHA-256，但首次下载读取超时退出 1。备用官方直连低速下载由本次进程主动停止，退出 -1，残片标记为 unverified.partial，未使用或假报校验通过。最终构建/测试/Lint 使用本机已安装、既有验证通过的 Gradle 8.13 成功；不要声称 Wrapper 自举完成。
- SDK XML v3/v4 工具提示及 Gradle 9 弃用提示仍存在；当前固定组合已通过构建，不因此盲目升级或修改其他项目环境。
- 未安装或运行任一 APK；未连接一加 13，未操作微信账号。所有入口、实际过滤、ART 性能和功耗仍待隔离测试机验证。
