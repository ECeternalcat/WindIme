# WindIme 代码库审计与三层输入结构实施建议

审计日期：2026-07-23  
审计范围：`Document/`、`app/src/main/`、`app/src/test/`、Gradle 配置、Rime 资源与预编译 JNI 库  
当前版本：`0.4.4`（`versionCode 16`）

## 1. 项目定位

WindIme 是面向 Android 5.1+ 物理九键翻盖机的 0-Touch 输入法。项目采用原生 Java/View，避免 Compose 和 Material3，目标是在低内存、无触摸或触摸不便的设备上，通过数字键、D-Pad、OK 和 BACK 完成全部输入与设置操作。

构建参数与设计文档基本一致：

- `minSdk 21`
- `targetSdk 28`
- Java 8
- 当前仅打包 `armeabi-v7a`
- Rime 不可用时回退到纯 Java T9 引擎

## 2. 当前架构

主要输入链路如下：

```text
Physical KeyEvent
    -> KeyMapper（KeyCode/ScanCode -> InputAction）
    -> GarahoImeService 状态与动作分发
    -> 当前 ImeEngine
    -> composing/candidates/commit 回调
    -> CandidateBar 与 InputConnection
```

核心模块：

- `GarahoImeService`：IME 生命周期、模式栏、按键路由、候选确认和文本提交。
- `KeyMapper`：读取内置或用户 JSON，将设备按键转换为抽象 `InputAction`。
- `ImeEngine`：中文 T9、中文 Multi-tap、英文 T9、英文 Multi-tap 的统一接口。
- `RimeEngine`：通过 Trime JNI ABI 调用预编译 `librime_jni.so`。
- `T9PinyinEngine`：不依赖 Android/Rime 的纯 Java 中文 T9 后端。
- `CandidateBar`：当前模式栏、拼音预览和候选词展示。
- `SymbolPanel`：符号及定型文模态面板。
- `GarahoPrefs`：模式循环、Multi-tap 间隔等设置的 `SharedPreferences` 持久化。

## 3. 已实现能力

- 中文 T9、中文 Multi-tap、英文 T9、英文 Multi-tap、数字五种模式。
- 用户可配置参与循环的输入模式。
- 701KC 默认按键配置及 `user_keymap.json` 校准配置。
- 模式、符号、退格、确认和四个方向键的校准向导。
- D-Pad 候选移动、OK 确认、模式快捷栏。
- 符号和定型文选择面板。
- 用户词典与常用短语的本地 JSON 存储。
- 原生 Rime 后端及纯 Java T9 fallback。
- 0-Touch 设置菜单、输入法启用和默认输入法切换入口。
- Direct Boot 声明及编辑器文本退格删除。

## 4. 文档与实现的主要差异

### 4.1 Rime 与词典

当前 Rime 路径不是原生九键方案。数字先由 Java 分词为一个最佳拼音，再把拼音字母发送给普通 `luna_pinyin` schema。因此，在进入 Rime 前已经丢失同一数字串对应的其他拼音读法。

初次审计时，内置 Rime 词典只是小型 starter 数据，不是设计文档计划的 `rime-ice-t9` 或同等级词库；该问题已由第 12 节记录的大词库集成解决。纯 Java拼音和英文词典仍属于轻量级内置数据，仅作为 fallback 使用，预测能力有限。

### 4.2 候选 UI

当前 `CandidateBar` 只有模式栏或“拼音预览 + 候选行”两种显示状态。所谓候选网格展开仍向单行 `LinearLayout` 添加项目，不是真正的多行网格，也没有二维候选导航。

数字 `1` 固定提交引擎候选 0，只有 OK 会提交当前 UI 高亮项，与设计文档“1 或 OK 提交当前高亮项”不完全一致。

### 4.3 设置与快捷行为

- 文档推荐的默认模式循环是中文 T9、英文 Multi-tap、数字；当前默认是中文 T9、英文 T9、数字。
- 模式键长按进入数字模式、再次长按恢复，以及数字输入后自动回退尚未实现。
- “按键反馈”和“首字母自动大写”可以保存，但输入服务和引擎没有消费这些设置。
- 用户词典没有导入/导出。
- 只有一个 701KC 机型预设，尚无 KYF、SH、W2019 和通用预设选择。
- 设置页面实际使用 `SharedPreferences`，不是文档描述的统一 JSON 设置文件。

