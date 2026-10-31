# InkVeil · 墨隐功能复刻

独立实现的 Android / Xposed 项目，参考工作区 InkHide 3.0.5 的行为分析，不修改原 APK，不复用原作者签名、服务器或追踪接口。

## 当前阶段

M1：本地配置、可独立测试的显隐规则、微信内设置入口及标题快捷手势的首版实现。微信会话、通讯录、搜索、通知的实际过滤适配尚未完成；不能把规则测试或构建成功理解为微信隐藏已生效。设备尚未连接，入口兼容性需在测试机确认。

保留的交互契约：

- 在微信主页搜索栏输入完整命令 `#hide`，显示设置。
- 微信「我 → 设置 → 更多 → 关怀模式」长按「开启」，显示同一设置；普通短按保留微信原行为。
- 多次点击首页标题「微信」，或长按标题，切换临时显示 / 重新隐藏；默认三击，长按采用系统标准阈值。
- 设置在微信宿主内显示，不通过远端网页或另一个 App 承担设置入口。
- 独立 App 仅作本地预览；其私有配置与微信沙箱配置不自动同步。

## 构建

已验证工具链固定为 JDK 17、Gradle 8.13、AGP 8.13.0、Android API 36 / Build Tools 36.0.0，最低 API 24。不升级全局 JDK，不依赖 Frida 构建。

```powershell
. D:\AndroidDev\Use-AndroidDev.ps1
gradle :core:test :app:assembleDebug :app:lintDebug --no-daemon --console=plain
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。`local.properties`、构建缓存和签名私钥不进入 Git。调试签名不等于发布签名。

上面的命令使用本机已验证的 Gradle 8.13。工程也提供固定版本及 SHA-256 的 `gradlew.bat`；首次使用需下载官方分发包，本次因网络读取超时未完成 Wrapper 启动，不能声称已验证 Wrapper 自举。网络可用时可把 `gradle` 换成 `.\gradlew.bat`，不要关闭分发包校验。

## 结构

- `core/`：无 Android 依赖的规则、入口判定、手势状态机和测试。
- `app/`：本地存储、原生设置视图、Xposed 宿主适配。
- `docs/DEVELOPMENT_PLAN_zh.md`：阶段计划、功能矩阵和验收门槛。
- `docs/PERFORMANCE_zh.md`：性能设计契约、测量方式和优化边界。
- `docs/ENTRY_POINT_zh.md`：原入口证据及复刻边界。
- `docs/VERIFICATION_zh.md`：本轮构建、测试、审阅、性能及交付验证结果。

## 安全与状态

仅针对本人可控测试设备和账号验证。默认离线，不做静默账号/消息上报，不连接原作者后台。宿主结构不匹配时不安装该功能的 Hook，维持微信原行为，并明确报告未适配。隐藏不等于删除、加密或擦除聊天记录。

Git 已初始化；未经用户要求不自动提交，不加入原 APK 和反编译大目录。
