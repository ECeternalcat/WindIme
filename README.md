# WindIme (Garaho T9 IME)

面向 **Android 翻盖机** 的开源 T9 实体键盘输入法。零触摸屏依赖（0-Touch），全部交互通过物理方向键 + 数字键完成。专为日本 Garaho 体系（京瓷 KYF、夏普 SH 等）及各类 512MB~1GB RAM 的低配机型优化。

- **目标系统**：Android 5.0+（`minSdk = 21`，`targetSdk = 28`）
- **ABI**：`armeabi-v7a`（预编译 `librime_jni.so`）
- **核心引擎**：librime（经 Trime 构建）+ rime-ice 词库 + 纯 Java 兜底 T9 引擎

> 设计文档见 [`Document/`](Document/)（架构规范、模式状态机、设置页规范、701kc 按键抓取记录）。

---

## 功能特性

### 五状态输入模式（单键循环，iWnn 风格）
通过设备语言切换键切换；打字时只显示当前模式。

| 状态 | 说明 |
|---|---|
| **中文 T9** | 数字键触发拼音联想，librime + rime-ice 词库检索 |
| **中文 Multi-tap** | 多击数字键逐字母拼写拼音（生僻字/首字母精确输入） |
| **英文 T9** | 英文词库概率预测（~800 词内嵌词典） |
| **英文 Multi-tap** | 经典 a-b-c 循环 + 大小写 Caps 机制（密码 / 网址 / 缩写） |
| **数字 123** | 直接输出物理数字 |

可勾选参与循环的模式（设置 → 输入设定 → 候选栏设置 → 模式循环）。默认循环为 中 / 英Multi-tap / 123：面向中文用户，英文输入场景多为密码与缩写，词库预测非默认。

### 英文 Multi-tap 大小写（Caps 机制）
英文 Multi-tap 与密码等私密输入框共用的统一大小写机制：

- **绑定 Caps 键**（校准向导中可选步骤，可跳过）：**短按** = 仅下一个字母反向（全局大写中短按 = 下一字母小写）；**长按** = 全局大小写开关。长按阈值为设置中的长按间隔。候选栏实时指示 `Abc / aBc / ABC / AbC`。
- **未绑定 Caps 键**：回退老九键混合循环，按 2 依次 `a b c A B C`。
- 中文、英文 T9、数字模式下 Caps 键无动作。

### 三层拼音 UI（中文 T9）
- **第 1 层**：拼音预览（`ni'hao`）
- **第 2 层**：当前数字组的「读音」选项（如按 `2 4` → `ai / bi / ci / a / b / c`），◀▶ + ▲▼ 选择；**光标移过去即锁定**，无需按 OK；连续打字时移动选中的完整音节会自动锁定，便于 `96 24 64 → wo|ai|ni` 这类连打
- **第 3 层**：候选词；超过一屏时 ◀▶ 翻页，右下角显示 `n/总` 位置；候选格宽度按词长自适应，长词不再被截断

**候选音模式**（设置 → 输入设定 → 候选栏设置）：
- **现代**（默认）：音栏按 OK 确认读音后直接跳到词栏选词，节奏快。
- **循环选音**：整句自动分词后逐个音位确认读音，末位确认后回到第一个音复核，音栏显示 `n/总数`——适合精确输入的逐音控制。
- **默认候选光标**：打字后光标默认落在词栏还是音栏，可自选。

### 符号 / 定型文面板
全屏网格，顶部 **符号 | 定型文** 页签（▲ 在首行切换页签）。符号页为标点网格，定型文页列出用户预设的常用短语，选中即上屏。

### 首次使用向导
全新安装首次打开自动进入：欢迎 → 设置默认输入法（未完成不可跳过）→ 按键校准（未完成不可跳过）→ 完成后进入设置页。「清除全部设置」后向导重新出现。

### 私密输入框（密码 / WiFi 密码等）
自动切换独立英文 Multi-tap / 数字输入：左软键切换 en/123，禁用联想与用户词库学习；大小写与英文 Multi-tap 同一套 Caps 机制（校准的 Caps 键；未绑定时 abcABC 混合循环）。