## 5. 已确认问题

### P0：会直接影响三层输入功能

1. Rime 用户词候选索引错位

   `RimeEngine` 把用户词插到 UI 候选前面，但确认时仍把 UI 索引直接传给原始 Rime 候选。用户词置顶后，显示项和实际提交项可能不一致。三层结构会更频繁地重排候选，因此必须先解决候选身份与提交目标不一致的问题。

2. 候选展示与选择状态耦合不足

   `CandidateBar` 保存自己的 `focusIndex`，引擎只保存候选文本列表，服务再把 UI 索引传回引擎。加入第二层拼音选项后，如果仍只依靠裸索引，实时刷新第三层时很容易出现焦点越界、选择错词或刷新后焦点指向不同对象。

3. 当前上下键已经承担候选“展开/收起”

   新需求要求第二层与第三层之间随时跳转。现有 `NAV_UP/NAV_DOWN` 行为必须改为显式的层级导航状态，否则会与旧的 `expandGrid()` 语义冲突。

4. `PinyinLayer` 尚未接入运行时（审计时问题，现已解决）

   初次审计时，工作区已有 `PinyinLayer.java` 和 `PinyinLayerTest.java`，能够为数字串生成“锁定前缀 + 尾部读音选项”，例如 `24 -> ai, bi, ci, a, b, c`，但中文 T9 引擎、Rime、IME Service 和 CandidateBar 均未调用它。本轮已将其正式接入，具体结果见第 10 节。

   原测试中 `twoDigitsYieldSyllablesPlusLetters()` 对 `tailDigits` 的断言是恒真表达式；本轮已改为验证真实尾串和完整选项顺序。

### P1：输入可靠性问题

1. 校准保存后只刷新向导内部的 `KeyMapper`，正在运行的 IME Service 不会立即读取新映射，通常需要服务重建。

2. 安全复位没有正确实现“退格 + # 同时长按 5 秒”。当前逻辑可能在两键相隔 50ms 时立即复位，也可能单独长按退格触发复位。

3. 符号面板直接处理原始 Android KeyCode，没有经过 `KeyMapper`，校准后的特殊方向键和确认键可能在该面板失效。

4. 定型文编辑时“复制”覆盖了原来的 positive button，导致已有短语不能保存修改。

5. “清空用户词库与历史”只删除 Rime 用户目录，不会清除应用自己的 `user_dict.json`。

### P2：功能完整度问题

- 符号数据定义了三页，但没有翻页入口。
- 长按 `*` 打开定型文尚未实现。
- 用户词典和短语写入不是原子替换，存储中断可能留下损坏 JSON。
- 用户词典和短语缺少 UI/设备级集成测试。
- Rime 生命周期没有显式执行同步和关闭。
- APK 只有 `armeabi-v7a`，不能安装到只支持 64 位 ABI 的设备。

## 6. 三层输入结构需求解释

本次需求建议定义为中文 T9 输入态下的固定三层结构：

```text
第一层：现有模式与数字/拼音组合预览
第二层：当前可选拼音读音
第三层：与第二层当前读音对应的汉字或词语候选
```

示例：输入数字 `2, 4` 后：

```text
第一层：[中] 24 / 当前组合
第二层：> ai <  bi  ci  a  b  c
第三层：> 爱 <  挨  矮  ...
```

建议的物理键语义：

- `UP/DOWN`：在第二层和第三层之间切换活动层。
- `LEFT/RIGHT`：移动当前活动层的高亮项。
- `OK`：确认当前活动层的高亮项。
- 第二层高亮变化：立即重算并刷新第三层，不要求先按 OK。
- 第二层按 OK：锁定完整音节，并继续处理后续数字或进入第三层。
- 第三层按 OK：提交当前候选词。
- `BACKSPACE`：优先删除最后一个输入数字或解除最近一次音节锁定；没有组合内容时才删除编辑器文本。
- `BACK`：先退出三层组合状态回到模式栏，再次按下交给系统关闭输入法。

单字母 `a/b/c` 选项应被视为“未完成音节的继续输入路径”，完整音节 `ai/bi/ci` 则可以立即驱动第三层候选。两类选项在模型中应有明确类型，不能只靠字符串内容猜测。

## 7. 建议的数据与状态模型

不建议继续把所有选择状态放在 `CandidateBar` 内。三层结构至少需要由控制器或中文 T9 会话统一维护以下状态：

