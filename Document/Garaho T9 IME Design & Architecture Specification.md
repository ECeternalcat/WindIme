Garaho T9 IME (ガラケー/翻盖机开源 T9 输入法) 架构与设计文档
=========================================

1. 项目概述与目标环境 (Project Overview & Target Environment)

----------------------------------------------------

### 1.1 项目背景

现代 Android 输入法（如 Gboard、搜狗、微信输入法）均针对大屏触控进行设计，在传统物理 3x4 键盘翻盖机（如日本 Garaho KYF31/SH-01J、三星 W2019 等）上存在选词焦点丢失、按键延迟高、依赖高版本 AndroidX 库导致安装失败等问题。本项目旨在打造一款专为**Android 5.1+ (API 22+) 翻盖设备**设计的开源、无触控依赖（0-Touch）、极轻量、高可扩展的 T9 拼音/英文实体键盘输入法。

### 1.2 目标硬件与系统规范

* **目标系统**：Android 5.1 (Lollipop, API 22) 及以上，`minSdk = 21`，`targetSdk = 28`（保证老设备兼容性与权限温和性）。

* **硬件设备**：
  
  * 日本 Garaho 体系（京瓷 KYF/G'zOne 系列、夏普 SH 系列等）
  
  * 双屏/翻盖 Android 现代机型（三星 W2016-W2019、飞利浦等）

* **核心约束**：
  
  * **零触控依赖**：所有 UI（设置页、校准页、候选词表、符号表）必须完全通过物理方向键（D-Pad）与确认键（OK）进行焦点控制。
  
  * **零现代依赖膨胀**：禁止引入 Compose、Material3 等破坏低 API 兼容性的依赖，采用纯原生 `View` / `Focus` 机制。
  
  * **内存敏感**：在 512MB ~ 1GB RAM 设备上稳定运行，防止被系统 LMK（Low Memory Killer）杀死。
2. 系统总体架构 (System Architecture)

-------------------------------

系统采用“**硬件按键拦截层** $\rightarrow$ **键位动作翻译引擎** $\rightarrow$ **状态机与 RIME 核心** $\rightarrow$ **0-Touch UI 渲染**”的四层管道（Pipeline）架构。
    ┌─────────────────────────────────────────────────────────────────────────┐
    │                      1. 物理键盘中断 (Physical Key Events)               │
    └────────────────────────────────────┬────────────────────────────────────┘
                                         │ ScanCode / KeyCode
                                         ▼
    ┌─────────────────────────────────────────────────────────────────────────┐
    │              2. 键位映射引擎 (KeyMapper & Action Dispatcher)             │
    │   (解析 user_keymap.json，将物理 ScanCode 转换为抽象 InputAction)         │
    └──────────────────┬──────────────────────────────────────┬───────────────┘
                       │                                      │
             普通 T9 字符/状态迁移                              功能键 (符号/设置/模式切换)
                       │                                      │
                       ▼                                      ▼
    ┌─────────────────────────────────────┐┌──────────────────────────────────┐
    │  3. 核心算法引擎 (librime.so JNI)   ││  4. 0-Touch Focus UI 引擎        │
    │  - T9 拼音词库 (雾凇/白霜方案)       ││  - 候选词悬浮窗 (CandidateBar)   │
    │  - T9 英文联想/ASCII 直通模式        ││  - 字符矩阵选择器 (SymbolPanel) │
    │  - 方案/字典状态机                  ││  - 全物理向导设置 (SetupWizard) │
    └──────────────────┬──────────────────┘└──────────────────┬───────────────┘
                       │                                      │
                       └──────────────────┬───────────────────┘
                                          │ 提交字符 (commitText)
                                          ▼
    ┌─────────────────────────────────────────────────────────────────────────┐
    │               5. 系统接口层 (Android InputMethodService)                 │
    └─────────────────────────────────────────────────────────────────────────┘

3. 核心模块设计 (Core Modules Design)

-------------------------------

### 3.1 键位映射引擎 (KeyMapper Engine)

#### 3.1.1 抽象动作枚举 (InputAction)