### 检查更新与更新日志（设置 → 关于）
通过 GitHub Release 检测新版本，支持手动检查与每日一次静默检查；「去下载」经浏览器直下 APK。更新日志弹窗带轻量 Markdown 排版，D-pad 全程可操作。

### MENU 快捷菜单
当日系翻盖机系统把当前左软键标记为 **MENU** 时，可通过按键校准把该场景实际发送的 KeyCode/ScanCode 绑定为“快捷菜单”。标准 Android `KEYCODE_MENU` 仍可直接打开：

- 在系统默认及已创建的用户映射之间切换
- 打开系统输入法选择器
- 直接勾选参与循环的 T9、Multi-tap 和数字模式
- 打开 WindIme 设置

### 按键校准与多配置管理（0-Touch）
仅校准**真正因机型而异的功能键**：中英切换、符号表、快捷菜单、退格、回车、**大写切换（Caps）**、关闭输入法（除前三项外均可跳过）。
- 数字 0-9、方向键、确认键等 Android 标准键**无需绑定**。
- `*` / `#` 可自由绑定（日系机常作符号/回车键）。
- **回车（ENTER）**：纯换行，用于格式文本；建议绑 `*`（701KC 丝印即回车）。
- **大写切换（TOGGLE_CAPS）**：英文 Multi-tap / 私密框的大小写触发键，短按下一字母反向、长按全局开关；跳过该步则回退 abcABC 混合循环。
- **关闭输入法（DISMISS）**：收起 IME / 退出全屏，文本保留。
- **退格 + 长按收起组合（任意按键）**：在"长按收起输入法"步骤按下已绑定为退格的同一按键并确认，该键即短按（松键时）删除、长按收起输入法，取代长按快删。注意：NP701KC 的物理返回键为固件虚拟键，编辑框有文本时长按初期固件会抢先删除 1-2 字符（任何输入法无法拦截），推荐把组合绑到其他物理键获得完整体验。
- 校准时 **OK / ENTER** 或 **右方向键**跳过当前步骤，左方向键返回上一步。

### 设置页（D-Pad 全程可达）
主菜单分类：输入设定 / 输入法与按键 / Rime 词库 / 用户词典 / 定型文 / 重置 / 关于。
- **输入设定**：设置默认输入法（启用 + 切换两步引导）、候选栏设置（模式循环 / 候选音模式 / 默认候选光标）、按键反馈（震动/声音/无）、首字母大写、长按间隔（多击字母循环与各长按功能的统一阈值）；Kyocera 机型另有全屏兼容列表
- **用户词典**：自造词（拼音→汉字）CRUD，自动并入候选；支持导出/导入（写入应用专属外部目录，可用 ADB/MTP 取放）
- **定型文**：邮箱 / 问候 / 个人信息等快捷短语 CRUD；编辑可保存/复制/删除互不覆盖；支持导出/导入
- **重置**：切换默认按键映射 / 清除 Rime 学习 / 清空用户词典 / 清空定型文 / 清除全部设置（各档独立确认）

从桌面图标或**系统输入法设置的齿轮**都能进入。

### 安全逃生（Safe Escape Hatch）
**长按 退格 + `#` 5 秒** → 硬编码恢复出厂 keymap。也可 ADB：
```
adb shell settings delete secure default_input_method
```

---

## 构建与安装

### 环境要求
- JDK 17+（实测 JDK 21）
- Android SDK（含 platform `android-34`、build-tools、NDK 仅在重编原生库时需要）
- 本仓库已内置 Gradle Wrapper（`gradlew`），无需全局安装 Gradle

### 已知坑：SDK 路径
**请用环境变量指定 SDK，不要用 `local.properties`**：

PowerShell：
```powershell
$env:ANDROID_SDK_ROOT = "C:\Users\<你>\AppData\Local\Android\Sdk"
$env:ANDROID_HOME    = $env:ANDROID_SDK_ROOT
.\gradlew :app:assembleDebug
```

