# NP701KC iWnn IME、Softkey Guide 与系统 Mail 完成输入协议逆向报告

## 1. 调查目的

目标问题是：701KC 的系统 Mail/短信编辑页中，iWnn IME 空闲时底部中央 Softkey Guide 显示“完成”，按中央 OK 后会保存当前编辑内容并退出编辑页；WindIme 的中央位置显示实心方块，按 OK 也不能完成保存。

本报告只调查接口和实现，不修改 WindIme 代码。调查对象包括：

- `Document/apks/701kc/iWnnIME.apk`
- 设备 `/system/app/iWnnIME/arm/iWnnIME.odex`
- 设备 `/system/framework/com.nextfp.android.util.jar` 及其 ODEX
- 设备 `/system/priv-app/fpmail/fpmail.apk` 及其 ODEX
- 已连接的 `NP701KC`（Android 5.1.1/API 22）运行时 `dumpsys`

## 2. 结论摘要

问题不是单一的 `imeOptions` 或按键透传问题，而是由三个相互独立的层次组成：

1. **底部文案显示**：iWnn 主动调用厂商共享库 `com.nextfp.android.util.NfpSoftkeyGuide`，在自己的 IME `Window` 上设置中央软键文案。系统不会仅凭 `EditorInfo.imeOptions` 自动替第三方 IME 把实心方块改成“完成”。
2. **中央键事件只是第一阶段**：中央 OK 时 iWnn 调用 `performPrivateCommand("IME_User_Action_CSKKey", null)`，但 Mail 的 Kyocera parser **不认识这个字符串**，它会落入默认枚举 `g.h`。即使调用返回 `handled=true`，也不表示 Mail 执行了保存。
3. **真正完成命令是第二阶段 `Finish_IME`**：iWnn 先把 `mIsActiveFinish` 设为 `true` 并结束输入视图；随后在 `IWnnLanguageSwitcher.onFinishInputView()` 调用 `performPrivateCommand("Finish_IME", bundle)`，其中 `bundle["isActiveFinish"]=true`。Mail 将 `Finish_IME` 映射为 `g.a`，正文框通常调用 `pk.b() -> MailComposerEditActivity.onBackPressed()`，完成正文回传并退出。
4. **标准 Android 编辑器动作**：当 `imeOptions` 的动作低字节是 Done/Go/Next/Search/Send 等时，iWnn 在部分分支还会调用 `InputConnection.performEditorAction(action)`。它不是 Mail 厂商完成协议的替代品。

对当前 Mail 正文编辑框，设备实测 `privateImeOptions=sbm_y_mail`、`imeOptions=0x52000006`，低字节 `0x06` 即 `IME_ACTION_DONE`，同时包含 `IME_FLAG_NO_ENTER_ACTION (0x40000000)`。`sbm_y_mail` 使 iWnn 启用专门的 SoftBank Mail 兼容逻辑。真正使 Mail 保存退出的接口是 `performPrivateCommand("Finish_IME", Bundle{"isActiveFinish": true})`，不是 `CSKKey`，也不是 `performEditorAction(DONE)`。

## 3. 样本与设备信息

### 3.1 iWnn 包信息

- 包名：`jp.co.omronsoft.iwnnime.ml`
- IME Service：`jp.co.omronsoft.iwnnime.ml.standardcommon.IWnnLanguageSwitcher`
- 版本：`2.4.0.jp-Kyocera-sb0714-EPS`
- `versionCode=2400`
- `targetSdkVersion=18`
- 系统路径：`/system/app/iWnnIME/iWnnIME.apk`
- 优化代码：`/system/app/iWnnIME/arm/iWnnIME.odex`
- APK 本身没有 `classes.dex`，实际方法体位于预优化 ODEX，所以仅对 APK 使用普通 JADX 会看不到 Java 代码。
- Manifest 声明 `<uses-library android:name="com.nextfp.android.util"/>`。

### 3.2 系统 Mail 信息

- 包名：`jp.kyocera.fpmail`
- 版本：`2.1 build#163`
- `targetSdkVersion=8`
- 系统特权应用路径：`/system/priv-app/fpmail`
- 本次目标页面：`jp.kyocera.fpmail/.ui.composer.MailComposerEditActivity`
- 焦点控件：`com.access_company.android.nfcommunicator.UIUtl.PreImeEditText`
- 控件 ID：`app:id/body_edit_text`