为了彻底解耦各家翻盖机功能键差异，底层不硬编码任何按键逻辑，统一映射为抽象动作：
    public enum InputAction {
        // T9 基础输入
        INPUT_KEY_1, INPUT_KEY_2, INPUT_KEY_3,
        INPUT_KEY_4, INPUT_KEY_5, INPUT_KEY_6,
        INPUT_KEY_7, INPUT_KEY_8, INPUT_KEY_9, INPUT_KEY_0,

        // 状态与模式控制
        TOGGLE_LANG_MODE,   // 切换 中/英/数字/符号
        SHOW_SYMBOL_PANEL,  // 弹出全屏符号网格

        // 候选词与导航
        NAV_LEFT, NAV_RIGHT, NAV_UP, NAV_DOWN, // 方向键
        CONFIRM_SELECTION,  // 确认选中候选词 / 上屏
        BACKSPACE_DELETE,   // 退格删除

        // 自定义扩展动作
        SWITCH_RIME_SCHEMA  // 切换 RIME 方案
    }

#### 3.1.2 配置文件规范 (`garaho_keymap.json`)

配置文件用于将物理按键的 `ScanCode` 或 `KeyCode` 绑定到 `InputAction`：
    {
      "device_profile": "Kyocera_KYF31_Preset",
      "version": 1,
      "mappings": [
        { "scan_code": 2, "keycode": 9, "action": "INPUT_KEY_1" },
        { "scan_code": 3, "keycode": 10, "action": "INPUT_KEY_2" },
        { "scan_code": 228, "keycode": 0, "action": "TOGGLE_LANG_MODE" },
        { "scan_code": 18, "keycode": 0, "action": "SHOW_SYMBOL_PANEL" },
        { "scan_code": 28, "keycode": 66, "action": "CONFIRM_SELECTION" }
      ]
    }

### 3.2 0-Touch 全物理设置与按键校准向导 (Calibration Wizard)

针对无触摸屏设备，设置界面采用 **向导式链表驱动（State Machine-Driven Wizard）**：
    [启动校准] ──► [步骤 1: 按下“中英切换”键] ──► (捕获 ScanCode) ──► [震动/音效反馈]
                   ▲                                                       │
                   └─────────────── [自动跳转下一个步骤] ◄──────────────────┘

#### 逻辑实现流程：

1. `SetupWizardActivity` 拦截 `dispatchKeyEvent(KeyEvent event)`。

2. 当收到 `ACTION_DOWN` 时，读取 `event.getScanCode()` 与 `event.getKeyCode()`。

3. 提示框通过高亮动画更新，播放 50ms 轻微震动反馈。

4. 自动写入内存中的映射 Map，并触发下一阶段（如：“请按下【符号表】键”）。

5. 完成所有步骤后，直接保存为 `user_keymap.json` 并刷新 IME 引擎。

### 3.3 RIME 核心解耦与 JNI 绑定 (`librime.so`)

为了提供顶级的中文联想体验同时保持极低开销，放弃前端复杂的 Trime/Fcitx5 框架，直接集成 C++ `librime.so` 原生库。

#### 3.1.1 JNI 桥接接口

    // native-lib.cpp
    extern "C" {
        JNIEXPORT void JNICALL Java_com_garaho_ime_RimeBridge_rimeInit(JNIEnv* env, jobject thiz, jstring shared_dir, jstring user_dir);
        JNIEXPORT jboolean JNICALL Java_com_garaho_ime_RimeBridge_rimeProcessKey(JNIEnv* env, jobject thiz, jint keycode, jint mask);
        JNIEXPORT jobjectArray JNICALL Java_com_garaho_ime_RimeBridge_rimeGetCandidates(JNIEnv* env, jobject thiz);
        JNIEXPORT void JNICALL Java_com_garaho_ime_RimeBridge_rimeCommit(JNIEnv* env, jobject thiz);
    }

#### 3.1.2 T9 拼音与英文方案设计

* **中文 T9**：采用轻量化的 `rime-ice-t9`（基于雾凇拼音词库定制的 3x4 映射矩阵），利用二进制 `mmap` 技术读取词库，减少 JVM 堆内存占用。