```text
digitBuffer
lockedSyllables
tailDigits
pinyinOptions
selectedPinyinIndex
wordCandidates
selectedCandidateIndex
activeLayer (PINYIN / CANDIDATE)
```

拼音选项建议使用结构化对象：

```text
PinyinOption {
    text
    type: COMPLETE_SYLLABLE | PARTIAL_LETTER
    sourceDigits
}
```

候选项也应携带来源和稳定提交信息，而不是只保存字符串：

```text
CandidateItem {
    text
    source: USER | RIME | BUILTIN
    backendIndex
}
```

这样可以解决用户词置顶后的 Rime 索引错位，并允许第二层实时变化时安全地重建第三层。

## 8. 实施顺序建议

建议先修复与三层结构直接冲突的 P0 问题，再添加功能；不需要先清完所有 P1/P2 问题。

推荐顺序：

1. 修复 Rime/用户词候选身份与提交索引，建立结构化候选项。
2. 修正并提交 `PinyinLayer` 的测试，使 `24`、`64`、`64426`、不完整音节和退格行为具有确定结果。
3. 把中文 T9 会话状态从 CandidateBar 的纯 UI 索引中分离，明确活动层和两层焦点状态。
4. 实现三层 CandidateBar 布局及 `UP/DOWN/LEFT/RIGHT/OK` 状态机。
5. 将第二层读音选择接入纯 Java T9，做到高亮变化实时刷新第三层。
6. 再接入 Rime；若当前“单一路径拼音重放”无法保留 T9 歧义，应优先保证 Java 后端行为正确，再决定是否更换为真正的 Rime T9 schema。
7. 添加服务路由和 UI 状态测试，并在 701KC 真机验证按键重复、长按、焦点切层和提交行为。
8. 随后修复校准热刷新、安全复位和符号面板按键路由等 P1 问题。

原因是三层结构不是单纯增加一行 View，而是改变中文输入会话、候选身份、方向键语义和确认流程。如果直接叠加在现有裸索引与单行 CandidateBar 上，已知的候选错位问题会扩散到第二层和第三层，之后需要再次重写导航与提交逻辑。

## 9. 当前验证结果

- `gradlew testDebugUnitTest --no-daemon`：17 个测试套件，81 项测试，0 失败。
- `gradlew assembleDebug`：成功。
- 构建仅报告 JDK 21 编译 Java 8 source/target 的弃用警告。
- Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 10. 三层结构实施结果

本轮已按第 8 节的建议完成三层中文 T9 主链路：

- 新增 `PinyinSession`，统一管理已锁定音节、当前尾部数字、拼音选项、当前读音和确认状态。
- 新增 `LayeredPinyinEngine`，使纯 Java T9 与 Rime 共用第二层预览和确认接口。
- `PinyinLayer` 已正式接入输入运行时；输入 `24` 时第二层按固定顺序显示 `ai, bi, ci, a, b, c`。
- `CandidateBar` 已形成组合预览、拼音读音、词候选三行结构。
- `UP/DOWN` 在拼音层和词候选层之间移动；`LEFT/RIGHT` 移动当前层高亮；`OK` 锁定完整音节或提交词候选。
- 第二层完整音节的高亮变化会立即重算第三层，不要求先按 OK。
- `a/b/c` 等未完成字母不会产生伪词候选；用户主动选中后继续输入，下一数字会沿该字母路径收敛。
- 完整音节确认后，下一数字开始新音节；词典中存在整词时，后续音节默认项优先匹配该整词。
- 第三层候选刷新前会重置其焦点，避免新候选沿用旧索引。
- 空候选层不可进入，三层状态下方向键和无效确认不会泄漏给宿主编辑器。
- Rime 重放保留拼音音节分隔符，避免 `xi'an` 被错误解释为 `xian`。
- Rime 用户词置顶后，提交会按候选文本映射回 native 索引；纯用户词直接提交，不再把 UI 索引错误传给 Rime。

新增测试覆盖 `24` 选项顺序、实时切换 `ai/bi/ci` 候选、字母路径约束、完整音节锁定、多音节整词优先和跨锁定边界退格。

仍需在 701KC 真机验证：三行高度是否适合实际屏幕、物理键重复事件、Rime schema 部署完成后的候选表现，以及长词候选的截断可读性。

## 11. 结论