### 3.3 当前正文框 EditorInfo 实测

由 `adb shell dumpsys input_method` 取得：

```text
inputType=0x60001
imeOptions=0x52000006
privateImeOptions=sbm_y_mail
actionLabel=null
actionId=0
packageName=jp.kyocera.fpmail
fieldId=2131689980
```

`imeOptions & 0xff = 6`，即 `EditorInfo.IME_ACTION_DONE`。高位还包含厂商/框架标志，其中包括 Android 的 `IME_FLAG_NO_ENTER_ACTION`；iWnn 的完成处理并不是简单地把中央键转换成换行。

## 4. 厂商 Softkey Guide API

### 4.1 共享库位置与加载方式

设备注册文件把 `com.nextfp.android.util` 作为共享 Java 库提供给应用：

```text
/system/etc/permissions/com.nextfp.android.util.xml
/system/framework/com.nextfp.android.util.jar
/system/framework/arm/com.nextfp.android.util.odex
```

JAR 只有 Manifest，代码在 ODEX/Dalvik cache 中。iWnn 通过 Manifest 的 `uses-library` 加载它。该库本身没有签名、UID 或权限检查；主要限制是只有相应厂商固件才存在这些类和经过扩展的 `Window`。

### 4.2 `NfpSoftkeyGuide` 的真实公共接口

此前笔记中的 `setSoftKey(index, text)` 不存在。实际类为：

```java
public class com.nextfp.android.util.NfpSoftkeyGuide {
    public static final int INDEX_CSK = 0;
    public static final int INDEX_SK1 = 1;
    public static final int INDEX_SK2 = 2;

    public static final String SK_AUTO = "@SK_AUTO";
    public static final String SK_AUTO_ENTER = "@SK_AUTO_ENTER";
    public static final String SK_AUTO_MENU = "@SK_AUTO_MENU";
    public static final String SK_AUTO_SELECT = "@SK_AUTO_SELECT";

    public static NfpSoftkeyGuide getSoftkeyGuide(android.view.Window window);
    public boolean getEnabled(int index);
    public CharSequence getText(int index);
    public void hide();
    public void invalidate();
    public void setEnabled(int index, boolean enabled);
    public void setText(int index, int resourceId);
    public void setText(int index, CharSequence text);
    public void show();
}
```

索引语义：

| 索引 | 常量 | 含义 |
|---:|---|---|
| 0 | `INDEX_CSK` | 中央软键/中央 OK（Center Soft Key） |
| 1 | `INDEX_SK1` | 左软键 |
| 2 | `INDEX_SK2` | 右软键 |

### 4.3 底层调用链

`getSoftkeyGuide(Window)` 的反汇编等价逻辑：

```java
if (window == null) return null;
KCfpSoftkeyGuide base = window.getSoftkeyGuide();
if (base == null) return null;
NfpSoftkeyGuide guide = (NfpSoftkeyGuide) base.getCarrierSoftkeyGuide();
if (guide == null) {
    guide = new NfpSoftkeyGuide(window, base);
    base.setCarrierSoftkeyGuide(guide);
}
return guide;
```

`NfpSoftkeyGuide` 只是包装器，其方法转发到 Kyocera 框架类 `jp.kyocera.kcfp.util.KCfpSoftkeyGuide`：

- `setText(...) -> KCfpSoftkeyGuide.setText(...)`
- `setEnabled(...) -> KCfpSoftkeyGuide.setEnabled(...)`
- `show()/hide()/invalidate()` 同名转发
- `setCarrierSoftkeyGuide(Object)` 是底层缓存包装器的方法，不是应用应主动调用的公共 NFP API

注意：标准 Android SDK 的 `android.view.Window` 没有 `getSoftkeyGuide()`。这是 701KC framework 对 `Window` 的厂商扩展。使用反射可以避免普通 Android 构建直接依赖厂商 stub，但运行时仍必须检查类、方法和返回值是否存在。

## 5. iWnn 如何设置 Softkey Guide

### 5.1 获取的是 IME 自己的 Window