* **英文 T9**：配置纯 ASCII 与预设英文 Trie 词典，支持在拼音模式下直接点击特定按键输入大写英文词汇。
4. UI/UX 交互规范（0-Touch Focus System）

-----------------------------------

### 4.1 候选词悬浮窗 (Candidate View)

* **布局结构**：
  
      ┌─────────────────────────────────────────────────────────────┐
      │ [中] 拼音: ni'hao                                           │
      ├─────────────────────────────────────────────────────────────┤
      │ > 1.你好 <   2.拟好   3.你好吗   4.逆航   5.泥豪  [▼ 更多]    │
      └─────────────────────────────────────────────────────────────┘

* **焦点移动原则**：
  
  * 默认索引 `0`（第一个候选词）加粗高亮背景。
  
  * 按 `D-Pad Right` / `D-Pad Left`：高亮焦点在 1~5 候选词之间移动。
  
  * 按 `D-Pad Down`：展开多行候选词网格（Grid Mode）。
  
  * 按 `1` 键或 `OK` 确认键：提交当前高亮词汇至输入框。

### 4.2 符号矩阵选择器 (Symbol Panel)

当触发 `SHOW_SYMBOL_PANEL` 动作时，弹出全屏悬浮 PopWindow，完全屏蔽底部输入框事件：
    ┌─────────────────────────────────────────┐
    │              符号选择 (常用)             │
    ├───────────┬───────────┬───────────┬─────┤
    │   > , <   │     .     │     ?     │  !  │
    ├───────────┼───────────┼───────────┼─────┤
    │     ;     │     :     │     "     │  '  │
    ├───────────┴───────────┴───────────┴─────┤
    │ [1/3 页]   (物理方向键移动焦点，OK 键选择) │
    └─────────────────────────────────────────┘

* **导航逻辑**：`D-Pad` 4 向控制焦点的二维矩阵移动，按 `OK` 键发送选中的字符并自动关闭面板。
5. 安全隔离与边界处理 (Security & Edge Cases)

------------------------------------

### 5.1 FDE/FBE 全盘加密锁屏界面处理

* **现象**：Android 重启后第一次输入密码时，系统处于预启动（Pre-boot）阶段，Data 分区未解密，第三方 IME 及 Service 无法被加载。

* **规避方案**：
  
  1. 不试图替换锁屏阶段的密码输入界面，该界面由固件层硬件安全区（TEE/Bootloader）直接处理。
  
  2. IME 服务配置 `android:directBootAware="true"`，在 Android 7.0+ 设备上确保解锁后安全拉起。
  
  3. 保留系统原生 IME 作为退路，不通过 Root 强制删除系统原装输入法。

### 5.2 死锁与异常恢复机制 (Safe Escape Hatch)

为防止用户在校准过程中把所有按键映射搞砸导致设备无法输入：

* **物理组合键复位**：长按 `物理退格键` + `#` 键 5 秒，硬编码触发重置逻辑，将 `user_keymap.json` 恢复为出厂预设。

* **ADB 紧急急救指令**：
  
      # 一键恢复系统默认输入法
      adb shell settings delete secure default_input_method
  
  
6. 开发路线图与里程碑 (Roadmap)

----------------------

| 阶段          | 目标                            | 核心产出                                                         |
| ----------- | ----------------------------- | ------------------------------------------------------------ |
| **Phase 1** | **底层编译与环境验证**                 | 搭建 API 22 NDK 工程，成功编译静态 `librime.so` 并完成 JNI 极简拼音转换 Demo。    |
| **Phase 2** | **KeyMapper & 校准向导**          | 完成 ScanCode 捕获、`garaho_keymap.json` 读写及全物理 D-Pad 导航的图形化校准界面。 |
| **Phase 3** | **0-Touch UI 与 CandidateBar** | 实现基于 `InputMethodService` 的高亮焦点候选词悬浮窗与 PopWindow 符号矩阵。       |
| **Phase 4** | **预设集成与性能调优**                 | 打包京瓷 KYF、夏普 SH、三星 W2019 等主流机型预设 JSON，优化 `mmap` 词库加载速度。       |
