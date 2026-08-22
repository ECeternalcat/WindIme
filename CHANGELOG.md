# Wind IME 更新日志

## v0.5.8

### 新增

- 在首次使用向导开头加入“使用说明与免责声明”，确认后才进入默认输入法和按键校准步骤。
- 新增“长按收起输入法”按键动作，可在按键设置向导中绑定，适合无法直接使用系统返回键收起输入法的翻盖机。
- 按键映射详情页添加了“长按收起输入法”动作。

### 改进

- 统一输入法编辑器选项掩码的命名，保持原有行为不变，便于后续维护。
- 从设置页单独进入按键绑定时保持原有流程，不再显示首次使用向导专属的免责声明。
- 修复 Rime 选择部分候选词后，剩余拼音被直接上屏的问题；例如输入“适用于”并选择“适用”后，仍可继续选择“于”。

### 说明

- 本版本的 `versionCode` 为 25，`versionName` 为 0.5.8，可覆盖安装在 0.5.7 之上，假如你用的是github里下的安装包的话（x）。
- Rime 词库仍随 APK 提供，不需要联网下载。

## v0.5.8 (English)

### Added

- Added “Usage Notes and Disclaimer” at the beginning of the first-run wizard.
- Added a “Long-press to collapse the input method” key action. It can be bound in the key settings wizard and is useful on flip phones where the system Back key cannot directly hide the keyboard.
- The key-mapping details page has now added the “Long-press to collapse the input method” action.

### Improved

- Standardized the naming of input-editor option masks without changing their behavior, making future maintenance easier.
- Opening key binding separately from the Settings page keeps the original flow and no longer shows the first-run wizard's disclaimer.
- Fixed an issue where selecting a partial Rime candidate committed the remaining pinyin directly to the editor. ( For example, after entering “适用于” and selecting “适用”, users can still select “于”. ) 

### Notes

- This release uses `versionCode` 25 and `versionName` 0.5.8, and can be installed over version 0.5.7.
- The Rime dictionaries are bundled in the APK and do not require a network download.