`IWnnImeJaJp.setSoftkeyText(...)` 的调用链是：

```text
IWnnLanguageSwitcher.getWindow()        // InputMethodService 的 Dialog
  -> Dialog.getWindow()                 // IME Window
  -> NfpSoftkeyGuide.getSoftkeyGuide(window)
```

随后设置：

```text
setText(1, sk1Text)
setText(2, sk2Text)
setText(0, centerText)
invalidate()
```

因此必须操作 IME 的窗口，而不是宿主 Mail Activity 的窗口。更新完三个位置后需要 `invalidate()`，否则底部 Guide 不一定重绘。

### 5.2 “完成”标签与状态机

iWnn 资源包含：

```text
0x7f070063 ti_softkey_completion_txt
0x7f070064 ti_softkey_menu_txt
0x7f070065 ti_softkey_close_txt
0x7f070066 ti_softkey_change_txt
0x7f070067 ti_softkey_delete_txt
0x7f070070 ti_softkey_confirm_txt
```

日文资源中 `ti_softkey_completion_txt` 与触屏 Enter 的 `ti_enterkey_done` 指向同一字符串值，即用户看到的“完成”。

`IWnnImeJaJp.setSoftKey()` 会依据以下状态重新计算三个软键文本：

- 当前是否有 composing 文本
- 候选是否聚焦
- 当前 selection mode
- 是否在符号/颜文字列表
- 当前转换、预测、英数假名状态
- `CommonImeOptionManager.isMinimumCheck()`
- 子菜单和符号列表是否启用

空闲状态最终进入 `setDefaultSoftKey(boolean)`。其中传入的布尔值控制中央文案：需要结束输入时取得 `ti_softkey_completion_txt`，否则中央文案为空。候选选择、转换等状态会改用“确定/变换/取消”等文案。因此不能在整个输入会话中永久显示“完成”；必须在 composition/candidate/panel 状态改变后同步刷新。

另外，iWnn 的 `setSoftKey()` 在 `EditorInfo.imeOptions == 0` 时直接返回。对本次 Mail 正文框 `imeOptions` 非零，所以会正常设置 Guide。

### 5.3 实心方块的含义

WindIme 所见实心方块是厂商导航栏在未获得合适中央 Softkey Guide 文本时的占位/图标表现。它不是 `IME_ACTION_NONE` 的标准 Android 展示，也不能证明宿主会自动接管中央 OK。iWnn 能显示“完成”，直接原因是它主动对索引 0 调用了 `NfpSoftkeyGuide.setText()` 并 `invalidate()`。

## 6. iWnn 的中央 OK 完成调用链

### 6.1 物理键进入 `processImeHide()`

`IWnnImeJaJp.handleEvent(OpenWnnEvent)` 在隐藏请求路径中检查原始按键：

```text
event.keyEvent.getKeyCode() == 23  // KEYCODE_DPAD_CENTER
```

中央键满足条件时调用 `IWnnImeJaJp.processImeHide()`，然后清除 `IWnnLanguageSwitcher` 的 hide-request 标记并消费事件。

这也解释了为什么不能简单把所有 OK 都视为完成：在有 composing 文本、候选聚焦或 selection mode 时，事件会先由其他分支完成候选确认/转换；只有进入“结束 IME”的状态才走 `processImeHide()`。

### 6.2 `processImeHide()`：完成流程的第一阶段

核心反汇编等价流程如下。ODEX 的 `packed-switch` payload 未出现在 `oatdump` 文本中，因此下列两类分支的精确 case 值不能只凭该文本全部还原；但两个分支共有的私有命令及调用顺序是确定的：

```java
InputConnection ic = getInputConnection();
EditorInfo info = getCurrentInputEditorInfo();
int action = info.imeOptions & 0x400000ff;

switch (action) {
    // 某些无标准 action/特殊 action 分支：
    requestActiveFinish();
    sendIMEFinishEventInCSKKey(ic);
    inputViewManager.closing();
    requestHideSelf(0);
    return;

    // 允许标准 editor action 的分支：
    requestActiveFinish();
    sendIMEFinishEventInCSKKey(ic);
    ic.performEditorAction(action);
    return;
}
```

其中还有 `jp.softbank.mb.passwordmanager` 的单独兼容分支，但与 Mail 无关。

