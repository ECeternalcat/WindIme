package com.garaho.ime.rime;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RimeIceAssetsTest {

    private static final File RIME = new File("src/main/assets/rime");

    @Test
    public void defaultSelectsRimeIceAndStarterDictionaryIsGone() throws Exception {
        String defaults = read("default.yaml");
        assertTrue(defaults.contains("schema: rime_ice"));
        assertTrue(defaults.contains("schema: rime_ice_en"));
        assertFalse(new File(RIME, "luna_pinyin.dict.yaml").exists());
        assertFalse(new File(RIME, "luna_pinyin.schema.yaml").exists());
    }

    @Test
    public void dictionaryImportsEveryBundledChineseTable() throws Exception {
        String dictionary = read("rime_ice.dict.yaml");
        assertTrue(dictionary.contains("cn_dicts/8105"));
        assertTrue(dictionary.contains("cn_dicts/base"));
        assertTrue(dictionary.contains("cn_dicts/ext"));
        assertTrue(dictionary.contains("cn_dicts/others"));
        assertFalse(dictionary.contains("cn_dicts/tencent"));
        assertFalse(new File(new File(RIME, "cn_dicts"), "tencent.dict.yaml").exists());

        assertTable("8105.dict.yaml", 100_000L);
        assertTable("base.dict.yaml", 10_000_000L);
        assertTable("ext.dict.yaml", 10_000_000L);
        assertTable("others.dict.yaml", 10_000L);
        assertTable(new File(RIME, "en_dicts/en.dict.yaml"), 300_000L);
        assertTable(new File(RIME, "en_dicts/en_ext.dict.yaml"), 40_000L);
    }

    @Test
    public void minimalSchemaAvoidsUnavailableOptionalPlugins() throws Exception {
        String schema = read("rime_ice.schema.yaml");
        assertTrue(schema.contains("dictionary: rime_ice"));
        assertFalse(schema.contains("lua_"));
        assertFalse(schema.contains("simplifier"));
        assertFalse(schema.contains("grammar:"));
        assertTrue(read("rime_ice_en.dict.yaml").contains("en_dicts/en"));
        assertTrue(read("rime_ice_en.schema.yaml").contains("dictionary: rime_ice_en"));
    }

    @Test
    public void sourceAndLicenseNoticesAreBundled() {
        assertTrue(new File(RIME, "RIME_ICE_SOURCE.md").length() > 500L);
        assertTrue(new File(RIME, "LICENSE.rime-ice.txt").length() > 30_000L);
    }

    private static void assertTable(String name, long minimumBytes) {
        assertTable(new File(new File(RIME, "cn_dicts"), name), minimumBytes);
    }

    private static void assertTable(File file, long minimumBytes) {
        String name = file.getName();
        assertTrue(name + " missing or unexpectedly small", file.length() >= minimumBytes);
    }

    private static String read(String name) throws Exception {
        return new String(Files.readAllBytes(new File(RIME, name).toPath()), StandardCharsets.UTF_8);
    }
}
