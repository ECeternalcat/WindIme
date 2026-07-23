package com.garaho.ime.rime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RimeMaintenanceTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void learningSizeCountsOnlyUserDatabasesAndSyncData() throws Exception {
        File userDir = temporaryFolder.newFolder("rime_user");
        write(new File(userDir, "rime_ice.userdb"), 11);
        File sync = new File(userDir, "sync");
        assertTrue(sync.mkdir());
        write(new File(sync, "snapshot.userdb.txt"), 13);

        write(new File(userDir, "user.yaml"), 17);
        write(new File(userDir, "installation.yaml"), 19);
        write(new File(userDir, "rime_ice.userdb.backup"), 23);
        File build = new File(userDir, "build");
        assertTrue(build.mkdir());
        write(new File(build, "rime_ice.table.bin"), 29);

        assertEquals(24L, RimeMaintenance.learningSize(userDir));
        assertEquals(29L, RimeMaintenance.sizeOf(build));
    }

    @Test
    public void learningEntryMatchingDoesNotIncludeOrdinaryConfiguration() {
        assertTrue(RimeMaintenance.isLearningEntry(new File("rime_ice.userdb")));
        assertTrue(RimeMaintenance.isLearningEntry(new File("sync")));
        assertFalse(RimeMaintenance.isLearningEntry(new File("user.yaml")));
        assertFalse(RimeMaintenance.isLearningEntry(new File("installation.yaml")));
        assertFalse(RimeMaintenance.isLearningEntry(new File("rime_ice.userdb.backup")));
        assertFalse(RimeMaintenance.isLearningEntry(new File("build")));
    }

    @Test
    public void clearingLearningPreservesBuildAndConfiguration() throws Exception {
        File userDir = temporaryFolder.newFolder("clear_learning");
        File userDb = new File(userDir, "rime_ice.userdb");
        File sync = new File(userDir, "sync");
        File build = new File(userDir, "build");
        assertTrue(userDb.mkdir());
        assertTrue(sync.mkdir());
        assertTrue(build.mkdir());
        write(new File(userDb, "data.bin"), 7);
        write(new File(sync, "snapshot.bin"), 11);
        File compiled = new File(build, "rime_ice.table.bin");
        write(compiled, 13);
        File userYaml = new File(userDir, "user.yaml");
        write(userYaml, 17);

        assertTrue(RimeMaintenance.deleteLearningFiles(userDir));

        assertFalse(userDb.exists());
        assertFalse(sync.exists());
        assertTrue(compiled.exists());
        assertTrue(userYaml.exists());
    }

    private static void write(File file, int bytes) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(new byte[bytes]);
        } finally {
            out.close();
        }
    }
}