关键顺序非常明确：

1. `requestActiveFinish()`
2. `performPrivateCommand("IME_User_Action_CSKKey", null)`
3. 视带 flag 的 `imeOptions` 分支决定 `performEditorAction(action)` 或 `closing()+requestHideSelf(0)`

`CSKKey` 发生在标准 editor action 或隐藏 IME 之前。当前 Mail 掩码后的值是 `0x40000006`，不是裸 `6`；不能在没有 payload 证据时断言它一定进入 `performEditorAction()` 分支。更重要的是，Mail parser 不认识 `IME_User_Action_CSKKey`，所以它不是保存兼容点；它只是 iWnn 内部完成时序中的前置通知。

### 6.3 私有命令的精确实现

`CommonImeOptionManager.sendIMEFinishEventInCSKKey(InputConnection)`：

```java
sendToApplication(ic, "IME_User_Action_CSKKey", null);
```

基类 `PrivateImeOptionManagerBase.sendToApplication(...)`：

```java
if (ic != null) {
    ic.performPrivateCommand(action, bundle);
}
```

同一协议族还包括：

- `IME_User_Action_BackKey`
- `IME_User_Action_EditCancel`

它们分别由返回键结束和编辑取消路径发送，不能与中央 OK 的 `CSKKey` 混用。

### 6.4 `onFinishInputView()`：真正完成流程的第二阶段

`processImeHide()` 开头调用的 `requestActiveFinish()` 只做一件事：

```java
mIsActiveFinish = true;
```

输入视图随后结束时，`IWnnLanguageSwitcher.onFinishInputView(boolean)` 在重置各 option manager 后执行：

```java
if (mInputConnection != null && !MushroomControl.getInstance().isStartupMushroom()) {
    Bundle bundle = new Bundle();
    bundle.putBoolean("isActiveFinish", mIsActiveFinish);
    mInputConnection.performPrivateCommand("Finish_IME", bundle);
    mIsActiveFinish = false;
}
```

因此中央 OK 的完整时序是：

```text
KEYCODE_DPAD_CENTER
  -> processImeHide()
  -> requestActiveFinish()                  // mIsActiveFinish=true
  -> performPrivateCommand("IME_User_Action_CSKKey", null)
  -> requestHideSelf()/结束输入视图
  -> onFinishInputView()
  -> performPrivateCommand(
         "Finish_IME",
         Bundle{"isActiveFinish": true})   // Mail 真正识别的完成命令
```

这解释了实测现象：`IME_User_Action_CSKKey` 可以返回 `handled=true`，但不保存；真正产生保存/退出的是 IME 结束生命周期中后发的 `Finish_IME`。

### 6.5 标准 Enter 处理是另一条路径

`IWnnImeBase.processKeyEventEnter()` 会把 `imeOptions` 与 `0x400000ff` 做掩码：

- 匹配标准 action 时调用 `InputConnection.performEditorAction(action)`
- 普通多行输入时 `commitText("\n", 1)`
- 对较老目标应用还可能发送兼容 `KEYCODE_ENTER`

该方法没有发送 `IME_User_Action_CSKKey`。所以物理 Enter、中央 OK、候选确认在 iWnn 中是不同语义。

## 7. `privateImeOptions=sbm_y_mail` 的作用

`IWnnLanguageSwitcher.onStartInputView()` 会初始化多个厂商兼容管理器，其中包括：

```text
EMailImeOptionManager.init(EditorInfo)
CommonImeOptionManager.init(EditorInfo)
PhoneBookImeOptionManager.init(EditorInfo)
PostalAppsImeOptionManager.init(EditorInfo)
LimitedManager.init(EditorInfo)
```

`EMailImeOptionManager.init()` 检查：

```java
containsPrivateImeOptions("sbm_y_mail", editorInfo.privateImeOptions)
```

命中后 `mIsEmailApplication=true`。当前系统 Mail 正文框正好提供 `privateImeOptions=sbm_y_mail`。iWnn 的事件和 selection 逻辑会读取 `isEmailApplication()` 采取 Mail 专用行为。

iWnn 还识别以下 Mail 私有选项/命令：

