package com.garaho.ime.rime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class RimeDataTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test(expected = IOException.class)
    public void markerWriteFailurePropagates() throws Exception {
        File markerAsDirectory = new File(tmp.getRoot(), ".data_version");
        assertTrue(markerAsDirectory.mkdir());
        RimeData.writeMarker(tmp.getRoot());
    }
}
