package com.garaho.ime.keymap;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class KeyMapConfigTest {

    @Test
    public void parsesSpecSample() throws JSONException {
        String json = "{\n" +
                "  \"device_profile\": \"Kyocera_KYF31_Preset\",\n" +
                "  \"version\": 1,\n" +
                "  \"mappings\": [\n" +
                "    { \"scan_code\": 2, \"keycode\": 9, \"action\": \"INPUT_KEY_1\" },\n" +
                "    { \"scan_code\": 228, \"keycode\": 0, \"action\": \"TOGGLE_LANG_MODE\" },\n" +
                "    { \"scan_code\": 28, \"keycode\": 66, \"action\": \"CONFIRM_SELECTION\" }\n" +
                "  ]\n" +
                "}";

        KeyMapConfig config = KeyMapConfig.fromJson(json);

        assertEquals("Kyocera_KYF31_Preset", config.deviceProfile);
        assertEquals(1, config.version);
        assertEquals(3, config.mappings.size());

        KeyMapConfig.Mapping m0 = config.mappings.get(0);
        assertEquals(2, m0.scanCode);
        assertEquals(9, m0.keycode);
        assertEquals(InputAction.INPUT_KEY_1, m0.action);

        assertEquals(InputAction.TOGGLE_LANG_MODE, config.mappings.get(1).action);
        assertEquals(InputAction.CONFIRM_SELECTION, config.mappings.get(2).action);
    }

    @Test
    public void unknownActionBecomesNone() throws JSONException {
        String json = "{\"mappings\":[{\"scan_code\":1,\"keycode\":2,\"action\":\"BOGUS_ACTION\"}]}";
        KeyMapConfig config = KeyMapConfig.fromJson(json);
        assertEquals(InputAction.NONE, config.mappings.get(0).action);
    }

    @Test
    public void roundTrip_preservesData() throws JSONException {
        KeyMapConfig config = new KeyMapConfig();
        config.deviceProfile = "Round_Trip";
        config.version = 7;
        config.mappings.add(new KeyMapConfig.Mapping(11, 22, InputAction.NAV_UP));
        config.mappings.add(new KeyMapConfig.Mapping(33, 44, InputAction.SHOW_SYMBOL_PANEL));

        String json = config.toJson();
        KeyMapConfig parsed = KeyMapConfig.fromJson(json);

        assertEquals("Round_Trip", parsed.deviceProfile);
        assertEquals(7, parsed.version);
        assertEquals(2, parsed.mappings.size());
        assertEquals(InputAction.NAV_UP, parsed.mappings.get(0).action);
        assertEquals(22, parsed.mappings.get(0).keycode);
        assertEquals(InputAction.SHOW_SYMBOL_PANEL, parsed.mappings.get(1).action);
        assertTrue(json.contains("\"action\": \"NAV_UP\""));
    }

    @Test
    public void copyIsDeep() {
        KeyMapConfig original = new KeyMapConfig();
        original.deviceProfile = "Original";
        original.mappings.add(new KeyMapConfig.Mapping(10, 20, InputAction.TOGGLE_LANG_MODE));

        KeyMapConfig copy = original.copy();
        copy.deviceProfile = "Copy";
        copy.mappings.get(0).keycode = 99;

        assertNotSame(original, copy);
        assertNotSame(original.mappings.get(0), copy.mappings.get(0));
        assertEquals("Original", original.deviceProfile);
        assertEquals(20, original.mappings.get(0).keycode);
    }

    @Test
    public void mergeReplacesActionAndConflictingPhysicalKey() {
        KeyMapConfig base = new KeyMapConfig();
        base.mappings.add(new KeyMapConfig.Mapping(10, 100, InputAction.TOGGLE_LANG_MODE));
        base.mappings.add(new KeyMapConfig.Mapping(20, 200, InputAction.SHOW_SYMBOL_PANEL));
        base.mappings.add(new KeyMapConfig.Mapping(30, 300, InputAction.BACKSPACE_DELETE));

        java.util.Map<InputAction, KeyMapConfig.Mapping> replacements = new java.util.LinkedHashMap<>();
        replacements.put(InputAction.TOGGLE_LANG_MODE,
                new KeyMapConfig.Mapping(20, 200, InputAction.TOGGLE_LANG_MODE));

        KeyMapConfig merged = KeyMapConfig.merge(base, replacements);

        assertEquals(2, merged.mappings.size());
        assertEquals(InputAction.BACKSPACE_DELETE, merged.mappings.get(0).action);
        assertEquals(InputAction.TOGGLE_LANG_MODE, merged.mappings.get(1).action);
        assertEquals(200, merged.mappings.get(1).keycode);
        assertEquals(3, base.mappings.size());
    }

    @Test
    public void mergeWithNoCapturesPreservesDetachedBase() {
        KeyMapConfig base = new KeyMapConfig();
        base.mappings.add(new KeyMapConfig.Mapping(10, 100, InputAction.TOGGLE_LANG_MODE));

        KeyMapConfig merged = KeyMapConfig.merge(base,
                java.util.Collections.<InputAction, KeyMapConfig.Mapping>emptyMap());
        merged.mappings.get(0).keycode = 999;

        assertEquals(100, base.mappings.get(0).keycode);
    }

    @Test
    public void quickMenuActionRoundTrips() throws JSONException {
        KeyMapConfig config = new KeyMapConfig();
        config.mappings.add(new KeyMapConfig.Mapping(59, 131, InputAction.SHOW_QUICK_MENU));

        KeyMapConfig parsed = KeyMapConfig.fromJson(config.toJson());

        assertEquals(InputAction.SHOW_QUICK_MENU, parsed.mappings.get(0).action);
        assertEquals(59, parsed.mappings.get(0).scanCode);
        assertEquals(131, parsed.mappings.get(0).keycode);
    }
}