- `sbm_y_mail_copy`
- `sbm_y_mail_cut`
- `sbm_y_mail_paste`
- `sbm_y_mail_signature`
- `EditorInfo.extras["enabledSignature"]`

这些与本次中央完成键不是同一个命令，但说明 iWnn 与系统 Mail 之间确实存在一整套私有协议，而非仅依赖 Android 标准 IME API。

## 8. Mail 如何接收完成通知

### 8.1 parser 的具体实现类

接口 `com.access_company.android.androidfp.a.e` 的具体实现是：

```text
com.access_company.android.androidfp.for_kyocera.a.e
```

工厂选择和构造链为：

```text
CustomizedNfcConfiguration.w()
  -> "com.access_company.android.androidfp.for_kyocera.AndroidFpInterfaceFactoryImpl"
  -> Class.forName(...).newInstance()
  -> AndroidFpInterfaceFactoryImpl.d()
  -> singleton com.access_company.android.androidfp.for_kyocera.a.e
```

Mail 的自定义 `EditText` 将收到的 action 交给接口：

```text
com.access_company.android.androidfp.a.e.a(String) -> int 枚举值
```

### 8.2 `a(String)` 的完整字符串映射

方法 `com.access_company.android.androidfp.for_kyocera.a.e.a(String)` 是普通 DEX 方法，不是 JNI。完整映射如下：

| action 字符串 | `g` 枚举 | 整数 | Mail 语义 |
|---|---|---:|---|
| `Finish_IME` | `g.a` | 1 | 完成/退出输入 |
| `com.sbm.android.ime.ACTION_INPUT_MYEMOJI` | `g.b` | 2 | 插入我的绘文字 |
| `sbm_y_mail_signature` | `g.c` | 3 | 插入/处理签名 |
| `sbm_y_mail_fixed_phrase` | `g.d` | 4 | 插入固定短语 |
| `sbm_y_mail_paste` | `g.e` | 5 | 粘贴 |
| `sbm_y_mail_cut` | `g.f` | 6 | 剪切 |
| `sbm_y_mail_copy` | `g.g` | 7 | 复制 |
| 其他任意字符串或 `null` | `g.h` | 8 | 未知/default |

所以：

```text
"IME_User_Action_CSKKey" -> g.h -> default/superclass
"Finish_IME"             -> g.a -> Mail 完成分支
```

`CSKKey handled=true` 只能说明默认/父类链消费了命令，不能说明 Kyocera Mail parser 命中了完成枚举。

### 8.3 当前正文框 `PreImeEditText` 的精确 switch

`com.access_company.android.nfcommunicator.UIUtl.PreImeEditText.onPrivateIMECommand(String, Bundle)` 的流程：

```text
action string
  -> com.access_company.android.androidfp.a.e.a(action)
  -> com.access_company.android.androidfp.a.g 枚举
  -> packed-switch
  -> 已注册的 do/dn listener 或复制/剪切/粘贴处理
```

还原 packed-switch payload 后，枚举到 listener 的映射为：

| `g` 枚举 | `PreImeEditText` 行为 |
|---|---|
| `g.a` | 通常调用 `do.b()`；仅字段 `l=true` 时调用 `do.a()` |
| `g.b` | 从 Bundle 取 URI，调用 `do.a(String)` |
| `g.c` | 调用 `do.c()` |
| `g.d` | 调用 `do.d()` |
| `g.e` | 执行粘贴 |
| `g.f` | 执行剪切 |
| `g.g` | 执行复制后调用 `do.e()` |
| `g.h` | 调父类 `onPrivateIMECommand()` |

字段 `l` 只在 `PreImeEditText.onKeyPreIme()` 收到 `KEYCODE_BACK` 时置为 `true`，正常中央 OK 完成路径为 `false`。因此 `Finish_IME -> g.a` 在正文框通常调用 `do.b()`。

当前 `MailComposerEditActivity` 安装的 `do` listener 是 `com.access_company.android.nfcommunicator.UI.pk`。精确映射为：