现有代码已经完成三层输入结构的主链路：抽象按键、分层拼音会话、读音实时预览、候选提交和物理层间导航已连通。实现保留原有五态模式及纯 Java fallback，并同步修复了会直接影响该功能的 Rime 候选索引问题。

后续应优先进行 701KC 真机交互验证，再处理校准热刷新、安全复位和符号面板按键路由等 P1 问题。

## 12. rime-ice 大词库集成

本轮已移除原有 `luna_pinyin` starter 词典，改为集成你提供的、已清理注释的雾凇拼音常用中文词库：

- 8105 常用字表
- `base` 基础及双字词库
- `ext` 多字扩展词库
- `others` 容错和杂项读音

同时加入独立的 `en + en_ext` 英文 Rime 词库资源，但不将它混入中文候选列表；当前 Java English T9 仍是默认英文输入后端。

内置中文源数据约 28.5 MB，主要是 `8105 + base + ext + others`；英文 `en + en_ext` 约 0.4 MB。Tencent 大词库不再随 APK 打包，也不在默认 import 列表中。未启用可选的 41,448 生僻字表、Emoji、OpenCC、Lua 和语法模型。这些模块会增加 APK、部署和运行内存，并且当前预编译 `librime_jni.so` 没有证明包含 Lua 或 octagram 插件。

WindIme 使用自己的极简 `rime_ice.schema.yaml` 驱动中文词典，并提供独立的 `rime_ice_en.schema.yaml`。中文仍由 Java 三层拼音会话确定读音，再交给 Rime 完成大词库检索。来源、版本、上游地址、文件哈希、修改内容和 GPL v3 全文均随 APK 打包。

大词库首次部署不再阻塞 IME 主线程：应用立即使用纯 Java T9 fallback，后台解包并编译 rime-ice；确认 schema 就绪后，在下一次输入会话切换到 native Rime。针对低性能设备，部署等待上限为 30 分钟；I/O 失败、超时或 ABI 不支持时继续使用 Java fallback。IME Service 销毁时会中断后台等待。

Tencent 后续建议作为联网下载的可选扩展词库：默认不加载、不占用首装 APK 体积，用户在设置中主动下载后再写入 `rime_user` 或独立扩展目录并触发部署。移除 Tencent 后 Debug APK 约 16.7 MB。安装后还需要约 29 MB 的共享源词典空间及额外的 Rime 编译产物空间，实际占用需在 701KC 真机部署后测量。

设备升级时 `RimeData` 会清理旧共享 schema 后安装新快照，但不会删除独立的 `rime_user` 用户学习数据。

## 13. 输入结束后的按键拦截修复

曾出现输入框退出后，IME 日志仍显示类似 `keyCode=23 scan=28 -> CONFIRM_SELECTION`，随后确认键、菜单或其他应用焦点操作像是失效。原因是 `InputMethodService` 可能收到输入视图结束后的迟到硬件事件，而 `GarahoImeService.onKeyDown()` 原先仍会继续执行 `KeyMapper` 和动作分发。

本轮增加输入会话与输入视图双重生命周期门控：只有 `onStartInput()` 和 `onStartInputView()` 均已建立、且尚未调用 `onFinishInputView()`/`onFinishInput()` 时才解析和消费物理按键。输入结束时还会清理当前引擎组合状态并关闭符号面板，防止旧会话状态污染下一次输入。

新增 `InputEventGateTest` 覆盖会话结束、视图结束和正常输入三种状态。该修复无法替代 701KC 真机验证，仍需测试输入框快速切换、返回桌面、启动菜单和多应用连续切换等迟到事件场景。

## 14. 返回键兼作退格

按键校准允许把机身 BACK 键绑定为 `BACKSPACE_DELETE`。Service 现在会先经过 `KeyMapper` 再处理 BACK，因此该绑定能够真正生效：

- 有拼音组合时，优先删除组合内容。
- 编辑框有选区或光标前有字符时，删除对应文本。
- 编辑框明确为空时，再按一次关闭输入法。
- 同一次按键的 KEY_UP 也由 IME 消费，避免关闭输入法后立即触发宿主返回。
- 输入法关闭后，生命周期门控释放后续 BACK，恢复系统返回行为。
- 光标位于非空文本开头时保持输入法，不会因为无法向前删除而误关闭。
- 密码框等拒绝返回前后文本的特殊编辑器采用保守策略，不把“无法读取”当成“内容为空”。

