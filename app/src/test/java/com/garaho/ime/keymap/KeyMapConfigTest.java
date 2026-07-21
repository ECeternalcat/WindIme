package com.garaho.ime.keymap;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}