- `g.a -> do.b() -> pk.b() -> activity.onBackPressed()`：**真正的正常完成路径**
- `g.a` 且 `l=true -> do.a() -> pk.a() -> MailComposerEditActivity.c(activity)`：BACK pre-IME 特殊路径
- `g.b -> pk.a(String)`：我的绘文字
- `g.c -> pk.c()`：签名命令，读取正文并执行签名相关重建/校验，不是中央完成命令
- `g.d -> pk.d()`：固定短语命令
- `g.g -> pk.e()`：复制完成提示

`MailComposerEditActivity.onBackPressed()` 会读取正文、检查内容是否合法/是否超限，构造包含 `Body`、`MailType`、`MsgId` 等字段的结果 Intent，调用 `setResult(0, intent)` 后 `finish()` 返回上层 composer。这里的 result code 是 `0`（不是 `RESULT_OK`）；上层 composer 依赖返回 Intent 中的正文数据。它才是用户所见“保存输入并退出正文编辑页”的路径。

### 8.4 `DisplayEditText` 与地址编辑框

`jp.kyocera.fpmail.mailgroup.ui.DisplayEditText.onPrivateIMECommand()` 的 packed-switch 只有一个 case：`g.a`。所以同样只有 `Finish_IME` 会调用 `listener.a()`。

其实际 listener 会：

1. 从 EditText 取值
2. 放入结果 Intent 的 `EXTRA_INPUT_TEXT`
3. `setResult(RESULT_OK, intent)`
4. `finish()` 关闭 Activity

`jp.kyocera.fpmail.util.DisplayAddressSwitchEditText` 的 packed-switch 也只有 `g.a`；完成回调会校验/规范化地址，写入结果 Intent，`setResult(RESULT_OK, intent)` 后 `finish()`。

这说明该厂商私有 IME 命令的设计目的就是让功能机 IME 的中央软键驱动“确认当前编辑并返回”，而不是向文本框插入字符。

## 9. 对 WindIme 兼容实现的接口要求

本节是逆向结论导出的实现边界，不是本次代码修改。

### 9.1 必需：Softkey Guide 显示层

在 701KC/兼容机型上，需要：

1. 从 `InputMethodService.getWindow().getWindow()` 取得 IME `Window`
2. 调用 `NfpSoftkeyGuide.getSoftkeyGuide(window)`
3. 空闲且中央 OK 语义为结束输入时调用 `setText(INDEX_CSK, completionText)`
4. 根据实际 UI 状态同步 SK1/SK2 或保留合理默认值
5. 调用 `invalidate()`

需要在至少这些时机刷新：

- `onStartInputView`
- composition 从空变为非空或从非空变为空
- 候选焦点进入/退出
- 符号、短语、菜单等 modal panel 显示/关闭
- 输入模式条进入/退出
- `onFinishInputView` 前按需要恢复/清空，避免标签泄漏到后续窗口

### 9.2 必需：中央 OK 的 Mail 私有完成命令

只在以下条件同时成立时发送：

- 物理动作确实是中央确认键，而不是 Enter
- 当前没有待确认的 composing/cycling 状态
- 当前没有候选、模式选择、符号面板或菜单需要消费 OK
- 当前 OK 的 UI 文案/语义是“完成输入”
- `InputConnection` 非空

精确复刻 iWnn 时，应先标记 active finish 并结束输入视图，再在结束生命周期发送：

```java
Bundle bundle = new Bundle();
bundle.putBoolean("isActiveFinish", true);
inputConnection.performPrivateCommand("Finish_IME", bundle);
```

也可以在中央 OK 的完成分支直接发送 `Finish_IME` 后隐藏 IME；Mail 当前 switch 不读取 Bundle 来选择 `g.a`，但携带 `isActiveFinish=true` 最符合 iWnn 协议。必须防止 `onFinishInputView()` 因普通焦点切换、IME 切换或 BACK 也误发 active finish，因此需要会话级布尔门控并在发送后清零。

对当前系统 Mail，可以用 `privateImeOptions` 包含 `sbm_y_mail` 作为强兼容信号。为减少对未知应用的副作用，应将 `Finish_IME` 限制在该选项或经过验证的厂商应用，而不是对所有 Android 文本框无条件发送。

### 9.3 标准 editor action 与隐藏策略

推荐按 iWnn 的顺序：