按键设置页和校准向导的退格步骤均增加了日系机共享返回键说明。校准映射会在下一次 `onStartInput()` 自动 reload。

同时修复安全复位组合：不再在退格和 `#` 相隔 50ms 时立即重置，也不会单独长按退格触发；现在必须真正同时按住两键 5 秒。

## 15. 桌面图标启动分流

桌面图标改由透明 `LauncherActivity` 处理，不再直接显示完整设置页：

- WindIme 未启用：进入“设置默认输入法”两步引导页。
- WindIme 已启用但不是当前输入法：只调用系统输入法选择框，不显示完整设置页。
- WindIme 已启用且当前正在使用：进入完整设置页。

系统输入法设置中的 WindIme 齿轮通过 `method.xml/settingsActivity` 直接进入 `SettingsActivity`，不执行桌面图标分流。这样日系系统自动切回 iWnn 后，用户点击 WindIme 图标即可快速重新选择，同时仍能保留 iWnn 处理 PIN/预启动输入。

返回键兼退格说明已从按键设置列表移除，只在校准向导的“退格删除”步骤显示。提示使用独立深色背景、白色粗体，不再与已捕获按键状态文本混排。

## 16. 可靠性与可观测性批次（2026-07-24）

本轮按改进建议 §3、§4、§5、§7 推进，并修复三处既有缺陷。纯 Java 逻辑均有 JUnit 覆盖，`gradlew testDebugUnitTest` 共 28 个套件、147 项、0 失败；`assembleDebug` 成功。

### 16.1 设置落地（§4）

新增纯逻辑 `EnglishCapitalization`（句首判定 + 首字母大写）与 `KeyFeedback`（震动 / 声音 / 无三档）。`GarahoImeService` 在 `onKeyDown` 消费点（`repeatCount==0`）触发反馈；IME 消费按键已抑制平台按键音，反馈为唯一来源。`onCommit` 在英文 T9 / 英文 Multi-tap 且开启首字母大写时，按光标前文本判定句首并大写。设置改动在下次 `onStartInput` 生效。

### 16.2 候选翻页（§3）

新增纯逻辑 `CandidatePagination`（页对齐：窗口始终从 `page*window` 开始，焦点越界跳整页）与位置指示器。`CandidateBar` 的 `visibleStart` 改为页对齐，`setCandidates` 刷新即重置候选焦点为 0（杜绝提交旧索引），底部新增 `n/total` 位置指示（仅翻页时显示）。

### 16.3 用户数据可靠性（§5）

新增 `AtomicStore`（临时文件 + `sync()` + rename 原子写、损坏文件移名 `.corrupt` 备份）与 `StoreResult`（空 / 过长 / 重复 / IO 校验）。`UserDictionary`、`PhraseStore` 全量改为原子写入、加入校验与去重、损坏时保留原文件并清空内存、新增 `exportTo` / `importFrom`（写入 `getExternalFilesDir`，免权限）。`ResetSettingsActivity` 扩展为五档独立清除（按键映射 / Rime 学习 / 用户词典 / 定型文 / 全部设置）。修复定型文编辑对话框“复制覆盖保存”的缺陷：编辑态改为保存(正)/复制(中)/删除(负)，BACK 取消。

### 16.4 Rime 生命周期与可观测性（§7）

新增 `RimeLifecycle`：进程级单调会话编号、单槽并发守卫（`beginSession`/`endSession`，CAS，`finally` 必释放）、结构化日志 `Rime[#id] event: detail`。`GarahoImeService.prepareRimeInBackground` 显式标注为 native Rime 唯一所有者：Service 重建或并发重试时若已有会话在跑则拒绝；native 已启动时走 `RimeEngine.tryReattach` 重新挂载，不再二次 `startupRime`、不触碰其文件；schema 就绪后调 `RimeEngine.syncUserData()` 同步学习；任一失败保留 Java T9 fallback 并置 FAILED，下次全新进程自动重试。`onDestroy` 只中断后台线程，从不调用 `exitRime`（native 为进程级资源）。

### 16.5 三处缺陷修复