Bash：
```bash
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

### 安装到设备
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
安装后：进入 **设置 → 输入设定 → 设置默认输入法**，即可。

---

## 使用指南

1. **启用并切换**到 WindIme（见上）。
2. （可选）**校准功能键**：设置 → 输入法与按键 → 按键校准向导。
3. 点任意文本框：底部出现模式条 `[中][En][拼][Abc][123][符]`，◀▶ 选模式。
4. 中文 T9 示例（打「你好」）：
   - 按 `6 4` → 第 2 层出 `ni`，第 3 层出 `你` 等
   - 继续按 `4 2 6` → 第 2 层 `hao`，第 3 层 `你好`
   - ▲▼ 进候选层，◀▶ 选词，**OK** 上屏
5. 想回到模式条快速切模式：**退格清空 composing**，或按 **BACK**。

---

## 技术架构

四层管道：**物理按键 → KeyMapper → 引擎/状态机 → 0-Touch UI → InputMethodService**。

关键包（`app/src/main/java/com/garaho/ime/`）：

| 包 | 职责 |
|---|---|
| `keymap/` | `InputAction` 抽象、`KeyMapper`（JSON + 标准兜底）、`KeyMapConfig` |
| `engine/` | `ImeEngine` 接口；`T9PinyinEngine`（纯 Java 兜底）、`RimeEngine`（librime）、`EnglishT9Engine`、`*MultiTapEngine`、`CapsState`（统一大小写状态机）、`PinyinSession`/`PinyinLayer`（三层模型 + 循环选音音位游标）、`EnglishCapitalization`（句首大写）、`T9Segmenter`、`PinyinSyllables`/`PinyinDictionary` |
| `rime/` | `RimeData`（assets→filesDir 解包）、`RimeMaintenance`、`RimeRuntimeStatus`、`RimeLifecycle`（进程级会话编号/单一所有者/结构化日志） |
| `user/` | `UserDictionary`、`PhraseStore`（原子 JSON 持久化 + 导入导出）、`AtomicStore`、`StoreResult` |
| `feedback/` | `KeyFeedback`（震动/声音/无三档按键反馈） |
| `settings/` | 设置页、`GarahoPrefs`、校准向导、`settings/update/`（GitHub 更新检查：`UpdateChecker`/`GitHubReleaseParser`/`VersionUtil`/`ReleaseNotesFormatter`） |
| `ui/` | `CandidateBar`（三层候选条 + 翻页位置指示）、`CandidatePagination`、`SymbolPanel`、`QuickMenuPanel`、`SetupWizardActivity`、`FirstRunWizardActivity`（首次使用向导） |
| `com.osfans.trime.core/` | 内嵌的 JNI 契约类（匹配预编译 `librime_jni.so` 的符号名） |

纯 Java 逻辑（引擎、切分、词典、session）全部有 **JUnit 单元测试**：
```bash
./gradlew :app:testDebugUnitTest
```

---

## 许可证

本项目的中文词库数据来自 [iDvel/rime-ice](https://github.com/iDvel/rime-ice)（**GPL v3**），预编译的 `librime_jni.so` 由 [Trime](https://github.com/osfans/trime)（GPL v3）构建。由于这些 GPL 组件的链接/聚合，**WindIme 整体以 GNU GPL v3 协议发布**。详见各资产目录内的 `LICENSE.*.txt` 与 `RIME_ICE_SOURCE.md`。

## 致谢

- [rime / librime](https://github.com/rime/librime) — RIME 输入法核心
- [osfans/Trime](https://github.com/osfans/trime) — 同文输入法（Android），提供 librime 的 Android 构建与 JNI 参考
- [iDvel/rime-ice](https://github.com/iDvel/rime-ice) — 雾凇拼音词库
- [wairudogisu](https://github.com/wairudogisu) — 新功能添加，测试以及修复bug（详见Pull request）
