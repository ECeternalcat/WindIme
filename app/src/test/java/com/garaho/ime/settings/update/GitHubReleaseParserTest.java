package com.garaho.ime.settings.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GitHubReleaseParserTest {

    /** Trimmed but structurally faithful /releases/latest payload. */
    private static final String SAMPLE = "{\n"
            + "  \"url\": \"https://api.github.com/repos/ECeternalcat/WindIme/releases/123\",\n"
            + "  \"html_url\": \"https://github.com/ECeternalcat/WindIme/releases/tag/v0.5.6\",\n"
            + "  \"tag_name\": \"v0.5.6\",\n"
            + "  \"name\": \"v0.5.6\",\n"
            + "  \"draft\": false,\n"
            + "  \"prerelease\": false,\n"
            + "  \"body\": \"Wind IME v0.5.6 \\u66f4\\u65b0\\u65e5\\u5fd7\\n\\n### \\u65b0\\u589e\\n- \\u5faa\\u73af\\u9009\\u97f3\\u6a21\\u5f0f\\n- \\\"quoted\\\" text\",\n"
            + "  \"assets\": [\n"
            + "    {\n"
            + "      \"url\": \"https://api.github.com/repos/ECeternalcat/WindIme/releases/assets/1\",\n"
            + "      \"name\": \"Wind.IME_armabi_v7a_056.apk\",\n"
            + "      \"content_type\": \"application/vnd.android.package-archive\",\n"
            + "      \"size\": 14365513,\n"
            + "      \"browser_download_url\": \"https://github.com/ECeternalcat/WindIme/releases/download/v0.5.6/Wind.IME_armabi_v7a_056.apk\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"tarball_url\": \"https://api.github.com/repos/ECeternalcat/WindIme/tarball/v0.5.6\"\n"
            + "}";

    @Test
    public void parsesTagBodyAndUrls() {
        UpdateInfo info = GitHubReleaseParser.parse(SAMPLE);

        assertNotNull(info);
        assertEquals("v0.5.6", info.tagName);
        assertEquals("0.5.6", info.versionName());
        assertEquals("https://github.com/ECeternalcat/WindIme/releases/tag/v0.5.6",
                info.htmlUrl);
        assertEquals("https://github.com/ECeternalcat/WindIme/releases/download/v0.5.6/Wind.IME_armabi_v7a_056.apk",
                info.apkUrl);
        assertEquals(info.apkUrl, info.downloadUrl());
    }

    @Test
    public void unescapesJsonStrings() {
        UpdateInfo info = GitHubReleaseParser.parse(SAMPLE);

        assertTrue(info.notes.contains("更新日志"));
        assertTrue(info.notes.contains("\n"));
        assertTrue(info.notes.contains("循环选音模式"));
        assertTrue(info.notes.contains("\"quoted\""));
    }

    @Test
    public void apkAssetPickedByExtensionNotOrder() {
        // Non-APK asset first (source zip), APK second: the APK must win.
        String json = "{"
                + "\"tag_name\":\"v0.6.0\","
                + "\"html_url\":\"https://github.com/x/y/releases/tag/v0.6.0\","
                + "\"body\":\"notes\","
                + "\"assets\":["
                + "{\"name\":\"src.zip\",\"browser_download_url\":\"https://host/src.zip\"},"
                + "{\"name\":\"app.APK\",\"browser_download_url\":\"https://host/app.APK\"}"
                + "]}";
        UpdateInfo info = GitHubReleaseParser.parse(json);

        assertEquals("https://host/app.APK", info.apkUrl);
    }

    @Test
    public void noApkAssetFallsBackToHtmlUrl() {
        String json = "{\"tag_name\":\"v0.6.0\",\"html_url\":\"https://h/tag\",\"body\":\"b\"}";
        UpdateInfo info = GitHubReleaseParser.parse(json);

        assertNull(info.apkUrl);
        assertEquals("https://h/tag", info.downloadUrl());
    }

    @Test
    public void unusablePayloadReturnsNull() {
        assertNull(GitHubReleaseParser.parse(null));
        assertNull(GitHubReleaseParser.parse(""));
        assertNull(GitHubReleaseParser.parse("{\"message\":\"Rate limit exceeded\"}"));
        assertNull(GitHubReleaseParser.parse("not json at all"));
    }

    @Test
    public void newerThanUsesVersionName() {
        UpdateInfo info = GitHubReleaseParser.parse(SAMPLE);

        assertTrue(info.isNewerThan("0.5.5"));
        assertFalse(info.isNewerThan("0.5.6"));
        assertFalse(info.isNewerThan("0.6.0"));
    }
}
