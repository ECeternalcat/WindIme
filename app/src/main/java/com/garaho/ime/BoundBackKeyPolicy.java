package com.garaho.ime;

/** Decision helper for a physical BACK key explicitly mapped as backspace. */
final class BoundBackKeyPolicy {

    private BoundBackKeyPolicy() {
    }

    static boolean shouldHideIme(boolean deleted, boolean editorKnownEmpty) {
        return !deleted && editorKnownEmpty;
    }
}