1. 完成/提交 WindIme 自己的 composing 文本
2. 标记本次隐藏属于 active finish
3. 可选发送 iWnn 的前置 `IME_User_Action_CSKKey`，但不要依赖其返回值
4. 结束/隐藏输入视图
5. 发送 `Finish_IME` 和 `isActiveFinish=true`
6. 清除 active-finish 标记
7. 对非 `sbm_y_mail` 编辑器，再按标准 action 与 `NO_ENTER_ACTION` 走 Android editor action 策略

注意当前 Mail 的 `imeOptions` 带 `IME_FLAG_NO_ENTER_ACTION`。不能只取低字节 `6` 后无条件执行 Done，更不能插入换行；`sbm_y_mail` 应优先走 `Finish_IME` 厂商协议。

### 9.4 不能作为完整修复的方案

以下方案单独使用都不完整：

- **空闲时 `onKeyDown()` 返回 false**：按键可能回到宿主，但不会替 IME 设置 Guide，也不能保证 Mail 收到私有命令。
- **发送 `KEYCODE_DPAD_CENTER` 或 `KEYCODE_ENTER`**：iWnn 的完成通知走 `performPrivateCommand`，不是普通键事件；Enter 还可能导致换行。
- **只发送 `IME_User_Action_CSKKey`**：Mail parser 将其映射到 `g.h` 默认分支，不保存。
- **只调用 `sendDefaultEditorAction()`/`performEditorAction(6)`**：属于标准 Android 层，不能替代 Mail 的 `Finish_IME` 协议。
- **只设置“完成”文案**：只改变显示，不会保存或退出。
- **始终显示“完成”并始终发送私有命令**：会破坏候选确认、模式选择、符号面板等正常 OK 行为。

### 9.5 兼容和安全边界

- `NfpSoftkeyGuide.getSoftkeyGuide()` 可能返回 `null`，所有反射和返回值必须容错。
- 普通 Android 不存在 `Window.getSoftkeyGuide()`；不应把厂商类作为无条件运行依赖。
- 可选择 Manifest `uses-library required=false` 加反射，或完全反射；若使用 `required=true`，APK 将无法安装到没有该共享库的设备。
- `performPrivateCommand()` 是标准 Android `InputConnection` API，调用本身不需要私有权限；未知宿主通常会忽略不认识的 action，但仍应限定触发场景。
- Softkey Guide API 与 Mail 私有命令相互独立。即使反射加载 Softkey API 失败，仍可在已识别的 Mail 场景发送 `Finish_IME`；反之，Guide 显示成功也不能视为完成命令已处理。

## 10. 建议的验证矩阵

后续实现时至少验证以下状态：

| 场景 | 中央 Guide | 按 OK 的预期 |
|---|---|---|
| Mail 正文空闲 | 完成 | 保存正文并退出编辑页 |
| Mail 正文正在组字 | 确定/候选语义 | 提交当前组字，不退出 |
| Mail 候选聚焦 | 确定 | 选择候选，不退出 |
| Mail 符号/短语面板 | 面板对应文案 | 选择项目，不退出 |
| 普通单行 `actionDone` 且无 `NO_ENTER_ACTION` | 完成 | 标准 `performEditorAction(DONE)` |
| 普通多行文本框 | 非完成或 Enter 语义 | 插入换行，不误发 Mail 完成 |
| 无 `com.nextfp` 的普通 Android | 不崩溃 | 标准 IME 行为正常 |
| IME 切换/输入页关闭 | 不残留旧文案 | 下个窗口 Guide 正常 |

设备侧可用命令：

```powershell
adb shell dumpsys input_method
adb logcat -c
adb logcat -v time | findstr /i "WindIme GarahoIme fpmail InputMethod"
```

`dumpsys input_method` 可以核对 `inputType`、`imeOptions`、`privateImeOptions`、焦点控件和当前 IME，但该固件的 `dumpsys window/input_method/activity` 不输出 Softkey Guide 的当前文本；Guide 文案需要肉眼或屏幕截图确认。

## 11. 证据定位与复现命令

### 11.1 主要类和方法