1. 候选宽度自适应失效：`fae0ae3` 的按文本长度比例权重被三层重写覆盖回 `1f`；已恢复 `Math.max(1f, label.length())`，长词不再被截断。
2. 快捷菜单绑定返回键无反应：菜单判断返回仅用 `keyCode == KEYCODE_BACK`，而绑定退格的物理返回键在这类日系机上经 KeyMapper 为 `BACKSPACE_DELETE`，被 `handleAction` 默认分支静默吞掉。已改为 `keyCode == BACK || action == BACKSPACE_DELETE`，快捷菜单与符号面板同步修复。
3. T9 移动读音需按 OK：根因在 `PinyinSession`——`processDigit` 仅在 `confirmed` 时锁定音节，手动 `preview` 选中的完整音节未被携带，下一数字重新分词又回到默认读音。新增 `completeSelectionPinned`：预览完整音节时置位，下一数字自动锁定；自动默认不锁定，故 `426→hao`、`64→ni` 等多字母单音节仍正常组合。

三处仍需 701KC 真机验证：候选宽度比例与长词截断、绑定返回键在快捷菜单子页/主页的导航、连打 `96 24 64` 切读音的实际候选。

## 17. 全屏输入模式与可校准的回车/关闭动作（2026-07-24）

### 17.1 全屏输入模式（自绘，非原生提取）

原生 `InputMethodService` 全屏提取模式（`onEvaluateFullscreenMode`）在 701KC 这类物理键翻盖机上不工作：强制开启后框架既不渲染候选栏、也不让 `onKeyDown` 消费按键（`inputViewActive` 未置真 → `InputEventGate` 拦截一切），表现为"输入法不出现、数字直上屏"。故改为自绘：开关开启时把输入视图本身做成全屏——`rootContainer` 白底 + `setMinimumHeight(屏高)`，顶部白色文本镜像区（显示 `光标前 │ 光标后`，由 `onStartInputView/onCommit/onUpdateSelection` 刷新），底部钉住既有候选栏。按键仍走 `onKeyDown`，T9/震动/菜单全不变；文本仍增量上屏。设置项 `fullscreen_input`，默认关。符号/快捷面板覆盖视图改为 `Gravity.BOTTOM`，全屏下落在候选栏位置。

**修复**：开关切换后输入视图不重建导致"关不掉全屏"——`onCreateInputView` 只在进程启动时读一次设置。已抽出 `buildInputView()`，在 `onStartInputView` 检测到 `isFullscreenInputEnabled() != inputViewFullscreen` 时重建（重建时重置 `rootContainer` 的背景与 `minimumHeight`，并丢弃旧的面板实例）。

### 17.2 可校准动作 ENTER / DISMISS_IME

日系机 BACK 常作退格、OK 常作回车，两者都被占用，缺少"关闭输入法"和"纯换行"的途径。新增两个**可跳过**的校准动作：

- `ENTER`（回车/换行）：若正在组词先提交首候选（不丢输入），再插入 `\n`。与 OK 区分（OK 触发编辑动作如发送/完成）。建议绑 `*`（701KC 丝印即回车）。
- `DISMISS_IME`（关闭输入法）：先提交组词、reset 引擎、关面板，再 `requestHideSelf` 收起 IME/退出全屏，文本保留。

`*`/`#` 在 `KeyMapper.isReservedFor` 本就允许重绑；用户把 `*` 绑到 `ENTER` 后 `resolve()` 用用户配置覆盖默认的 `INPUT_KEY_STAR`。未绑定时默认行为不变。校准向导 `STEPS` 末尾新增这两步（可跳过），输入态与模式条态都接。

### 17.3 其它随附修复

- 候选行改用 `HorizontalScrollView` + 单格 `WRAP_CONTENT` + `setMaxWidth(屏宽)`：所有长词/长句完整显示，仅单个超屏句在屏幕末端省略；◀▶ 移焦点后 `smoothScrollTo` 跟随；位置指示在实际可滚动时才显示。
- 首次按键配置提示改用透明 `KeymapPromptActivity` 承载（原 `TYPE_SYSTEM_ALERT` 无 `SYSTEM_ALERT_WINDOW` 权限会 `BadTokenException`）。
- 无 composing 时 ◀▶ 经 `sendDownUpKeyEvents(DPAD_LEFT/RIGHT)` 移动宿主光标；有 composing 时仍导航候选。

仍需真机验证：全屏文本镜像同步、`DISMISS`/`ENTER` 绑定键的可用性、`sendDownUpKeyEvents` 在各宿主编辑框的光标移动效果。
