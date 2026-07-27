package com.garaho.ime.keymap;

import com.garaho.ime.user.AtomicStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyMapperLoadTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void userSlotLoadRestoresBackupBeforeMissingFileCheck() throws Exception {
        File file = new File(tmp.getRoot(), KeymapSlots.fileName(1));
        File backup = new File(tmp.getRoot(), KeymapSlots.fileName(1) + ".bak");
        String json = "{\"device_profile\":\"restored\",\"version\":1,\"mappings\":[]}";
        AtomicStore.writeAtomic(backup, json.getBytes("UTF-8"));

        KeyMapConfig config = KeyMapper.loadUserFile(file);

        assertEquals("restored", config.deviceProfile);
        assertTrue(file.exists());
        assertFalse(backup.exists());
    }
}