| 层次 | 类/方法 | 证据 |
|---|---|---|
| Guide 包装器 | `NfpSoftkeyGuide.getSoftkeyGuide(Window)` | `Window.getSoftkeyGuide()`，包装 `KCfpSoftkeyGuide` |
| Guide 更新 | `IWnnImeJaJp.setSoftkeyText(...)` | 设置索引 1、2、0 后 `invalidate()` |
| Guide 状态机 | `IWnnImeJaJp.setSoftKey()` / `setDefaultSoftKey(boolean)` | 按 composition、candidate、selection 状态切换文案 |
| Mail 识别 | `EMailImeOptionManager.init(EditorInfo)` | 检查 `privateImeOptions` 中的 `sbm_y_mail` |
| 中央键第一阶段 | `IWnnImeJaJp.processImeHide()` | 标记 active finish，发送 `CSKKey` 并结束输入 |
| 真正完成命令 | `IWnnLanguageSwitcher.onFinishInputView()` | 发送 `Finish_IME` + `isActiveFinish` |
| 字符串 parser | `for_kyocera.a.e.a(String)` | `Finish_IME -> g.a`；`CSKKey -> g.h` |
| Mail 正文接收 | `PreImeEditText.onPrivateIMECommand()` | `g.a -> pk.b() -> onBackPressed()` |
| 其他编辑页接收 | `DisplayEditText.onPrivateIMECommand()` | `g.a -> setResult()` + `finish()` |

关键 DEX/ODEX 定位：

| 方法/数据 | 定位 |
|---|---|
| `for_kyocera.a.e.a(String)` | `fpmail-oatdump.txt:165295`，逐字符串比较从 `Finish_IME` 开始 |
| `g.a..g.h = 1..8` | `fpmail-oatdump.txt:163909` 附近 |
| `PreImeEditText.onPrivateIMECommand()` | `dex_method_idx=17303`，DEX code item `0x21279C`，ODEX `0x21A788` |
| 外层 packed-switch payload | DEX `0x21299C`，ODEX `0x21A988`，`first_key=1`、`size=7` |
| `MailComposerEditActivity` 注册 `pk` | DEX `0x1AEDD0` 附近，ODEX `0x1B6DBC` 附近 |
| `pk.b()` | `dex_method_idx=16207`，DEX `0x1FED14`，ODEX `0x206D00` |
| `MailComposerEditActivity.onBackPressed()` | `dex_method_idx=11392`，`fpmail-oatdump.txt:220744` |
| `DisplayEditText.onPrivateIMECommand()` | `dex_method_idx=46623`，`fpmail-oatdump.txt:1197397` |
| iWnn `onFinishInputView()` 发 `Finish_IME` | `iwnn-oatdump-nodisasm.txt:139734-139772` |

### 11.2 使用过的核心命令

```powershell
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell pm path jp.co.omronsoft.iwnnime.ml
adb shell dumpsys package jp.co.omronsoft.iwnnime.ml
adb shell dumpsys package jp.kyocera.fpmail
adb shell dumpsys input_method
adb pull /system/app/iWnnIME/arm/iWnnIME.odex <temp>
adb pull /system/framework/arm/com.nextfp.android.util.odex <temp>
aapt dump badging Document/apks/701kc/iWnnIME.apk
aapt dump xmltree Document/apks/701kc/iWnnIME.apk AndroidManifest.xml
aapt dump resources Document/apks/701kc/iWnnIME.apk
```

ODEX 使用设备自带 `oatdump` 输出 DEX CODE 后，按上述类名、方法名和字符串交叉检索。临时逆向产物未写入仓库。

## 12. 最终判断

要在 701KC 系统 Mail 中达到 iWnn 的行为，最低必要实现不是“让 OK 透传”，而是：

```text
空闲状态显示完成
  = NfpSoftkeyGuide.setText(INDEX_CSK, "完成") + invalidate()

中央 OK 完成编辑
  = commit composing
  + activeFinish = true
  + hide/finish input view
  + Bundle{"isActiveFinish": true}
  + InputConnection.performPrivateCommand("Finish_IME", bundle)
  + activeFinish = false
```

其中状态门控是实现正确性的关键：有组字、候选或面板时 OK 仍应由输入法内部消费；只有 iWnn 状态机所称的 active finish 状态，才显示“完成”并发出 `Finish_IME`。`IME_User_Action_CSKKey` 可以作为前置兼容通知保留，但它不是保存命令，也不能用 `handled=true` 判断成功。
