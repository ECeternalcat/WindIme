package com.garaho.ime.settings.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReleaseNotesFormatterTest {

    @Test
    public void headingsBecomeBracketedLines() {
        assertEquals("【新增】", ReleaseNotesFormatter.format("### 新增"));
        assertEquals("【新增】", ReleaseNotesFormatter.format("# 新增"));
        assertEquals("【改进】", ReleaseNotesFormatter.format("## 改进 ###"));
    }

    @Test
    public void bulletsBecomeDots() {
        assertEquals("· 循环选音模式", ReleaseNotesFormatter.format("- 循环选音模式"));
        assertEquals("· star item", ReleaseNotesFormatter.format("* star item"));
        assertEquals("  · nested", ReleaseNotesFormatter.format("  - nested"));
    }

    @Test
    public void linksKeepLabelOnly() {
        String md = "- 感谢 [@wairudogisu](https://github.com/wairudogisu)，PR [#1](https://x/pull/1)";
        assertEquals("· 感谢 @wairudogisu，PR #1", ReleaseNotesFormatter.format(md));
    }

    @Test
    public void emphasisAndCodeMarkersStripped() {
        assertEquals("重要 fix", ReleaseNotesFormatter.format("**重要** *fix*"));
        assertEquals("code", ReleaseNotesFormatter.format("`code`"));
    }

    @Test
    public void horizontalRuleBecomesSolidLine() {
        assertEquals("————————————", ReleaseNotesFormatter.format("---"));
        assertEquals("————————————", ReleaseNotesFormatter.format("***"));
    }

    @Test
    public void blankLinesArePreserved() {
        String md = "【新增】\n\n· item\n\n【修复】\n· item2";
        assertEquals(md, ReleaseNotesFormatter.format(md));
    }

    @Test
    public void crlfIsNormalised() {
        assertEquals("【a】\n· b", ReleaseNotesFormatter.format("### a\r\n- b"));
    }

    @Test
    public void realReleaseExcerpt() {
        String md = "Wind IME v0.5.6 更新日志\n\n### 新增\n"
                + "- **循环选音模式**（设置 → 输入设定）：整句分词后逐个音位确认读音。\n"
                + "- 私密输入框独立英文/数字模式（WiFi 密码等）。（感谢 [@wairudogisu](https://github.com/wairudogisu)，PR [#1](https://x/1)）\n";
        String out = ReleaseNotesFormatter.format(md);
        assertEquals("Wind IME v0.5.6 更新日志\n\n【新增】\n"
                + "· 循环选音模式（设置 → 输入设定）：整句分词后逐个音位确认读音。\n"
                + "· 私密输入框独立英文/数字模式（WiFi 密码等）。（感谢 @wairudogisu，PR #1）\n", out);
    }

    @Test
    public void emptyInput() {
        assertEquals("", ReleaseNotesFormatter.format(null));
        assertEquals("", ReleaseNotesFormatter.format(""));
    }
}
