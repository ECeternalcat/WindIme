# WindIme 工作日志

## 2026-07-27 — Notepad 文本可见性修复 + 方向键归还 + 全屏兼容设置

### 背景 / 问题
- 在 701KC Notepad（`jp.kyocera.memo` 的 `CreateMemoActivity`）正文框里用 WindIme 输入时，输入视图弹出后**整个文本区空白**：无光标、无文本，但输入本身正常（`commitText` 成功、按"完成"能正确保存），关闭输入法后文本才出现。系统 iWnn IME 在同一字段正常。
- 顺带发现：第三方 app 会被强制弹空全屏输入框；系统 Mail 在早期 `onEvaluateFullscreenMode()->true` 的全量修复后无法移动光标。

### 根因（逆向确认，详见 `np701kc.md §15`）
拉取并反编译 `MemoPad.apk`/`MemoPad.odex`（`Document/apks/701kc/`），结合 `dumpsys`/`uiautomator`/logcat 排查：
- 该 Kyocera framework **无论 IME 是否请求 fullscreen，都把 `mFullscreenApplied` 置为 `true`**，进入 extract（抽取）模式：宿主 `EditText` 停止自绘，改由 IME 的 extract 视图（`ExtractEditText`）显示文本。
- iWnn 走原生 fullscreen extract → framework 自动在候选栏上方放一个真实可编辑的 `ExtractEditText`（带光标、自动同步字段文本）→ 看得到文本。
- WindIme 早期实现为绕开"原生 fullscreen 在翻盖机不显示候选栏/不消费按键"的**过时判断**，自定义了白色文字镜像 + 紧凑候选栏，**跳过了原生 extract** → framework 没放 `ExtractEditText`、宿主又被停绘 → 空白。
- 实测推翻那个旧判断：本机原生 extract 下候选栏、`onKeyDown`、`commitText` 全部正常。

### 解决方案
**按宿主包名白名单启用原生 fullscreen extract：**
- `GarahoImeService.onEvaluateFullscreenMode()` 返回 `true` **仅当**当前宿主包在白名单内；其余 app 返回 `false`（保持各自正常字段可见、方向键可移动光标）。
- 默认白名单 = `{jp.kyocera.memo}`（Notepad）。
- 白名单改为 `GarahoPrefs` 持久化（`fullscreen_compat_packages` StringSet），可在设置页编辑。
- IME 在 `onStartInput` 记录 `currentHostPackage` 与 `lastHostPackage`（最近使用的宿主），供设置页"添加最近应用"使用。

### 新增设置页：全屏兼容列表
- 路径：设置 → 输入设定 → 最底部"全屏兼容列表"。
- 仅 Kyocera 机器显示该入口（`SoftkeyGuideHelper.create(context) != null` 判断）。
- 页面最顶部为提示行（不可点击）："本功能是为了解决某些软件打字时无法正确显示文本，默认已经配置好的，普通应用不需要修改本设置。"
- 提示下为当前白名单包名，每项 `OK` 移除；若最近使用过的应用不在列表里，末尾多一行"添加最近使用的应用：\<包名\>"，`OK` 加入。
- 文件：`FullscreenCompatActivity.java`、`AndroidManifest.xml` 注册、`strings.xml` 文案。

### 方向键行为调整（`handleModeBarAction`）
- 空闲态（模式条仅作指示器、焦点不在读音/候选行）：`NAV_LEFT/RIGHT/UP/DOWN` → `return false`，交给系统移动宿主编辑器（ExtractEditText）的文本光标。
- 模式切换**只能用切换键** `TOGGLE_LANG_MODE`（`advanceModeBarToNextInputMode`），方向键不再切模式。
- 组字态（焦点在读音/候选行）方向键行为不变（行内导航）。

### 死代码清理
移除因上述修复而失效的自定义全屏镜像机制：
- `buildInputView` 的 fullscreen 分支（白色镜像 + 整屏覆盖）。
- `fullscreenText` 字段、`refreshFullscreenText()` 及其调用、`displayHeightPx()`、`dp()`、`FS_LOOKBACK`、`inputViewFullscreen` 字段及 `onStartInputView` 的重建逻辑。
- `moveModeBar()` / `confirmModeBar()`（方向键不再导航模式条后失效）。
- `GarahoPrefs` 的 `fullscreen_input` 键/方法、设置页"全屏输入模式"开关、`input_fullscreen` 字符串。

### 涉及文件
- `GarahoImeService.java`：`onEvaluateFullscreenMode()`、`onStartInput`、`handleModeBarAction`、`buildInputView`、`onStartInputView`、`onUpdateSelection`、`onCommit`，移除镜像/`moveModeBar`/`confirmModeBar`。
- `GarahoPrefs.java`：`fullscreen_compat_packages` / `last_host_package` 键与方法；移除 `fullscreen_input`。
- `InputSettingsActivity.java`：动态菜单 + 最底部"全屏兼容列表"（Kyocera 限定）。
- `FullscreenCompatActivity.java`（新增）：全屏兼容列表设置页。
- `AndroidManifest.xml`：注册 `FullscreenCompatActivity`。
- `strings.xml`：标题/提示/前后缀；删除 `input_fullscreen`。
- `np701kc.md`：新增 §15（Notepad 编辑区空白问题：必须使用厂商 fullscreen extract 视图）。

### 验证
- 单元测试全绿（`:app:testDebugUnitTest`），`:app:assembleDebug` 通过。
- 设备实测：Notepad 1:1 复刻 iWnn（extract 视图、文本/光标可见、`commitText`/完成保存正常）；Mail 与第三方 app 恢复正常字段显示与光标移动。
