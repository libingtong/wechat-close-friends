# 入口行为与证据

原始参考对象为工作区 InkHide 3.0.5；以下是本地静态证据，不是已在当前微信版本执行的记录。本文证据路径相对于 inkveil 仓库根目录，而非 docs 目录。

## 关怀模式

- `../decompiled/sources/p027q0/a.java:347`：目标类 `com.tencent.mm.plugin.setting.ui.setting.SettingsCareModeIntro`，Hook `initView`。
- `../recovery/apktool/smali/q0.1/a.smali:1228`：原指令包含该类名，随后以 `initView` 安装回调。
- `../decompiled/sources/B0/c.java:97`：after 回调取得 Activity 的 `android.R.id.content`，对选中的 View 安装长按。
- `../recovery/apktool/smali/r0.1/P0.smali:1907`：原筛选实际上是 `Button && id > 0`，不是按「开启」文本精确匹配。
- `../decompiled/sources/p028r0/ViewOnLongClickListenerC0099b1.java:38`：长按执行 `new V(Activity).g()`，在宿主上下文显示设置并返回 true。

复刻按用户字面目标收窄到该页面唯一「开启/開啟」按钮，保留短按；目标缺失或歧义时不误绑其他按钮。微信改变类名、方法或布局时，需要版本证据重新适配，不能声称泛版本可靠。

## 搜索命令

- `../decompiled/sources/p028r0/C0132j2.java:305`：原实现把 `com.tencent.mm.ui.tools.ActionBarSearchView` 加入搜索组件类集合，在 `:322` 对其构造后附加监听。
- `../decompiled/sources/p028r0/C0132j2.java:257`：找到 EditText 后幂等注册 TextWatcher。
- `../decompiled/sources/p028r0/C0145n.java:45` 起：在 afterTextChanged 比较命令，默认 `#hide`，并有防重入、清输入、收键盘、显示设置的路径。
- 原实现还含 DexKit 扫描、口令和 `#list`；这些不能自动视为本轮已复刻。

复刻首版只挂有证据的搜索组件。命令支持两端空白但不构造中间字符串；无空白的普通输入快速退出，最坏空白扫描为 O(L)。完整 `#hide` 才进行界面操作，两入口共用宿主设置面板。原反编译函数含恢复瑕疵，不能机械照抄不终止循环或错误跳转。

## 首页标题

- `../decompiled/sources/p028r0/x2.java:265` 起：原实现优先沿 `chattingTabUI/chattingTabUl → b → e` 寻找标题 TextView，并检查文本「微信」；失败时有更广反射搜索。
- `../decompiled/sources/p028r0/x2.java:308` 起：原实现附加触摸长按计时；可配置长按时长。
- `../decompiled/sources/com/lu/wxmask/bean/OptionData.java:104`：默认多击次数 3、长按 500ms。
- `../decompiled/sources/p028r0/x2.java:669` 起为口令验证；`:701` 起已有临显则清空豁免重新隐藏，否则开启临显。

复刻首版保留多击/长按触发同一临显状态机的交互，不复制任意对象图反射遍历，不全局 Hook 所有 View 或每次绘制。手势取消、长按后抬手、重复绑定、宿主原监听共存必须独立测试和真机核验。切换规则状态不等于会话实际刷新适配已经完成。
