package com.garaho.ime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoundBackKeyPolicyTest {

    @Test
    public void keepsImeOpenAfterDeletingText() {
        assertFalse(BoundBackKeyPolicy.shouldHideIme(true, true));
    }

    @Test
    public void keepsImeOpenWhenCursorCannotDeleteButEditorStillHasText() {
        assertFalse(BoundBackKeyPolicy.shouldHideIme(false, false));
    }

    @Test
    public void hidesImeOnlyWhenNothingWasDeletedAndEditorIsEmpty() {
        assertTrue(BoundBackKeyPolicy.shouldHideIme(false, true));
    }
}
