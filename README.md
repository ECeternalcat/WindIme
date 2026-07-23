# WindIme (Garaho T9 IME)

面向 **Android 翻盖机** 的开源 T9 实体键盘输入法。零触摸屏依赖（0-Touch），全部交互通过物理方向键 + 数字键完成。专为日本 Garaho 体系（京瓷 KYF、夏普 SH 等）及各类 512MB~1GB RAM 的低配机型优化。

- **目标系统**：Android 5.0+（`minSdk = 21`，`targetSdk = 28`）
- **ABI**：`armeabi-v7a`（预编译 `librime_jni.so`）
- **核心引擎**：librime（经 Trime 构建）+ rime-ice 词库 + 纯 Java 兜底 T9 引擎

> 设计文档见 [`Document/`](Document/)（架构规范、模式状态机、设置页规范、701kc 按键抓取记录）。

---

## 功能特性

### 五状态输入模式（单键循环，iWnn 风格）
方向键 ◀▶ 在模式条上快速切换；打字时只显示当前模式。

| 状态 | 说明 |
|---|---|
| **中文 T9** | 数字键触发拼音联想，librime + rime-ice 词库检索 |
| **中文 Multi-tap** | 多击数字键逐字母拼写拼音（生僻字/首字母精确输入） |
| **英文 T9** | 英文词库概率预测（~800 词内嵌词典） |
| **英文 Multi-tap** | 经典 a-b-c 循环（密码 / 网址 / 缩写） |
| **数字 123** | 直接输出物理数字 |

可勾选参与循环的模式（设置 → 输入设定 → 模式循环），避免每次切换按 4-5 次。

### 三层拼音 UI（中文 T9）
- **第 1 层**：拼音预览（`ni'hao`）
- **第 2 层**：当前数字组的「读音」选项（如按 `2 4` → `ai / bi / ci / a / b / c`），◀▶ + ▲▼ 选择；**光标移过去即锁定**，无需按 OK
- **第 3 层**：候选词

### 符号 / 定型文面板
全屏网格，顶部 **符号 | 定型文** 页签（▲ 在首行切换页签）。符号页为标点网格，定型文页列出用户预设的常用短语，选中即上屏。

### 按键校准向导（0-Touch）
可以校准**因机型而异的功能键**：中英切换、符号表、退格（可选）。
- 数字 0-9、方向键、确认键等 Android 标准键**不可绑定**。
- `*` / `#` 可自由绑定（日系机常作符号/回车键）。
- 按 **返回** 跳过当前步，不会死锁。

### 设置页（D-Pad 全程可达）
主菜单五分类：输入设定 / 输入法与按键 / 用户词典 / 定型文 / 重置。
- **输入设定**：设置默认输入法（启用 + 切换两步引导）、模式循环、按键反馈、首字母大写、Multi-tap 间隔
- **用户词典**：自造词（拼音→汉字）CRUD，自动并入候选
- **定型文**：邮箱 / 问候 / 个人信息等快捷短语 CRUD
- **重置**：恢复出厂按键映射 / 清空 RIME 用户数据

从桌面图标或**系统输入法设置的齿轮**都能进入。

### 安全逃生（Safe Escape Hatch）
万一校准把按键搞乱：**长按 退格 + `#` 5 秒** → 硬编码恢复出厂 keymap。也可 ADB：
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
本项目使用的 AGP 版本在配合 `local.properties` 时存在路径校验问题。**请用环境变量指定 SDK，不要用 `local.properties`**：

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
安装后：进入 **设置 → 输入设定 → 设置默认输入法**，按两步引导「启用 WindIme」+「切换到 WindIme」即可。

---

## 使用指南

1. **启用并切换**到 WindIme（见上）。
2. （可选）**校准功能键**：设置 → 输入法与按键 → 按键校准向导。
3. 点任意文本框：底部出现模式条 `[中][En][拼][Abc][123][符]`，◀▶ 选模式。
4. 中文 T9 示例（打「你好」）：
   - 按 `6 4` → 第 2 层出 `ni`，第 3 层出 `你` 等
   - 继续按 `4 2 6` → 第 2 层 `hao`，第 3 层 `你好`
   - ▲▼ 进候选层，◀▶ 选词，**OK** 上屏
5. 想回到模式条快速切模式：**退格清空 composing**，或按 **BACK**（两段式：第一次回模式条，第二次收起 IME）。

---

## 技术架构

四层管道：**物理按键 → KeyMapper → 引擎/状态机 → 0-Touch UI → InputMethodService**。

关键包（`app/src/main/java/com/garaho/ime/`）：

| 包 | 职责 |
|---|---|
| `keymap/` | `InputAction` 抽象、`KeyMapper`（JSON + 标准兜底）、`KeyMapConfig` |
| `engine/` | `ImeEngine` 接口；`T9PinyinEngine`（纯 Java 兜底）、`RimeEngine`（librime）、`EnglishT9Engine`、`*MultiTapEngine`、`PinyinSession`/`PinyinLayer`（三层模型）、`T9Segmenter`、`PinyinSyllables`/`PinyinDictionary` |
| `rime/` | `RimeData`（assets→filesDir 解包）、`RimeMaintenance`、运行时状态 |
| `user/` | `UserDictionary`、`PhraseStore`（JSON 持久化） |
| `settings/` | 设置页、`GarahoPrefs`、校准向导等 |
| `ui/` | `CandidateBar`（三层候选条）、`SymbolPanel`、`SetupWizardActivity` |
| `com.osfans.trime.core/` | 内嵌的 JNI 契约类（匹配预编译 `librime_jni.so` 的符号名） |

纯 Java 逻辑（引擎、切分、词典、session）全部有 **JUnit 单元测试**，可在 host JVM 跑：
```bash
./gradlew :app:testDebugUnitTest
```

---

## 许可证

本项目的中文词库数据来自 [iDvel/rime-ice](https://github.com/iDvel/rime-ice)（**GPL v3**），预编译的 `librime_jni.so` 由 [Trime](https://github.com/osfans/trime)（GPL v3）构建。由于这些 GPL 组件的链接/聚合，**WindIme 整体以 GNU GPL v3 协议发布**。详见各资产目录内的 `LICENSE.*.txt` 与 `RIME_ICE_SOURCE.md`。

> 含义：任何人可以自由使用、研究、修改和再分发，但衍生作品必须同样以 GPL v3 开源。

## 致谢

- [rime / librime](https://github.com/rime/librime) — RIME 输入法核心
- [osfans/Trime](https://github.com/osfans/trime) — 同文输入法（Android），提供 librime 的 Android 构建与 JNI 参考
- [iDvel/rime-ice](https://github.com/iDvel/rime-ice) — 雾凇拼音词库

---

## 状态

当前 `versionCode 21 / 0.5.4`。中文 T9（三层）、英文 T9、Multi-tap、模式条、符号/定型文、设置页、用户词典均已可用，仍持续迭代中。详见 `Document/` 下的设计与路线图文档。
